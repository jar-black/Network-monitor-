import { useParams } from 'react-router-dom'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { api, getDeviceLabel } from '../api/client'
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
        {getDeviceLabel(device)}
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
        <EditableDisplayName deviceId={deviceId!} currentName={device.displayName} />
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
          <ActivityBar entries={activity?.entries ?? []} />
          <div style={{
            marginTop: '0.5rem',
            fontSize: '0.78rem',
            color: 'var(--color-text-muted)',
          }}>
            Each bar = 5 min interval &nbsp;·&nbsp; <span style={{ color: 'var(--color-success)' }}>green = online</span> &nbsp;·&nbsp; hover for details
          </div>
        </div>
      )}
    </div>
  )
}

function EditableDisplayName({ deviceId, currentName }: { deviceId: string; currentName: string | null }) {
  const queryClient = useQueryClient()
  const [editing, setEditing] = useState(false)
  const [inputValue, setInputValue] = useState(currentName ?? '')

  const mutation = useMutation({
    mutationFn: (name: string | null) => api.updateDeviceName(deviceId, name),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['device', deviceId] })
      queryClient.invalidateQueries({ queryKey: ['devices'] })
      setEditing(false)
    },
  })

  const handleSave = () => {
    const trimmed = inputValue.trim()
    mutation.mutate(trimmed || null)
  }

  const handleCancel = () => {
    setInputValue(currentName ?? '')
    setEditing(false)
  }

  if (editing) {
    return (
      <div>
        <div style={{ fontSize: '0.8rem', color: 'var(--color-text-muted)', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
          Display Name
        </div>
        <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'center', marginTop: '0.25rem' }}>
          <input
            type="text"
            value={inputValue}
            onChange={(e) => setInputValue(e.target.value)}
            onKeyDown={(e) => { if (e.key === 'Enter') handleSave(); if (e.key === 'Escape') handleCancel() }}
            autoFocus
            style={{
              padding: '0.25rem 0.5rem',
              border: '1px solid var(--color-border)',
              borderRadius: '4px',
              background: 'var(--color-bg)',
              color: 'var(--color-text)',
              fontSize: '0.9rem',
              width: '160px',
            }}
            placeholder="e.g. Living Room TV"
          />
          <button onClick={handleSave} disabled={mutation.isPending}
            style={{ padding: '0.25rem 0.75rem', borderRadius: '4px', border: '1px solid var(--color-border)', background: 'var(--color-success)', color: '#fff', cursor: 'pointer', fontSize: '0.8rem' }}>
            Save
          </button>
          <button onClick={handleCancel}
            style={{ padding: '0.25rem 0.75rem', borderRadius: '4px', border: '1px solid var(--color-border)', background: 'transparent', color: 'var(--color-text-muted)', cursor: 'pointer', fontSize: '0.8rem' }}>
            Cancel
          </button>
        </div>
      </div>
    )
  }

  return (
    <div>
      <div style={{ fontSize: '0.8rem', color: 'var(--color-text-muted)', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
        Display Name
      </div>
      <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'center' }}>
        <span style={{ fontSize: '1rem', fontWeight: 500, color: currentName ? 'var(--color-text)' : 'var(--color-text-muted)' }}>
          {currentName ?? 'Not set'}
        </span>
        <button onClick={() => { setInputValue(currentName ?? ''); setEditing(true) }}
          style={{ padding: '0.15rem 0.5rem', borderRadius: '4px', border: '1px solid var(--color-border)', background: 'transparent', color: 'var(--color-text-muted)', cursor: 'pointer', fontSize: '0.75rem' }}>
          Edit
        </button>
      </div>
    </div>
  )
}

