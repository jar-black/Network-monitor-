import { useParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { api } from '../api/client'
import { format } from 'date-fns'

function DeviceDetailPage() {
  const { deviceId } = useParams<{ deviceId: string }>()

  const { data: device, isLoading: deviceLoading } = useQuery({
    queryKey: ['device', deviceId],
    queryFn: () => api.getDevice(deviceId!),
    enabled: !!deviceId,
  })

  const { data: activity, isLoading: activityLoading } = useQuery({
    queryKey: ['device-activity', deviceId],
    queryFn: () => api.getDeviceActivity(deviceId!),
    enabled: !!deviceId,
  })

  if (deviceLoading) return <p>Loading device...</p>
  if (!device) return <p>Device not found</p>

  return (
    <div>
      <h1 style={{ fontSize: '1.5rem', marginBottom: '1rem' }}>
        {device.hostname || device.ipAddress}
      </h1>

      {/* Device info card */}
      <div style={{
        background: 'var(--color-surface)',
        borderRadius: '8px',
        border: '1px solid var(--color-border)',
        padding: '1.5rem',
        marginBottom: '2rem',
        display: 'grid',
        gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))',
        gap: '1rem',
      }}>
        <InfoItem label="IP Address" value={device.ipAddress} />
        <InfoItem label="MAC Address" value={device.macAddress} />
        <InfoItem label="Hostname" value={device.hostname || '-'} />
        <InfoItem label="Vendor" value={device.vendor || 'Unknown'} />
        <InfoItem label="First Seen" value={format(new Date(device.firstSeenAt), 'yyyy-MM-dd HH:mm')} />
        <InfoItem label="Last Seen" value={format(new Date(device.lastSeenAt), 'yyyy-MM-dd HH:mm')} />
        <InfoItem
          label="Status"
          value={device.isActive ? 'Active' : 'Inactive'}
          color={device.isActive ? 'var(--color-success)' : 'var(--color-danger)'}
        />
      </div>

      {/* Activity timeline */}
      <h2 style={{ fontSize: '1.2rem', marginBottom: '1rem' }}>Activity (last 24h)</h2>
      {activityLoading ? (
        <p>Loading activity...</p>
      ) : (
        <div style={{
          background: 'var(--color-surface)',
          borderRadius: '8px',
          border: '1px solid var(--color-border)',
          padding: '1.5rem',
        }}>
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: '2px' }}>
            {activity?.entries.map((entry, i) => (
              <div
                key={i}
                title={format(new Date(entry.scanTime), 'HH:mm')}
                style={{
                  width: '8px',
                  height: '32px',
                  borderRadius: '2px',
                  background: entry.active ? 'var(--color-success)' : 'var(--color-border)',
                }}
              />
            ))}
          </div>
          <div style={{
            marginTop: '0.5rem',
            fontSize: '0.8rem',
            color: 'var(--color-text-muted)',
          }}>
            Each bar = 5 minute scan interval. Green = active.
          </div>
        </div>
      )}
    </div>
  )
}

function InfoItem({ label, value, color }: { label: string; value: string; color?: string }) {
  return (
    <div>
      <div style={{ fontSize: '0.8rem', color: 'var(--color-text-muted)', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
        {label}
      </div>
      <div style={{ fontSize: '1rem', fontWeight: 500, color: color || 'var(--color-text)' }}>
        {value}
      </div>
    </div>
  )
}

export default DeviceDetailPage
