import { useEffect, useState } from 'react'

import { errorMessage, fetchStats } from '../api/client'
import type { Stats } from '../api/types'

const TILES: Array<{ key: keyof Stats; label: string }> = [
  { key: 'total_servers', label: 'Servers' },
  { key: 'online_servers', label: 'Online' },
  { key: 'total_users', label: 'Users' },
  { key: 'active_users', label: 'Active' },
  { key: 'premium_users', label: 'Premium' },
  { key: 'admin_users', label: 'Admins' },
]

export default function Dashboard() {
  const [stats, setStats] = useState<Stats | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    fetchStats()
      .then((data) => { if (!cancelled) setStats(data) })
      .catch((err) => { if (!cancelled) setError(errorMessage(err)) })
    return () => { cancelled = true }
  }, [])

  return (
    <>
      <h1>Dashboard</h1>
      <p className="subtitle">Live counts from the API.</p>

      {error && <div className="alert alert-error">{error}</div>}

      {!stats && !error ? (
        <div className="loading">Loading…</div>
      ) : stats ? (
        <div className="stat-grid">
          {TILES.map(({ key, label }) => (
            <div className="card" key={key}>
              <div className="stat-value">{stats[key]}</div>
              <div className="stat-label">{label}</div>
            </div>
          ))}
        </div>
      ) : null}
    </>
  )
}
