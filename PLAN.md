# Network Monitor - Implementation Plan

A local network monitoring system that runs on a Raspberry Pi 4, scanning the network every 5 minutes and displaying active devices with their activity history via a web UI.

## Architecture Overview

```
┌──────────────────────────────────────────────────────────────┐
│  Raspberry Pi 4                                              │
│                                                              │
│  ┌─────────────┐    ┌─────────────┐    ┌──────────────────┐ │
│  │   React UI  │───▶│  Pekko HTTP │───▶│   PostgreSQL 16  │ │
│  │  (Nginx)    │    │  (Scala 3)  │    │                  │ │
│  │  Port 3000  │    │  Port 8080  │    │   Port 5432      │ │
│  └─────────────┘    └──────┬──────┘    └──────────────────┘ │
│                            │                                 │
│                     ┌──────▼──────┐                          │
│                     │  Network    │                          │
│                     │  Scanner    │                          │
│                     │ (nmap/arp)  │                          │
│                     └─────────────┘                          │
│                            │                                 │
│                      LAN (192.168.x.0/24)                    │
└──────────────────────────────────────────────────────────────┘
```

## Technology Stack

| Layer       | Technology                                   |
|-------------|----------------------------------------------|
| Frontend    | React 19, TypeScript, Vite, React Query       |
| API Server  | Scala 3.5, Apache Pekko HTTP 1.1              |
| Effects     | Cats Effect 3                                 |
| DB Access   | Doobie 1.0 (with HikariCP)                    |
| Serialization | Circe (JSON)                               |
| Database    | PostgreSQL 16                                 |
| Migrations  | Flyway 10                                     |
| Packaging   | Docker, Docker Compose                        |
| Scanner     | nmap (primary), arp fallback                  |

## Project Structure

```
Network-monitor-/
├── PLAN.md                        # This file
├── docker-compose.yml             # Orchestrates all services
├── .gitignore
│
├── openapi/
│   └── network-monitor-api.yaml   # OpenAPI 3.1 specification
│
├── db/
│   └── migrations/
│       └── V001__initial_schema.sql
│
├── backend/
│   ├── Dockerfile
│   ├── build.sbt
│   ├── project/
│   │   ├── build.properties
│   │   └── plugins.sbt
│   └── src/main/
│       ├── resources/
│       │   ├── application.conf
│       │   └── logback.xml
│       └── scala/com/networkmonitor/
│           ├── Main.scala
│           ├── config/
│           │   └── AppConfig.scala
│           ├── domain/
│           │   └── Models.scala
│           ├── db/
│           │   ├── DatabaseMigration.scala
│           │   ├── DoobieTransactor.scala
│           │   ├── DeviceRepository.scala
│           │   └── ScanRepository.scala
│           ├── scanner/
│           │   ├── NetworkScanner.scala
│           │   └── ScanScheduler.scala
│           └── api/
│               ├── JsonCodecs.scala
│               └── Routes.scala
│
└── frontend/
    ├── Dockerfile
    ├── nginx.conf
    ├── package.json
    ├── tsconfig.json
    ├── vite.config.ts
    ├── index.html
    └── src/
        ├── main.tsx
        ├── App.tsx
        ├── index.css
        ├── vite-env.d.ts
        ├── api/
        │   └── client.ts
        ├── components/
        │   └── Layout.tsx
        └── pages/
            ├── DashboardPage.tsx
            ├── DeviceDetailPage.tsx
            └── ScansPage.tsx
```

---

## Implementation Phases

### Phase 1: OpenAPI Specification (done)

The API contract is defined first in `openapi/network-monitor-api.yaml`. All endpoints:

| Method | Path                          | Description                        |
|--------|-------------------------------|------------------------------------|
| GET    | /api/v1/health                | Service health + DB status         |
| GET    | /api/v1/devices               | List devices (filter, paginate)    |
| GET    | /api/v1/devices/{id}          | Single device detail               |
| GET    | /api/v1/devices/{id}/activity | 5-min activity timeline            |
| GET    | /api/v1/scans                 | Scan history (paginated)           |
| GET    | /api/v1/scans/latest          | Most recent scan + devices         |
| GET    | /api/v1/scans/{id}            | Specific scan result               |

### Phase 2: Database Schema (done)

Three tables in PostgreSQL:

- **`devices`** — one row per unique MAC address; upserted on every scan
  - Stores IP, hostname (DNS reverse lookup), vendor (OUI), first/last seen timestamps
- **`scans`** — one row per scan execution (every 5 min)
  - Tracks start/complete time and device count
- **`scan_results`** — junction table (scan_id, device_id)
  - Records which devices were seen in which scan; this is the activity data

### Phase 3: Backend Implementation

