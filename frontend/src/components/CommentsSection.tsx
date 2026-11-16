import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { ApiError } from '../api/client';
import { addComment, COMMENT_MAX, listComments, type Comment } from '../api/tasks';
import { formatDate, userLabel } from '../labels';

type Load = { status: 'loading' } | { status: 'error' } | { status: 'ready'; comments: Comment[] };

/** Comments of a task (FR11, US7): author and date from the server; no edit or delete (A14). */
export function CommentsSection({ taskId }: { taskId: number }) {
  const [load, setLoad] = useState<Load>({ status: 'loading' });
  const [body, setBody] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);

  const reload = useCallback(async () => {
    try {
      setLoad({ status: 'ready', comments: await listComments(taskId) });
    } catch (e) {
      if (!(e instanceof ApiError && e.status === 401)) setLoad({ status: 'error' });
    }
  }, [taskId]);

  useEffect(() => {
    void reload();
  }, [reload]);

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    if (pending) return;
    if (!body.trim()) {
      setError('O comentário não pode estar vazio nem ter só espaços.');
      return;
    }
    setPending(true);
    setError(null);
    try {
      const comment = await addComment(taskId, body);
      // use the comment confirmed by the API, without duplicating it
      setLoad((current) =>
        current.status === 'ready' && !current.comments.some((c) => c.id === comment.id)
          ? { status: 'ready', comments: [...current.comments, comment] }
          : current,
      );
      setBody('');
    } catch (e) {
      // the text is kept so it can be sent again
      if (e instanceof ApiError && e.status === 401) return;
      if (e instanceof ApiError && e.fieldCode('body') === 'TOO_LONG') {
        setError(`O comentário pode ter no máximo ${COMMENT_MAX} caracteres.`);
      } else if (e instanceof ApiError && e.fieldCode('body')) {
        setError('O comentário não pode estar vazio nem ter só espaços.');
      } else if (e instanceof ApiError && e.status === 404) {
        setError('A tarefa já não existe.');
      } else {
        setError('Não foi possível enviar o comentário. Tente novamente.');
      }
    } finally {
      setPending(false);
    }
  }

  return (
    <section className="panel" data-testid="comments-section">
      <h2>Comentários{load.status === 'ready' ? ` (${load.comments.length})` : ''}</h2>
      {load.status === 'loading' && <p data-testid="comments-loading">A carregar…</p>}
      {load.status === 'error' && (
        <p role="alert" className="error" data-testid="comments-error">
          Não foi possível carregar os comentários.{' '}
          <button type="button" onClick={() => void reload()}>
            Tentar novamente
          </button>
        </p>
      )}
      {load.status === 'ready' && load.comments.length === 0 && (
        <p className="muted" data-testid="comments-empty">
          Ainda não há comentários.
        </p>
      )}
      {load.status === 'ready' && load.comments.length > 0 && (
        <ul className="comments" data-testid="comment-list">
          {load.comments.map((c) => (
            <li key={c.id} data-testid="comment-item">
              <p className="muted">
                <strong data-testid="comment-author">{userLabel(c.author)}</strong> ·{' '}
                <span data-testid="comment-date">
                  {formatDate(c.createdAt)}{' '}
                  {new Date(c.createdAt).toLocaleTimeString('pt-PT', { hour: '2-digit', minute: '2-digit' })}
                </span>
              </p>
              <p className="description" data-testid="comment-body">
                {c.body}
              </p>
            </li>
          ))}
        </ul>
      )}
      <form onSubmit={onSubmit} noValidate data-testid="comment-form">
        <label>
          Novo comentário
          <textarea
            value={body}
            onChange={(e) => setBody(e.target.value)}
            maxLength={COMMENT_MAX}
            rows={3}
            data-testid="comment-input"
          />
        </label>
        <span className="muted" data-testid="comment-counter">
          {body.length}/{COMMENT_MAX}
        </span>
        {error && (
          <p role="alert" className="error" data-testid="comment-error">
            {error}
          </p>
        )}
        <button type="submit" disabled={pending} data-testid="comment-submit">
          {pending ? 'A enviar…' : 'Comentar'}
        </button>
      </form>
    </section>
  );
}
