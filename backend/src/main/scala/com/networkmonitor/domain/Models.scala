package com.networkmonitor.domain

import java.time.Instant
import java.util.UUID

final case class Device(
    id: UUID,
    macAddress: String,
    ipAddress: String,
    hostname: Option[String],
    displayName: Option[String],
    vendor: Option[String],
    firstSeenAt: Instant,
    lastSeenAt: Instant,
    isActive: Boolean
)

final case class UpdateDeviceRequest(displayName: Option[String])

final case class Scan(
    id: UUID,
    startedAt: Instant,
    completedAt: Option[Instant],
    devicesFound: Int
)

final case class ScanResult(
    scanId: UUID,
    deviceId: UUID,
    ipAddress: String
)

/** Raw result from network scanning before DB persistence. */
final case class DiscoveredHost(
    ipAddress: String,
    macAddress: String,
    hostname: Option[String]
)

final case class ActivityEntry(
    scanTime: Instant,
    active: Boolean
)

final case class Pagination(
    total: Int,
    limit: Int,
    offset: Int
)
