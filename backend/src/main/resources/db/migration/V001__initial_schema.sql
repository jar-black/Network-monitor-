-- V001__initial_schema.sql
-- Network Monitor - Initial database schema
-- Managed by Flyway migrations

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-----------------------------------------------------------
-- Devices: one row per unique MAC address seen on the network
-----------------------------------------------------------
CREATE TABLE devices (
    id           UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    mac_address  MACADDR    NOT NULL UNIQUE,
    ip_address   INET       NOT NULL,
    hostname     TEXT,
    vendor       TEXT,
    first_seen_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_seen_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_devices_mac_address ON devices (mac_address);
CREATE INDEX idx_devices_last_seen   ON devices (last_seen_at DESC);

-----------------------------------------------------------
-- Scans: one row per 5-minute network scan execution
-----------------------------------------------------------
CREATE TABLE scans (
    id            UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    started_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at  TIMESTAMPTZ,
    devices_found INTEGER     NOT NULL DEFAULT 0
);

CREATE INDEX idx_scans_started_at ON scans (started_at DESC);

-----------------------------------------------------------
-- Scan results: junction table linking scans to devices
-- Each row means "this device was seen in this scan"
-----------------------------------------------------------
CREATE TABLE scan_results (
    scan_id    UUID NOT NULL REFERENCES scans(id) ON DELETE CASCADE,
    device_id  UUID NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    ip_address INET NOT NULL,
    PRIMARY KEY (scan_id, device_id)
);

CREATE INDEX idx_scan_results_device ON scan_results (device_id, scan_id);