function ActivityBar({ entries }: { entries: { scanTime: string; active: boolean }[] }) {
  const [hoveredIndex, setHoveredIndex] = useState<number | null>(null)

  const now = new Date()
  now.setSeconds(0, 0)
  now.setMinutes(Math.floor(now.getMinutes() / 5) * 5)
  const startTime = new Date(now.getTime() - 24 * 60 * 60 * 1000)

  const entryMap = new Map(
    entries.map(e => [
      Math.floor(new Date(e.scanTime).getTime() / (5 * 60 * 1000)),
      e.active,
    ])
  )

  const SLOTS = 288
  const slots = Array.from({ length: SLOTS }, (_, i) => {
    const slotTime = new Date(startTime.getTime() + i * 5 * 60 * 1000)
    const key = Math.floor(slotTime.getTime() / (5 * 60 * 1000))
    return {
      time: slotTime,
      active: entryMap.get(key) ?? false,
      hasData: entryMap.has(key),
    }
  })

  const hourMarks: { slot: number; hour: number }[] = []
  for (let i = 0; i < SLOTS; i++) {
    if (slots[i].time.getMinutes() === 0) {
      hourMarks.push({ slot: i, hour: slots[i].time.getHours() })
    }
  }

  const activeCount = slots.filter(s => s.active).length
  const dataCount = slots.filter(s => s.hasData).length

  return (
    <div>
      {/* Summary line */}
      <div style={{
        display: 'flex',
        justifyContent: 'space-between',
        marginBottom: '0.5rem',
        fontSize: '0.78rem',
        color: 'var(--color-text-muted)',
      }}>
        <span>{format(startTime, 'HH:mm')} — {format(now, 'HH:mm')}</span>
        {dataCount > 0 && (
          <span style={{ color: activeCount > 0 ? 'var(--color-success)' : 'var(--color-text-muted)', fontWeight: 500 }}>
            Online {Math.round((activeCount / dataCount) * 100)}% of monitored time
          </span>
        )}
      </div>

      {/* Bars — all 288 slots, full width, no scroll */}
      <div style={{ display: 'flex', gap: '1px', height: '28px' }}>
        {slots.map((slot, i) => (
          <div
            key={i}
            onMouseEnter={() => setHoveredIndex(i)}
            onMouseLeave={() => setHoveredIndex(null)}
            title={`${format(slot.time, 'HH:mm')} — ${slot.active ? 'Online' : 'Offline'}`}
            style={{
              flex: 1,
              borderRadius: '1px',
              background: slot.active
                ? 'var(--color-success)'
                : slot.hasData
                ? 'rgba(128,128,128,0.22)'
                : 'rgba(128,128,128,0.08)',
              position: 'relative',
              opacity: hoveredIndex === i ? 0.65 : 1,
              transition: 'opacity 0.08s',
              cursor: 'default',
            }}
          >
            {hoveredIndex === i && (
              <div style={{
                position: 'absolute',
                bottom: 'calc(100% + 6px)',
                left: '50%',
                transform: 'translateX(-50%)',
                background: 'var(--color-text)',
                color: 'var(--color-bg)',
                padding: '4px 8px',
                borderRadius: '4px',
                fontSize: '0.75rem',
                whiteSpace: 'nowrap',
                zIndex: 10,
                pointerEvents: 'none',
              }}>
                {format(slot.time, 'HH:mm')} — {slot.active ? 'Online' : 'Offline'}
              </div>
            )}
          </div>
        ))}
      </div>

      {/* Hour axis with tick marks and 0–23 labels */}
      <div style={{ position: 'relative', height: '22px', marginTop: '2px' }}>
        {hourMarks.map(({ slot, hour }) => (
          <div
            key={hour}
            style={{
              position: 'absolute',
              left: `${(slot / SLOTS) * 100}%`,
              top: 0,
              display: 'flex',
              flexDirection: 'column',
              alignItems: 'center',
            }}
          >
            <div style={{
              width: '1px',
              height: '4px',
              background: 'var(--color-text-muted)',
              opacity: 0.5,
            }} />
            <span style={{
              fontSize: '0.6rem',
              color: 'var(--color-text-muted)',
              transform: 'translateX(-50%)',
              userSelect: 'none',
              marginTop: '1px',
            }}>
              {hour}
            </span>
          </div>
        ))}
      </div>
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
