package com.networkmonitor.scanner

import cats.effect.IO
import cats.implicits.*
import com.networkmonitor.config.ScannerConfig
import com.networkmonitor.db.{DeviceRepository, ScanRepository}
import org.slf4j.LoggerFactory

import scala.concurrent.duration.*

/** Schedules periodic network scans and persists results. */
class ScanScheduler(
    config: ScannerConfig,
    scanner: NetworkScanner,
    deviceRepo: DeviceRepository,
    scanRepo: ScanRepository
):

  private val logger = LoggerFactory.getLogger(getClass)

  /** Execute a single scan cycle: scan network, upsert devices, record results. */
  def runOnce: IO[Unit] =
    for
      _         <- IO(logger.info("Starting scheduled network scan"))
      scanId    <- scanRepo.createScan()
      hosts     <- scanner.scan
      _         <- IO(logger.info(s"Discovered ${hosts.size} host(s)"))
      _         <- hosts.traverse_ { host =>
        for
          deviceId <- deviceRepo.upsertDevice(host.macAddress, host.ipAddress, host.hostname)
          _        <- scanRepo.addScanResult(scanId, deviceId, host.ipAddress)
        yield ()
      }
      _         <- scanRepo.completeScan(scanId, hosts.size)
      _         <- IO(logger.info(s"Scan $scanId completed with ${hosts.size} device(s)"))
    yield ()

  /** Run scans in a loop forever with the configured interval. */
  def runForever: IO[Nothing] =
    (runOnce.handleErrorWith(e =>
      IO(logger.error(s"Scan failed: ${e.getMessage}", e))
    ) *> IO.sleep(config.interval)).foreverM
