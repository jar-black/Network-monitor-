package com.networkmonitor

import cats.effect.{IO, IOApp}
import cats.effect.unsafe.implicits.global as catsGlobal
import com.networkmonitor.api.Routes
import com.networkmonitor.config.AppConfig
import com.networkmonitor.db.{DatabaseMigration, DeviceRepository, DoobieTransactor, ScanRepository}
import com.networkmonitor.scanner.{NetworkScanner, ScanScheduler}
import org.apache.pekko.actor.typed.ActorSystem
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.apache.pekko.http.scaladsl.Http
import org.slf4j.LoggerFactory
import pureconfig.*
import pureconfig.generic.derivation.default.*

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.*
import scala.io.StdIn

object Main:

  private val logger = LoggerFactory.getLogger(getClass)

  given ConfigReader[AppConfig]     = ConfigReader.derived
  given ConfigReader[com.networkmonitor.config.HttpConfig]     = ConfigReader.derived
  given ConfigReader[com.networkmonitor.config.DatabaseConfig]  = ConfigReader.derived
  given ConfigReader[com.networkmonitor.config.ScannerConfig]   = ConfigReader.derived

  def main(args: Array[String]): Unit =
    val config = ConfigSource.default.at("network-monitor").loadOrThrow[AppConfig]

    // Run DB migrations
    DatabaseMigration.migrate(config.database).unsafeRunSync()

    // Build transactor and repositories
    val (xa, closeXa) = DoobieTransactor.make(config.database).allocated.unsafeRunSync()
    val deviceRepo = DeviceRepository(xa)
    val scanRepo   = ScanRepository(xa)

    // Pekko actor system
    given system: ActorSystem[Nothing] = ActorSystem(Behaviors.empty, "network-monitor")
    given ExecutionContext = system.executionContext

    // HTTP routes
    val routes = Routes(deviceRepo, scanRepo)
    val bindingFuture = Http().newServerAt(config.http.host, config.http.port).bind(routes.routes)

    bindingFuture.foreach { binding =>
      logger.info(s"Server running at http://${config.http.host}:${config.http.port}/")
    }

    // Start the background scanner
    val scanner   = NetworkScanner(config.scanner)
    val scheduler = ScanScheduler(config.scanner, scanner, deviceRepo, scanRepo)

    // Run scanner in background fiber
    val scannerFiber = scheduler.runForever.unsafeRunAndForget()

    logger.info("Network Monitor started. Press ENTER to stop.")

    // Block until shutdown
    sys.addShutdownHook {
      logger.info("Shutting down...")
      bindingFuture.flatMap(_.unbind()).onComplete { _ =>
        closeXa.unsafeRunSync()
        system.terminate()
      }
    }

    // Keep main thread alive
    Thread.currentThread().join()
