import { Navigate, Route, Routes } from 'react-router-dom'

import Layout from './components/Layout'
import { useAuth } from './auth/AuthContext'
import Dashboard from './pages/Dashboard'
import Login from './pages/Login'
import Servers from './pages/Servers'
import Users from './pages/Users'

function App() {
  const { isAuthenticated } = useAuth()

  if (!isAuthenticated) {
    return (
      <Routes>
        <Route path="/login" element={<Login />} />
        <Route path="*" element={<Navigate to="/login" replace />} />
      </Routes>
    )
  }

  return (
    <Layout>
      <Routes>
        <Route path="/" element={<Dashboard />} />
        <Route path="/servers" element={<Servers />} />
        <Route path="/users" element={<Users />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </Layout>
  )
}

export default App
