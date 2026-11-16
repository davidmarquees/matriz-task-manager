import { useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import { changeOwnPassword } from '../api/auth';
import { ApiError } from '../api/client';
import { useAuth } from '../auth/AuthContext';
import { Alert } from '../ui/Bits';
import { useFeedback } from '../ui/Feedback';

const MIN_LENGTH = 8; // A21
const MAX_LENGTH = 72;

/**
 * Own password change (FR1e). Mandatory after an Admin-set password (D45). The new password must be
 * different from the current one (D47); the server checks every rule again.
 */
export function ChangePasswordPage() {
  const { t } = useTranslation();
  const { state, refresh } = useAuth();
  const { toast } = useFeedback();
  const navigate = useNavigate();
  const [current, setCurrent] = useState('');
  const [next, setNext] = useState('');
  const [confirm, setConfirm] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);

  const required = state.status === 'authenticated' && state.user.mustChangePassword;

  function validate(): string | null {
    if (!current || !next || !confirm) return t('password.fillAll');
    if (next.length < MIN_LENGTH) return t('password.tooShort', { min: MIN_LENGTH });
    if (next.length > MAX_LENGTH) return t('password.tooLong', { max: MAX_LENGTH });
    if (next === current) return t('password.same');
    if (next !== confirm) return t('password.mismatch');
    return null;
  }

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    if (pending) return;
    const problem = validate();
    if (problem) {
      setError(problem);
      return;
    }
    setPending(true);
    setError(null);
    try {
      await changeOwnPassword(current, next);
      await refresh();
      toast(t('password.changed'));
      navigate('/', { replace: true });
    } catch (e) {
      if (e instanceof ApiError && e.status === 401) return; // session ended: the app goes to login
      if (e instanceof ApiError && e.fieldCode('currentPassword') === 'WRONG_PASSWORD') setError(t('password.wrongCurrent'));
      else if (e instanceof ApiError && e.fieldCode('newPassword') === 'SAME_AS_CURRENT') setError(t('password.same'));
      else if (e instanceof ApiError && e.fieldCode('newPassword') === 'TOO_SHORT') setError(t('password.tooShort', { min: MIN_LENGTH }));
      else setError(t('password.failed'));
    } finally {
      setPending(false);
    }
  }

  return (
    <main className="stack">
      <div className="page-head">
        <div className="page-head-text">
          <span className="eyebrow">{t('password.subtitle')}</span>
          <h1 className="page-title">{t('password.title')}</h1>
        </div>
      </div>
      <section className="tile" style={{ maxWidth: 560 }}>
        <form className="form" onSubmit={onSubmit} noValidate data-testid="change-password-form">
          {required && (
            <Alert kind="info" testId="password-change-required">
              {t('password.required')}
            </Alert>
          )}
          <label className="field">
            {t('password.current')}
            <input
              type="password"
              autoComplete="current-password"
              value={current}
              onChange={(e) => setCurrent(e.target.value)}
              data-testid="current-password"
            />
          </label>
          <label className="field">
            {t('password.new')}
            <input
              type="password"
              autoComplete="new-password"
              value={next}
              onChange={(e) => setNext(e.target.value)}
              aria-describedby="password-hint"
              data-testid="new-password"
            />
            <span id="password-hint" className="hint">
              {t('password.hint', { min: MIN_LENGTH })}
            </span>
          </label>
          <label className="field">
            {t('password.confirm')}
            <input
              type="password"
              autoComplete="new-password"
              value={confirm}
              onChange={(e) => setConfirm(e.target.value)}
              data-testid="confirm-password"
            />
          </label>
          {error && (
            <Alert kind="error" testId="change-password-error">
              {error}
            </Alert>
          )}
          <div className="row">
            <button type="submit" className="btn btn-primary" disabled={pending} data-testid="change-password-submit">
              {pending ? t('common.saving') : t('password.submit')}
            </button>
            {!required && (
              <button type="button" className="btn btn-ghost" onClick={() => navigate('/')}>
                {t('common.cancel')}
              </button>
            )}
          </div>
        </form>
      </section>
    </main>
  );
}
