import { Link, Outlet } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { api } from '../api/client'

function Layout() {
  const { data: health } = useQuery({
    queryKey: ['health'],
    queryFn: api.getHealth,
    refetchInterval: 30_000,
  })

  return (
    <div style={{ minHeight: '100vh' }}>
      <header style={{
        background: 'var(--color-surface)',
        borderBottom: '1px solid var(--color-border)',
        padding: '1rem 2rem',
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
      }}>
        <nav style={{ display: 'flex', gap: '2rem', alignItems: 'center' }}>
          <Link to="/" style={{ fontSize: '1.2rem', fontWeight: 'bold' }}>
            Network Monitor
          </Link>
          <Link to="/">Devices</Link>
          <Link to="/scans">Scans</Link>
        </nav>
        <div style={{ fontSize: '0.85rem', color: 'var(--color-text-muted)' }}>
          {health && (
            <span>
              DB: {health.database} | Last scan: {health.lastScanAt
                ? new Date(health.lastScanAt).toLocaleTimeString()
                : 'never'}
            </span>
          )}
        </div>
      </header>
      <main style={{ padding: '2rem', maxWidth: '1200px', margin: '0 auto' }}>
        <Outlet />
      </main>
    </div>
  )
}

export default Layout
