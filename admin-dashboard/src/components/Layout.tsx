import type { ReactNode } from 'react'
import { NavLink } from 'react-router-dom'

import { useAuth } from '../auth/AuthContext'

export default function Layout({ children }: { children: ReactNode }) {
  const { signOut } = useAuth()

  return (
    <div className="shell">
      <nav className="sidebar">
        <div className="brand">
          Shield VPN
          <small>Backoffice</small>
        </div>
        <NavLink to="/" end className="nav-link">Dashboard</NavLink>
        <NavLink to="/servers" className="nav-link">Servers</NavLink>
        <NavLink to="/users" className="nav-link">Users</NavLink>
        <div className="sidebar-footer">
          <button onClick={signOut} style={{ width: '100%' }}>Sign out</button>
        </div>
      </nav>
      <main className="main">{children}</main>
    </div>
  )
}
