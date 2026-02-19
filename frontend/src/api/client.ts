const BASE_URL = '/api/v1'

async function fetchJson<T>(path: string, params?: Record<string, string>): Promise<T> {
  const url = new URL(path, window.location.origin)
  url.pathname = `${BASE_URL}${path}`
  if (params) {
    Object.entries(params).forEach(([key, value]) => {
      if (value !== undefined && value !== '') {
        url.searchParams.set(key, value)
      }
    })
  }
  const response = await fetch(url.toString())
  if (!response.ok) {
    const error = await response.json().catch(() => ({ message: response.statusText }))
    throw new Error(error.message || `HTTP ${response.status}`)
  }
  return response.json()
}

async function mutateJson<T>(path: string, method: string, body?: unknown): Promise<T> {
  const url = `${BASE_URL}${path}`
  const response = await fetch(url, {
    method,
    headers: { 'Content-Type': 'application/json' },
    body: body !== undefined ? JSON.stringify(body) : undefined,
  })
  if (!response.ok) {
    const error = await response.json().catch(() => ({ message: response.statusText }))
    throw new Error(error.message || `HTTP ${response.status}`)
  }
  return response.json()
}

export function getDeviceLabel(device: Device): string {
  return device.displayName ?? device.hostname ?? device.ipAddress
}

export interface Device {
  id: string
  macAddress: string
  ipAddress: string
  hostname: string | null
  displayName: string | null
  vendor: string | null
  firstSeenAt: string
  lastSeenAt: string
  isActive: boolean
}

export interface Pagination {
  total: number
  limit: number
  offset: number
}

export interface DeviceListResponse {
  data: Device[]
  pagination: Pagination
}

export interface ActivityEntry {
  scanTime: string
  active: boolean
}

export interface ActivityResponse {
  deviceId: string
  macAddress: string
  ipAddress: string
  hostname: string | null
  entries: ActivityEntry[]
}

export interface ScanSummary {
  id: string
  startedAt: string
  completedAt: string
  devicesFound: number
}

export interface ScanListResponse {
  data: ScanSummary[]
  pagination: Pagination
}

export interface HealthStatus {
  status: string
  timestamp: string
  database: string
  lastScanAt: string | null
}

// ── API functions ────────────────────────────────────

export const api = {
  getHealth: () =>
    fetchJson<HealthStatus>('/health'),

  listDevices: (params?: { activeOnly?: boolean; since?: string; until?: string; limit?: number; offset?: number }) =>
    fetchJson<DeviceListResponse>('/devices', {
      ...(params?.activeOnly !== undefined && { activeOnly: String(params.activeOnly) }),
      ...(params?.since && { since: params.since }),
      ...(params?.until && { until: params.until }),
      ...(params?.limit !== undefined && { limit: String(params.limit) }),
      ...(params?.offset !== undefined && { offset: String(params.offset) }),
    }),

  getDevice: (deviceId: string) =>
    fetchJson<Device>(`/devices/${deviceId}`),

  getDeviceActivity: (deviceId: string, params?: { since?: string; until?: string }) =>
    fetchJson<ActivityResponse>(`/devices/${deviceId}/activity`, {
      ...(params?.since && { since: params.since }),
      ...(params?.until && { until: params.until }),
    }),

  listScans: (params?: { limit?: number; offset?: number }) =>
    fetchJson<ScanListResponse>('/scans', {
      ...(params?.limit !== undefined && { limit: String(params.limit) }),
      ...(params?.offset !== undefined && { offset: String(params.offset) }),
    }),

  updateDeviceName: (deviceId: string, displayName: string | null) =>
    mutateJson<Device>(`/devices/${deviceId}`, 'PATCH', { displayName }),
}
