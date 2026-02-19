import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { api } from '../api/client'
import { formatDistanceToNow } from 'date-fns'

function DashboardPage() {
  const [activeOnly, setActiveOnly] = useState(false)

  const { data, isLoading, error } = useQuery({
    queryKey: ['devices', activeOnly],
    queryFn: () => api.listDevices({ activeOnly }),
  })

  if (isLoading) return <p>Loading devices...</p>
  if (error) return <p>Error loading devices: {(error as Error).message}</p>

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.5rem' }}>
        <h1 style={{ fontSize: '1.5rem' }}>Devices</h1>
        <label style={{ fontSize: '0.9rem', cursor: 'pointer' }}>
          <input
            type="checkbox"
            checked={activeOnly}
            onChange={(e) => setActiveOnly(e.target.checked)}
            style={{ marginRight: '0.5rem' }}
          />
          Active only
        </label>
      </div>

      <div style={{
        background: 'var(--color-surface)',
        borderRadius: '8px',
        border: '1px solid var(--color-border)',
        overflow: 'hidden',
      }}>
        <table>
          <thead>
            <tr>
              <th>Status</th>
              <th>Name</th>
              <th>IP Address</th>
              <th>Hostname</th>
              <th>MAC Address</th>
              <th>Last Seen</th>
            </tr>
          </thead>
          <tbody>
            {data?.data.map((device) => (
              <tr key={device.id}>
                <td>
                  <span style={{
                    display: 'inline-block',
                    width: '10px',
                    height: '10px',
                    borderRadius: '50%',
                    background: device.isActive ? 'var(--color-success)' : 'var(--color-danger)',
                  }} />
                </td>
                <td style={{ color: device.displayName ? 'var(--color-text)' : 'var(--color-text-muted)' }}>
                  {device.displayName ?? device.hostname ?? '-'}
                </td>
                <td>
                  <Link to={`/devices/${device.id}`}>
                    {device.ipAddress}
                  </Link>
                </td>
                <td style={{ color: device.hostname ? 'var(--color-text)' : 'var(--color-text-muted)' }}>
                  {device.hostname || '-'}
                </td>
                <td style={{ fontFamily: 'monospace', fontSize: '0.85rem' }}>
                  {device.macAddress}
                </td>
                <td style={{ color: 'var(--color-text-muted)', fontSize: '0.85rem' }}>
                  {formatDistanceToNow(new Date(device.lastSeenAt), { addSuffix: true })}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {data && (
          <div style={{ padding: '0.75rem 1rem', color: 'var(--color-text-muted)', fontSize: '0.85rem', borderTop: '1px solid var(--color-border)' }}>
            Showing {data.data.length} of {data.pagination.total} devices
          </div>
        )}
      </div>
    </div>
  )
}

export default DashboardPage
