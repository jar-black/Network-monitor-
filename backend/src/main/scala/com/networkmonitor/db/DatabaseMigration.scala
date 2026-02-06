package com.networkmonitor.db

import cats.effect.IO
import com.networkmonitor.config.DatabaseConfig
import org.flywaydb.core.Flyway
import org.slf4j.LoggerFactory

object DatabaseMigration:

  private val logger = LoggerFactory.getLogger(getClass)

  def migrate(config: DatabaseConfig): IO[Unit] =
    IO.blocking {
      logger.info("Running database migrations...")
      val flyway = Flyway.configure()
        .dataSource(config.url, config.user, config.password)
        .locations("classpath:db/migration")
        .load()
      val result = flyway.migrate()
      logger.info(s"Applied ${result.migrationsExecuted} migration(s)")
    }
