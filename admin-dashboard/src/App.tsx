import React, { useEffect, useState } from 'react';
import axios from 'axios';

interface Server {
  id: string;
  country: string;
  city: string;
  ip_address: string;
  is_premium: boolean;
  status: string;
  load_percent: number;
}

const App: React.FC = () => {
  const [servers, setServers] = useState<Server[]>([]);

  useEffect(() => {
    // In production, this would fetch from the real FastAPI backend
    // axios.get('/api/servers').then(...)
    setServers([
      { id: 'us-1', country: 'United States', city: 'New York', ip_address: '0.0.0.0', is_premium: false, status: 'online', load_percent: 45 }
    ]);
  }, []);

  return (
    <div style={{ padding: '2rem', fontFamily: 'sans-serif' }}>
      <h1>Shield VPN Admin Dashboard</h1>
      <p>Manage users, subscriptions, and server health.</p>
      
      <h2>Active Servers</h2>
      <table style={{ width: '100%', textAlign: 'left', borderCollapse: 'collapse' }}>
        <thead>
          <tr style={{ background: '#f4f4f4' }}>
            <th>ID</th>
            <th>Location</th>
            <th>IP Address</th>
            <th>Tier</th>
            <th>Status</th>
            <th>Load</th>
          </tr>
        </thead>
        <tbody>
          {servers.map(s => (
            <tr key={s.id} style={{ borderBottom: '1px solid #ddd' }}>
              <td>{s.id}</td>
              <td>{s.city}, {s.country}</td>
              <td>{s.ip_address}</td>
              <td>{s.is_premium ? 'Premium' : 'Free'}</td>
              <td>{s.status}</td>
              <td>{s.load_percent}%</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
};

export default App;
