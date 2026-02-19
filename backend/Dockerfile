# ── Stage 1: Build ─────────────────────────────────────────
FROM sbtscala/scala-sbt:eclipse-temurin-21.0.8_9_1.12.2_3.3.7 AS build

WORKDIR /app
COPY project/build.properties project/plugins.sbt project/
COPY build.sbt .
# Cache dependency resolution
RUN sbt update

COPY src/ src/
RUN sbt stage

# ── Stage 2: Runtime ───────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine

RUN apk add --no-cache nmap nmap-scripts arp-scan bash

WORKDIR /app
COPY --from=build /app/target/universal/stage/ ./

EXPOSE 8001
ENTRYPOINT ["bin/network-monitor-backend"]
