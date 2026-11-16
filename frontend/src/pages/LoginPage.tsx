import { useState, type FormEvent } from 'react';
import { Navigate, useLocation, useNavigate } from 'react-router-dom';
import { ApiError } from '../api/client';
import { useAuth } from '../auth/AuthContext';

/**
 * Login page (US1). The error message never says which field was wrong or whether the account is
 * locked, deactivated or unknown (D6, D7).
 */
export function LoginPage() {
  const { state, login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);

  // After login, go back to the page that asked for it (e.g. /projects), or to the home page.
  const from = (location.state as { from?: string } | null)?.from ?? '/';
  if (state.status === 'authenticated') {
    return <Navigate to={from} replace />;
  }
  const sessionExpired = state.status === 'anonymous' && state.sessionExpired;

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    if (pending) {
      return;
    }
    if (!email.trim() || !password) {
      setError('Indique o email e a palavra-passe.');
      return;
    }
    setPending(true);
    setError(null);
    try {
      await login(email.trim(), password);
      navigate(from, { replace: true });
    } catch (e) {
      // Fields are kept so the user can correct them (Worksheet 03, Ex. 7).
      if (e instanceof ApiError && e.status === 429) {
        setError('Demasiadas tentativas. Tente mais tarde.');
      } else if (e instanceof ApiError && (e.status === 401 || e.status === 422)) {
        setError('Email ou palavra-passe inválidos.');
      } else {
        setError('Não foi possível iniciar sessão. Tente novamente.');
      }
    } finally {
      setPending(false);
    }
  }

  return (
    <main className="auth-page">
      <h1>Matriz</h1>
      <form onSubmit={onSubmit} noValidate data-testid="login-form">
        <h2>Iniciar sessão</h2>
        {sessionExpired && (
          <p role="status" className="notice" data-testid="session-expired-message">
            A sessão expirou. Inicie sessão novamente.
          </p>
        )}
        <label>
          Email
          <input
            type="email"
            autoComplete="username"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            required
            data-testid="login-email"
          />
        </label>
        <label>
          Palavra-passe
          <input
            type="password"
            autoComplete="current-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
            data-testid="login-password"
          />
        </label>
        {error && (
          <p role="alert" className="error" data-testid="login-error">
            {error}
          </p>
        )}
        <button type="submit" disabled={pending} data-testid="login-submit">
          {pending ? 'A entrar…' : 'Entrar'}
        </button>
      </form>
    </main>
  );
}
