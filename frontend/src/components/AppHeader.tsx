import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { accountRoleLabel } from '../labels';

/** Header with the user's name and account role, and "Terminar sessão" (wireframe element 1). */
export function AppHeader() {
  const { state, logout } = useAuth();
  const navigate = useNavigate();
  const [pending, setPending] = useState(false);

  if (state.status !== 'authenticated') {
    return null;
  }

  async function onLogout() {
    setPending(true);
    try {
      await logout();
    } finally {
      navigate('/login', { replace: true });
    }
  }

  return (
    <header className="app-header">
      <strong>Matriz</strong>
      <nav className="app-nav">
        <Link to="/" data-testid="nav-home">
          Painel
        </Link>
        <Link to="/projects" data-testid="nav-projects">
          Projetos
        </Link>
      </nav>
      <span className="app-header-user">
        <span data-testid="current-user-name">{state.user.name}</span>
        {' · '}
        <span data-testid="current-user-role">{accountRoleLabel[state.user.accountRole]}</span>
      </span>
      <button type="button" onClick={onLogout} disabled={pending} data-testid="logout-button">
        Terminar sessão
      </button>
    </header>
  );
}