#### 3a. Configuration & Startup (`Main.scala`, `AppConfig.scala`)
- Load config from `application.conf` with env-var overrides
- Run Flyway migrations on startup
- Build HikariCP connection pool via Doobie
- Start Pekko HTTP server
- Launch scanner fiber in background using Cats Effect

#### 3b. Network Scanner (`NetworkScanner.scala`, `ScanScheduler.scala`)
- **Primary method**: `nmap -sn <CIDR>` — fast ping scan that returns IP, MAC, and hostname
- **Fallback**: Parse `arp -a` table after a ping sweep
- Runs as a Cats Effect `IO` loop with `IO.sleep(5.minutes)` between cycles
- Each scan:
  1. Create a `scans` row (started_at = now)
  2. Execute nmap/arp scan
  3. For each discovered host: upsert into `devices`, insert into `scan_results`
  4. Update `scans` row (completed_at = now, devices_found = N)

#### 3c. Doobie Repositories (`DeviceRepository.scala`, `ScanRepository.scala`)
- All queries use `doobie.Fragment` composition for dynamic filtering
- `ON CONFLICT (mac_address) DO UPDATE` for idempotent device upsert
- Activity query: join `scans` with `scan_results` to build the 5-minute timeline

#### 3d. Pekko HTTP Routes (`Routes.scala`, `JsonCodecs.scala`)
- Route tree matches the OpenAPI spec
- Circe codecs derived semi-automatically
- IO results bridged to Pekko via `unsafeRunSync()` (acceptable for this scale)
- CORS headers should be added for dev mode

### Phase 4: Frontend Implementation

#### 4a. Dashboard (`DashboardPage.tsx`)
- Table of all devices: status indicator, IP, hostname, MAC, last seen
- Toggle to filter "active only" (devices seen in the latest scan)
- Auto-refreshes via React Query (every 60s)

#### 4b. Device Detail (`DeviceDetailPage.tsx`)
- Full device info card (IP, MAC, hostname, vendor, first/last seen)
- Activity timeline visualization: a bar for each 5-minute scan slot
  - Green = device was active, gray = inactive
  - Covers last 24 hours (288 bars)

#### 4c. Scan History (`ScansPage.tsx`)
- Table of recent scans: time, devices found, scan duration
- Paginated

### Phase 5: Docker & Deployment

#### Docker Compose services:
1. **db** — PostgreSQL 16 Alpine, persistent volume, healthcheck
2. **backend** — Multi-stage build (sbt compile → JRE runtime + nmap)
3. **frontend** — Multi-stage build (npm build → Nginx serving static files)

#### Raspberry Pi 4 considerations:
- Use `linux/arm64` compatible base images (Alpine, Temurin JRE)
- Backend needs `NET_RAW` + `NET_ADMIN` capabilities and `network_mode: host` for ARP/nmap scanning
- PostgreSQL pool size kept small (4) to conserve RAM
- JVM heap should be limited: add `-Xmx512m` in production
- nmap + arp-scan packages installed in the backend container

---

## Raspberry Pi Deployment Steps

```bash
# 1. Install Docker on the Pi
curl -fsSL https://get.docker.com | sh
sudo usermod -aG docker $USER

# 2. Clone the repo
git clone <repo-url> ~/network-monitor
cd ~/network-monitor

# 3. Configure the network CIDR (edit .env or docker-compose.yml)
# Set SCAN_NETWORK_CIDR to your local subnet, e.g. 192.168.1.0/24

# 4. Start everything
docker compose up -d

# 5. Access the UI
# Open http://<raspberry-pi-ip>:3000 in your browser
```

---

## Next Steps (implementation order)

1. **Get the backend compiling** — run `sbt compile` and fix any issues
2. **Test DB migrations** — run `docker compose up db` and apply Flyway
3. **Test the scanner** — run nmap manually, verify parsing
4. **Wire up API routes** — verify with curl / Postman against the OpenAPI spec
5. **Build the frontend** — `npm install && npm run dev`, connect to live API
6. **Integration test** — full `docker compose up`, verify end-to-end
7. **Deploy to Pi** — build ARM64 images, test on real hardware

---

## Environment Variables

| Variable             | Default                                     | Description                  |
|----------------------|---------------------------------------------|------------------------------|
| `HTTP_HOST`          | `0.0.0.0`                                   | Bind address                 |
| `HTTP_PORT`          | `8080`                                       | HTTP port                    |
| `DATABASE_URL`       | `jdbc:postgresql://localhost:5432/network_monitor` | JDBC URL              |
| `DATABASE_USER`      | `netmon`                                     | DB username                  |
| `DATABASE_PASSWORD`  | `netmon`                                     | DB password                  |
| `SCAN_NETWORK_CIDR`  | `192.168.1.0/24`                             | Network to scan              |
| `SCAN_PING_TIMEOUT_MS` | `1000`                                     | Per-host ping timeout (ms)   |
