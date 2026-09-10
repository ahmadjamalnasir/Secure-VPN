// Mirrors backend/schemas.py. Keep in sync when the API changes.

export interface Server {
  id: string
  country: string
  city: string
  ip_address: string
  is_premium: boolean
  status: string
  load_percent: number
  wg_public_key: string | null
  wg_endpoint: string | null
  dns: string | null
  keepalive: number | null
}

export type ServerUpdate = Partial<Omit<Server, 'id'>>

export interface UserAdmin {
  id: string
  email: string
  is_premium: boolean
  is_admin: boolean
  is_active: boolean
  subscription_plan: string | null
  subscription_expiry: string | null
  created_at: string
}

export interface UserUpdate {
  is_admin?: boolean
  is_active?: boolean
  is_premium?: boolean
}

export interface SubscriptionGrant {
  plan: string
  duration_days: number
  extend?: boolean
}

export interface Stats {
  total_users: number
  active_users: number
  premium_users: number
  admin_users: number
  total_servers: number
  online_servers: number
}

export interface Page<T> {
  total: number
  limit: number
  offset: number
  items: T[]
}

export interface TokenResponse {
  access_token: string
  token_type: string
  is_premium: boolean
  is_admin: boolean
}

export const SERVER_STATUSES = ['online', 'offline', 'maintenance'] as const
