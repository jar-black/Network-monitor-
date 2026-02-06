package com.networkmonitor.db

import cats.effect.IO
import com.networkmonitor.domain.*
import doobie.*
import doobie.implicits.*
import doobie.postgres.implicits.*

import java.time.Instant
import java.util.UUID

class DeviceRepository(xa: Transactor[IO]):

  def findAll(activeOnly: Boolean, since: Instant, until: Instant, limit: Int, offset: Int): IO[List[Device]] =
    val baseQuery = fr"""
      SELECT d.id, d.mac_address::text, d.ip_address::text, d.hostname, d.vendor,
             d.first_seen_at, d.last_seen_at,
             EXISTS(
               SELECT 1 FROM scan_results sr
               JOIN scans s ON s.id = sr.scan_id
               WHERE sr.device_id = d.id
               AND s.started_at = (SELECT MAX(started_at) FROM scans WHERE completed_at IS NOT NULL)
             ) as is_active
      FROM devices d
      WHERE d.last_seen_at >= $since AND d.last_seen_at <= $until
    """
    val activeFilter = if activeOnly then fr"AND EXISTS(SELECT 1 FROM scan_results sr JOIN scans s ON s.id = sr.scan_id WHERE sr.device_id = d.id AND s.started_at = (SELECT MAX(started_at) FROM scans WHERE completed_at IS NOT NULL))" else fr""
    val q = baseQuery ++ activeFilter ++ fr"ORDER BY d.last_seen_at DESC LIMIT $limit OFFSET $offset"
    q.query[Device].to[List].transact(xa)

  def countAll(activeOnly: Boolean, since: Instant, until: Instant): IO[Int] =
    val baseQuery = fr"SELECT count(*) FROM devices d WHERE d.last_seen_at >= $since AND d.last_seen_at <= $until"
    val activeFilter = if activeOnly then fr"AND EXISTS(SELECT 1 FROM scan_results sr JOIN scans s ON s.id = sr.scan_id WHERE sr.device_id = d.id AND s.started_at = (SELECT MAX(started_at) FROM scans WHERE completed_at IS NOT NULL))" else fr""
    (baseQuery ++ activeFilter).query[Int].unique.transact(xa)

  def findById(id: UUID): IO[Option[Device]] =
    sql"""
      SELECT d.id, d.mac_address::text, d.ip_address::text, d.hostname, d.vendor,
             d.first_seen_at, d.last_seen_at,
             EXISTS(
               SELECT 1 FROM scan_results sr
               JOIN scans s ON s.id = sr.scan_id
               WHERE sr.device_id = d.id
               AND s.started_at = (SELECT MAX(started_at) FROM scans WHERE completed_at IS NOT NULL)
             ) as is_active
      FROM devices d WHERE d.id = $id
    """.query[Device].option.transact(xa)

  def getActivity(deviceId: UUID, since: Instant, until: Instant): IO[List[ActivityEntry]] =
    sql"""
      SELECT s.started_at,
             EXISTS(SELECT 1 FROM scan_results sr WHERE sr.scan_id = s.id AND sr.device_id = $deviceId) as active
      FROM scans s
      WHERE s.completed_at IS NOT NULL
        AND s.started_at >= $since
        AND s.started_at <= $until
      ORDER BY s.started_at ASC
    """.query[ActivityEntry].to[List].transact(xa)

  def upsertDevice(mac: String, ip: String, hostname: Option[String]): IO[UUID] =
    sql"""
      INSERT INTO devices (mac_address, ip_address, hostname, first_seen_at, last_seen_at)
      VALUES ($mac::macaddr, $ip::inet, $hostname, now(), now())
      ON CONFLICT (mac_address) DO UPDATE
        SET ip_address = EXCLUDED.ip_address,
            hostname = COALESCE(EXCLUDED.hostname, devices.hostname),
            last_seen_at = now()
      RETURNING id
    """.query[UUID].unique.transact(xa)
