package com.networkmonitor.api

import com.networkmonitor.domain.*
import io.circe.*
import io.circe.generic.semiauto.*

import java.time.Instant
import java.util.UUID

object JsonCodecs:

  // ── Encoders ───────────────────────────────────────────
  given Encoder[Device]        = deriveEncoder[Device]
  given Encoder[Scan]          = deriveEncoder[Scan]
  given Encoder[ActivityEntry] = deriveEncoder[ActivityEntry]
  given Encoder[Pagination]    = deriveEncoder[Pagination]

  given Encoder[DeviceListResponse] = deriveEncoder[DeviceListResponse]
  given Encoder[ActivityResponse]   = deriveEncoder[ActivityResponse]
  given Encoder[ScanResultResponse] = deriveEncoder[ScanResultResponse]
  given Encoder[ScanListResponse]   = deriveEncoder[ScanListResponse]
  given Encoder[HealthResponse]     = deriveEncoder[HealthResponse]
  given Encoder[ErrorResponse]      = deriveEncoder[ErrorResponse]

  // ── API response wrappers ──────────────────────────────

  final case class DeviceListResponse(
      data: List[Device],
      pagination: Pagination
  )

  final case class ActivityResponse(
      deviceId: UUID,
      macAddress: String,
      ipAddress: String,
      hostname: Option[String],
      entries: List[ActivityEntry]
  )

  final case class ScanResultResponse(
      id: UUID,
      startedAt: Instant,
      completedAt: Option[Instant],
      devicesFound: Int,
      devices: List[Device]
  )

  final case class ScanListResponse(
      data: List[Scan],
      pagination: Pagination
  )

  final case class HealthResponse(
      status: String,
      timestamp: Instant,
      database: String,
      lastScanAt: Option[Instant]
  )

  final case class ErrorResponse(
      error: String,
      message: String
  )
