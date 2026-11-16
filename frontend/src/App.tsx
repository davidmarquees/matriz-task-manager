import { useEffect, useState } from 'react';
import { API_URL } from './api/config';

type BackendState = 'checking' | 'up' | 'down';

// Skeleton screen for increment 1: shows the app name and whether the backend answers.
// The real screens (login, dashboard, projects, tasks) come in the next increments.
export function App() {
  const [backend, setBackend] = useState<BackendState>('checking');

  useEffect(() => {
    fetch(`${API_URL}/actuator/health`)
      .then((response) => setBackend(response.ok ? 'up' : 'down'))
      .catch(() => setBackend('down'));
  }, []);

  const label = {
    checking: 'A verificar a ligação ao servidor…',
    up: 'Servidor disponível',
    down: 'Servidor indisponível',
  }[backend];

  return (
    <main style={{ fontFamily: 'system-ui, sans-serif', padding: '1rem' }}>
      <h1 data-testid="app-title">Matriz</h1>
      <p data-testid="backend-status">{label}</p>
    </main>
  );
}
