import { ArrowDown, ArrowUpRight, Clock, Eye, EyeOff } from 'lucide-react';
import { useState, type FormEvent, type KeyboardEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom';
import { ApiError } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { AuthHero } from '../components/AuthHero';
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
  const [showPassword, setShowPassword] = useState(false);
  const [capsLock, setCapsLock] = useState(false);

  // After login, go back to the page that asked for it (e.g. /projects), or to the home page.
  const from = (location.state as { from?: string } | null)?.from ?? '/';
  if (state.status === 'authenticated') {
    return <Navigate to={from} replace />;
  }
  const sessionExpired = state.status === 'anonymous' && state.sessionExpired;

  function onPasswordKey(event: KeyboardEvent<HTMLInputElement>) {
    setCapsLock(event.getModifierState?.('CapsLock') ?? false);
  }

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
      <div className="auth-backdrop" aria-hidden="true">
        <span />
        <span />
        <span />
      </div>
      <div className="auth-board">
        <AuthHero />

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
            <div className="field">
              <label htmlFor="login-password">{t('login.password')}</label>
              <div className="password-field">
                <input
                  id="login-password"
                  type={showPassword ? 'text' : 'password'}
                  autoComplete="current-password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  onKeyDown={onPasswordKey}
                  onKeyUp={onPasswordKey}
                  onBlur={() => setCapsLock(false)}
                  aria-invalid={error ? true : undefined}
                  aria-describedby={[error && 'login-error', capsLock && 'login-caps'].filter(Boolean).join(' ') || undefined}
                  required
                  data-testid="login-password"
                />
                <button
                  type="button"
                  className="password-toggle"
                  onClick={() => setShowPassword((v) => !v)}
                  aria-pressed={showPassword}
                  aria-label={showPassword ? t('login.hidePassword') : t('login.showPassword')}
                  data-testid="login-password-toggle"
                >
                  {showPassword ? <EyeOff size={18} aria-hidden="true" /> : <Eye size={18} aria-hidden="true" />}
                </button>
              </div>
              {capsLock && (
                <span id="login-caps" className="caps-warning" role="status" data-testid="login-caps-lock">
                  {t('login.capsLock')}
                </span>
              )}
            </div>
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
          <Link to="/forgot-password" className="link-button" style={{ alignSelf: 'flex-start' }} data-testid="forgot-password-link">
            {t('reset.forgotLink')}
          </Link>
          <span className="eyebrow" style={{ fontSize: 11 }}>
            {t('login.footnote')}
          </span>
        </section>
      </div>

      <a className="scroll-hint" href="#about-matriz" data-testid="login-about-link">
        {t('login.about.title')}
        <ArrowDown size={14} aria-hidden="true" />
      </a>
      <section className="auth-info" id="about-matriz" aria-labelledby="about-matriz-title" data-testid="login-about">
        <h2 id="about-matriz-title">{t('login.about.title')}</h2>
        <ol className="info-grid">
          <li className="info-card" tabIndex={0}>
            <span className="info-num" aria-hidden="true">1</span>
            <h3>{t('login.about.whatTitle')}</h3>
            <p>{t('login.about.whatText')}</p>
          </li>
          <li className="info-card" tabIndex={0}>
            <span className="info-num" aria-hidden="true">2</span>
            <h3>{t('login.about.whoTitle')}</h3>
            <p>{t('login.about.whoText')}</p>
          </li>
          <li className="info-card" tabIndex={0}>
            <span className="info-num" aria-hidden="true">3</span>
            <h3>{t('login.about.featuresTitle')}</h3>
            <ul>
              <li>{t('login.about.feature1')}</li>
              <li>{t('login.about.feature2')}</li>
              <li>{t('login.about.feature3')}</li>
            </ul>
          </li>
          <li className="info-card" tabIndex={0}>
            <span className="info-num" aria-hidden="true">4</span>
            <h3>{t('login.about.accessTitle')}</h3>
            <p>{t('login.about.accessText')}</p>
          </li>
        </ol>
      </section>
    </main>
  );
}
