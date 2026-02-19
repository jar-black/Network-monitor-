import sbt._

val ScalaVersion       = "3.5.2"
val PekkoVersion       = "1.1.3"
val PekkoHttpVersion   = "1.1.0"
val CatsEffectVersion  = "3.5.7"
val DoobieVersion      = "1.0.0-RC6"
val CirceVersion       = "0.14.10"
val FlywayVersion      = "10.22.0"
val LogbackVersion     = "1.5.15"
val PureConfigVersion  = "0.17.8"
val ScalaTestVersion   = "3.2.19"

lazy val root = (project in file("."))
  .enablePlugins(JavaAppPackaging, DockerPlugin)
  .settings(
    name         := "network-monitor-backend",
    version      := "0.1.0",
    scalaVersion := ScalaVersion,
    organization := "com.networkmonitor",

    // Compiler options
    scalacOptions ++= Seq(
      "-deprecation",
      "-feature",
      "-unchecked",
      "-Xfatal-warnings"
    ),

    // ── Pekko (actor system & HTTP server) ──────────────────
    libraryDependencies ++= Seq(
      "org.apache.pekko" %% "pekko-actor-typed"    % PekkoVersion,
      "org.apache.pekko" %% "pekko-stream"         % PekkoVersion,
      "org.apache.pekko" %% "pekko-http"           % PekkoHttpVersion,
      "org.apache.pekko" %% "pekko-http-core"      % PekkoHttpVersion,
    ),

    // ── Cats Effect (IO runtime) ────────────────────────────
    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats-effect" % CatsEffectVersion,
    ),

    // ── Doobie (database layer) ─────────────────────────────
    libraryDependencies ++= Seq(
      "org.tpolecat" %% "doobie-core"     % DoobieVersion,
      "org.tpolecat" %% "doobie-postgres" % DoobieVersion,
      "org.tpolecat" %% "doobie-hikari"   % DoobieVersion,
    ),

    // ── Circe (JSON serialization) ──────────────────────────
    libraryDependencies ++= Seq(
      "io.circe" %% "circe-core"    % CirceVersion,
      "io.circe" %% "circe-generic" % CirceVersion,
      "io.circe" %% "circe-parser"  % CirceVersion,
    ),

    // ── Pekko-HTTP Circe integration ────────────────────────
    libraryDependencies ++= Seq(
      "com.github.pjfanning" %% "pekko-http-circe" % "3.0.0",
    ),

    // ── Flyway (DB migrations) ──────────────────────────────
    libraryDependencies ++= Seq(
      "org.flywaydb" % "flyway-core"                % FlywayVersion,
      "org.flywaydb" % "flyway-database-postgresql"  % FlywayVersion,
    ),

    // ── Configuration ───────────────────────────────────────
    libraryDependencies ++= Seq(
      "com.github.pureconfig" %% "pureconfig-core" % PureConfigVersion,
    ),

    // ── Logging ─────────────────────────────────────────────
    libraryDependencies ++= Seq(
      "ch.qos.logback"  % "logback-classic" % LogbackVersion,
      "org.apache.pekko" %% "pekko-slf4j"   % PekkoVersion,
    ),

    // ── Testing ─────────────────────────────────────────────
    libraryDependencies ++= Seq(
      "org.scalatest"    %% "scalatest"             % ScalaTestVersion % Test,
      "org.apache.pekko" %% "pekko-actor-testkit-typed" % PekkoVersion % Test,
      "org.apache.pekko" %% "pekko-http-testkit"    % PekkoHttpVersion % Test,
      "org.tpolecat"     %% "doobie-scalatest"      % DoobieVersion    % Test,
    ),

    // ── Docker settings (for sbt-native-packager) ───────────
    Docker / packageName := "network-monitor-backend",
    Docker / version     := version.value,
    dockerBaseImage      := "eclipse-temurin:21-jre-alpine",
    dockerExposedPorts   := Seq(8001),
    dockerUpdateLatest   := true,
  )
