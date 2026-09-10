import { useCallback, useEffect, useState } from 'react'

import {
  deleteUser,
  errorMessage,
  fetchUsers,
  grantSubscription,
  revokeSubscription,
  updateUser,
} from '../api/client'
import type { UserAdmin } from '../api/types'

const PAGE_SIZE = 25

const PLANS = [
  { plan: 'trial', duration_days: 7, label: 'Trial · 7d' },
  { plan: 'monthly', duration_days: 30, label: 'Monthly · 30d' },
  { plan: 'yearly', duration_days: 365, label: 'Yearly · 365d' },
]

function formatDate(iso: string | null): string {
  if (!iso) return '—'
  const d = new Date(iso)
  return Number.isNaN(d.getTime()) ? '—' : d.toISOString().slice(0, 10)
}

function isLapsed(user: UserAdmin): boolean {
  if (!user.is_premium || !user.subscription_expiry) return false
  return new Date(user.subscription_expiry).getTime() < Date.now()
}

export default function Users() {
  const [users, setUsers] = useState<UserAdmin[] | null>(null)
  const [total, setTotal] = useState(0)
  const [offset, setOffset] = useState(0)
  const [search, setSearch] = useState('')
  const [query, setQuery] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [notice, setNotice] = useState<string | null>(null)

  const load = useCallback(async () => {
    setError(null)
    try {
      const page = await fetchUsers(PAGE_SIZE, offset, query || undefined)
      setUsers(page.items)
      setTotal(page.total)
    } catch (err) {
      setError(errorMessage(err))
      setUsers([])
    }
  }, [offset, query])

  useEffect(() => { void load() }, [load])

  async function run(action: () => Promise<unknown>, message: string) {
    setError(null)
    setNotice(null)
    try {
      await action()
      setNotice(message)
      await load()
    } catch (err) {
      setError(errorMessage(err))
    }
  }

  return (
    <>
      <h1>Users</h1>
      <p className="subtitle">{total} total. Manage access and subscriptions.</p>

      {error && <div className="alert alert-error">{error}</div>}
      {notice && <div className="alert alert-ok">{notice}</div>}

      <form
        className="row"
        style={{ marginBottom: 16 }}
        onSubmit={(e) => { e.preventDefault(); setOffset(0); setQuery(search) }}
      >
        <input
          placeholder="Search by email…"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
        />
        <button type="submit">Search</button>
        {query && (
          <button type="button" onClick={() => { setSearch(''); setQuery(''); setOffset(0) }}>
            Clear
          </button>
        )}
      </form>

      {users === null ? (
        <div className="loading">Loading…</div>
      ) : users.length === 0 ? (
        <div className="card empty">No users match.</div>
      ) : (
        <>
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>Email</th><th>Role</th><th>Access</th>
                  <th>Plan</th><th>Expires</th><th>Joined</th><th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {users.map((u) => (
                  <tr key={u.id}>
                    <td>{u.email}</td>
                    <td>
                      <span className={`badge ${u.is_admin ? 'badge-warn' : 'badge-muted'}`}>
                        {u.is_admin ? 'admin' : 'user'}
                      </span>
                    </td>
                    <td>
                      <span className={`badge ${u.is_active ? 'badge-ok' : 'badge-danger'}`}>
                        {u.is_active ? 'active' : 'disabled'}
                      </span>
                    </td>
                    <td>
                      {u.is_premium ? (
                        <span className={`badge ${isLapsed(u) ? 'badge-danger' : 'badge-ok'}`}>
                          {u.subscription_plan ?? 'premium'}{isLapsed(u) ? ' (lapsed)' : ''}
                        </span>
                      ) : (
                        <span className="badge badge-muted">free</span>
                      )}
                    </td>
                    <td>{formatDate(u.subscription_expiry)}</td>
                    <td>{formatDate(u.created_at)}</td>
                    <td>
                      <div className="row">
                        <select
                          defaultValue=""
                          onChange={(e) => {
                            const chosen = PLANS.find((p) => p.plan === e.target.value)
                            e.target.value = ''
                            if (!chosen) return
                            void run(
                              () => grantSubscription(u.id, {
                                plan: chosen.plan,
                                duration_days: chosen.duration_days,
                              }),
                              `Granted ${chosen.plan} to ${u.email}`,
                            )
                          }}
                        >
                          <option value="">Grant…</option>
                          {PLANS.map((p) => (
                            <option key={p.plan} value={p.plan}>{p.label}</option>
                          ))}
                        </select>
                        {u.is_premium && (
                          <button
                            className="btn-sm"
                            onClick={() => void run(
                              () => revokeSubscription(u.id),
                              `Revoked premium for ${u.email}`,
                            )}
                          >
                            Revoke
                          </button>
                        )}
                        <button
                          className="btn-sm"
                          onClick={() => void run(
                            () => updateUser(u.id, { is_active: !u.is_active }),
                            `${u.is_active ? 'Disabled' : 'Enabled'} ${u.email}`,
                          )}
                        >
                          {u.is_active ? 'Disable' : 'Enable'}
                        </button>
                        <button
                          className="btn-sm btn-danger"
                          onClick={() => {
                            if (!window.confirm(`Delete ${u.email}? This cannot be undone.`)) return
                            void run(() => deleteUser(u.id), `Deleted ${u.email}`)
                          }}
                        >
                          Delete
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {total > PAGE_SIZE && (
            <div className="row" style={{ marginTop: 12 }}>
              <button disabled={offset === 0} onClick={() => setOffset((o) => Math.max(0, o - PAGE_SIZE))}>
                Previous
              </button>
              <span style={{ color: 'var(--muted)' }}>
                {offset + 1}–{Math.min(offset + PAGE_SIZE, total)} of {total}
              </span>
              <button
                disabled={offset + PAGE_SIZE >= total}
                onClick={() => setOffset((o) => o + PAGE_SIZE)}
              >
                Next
              </button>
            </div>
          )}
        </>
      )}
    </>
  )
}
