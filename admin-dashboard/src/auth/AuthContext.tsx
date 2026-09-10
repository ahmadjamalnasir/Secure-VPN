import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react'
import type { ReactNode } from 'react'

import { getToken, login as apiLogin, setToken, setUnauthorizedHandler } from '../api/client'

interface AuthState {
  token: string | null
  isAuthenticated: boolean
  signIn: (email: string, password: string) => Promise<void>
  signOut: () => void
}

const AuthContext = createContext<AuthState | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [token, setTokenState] = useState<string | null>(() => getToken())

  const signOut = useCallback(() => {
    setToken(null)
    setTokenState(null)
  }, [])

  // A 401 from any request drops the session, so an expired token cannot leave
  // the UI stuck on a page it can no longer load.
  useEffect(() => {
    setUnauthorizedHandler(() => setTokenState(null))
    return () => setUnauthorizedHandler(null)
  }, [])

  const signIn = useCallback(async (email: string, password: string) => {
    const response = await apiLogin(email, password)
    setToken(response.access_token)
    setTokenState(response.access_token)
  }, [])

  const value = useMemo<AuthState>(
    () => ({ token, isAuthenticated: Boolean(token), signIn, signOut }),
    [token, signIn, signOut],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider')
  return ctx
}
