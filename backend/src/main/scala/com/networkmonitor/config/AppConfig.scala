package com.networkmonitor.config

import scala.concurrent.duration.FiniteDuration

final case class AppConfig(
    http: HttpConfig,
    database: DatabaseConfig,
    scanner: ScannerConfig
)

final case class HttpConfig(
    host: String,
    port: Int
)

final case class DatabaseConfig(
    driver: String,
    url: String,
    user: String,
    password: String,
    poolSize: Int
)

final case class ScannerConfig(
    networkCidr: String,
    interval: FiniteDuration,
    pingTimeoutMs: Int
)
