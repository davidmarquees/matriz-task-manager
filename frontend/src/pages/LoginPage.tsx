import { ArrowUpRight, Clock } from 'lucide-react';
import { useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { Navigate, useLocation, useNavigate } from 'react-router-dom';
import { ApiError } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { PreferenceSwitches } from '../components/PreferenceSwitches';
import { Alert } from '../ui/Bits';

/**
 * Login (US1). The error never says which field was wrong or whether the account is locked,
 * deactivated or unknown (D6, D7). Same look as the rest of the app.
 */
export function LoginPage() {
  const { t } = useTranslation();
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
    if (pending) return;
    if (!email.trim() || !password) {
      setError(t('login.missing'));
      return;
    }
    setPending(true);
    setError(null);
    try {
      await login(email.trim(), password);
      navigate(from, { replace: true });
    } catch (e) {
      // the fields are kept so the user can correct them
      if (e instanceof ApiError && e.status === 429) setError(t('login.tooMany'));
      else if (e instanceof ApiError && (e.status === 401 || e.status === 422)) setError(t('login.invalid'));
      else setError(t('login.failed'));
    } finally {
      setPending(false);
    }
  }

  return (
    <main className="auth">
      <div className="auth-board">
        <section className="auth-hero" aria-hidden="false">
          <div className="brand" style={{ color: 'inherit' }}>
            <span className="brand-mark" aria-hidden="true">
              M
            </span>
            <span className="brand-name">{t('app.name')}</span>
          </div>
          <div className="stack" style={{ gap: 18 }}>
            <h1>
              {t('login.heroLine1')}
              <br />
              {t('login.heroLine2')}
              <br />
              <span className="accent">{t('login.heroLine3')}</span>
            </h1>
            <p style={{ maxWidth: 420, color: 'var(--on-dark-2)' }}>{t('app.tagline')}</p>
          </div>
          <div className="auth-bars" aria-hidden="true">
            <span style={{ height: '45%', border: '1.5px solid var(--on-dark)', backgroundImage: 'repeating-linear-gradient(135deg, var(--on-dark) 0 1.5px, transparent 1.5px 7px)' }} />
            <span style={{ height: '70%', background: '#e3b867' }} />
            <span style={{ height: '100%', background: '#7db39a' }} />
            <span style={{ height: '60%', background: '#d2694f' }} />
          </div>
        </section>

        <section className="auth-panel">
          <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
            <PreferenceSwitches />
          </div>
          <div className="stack" style={{ gap: 8 }}>
            <span className="eyebrow">{t('login.welcome')}</span>
            <h2 className="page-title" style={{ fontSize: 'clamp(2.2rem, 5vw, 3.5rem)' }}>
              {t('login.title')}
            </h2>
          </div>
          {sessionExpired && (
            <p role="status" className="alert alert-info" data-testid="session-expired-message">
              <Clock size={18} aria-hidden="true" />
              <span>{t('login.expired')}</span>
            </p>
          )}
          <form className="form" onSubmit={onSubmit} noValidate data-testid="login-form">
            <label className="field">
              {t('login.email')}
              <input
                type="email"
                autoComplete="username"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                aria-invalid={error ? true : undefined}
                aria-describedby={error ? 'login-error' : undefined}
                required
                data-testid="login-email"
              />
            </label>
            <label className="field">
              {t('login.password')}
              <input
                type="password"
                autoComplete="current-password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                aria-invalid={error ? true : undefined}
                aria-describedby={error ? 'login-error' : undefined}
                required
                data-testid="login-password"
              />
            </label>
            {error && (
              <div id="login-error">
                <Alert kind="error" testId="login-error">
                  {error}
                </Alert>
              </div>
            )}
            <button type="submit" className="btn btn-primary btn-arrow" disabled={pending} data-testid="login-submit">
              {pending ? t('login.submitting') : t('login.submit')}
              <span className="arrow-dot" aria-hidden="true">
                <ArrowUpRight size={18} />
              </span>
            </button>
          </form>
          <span className="eyebrow" style={{ fontSize: 11 }}>
            {t('login.footnote')}
          </span>
        </section>
      </div>
    </main>
  );
}
