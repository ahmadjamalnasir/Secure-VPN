import { useCallback, useEffect, useState } from 'react'

import {
  createServer,
  deleteServer,
  errorMessage,
  fetchServers,
  updateServer,
} from '../api/client'
import type { Server } from '../api/types'
import { SERVER_STATUSES } from '../api/types'

const BLANK: Server = {
  id: '',
  country: '',
  city: '',
  ip_address: '',
  is_premium: false,
  status: 'online',
  load_percent: 0,
  wg_public_key: '',
  wg_endpoint: '',
  dns: '8.8.8.8',
  keepalive: 25,
}

function statusBadge(status: string) {
  const cls =
    status === 'online' ? 'badge-ok' : status === 'offline' ? 'badge-danger' : 'badge-warn'
  return <span className={`badge ${cls}`}>{status}</span>
}

export default function Servers() {
  const [servers, setServers] = useState<Server[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)
  const [showForm, setShowForm] = useState(false)
  const [draft, setDraft] = useState<Server>(BLANK)
  const [busy, setBusy] = useState(false)

  const load = useCallback(async () => {
    setError(null)
    try {
      const page = await fetchServers()
      setServers(page.items)
    } catch (err) {
      setError(errorMessage(err))
      setServers([])
    }
  }, [])

  useEffect(() => { void load() }, [load])

  async function onCreate(e: React.FormEvent) {
    e.preventDefault()
    setBusy(true)
    setError(null)
    setNotice(null)
    try {
      await createServer({ ...draft, load_percent: Number(draft.load_percent) })
      setNotice(`Added ${draft.id}`)
      setDraft(BLANK)
      setShowForm(false)
      await load()
    } catch (err) {
      setError(errorMessage(err))
    } finally {
      setBusy(false)
    }
  }

  async function onToggleStatus(server: Server) {
    const next = server.status === 'online' ? 'offline' : 'online'
    setError(null)
    try {
      await updateServer(server.id, { status: next })
      await load()
    } catch (err) {
      setError(errorMessage(err))
    }
  }

  async function onDelete(server: Server) {
    if (!window.confirm(`Delete server "${server.id}"? This cannot be undone.`)) return
    setError(null)
    try {
      await deleteServer(server.id)
      setNotice(`Deleted ${server.id}`)
      await load()
    } catch (err) {
      setError(errorMessage(err))
    }
  }

  function field<K extends keyof Server>(key: K, value: Server[K]) {
    setDraft((d) => ({ ...d, [key]: value }))
  }

  return (
    <>
      <h1>Servers</h1>
      <p className="subtitle">VPN nodes available to clients.</p>

      {error && <div className="alert alert-error">{error}</div>}
      {notice && <div className="alert alert-ok">{notice}</div>}

      <div className="row" style={{ marginBottom: 16 }}>
        <button className="btn-primary" onClick={() => setShowForm((s) => !s)}>
          {showForm ? 'Cancel' : 'Add server'}
        </button>
        <button onClick={() => void load()}>Refresh</button>
      </div>

      {showForm && (
        <form className="card" style={{ marginBottom: 20 }} onSubmit={onCreate}>
          <h2 style={{ marginTop: 0 }}>New server</h2>
          <div className="form-grid">
            <div className="field">
              <label htmlFor="id">ID</label>
              <input id="id" value={draft.id} required
                onChange={(e) => field('id', e.target.value)} placeholder="us-east-1" />
            </div>
            <div className="field">
              <label htmlFor="country">Country</label>
              <input id="country" value={draft.country} required
                onChange={(e) => field('country', e.target.value)} />
            </div>
            <div className="field">
              <label htmlFor="city">City</label>
              <input id="city" value={draft.city} required
                onChange={(e) => field('city', e.target.value)} />
            </div>
            <div className="field">
              <label htmlFor="ip">IP / host</label>
              <input id="ip" value={draft.ip_address} required
                onChange={(e) => field('ip_address', e.target.value)} />
            </div>
            <div className="field">
              <label htmlFor="endpoint">WireGuard endpoint</label>
              <input id="endpoint" value={draft.wg_endpoint ?? ''}
                placeholder="host:51820"
                onChange={(e) => field('wg_endpoint', e.target.value)} />
            </div>
            <div className="field">
              <label htmlFor="pubkey">WireGuard public key</label>
              <input id="pubkey" value={draft.wg_public_key ?? ''}
                onChange={(e) => field('wg_public_key', e.target.value)} />
            </div>
            <div className="field">
              <label htmlFor="status">Status</label>
              <select id="status" value={draft.status}
                onChange={(e) => field('status', e.target.value)}>
                {SERVER_STATUSES.map((s) => <option key={s} value={s}>{s}</option>)}
              </select>
            </div>
            <div className="field">
              <label htmlFor="load">Load %</label>
              <input id="load" type="number" min={0} max={100} value={draft.load_percent}
                onChange={(e) => field('load_percent', Number(e.target.value))} />
            </div>
            <div className="field">
              <label htmlFor="tier">Tier</label>
              <select id="tier" value={draft.is_premium ? 'premium' : 'free'}
                onChange={(e) => field('is_premium', e.target.value === 'premium')}>
                <option value="free">Free</option>
                <option value="premium">Premium</option>
              </select>
            </div>
          </div>
          <div className="row" style={{ marginTop: 14 }}>
            <button className="btn-primary" type="submit" disabled={busy}>
              {busy ? 'Saving…' : 'Create server'}
            </button>
          </div>
        </form>
      )}

      {servers === null ? (
        <div className="loading">Loading…</div>
      ) : servers.length === 0 ? (
        <div className="card empty">
          No servers yet. Add one above, or seed the demo node with
          {' '}<code>docker compose exec backend python -m backend.seed_demo</code>.
        </div>
      ) : (
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>ID</th><th>Location</th><th>Endpoint</th><th>Tier</th>
                <th>Status</th><th>Load</th><th></th>
              </tr>
            </thead>
            <tbody>
              {servers.map((s) => (
                <tr key={s.id}>
                  <td className="mono">{s.id}</td>
                  <td>{s.city}, {s.country}</td>
                  <td className="mono">{s.wg_endpoint || s.ip_address}</td>
                  <td>
                    <span className={`badge ${s.is_premium ? 'badge-warn' : 'badge-muted'}`}>
                      {s.is_premium ? 'premium' : 'free'}
                    </span>
                  </td>
                  <td>{statusBadge(s.status)}</td>
                  <td>{s.load_percent}%</td>
                  <td>
                    <div className="row">
                      <button className="btn-sm" onClick={() => void onToggleStatus(s)}>
                        {s.status === 'online' ? 'Take offline' : 'Bring online'}
                      </button>
                      <button className="btn-sm btn-danger" onClick={() => void onDelete(s)}>
                        Delete
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </>
  )
}
