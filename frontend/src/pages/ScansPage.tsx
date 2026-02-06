import { useQuery } from '@tanstack/react-query'
import { api } from '../api/client'
import { format } from 'date-fns'

function ScansPage() {
  const { data, isLoading, error } = useQuery({
    queryKey: ['scans'],
    queryFn: () => api.listScans({ limit: 50 }),
  })

  if (isLoading) return <p>Loading scans...</p>
  if (error) return <p>Error loading scans: {(error as Error).message}</p>

  return (
    <div>
      <h1 style={{ fontSize: '1.5rem', marginBottom: '1.5rem' }}>Scan History</h1>

      <div style={{
        background: 'var(--color-surface)',
        borderRadius: '8px',
        border: '1px solid var(--color-border)',
        overflow: 'hidden',
      }}>
        <table>
          <thead>
            <tr>
              <th>Time</th>
              <th>Devices Found</th>
              <th>Duration</th>
            </tr>
          </thead>
          <tbody>
            {data?.data.map((scan) => {
              const started = new Date(scan.startedAt)
              const completed = new Date(scan.completedAt)
              const durationMs = completed.getTime() - started.getTime()
              return (
                <tr key={scan.id}>
                  <td>{format(started, 'yyyy-MM-dd HH:mm:ss')}</td>
                  <td>{scan.devicesFound}</td>
                  <td style={{ color: 'var(--color-text-muted)' }}>
                    {(durationMs / 1000).toFixed(1)}s
                  </td>
                </tr>
              )
            })}
          </tbody>
        </table>
      </div>
    </div>
  )
}

export default ScansPage
