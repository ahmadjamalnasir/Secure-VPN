import axios, { AxiosError } from 'axios'

import type {
  Page,
  Server,
  ServerUpdate,
  Stats,
  SubscriptionGrant,
  TokenResponse,
  UserAdmin,
  UserUpdate,
} from './types'

const TOKEN_KEY = 'shieldvpn.admin.token'

export function getToken(): string | null {
  try {
    return localStorage.getItem(TOKEN_KEY)
  } catch {
    return null
  }
}

export function setToken(token: string | null): void {
  try {
    if (token) localStorage.setItem(TOKEN_KEY, token)
    else localStorage.removeItem(TOKEN_KEY)
  } catch {
    /* storage unavailable (private mode); session stays in memory only */
  }
}

// Always same-origin /api. Vite proxies it in development, nginx proxies it in
// the built image, so the bundle never embeds a backend hostname.
export const api = axios.create({ baseURL: '/api' })

api.interceptors.request.use((config) => {
  const token = getToken()
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

let onUnauthorized: (() => void) | null = null
export function setUnauthorizedHandler(fn: (() => void) | null): void {
  onUnauthorized = fn
}

api.interceptors.response.use(
  (response) => response,
  (error: AxiosError) => {
    // An expired or revoked token should drop the session rather than leave
    // the UI in a half-authenticated state.
    if (error.response?.status === 401) {
      setToken(null)
      onUnauthorized?.()
    }
    return Promise.reject(error)
  },
)

/** Turns an axios failure into something worth showing a human. */
export function errorMessage(error: unknown): string {
  if (axios.isAxiosError(error)) {
    const detail = (error.response?.data as { detail?: unknown } | undefined)?.detail
    if (typeof detail === 'string') return detail
    // FastAPI validation errors arrive as a list of {loc, msg}.
    if (Array.isArray(detail)) {
      return detail
        .map((d: { loc?: unknown[]; msg?: string }) => {
          const field = Array.isArray(d.loc) ? d.loc[d.loc.length - 1] : undefined
          return field ? `${String(field)}: ${d.msg}` : d.msg
        })
        .filter(Boolean)
        .join('; ')
    }
    if (error.response) return `${error.response.status} ${error.response.statusText}`
    return 'Cannot reach the API. Is the backend running?'
  }
  return error instanceof Error ? error.message : 'Unexpected error'
}

// ---- Auth ----

export async function login(email: string, password: string): Promise<TokenResponse> {
  // /admin/auth/login uses OAuth2PasswordRequestForm, so form encoding.
  const body = new URLSearchParams({ username: email, password })
  const { data } = await api.post<TokenResponse>('/admin/auth/login', body, {
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
  })
  return data
}

// ---- Dashboard ----

export async function fetchStats(): Promise<Stats> {
  const { data } = await api.get<Stats>('/admin/stats')
  return data
}

// ---- Servers ----

export async function fetchServers(limit = 50, offset = 0): Promise<Page<Server>> {
  const { data } = await api.get<Page<Server>>('/admin/servers', {
    params: { limit, offset },
  })
  return data
}

export async function createServer(server: Server): Promise<Server> {
  const { data } = await api.post<Server>('/admin/servers', server)
  return data
}

export async function updateServer(id: string, changes: ServerUpdate): Promise<Server> {
  const { data } = await api.put<Server>(`/admin/servers/${id}`, changes)
  return data
}

export async function deleteServer(id: string): Promise<void> {
  await api.delete(`/admin/servers/${id}`)
}

// ---- Users ----

export async function fetchUsers(
  limit = 50,
  offset = 0,
  search?: string,
): Promise<Page<UserAdmin>> {
  const { data } = await api.get<Page<UserAdmin>>('/admin/users', {
    params: { limit, offset, ...(search ? { search } : {}) },
  })
  return data
}

export async function updateUser(id: string, changes: UserUpdate): Promise<UserAdmin> {
  const { data } = await api.patch<UserAdmin>(`/admin/users/${id}`, changes)
  return data
}

export async function deleteUser(id: string): Promise<void> {
  await api.delete(`/admin/users/${id}`)
}

export async function grantSubscription(
  id: string,
  grant: SubscriptionGrant,
): Promise<UserAdmin> {
  const { data } = await api.post<UserAdmin>(`/admin/users/${id}/subscription`, grant)
  return data
}

export async function revokeSubscription(id: string): Promise<UserAdmin> {
  const { data } = await api.delete<UserAdmin>(`/admin/users/${id}/subscription`)
  return data
}
