package com.networkmonitor.db

import cats.effect.IO
import com.networkmonitor.domain.*
import doobie.*
import doobie.implicits.*
import doobie.postgres.implicits.*

import java.time.Instant
import java.util.UUID

class ScanRepository(xa: Transactor[IO]):

  def createScan(): IO[UUID] =
    sql"INSERT INTO scans (started_at) VALUES (now()) RETURNING id"
      .query[UUID].unique.transact(xa)

  def completeScan(scanId: UUID, devicesFound: Int): IO[Unit] =
    sql"UPDATE scans SET completed_at = now(), devices_found = $devicesFound WHERE id = $scanId"
      .update.run.transact(xa).void

  def addScanResult(scanId: UUID, deviceId: UUID, ipAddress: String): IO[Unit] =
    sql"INSERT INTO scan_results (scan_id, device_id, ip_address) VALUES ($scanId, $deviceId, $ipAddress::inet)"
      .update.run.transact(xa).void

  def listScans(limit: Int, offset: Int): IO[List[Scan]] =
    sql"""
      SELECT id, started_at, completed_at, devices_found
      FROM scans
      WHERE completed_at IS NOT NULL
      ORDER BY started_at DESC
      LIMIT $limit OFFSET $offset
    """.query[Scan].to[List].transact(xa)

  def countScans(): IO[Int] =
    sql"SELECT count(*) FROM scans WHERE completed_at IS NOT NULL"
      .query[Int].unique.transact(xa)

  def findById(scanId: UUID): IO[Option[Scan]] =
    sql"SELECT id, started_at, completed_at, devices_found FROM scans WHERE id = $scanId"
      .query[Scan].option.transact(xa)

  def getLatestScan(): IO[Option[Scan]] =
    sql"""
      SELECT id, started_at, completed_at, devices_found
      FROM scans WHERE completed_at IS NOT NULL
      ORDER BY started_at DESC LIMIT 1
    """.query[Scan].option.transact(xa)

  def getDevicesForScan(scanId: UUID): IO[List[Device]] =
    sql"""
      SELECT d.id, d.mac_address::text, d.ip_address::text, d.hostname, d.vendor,
             d.first_seen_at, d.last_seen_at, true as is_active
      FROM devices d
      JOIN scan_results sr ON sr.device_id = d.id
      WHERE sr.scan_id = $scanId
      ORDER BY d.ip_address
    """.query[Device].to[List].transact(xa)
