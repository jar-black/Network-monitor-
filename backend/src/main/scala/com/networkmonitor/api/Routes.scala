package com.networkmonitor.api

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import com.github.pjfanning.pekkohttpcirce.FailFastCirceSupport.*
import com.networkmonitor.api.JsonCodecs.{*, given}
import com.networkmonitor.db.{DeviceRepository, ScanRepository}
import com.networkmonitor.domain.*
import io.circe.syntax.*
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.Directives.*
import org.apache.pekko.http.scaladsl.server.{ExceptionHandler, Route}

import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

class Routes(
    deviceRepo: DeviceRepository,
    scanRepo: ScanRepository
):

  private def runIO[A](io: IO[A]): A = io.unsafeRunSync()

  private val exceptionHandler = ExceptionHandler {
    case e: Exception =>
      complete(StatusCodes.InternalServerError -> ErrorResponse("internal_error", e.getMessage))
  }

  val routes: Route = handleExceptions(exceptionHandler) {
    pathPrefix("api" / "v1") {
      concat(
        healthRoute,
        devicesRoutes,
        scansRoutes
      )
    }
  }

  // ── Health ──────────────────────────────────────────────
  private def healthRoute: Route =
    path("health") {
      get {
        val health = runIO {
          for
            latestScan <- scanRepo.getLatestScan()
          yield HealthResponse(
            status     = "healthy",
            timestamp  = Instant.now(),
            database   = "connected",
            lastScanAt = latestScan.flatMap(_.completedAt)
          )
        }
        complete(health)
      }
    }

  // ── Devices ─────────────────────────────────────────────
  private def devicesRoutes: Route =
    pathPrefix("devices") {
      concat(
        pathEnd {
          get {
            parameters(
              "activeOnly".as[Boolean].withDefault(false),
              "since".optional,
              "until".optional,
              "limit".as[Int].withDefault(100),
              "offset".as[Int].withDefault(0)
            ) { (activeOnly, sinceParam, untilParam, limit, offset) =>
              val since = sinceParam.map(Instant.parse).getOrElse(Instant.now().minus(24, ChronoUnit.HOURS))
              val until = untilParam.map(Instant.parse).getOrElse(Instant.now())
              val result = runIO {
                for
                  devices <- deviceRepo.findAll(activeOnly, since, until, limit, offset)
                  total   <- deviceRepo.countAll(activeOnly, since, until)
                yield DeviceListResponse(devices, Pagination(total, limit, offset))
              }
              complete(result)
            }
          }
        },
        path(JavaUUID) { deviceId =>
          concat(
            get {
              runIO(deviceRepo.findById(deviceId)) match
                case Some(device) => complete(device)
                case None         => complete(StatusCodes.NotFound -> ErrorResponse("not_found", "Device not found"))
            },
            patch {
              entity(as[UpdateDeviceRequest]) { req =>
                val updated = runIO {
                  for
                    success <- deviceRepo.updateDisplayName(deviceId, req.displayName)
                    device  <- if success then deviceRepo.findById(deviceId) else IO.pure(None)
                  yield device
                }
                updated match
                  case Some(device) => complete(device)
                  case None         => complete(StatusCodes.NotFound -> ErrorResponse("not_found", "Device not found"))
              }
            }
          )
        },
        path(JavaUUID / "activity") { deviceId =>
          get {
            parameters("since".optional, "until".optional) { (sinceParam, untilParam) =>
              val since = sinceParam.map(Instant.parse).getOrElse(Instant.now().minus(24, ChronoUnit.HOURS))
              val until = untilParam.map(Instant.parse).getOrElse(Instant.now())
              runIO(deviceRepo.findById(deviceId)) match
                case None => complete(StatusCodes.NotFound -> ErrorResponse("not_found", "Device not found"))
                case Some(device) =>
                  val activity = runIO(deviceRepo.getActivity(deviceId, since, until))
                  complete(ActivityResponse(
                    deviceId   = device.id,
                    macAddress = device.macAddress,
                    ipAddress  = device.ipAddress,
                    hostname   = device.hostname,
                    entries    = activity
                  ))
            }
          }
        }
      )
    }

  // ── Scans ───────────────────────────────────────────────
  private def scansRoutes: Route =
    pathPrefix("scans") {
      concat(
        pathEnd {
          get {
            parameters("limit".as[Int].withDefault(100), "offset".as[Int].withDefault(0)) { (limit, offset) =>
              val result = runIO {
                for
                  scans <- scanRepo.listScans(limit, offset)
                  total <- scanRepo.countScans()
                yield ScanListResponse(scans, Pagination(total, limit, offset))
              }
              complete(result)
            }
          }
        },
        path("latest") {
          get {
            runIO(scanRepo.getLatestScan()) match
              case None => complete(StatusCodes.NotFound -> ErrorResponse("not_found", "No scans found"))
              case Some(scan) =>
                val devices = runIO(scanRepo.getDevicesForScan(scan.id))
                complete(ScanResultResponse(scan.id, scan.startedAt, scan.completedAt, scan.devicesFound, devices))
          }
        },
        path(JavaUUID) { scanId =>
          get {
            runIO(scanRepo.findById(scanId)) match
              case None => complete(StatusCodes.NotFound -> ErrorResponse("not_found", "Scan not found"))
              case Some(scan) =>
                val devices = runIO(scanRepo.getDevicesForScan(scan.id))
                complete(ScanResultResponse(scan.id, scan.startedAt, scan.completedAt, scan.devicesFound, devices))
          }
        }
      )
    }
