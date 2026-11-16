import { useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { changeOwnPassword } from '../api/auth';
import { ApiError } from '../api/client';
import { useAuth } from '../auth/AuthContext';

const MIN_LENGTH = 8; // A21
const MAX_LENGTH = 72;

/**
 * Own password change (FR1e). Mandatory after an Admin-set password (D45). The new password must be
 * different from the current one (D47); the server checks every rule again.
 */
export function ChangePasswordPage() {
  const { state, refresh } = useAuth();
  const navigate = useNavigate();
  const [current, setCurrent] = useState('');
  const [next, setNext] = useState('');
  const [confirm, setConfirm] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);

  const required = state.status === 'authenticated' && state.user.mustChangePassword;

  function validate(): string | null {
    if (!current || !next || !confirm) return 'Preencha todos os campos.';
    if (next.length < MIN_LENGTH) return `A nova palavra-passe tem de ter pelo menos ${MIN_LENGTH} caracteres.`;
    if (next.length > MAX_LENGTH) return `A nova palavra-passe pode ter no máximo ${MAX_LENGTH} caracteres.`;
    if (next === current) return 'A nova palavra-passe tem de ser diferente da atual.';
    if (next !== confirm) return 'A confirmação não coincide com a nova palavra-passe.';
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
      navigate('/', { replace: true });
    } catch (e) {
      if (e instanceof ApiError && e.fieldCode('currentPassword') === 'WRONG_PASSWORD') {
        setError('A palavra-passe atual está incorreta.');
      } else if (e instanceof ApiError && e.fieldCode('newPassword') === 'SAME_AS_CURRENT') {
        setError('A nova palavra-passe tem de ser diferente da atual.');
      } else if (e instanceof ApiError && e.fieldCode('newPassword') === 'TOO_SHORT') {
        setError(`A nova palavra-passe tem de ter pelo menos ${MIN_LENGTH} caracteres.`);
      } else if (e instanceof ApiError && e.status === 401) {
        return; // session ended: the app goes to the login page
      } else {
        setError('Não foi possível alterar a palavra-passe. Tente novamente.');
      }
    } finally {
      setPending(false);
    }
  }

  return (
    <main className="auth-page">
      <form onSubmit={onSubmit} noValidate data-testid="change-password-form">
        <h2>Alterar palavra-passe</h2>
        {required && (
          <p className="notice" data-testid="password-change-required">
            Tem de alterar a palavra-passe antes de continuar.
          </p>
        )}
        <label>
          Palavra-passe atual
          <input
            type="password"
            autoComplete="current-password"
            value={current}
            onChange={(e) => setCurrent(e.target.value)}
            data-testid="current-password"
          />
        </label>
        <label>
          Nova palavra-passe
          <input
            type="password"
            autoComplete="new-password"
            value={next}
            onChange={(e) => setNext(e.target.value)}
            data-testid="new-password"
          />
        </label>
        <label>
          Confirmar nova palavra-passe
          <input
            type="password"
            autoComplete="new-password"
            value={confirm}
            onChange={(e) => setConfirm(e.target.value)}
            data-testid="confirm-password"
          />
        </label>
        {error && (
          <p role="alert" className="error" data-testid="change-password-error">
            {error}
          </p>
        )}
        <button type="submit" disabled={pending} data-testid="change-password-submit">
          {pending ? 'A guardar…' : 'Alterar palavra-passe'}
        </button>
        {!required && (
          <button type="button" className="secondary" onClick={() => navigate('/')}>
            Cancelar
          </button>
        )}
      </form>
    </main>
  );
}
