import { ArrowLeft } from 'lucide-react';
import { useState, type FormEvent, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, useSearchParams } from 'react-router-dom';
import { confirmPasswordReset, requestPasswordReset } from '../api/auth';
import { ApiError } from '../api/client';
import { AuthHero } from '../components/AuthHero';
import { PreferenceSwitches } from '../components/PreferenceSwitches';
import { Alert } from '../ui/Bits';

const MIN_LENGTH = 8; // A21
const MAX_LENGTH = 72;

function AuthFrame({ eyebrow, title, children }: { eyebrow: string; title: string; children: ReactNode }) {
  const { t } = useTranslation();
  return (
    <main className="auth">
      <div className="auth-board">
        <AuthHero />
        <section className="auth-panel">
          <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
            <PreferenceSwitches inline />
          </div>
          <div className="stack" style={{ gap: 8 }}>
            <span className="eyebrow">{eyebrow}</span>
            <h2 className="page-title" style={{ fontSize: 'clamp(2.2rem, 5vw, 3.5rem)' }}>
              {title}
            </h2>
          </div>
          {children}
          <Link to="/login" className="row" style={{ gap: 6, fontWeight: 600, textDecoration: 'none' }} data-testid="back-to-login">
            <ArrowLeft size={16} aria-hidden="true" />
            {t('reset.backToLogin')}
          </Link>
        </section>
      </div>
    </main>
  );
}

/**
 * Ask for a reset link (US11, FR1c). The answer is the same whether or not the email exists (D30),
 * so the page never says which.
 */
export function ForgotPasswordPage() {
  const { t } = useTranslation();
  const [email, setEmail] = useState('');
  const [sent, setSent] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    if (pending) return;
    if (!email.trim()) {
      setError(t('reset.emailRequired'));
      return;
    }
    setPending(true);
    setError(null);
    try {
      await requestPasswordReset(email.trim());
      setSent(true);
    } catch {
      setError(t('reset.failed'));
    } finally {
      setPending(false);
    }
  }

  return (
    <AuthFrame eyebrow={t('reset.forgotLink')} title={t('reset.requestTitle')}>
      {sent ? (
        <Alert kind="success" testId="reset-request-sent">
          {t('reset.sent')}
        </Alert>
      ) : (
        <form className="form" onSubmit={onSubmit} noValidate data-testid="reset-request-form">
          <p className="muted">{t('reset.requestText')}</p>
          <label className="field">
            {t('login.email')}
            <input type="email" autoComplete="username" value={email} onChange={(e) => setEmail(e.target.value)} data-testid="reset-email" />
          </label>
          {error && (
            <Alert kind="error" testId="reset-request-error">
              {error}
            </Alert>
          )}
          <button type="submit" className="btn btn-primary" disabled={pending} data-testid="reset-request-submit">
            {pending ? t('reset.sending') : t('reset.send')}
          </button>
        </form>
      )}
    </AuthFrame>
  );
}

/** Choose a new password with the emailed link (D32 to D35). */
export function ResetPasswordPage() {
  const { t } = useTranslation();
  const token = useSearchParams()[0].get('token') ?? '';
  const [next, setNext] = useState('');
  const [confirm, setConfirm] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [invalidLink, setInvalidLink] = useState(!token);
  const [done, setDone] = useState(false);
  const [pending, setPending] = useState(false);

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    if (pending) return;
    if (!next || !confirm) return setError(t('password.fillAll'));
    if (next.length < MIN_LENGTH) return setError(t('password.tooShort', { min: MIN_LENGTH }));
    if (next.length > MAX_LENGTH) return setError(t('password.tooLong', { max: MAX_LENGTH }));
    if (next !== confirm) return setError(t('password.mismatch'));
    setPending(true);
    setError(null);
    try {
      await confirmPasswordReset(token, next);
      setDone(true);
    } catch (e) {
      if (e instanceof ApiError && e.fieldCode('token') === 'INVALID_TOKEN') setInvalidLink(true);
      else if (e instanceof ApiError && e.fieldCode('newPassword') === 'TOO_SHORT') setError(t('password.tooShort', { min: MIN_LENGTH }));
      else setError(t('reset.failed'));
    } finally {
      setPending(false);
    }
  }

  return (
    <AuthFrame eyebrow={t('reset.requestTitle')} title={t('reset.confirmTitle')}>
      {done && (
        <>
          <Alert kind="success" testId="reset-done">
            {t('reset.done')}
          </Alert>
          <Link to="/login" className="btn btn-primary" style={{ alignSelf: 'flex-start' }} data-testid="reset-go-login">
            {t('login.submit')}
          </Link>
        </>
      )}
      {!done && invalidLink && (
        <>
          <Alert kind="error" testId="reset-invalid-token">
            {token ? t('reset.invalidToken') : t('reset.noToken')}
          </Alert>
          <Link to="/forgot-password" className="btn" style={{ alignSelf: 'flex-start' }} data-testid="reset-new-link">
            {t('reset.newLink')}
          </Link>
        </>
      )}
      {!done && !invalidLink && (
        <form className="form" onSubmit={onSubmit} noValidate data-testid="reset-confirm-form">
          <p className="muted">{t('reset.confirmText')}</p>
          <label className="field">
            {t('password.new')}
            <input
              type="password"
              autoComplete="new-password"
              value={next}
              onChange={(e) => setNext(e.target.value)}
              aria-describedby="reset-password-hint"
              data-testid="reset-new-password"
            />
            <span id="reset-password-hint" className="hint">
              {t('password.hint', { min: MIN_LENGTH })}
            </span>
          </label>
          <label className="field">
            {t('password.confirm')}
            <input type="password" autoComplete="new-password" value={confirm} onChange={(e) => setConfirm(e.target.value)} data-testid="reset-confirm-password" />
          </label>
          {error && (
            <Alert kind="error" testId="reset-confirm-error">
              {error}
            </Alert>
          )}
          <button type="submit" className="btn btn-primary" disabled={pending} data-testid="reset-confirm-submit">
            {pending ? t('common.saving') : t('reset.submit')}
          </button>
        </form>
      )}
    </AuthFrame>
  );
}
