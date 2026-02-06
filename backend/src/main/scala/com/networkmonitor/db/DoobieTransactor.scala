package com.networkmonitor.db

import cats.effect.{IO, Resource}
import com.networkmonitor.config.DatabaseConfig
import doobie.hikari.HikariTransactor
import doobie.util.ExecutionContexts

object DoobieTransactor:

  def make(config: DatabaseConfig): Resource[IO, HikariTransactor[IO]] =
    for
      ec <- ExecutionContexts.fixedThreadPool[IO](config.poolSize)
      xa <- HikariTransactor.newHikariTransactor[IO](
        driverClassName = config.driver,
        url             = config.url,
        user            = config.user,
        pass            = config.password,
        connectEC       = ec
      )
    yield xa
