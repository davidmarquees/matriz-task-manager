import { MessageSquare } from 'lucide-react';
import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { ApiError } from '../api/client';
import { addComment, COMMENT_MAX, listComments, type Comment } from '../api/tasks';
import { formatDate, formatTime, userLabel } from '../labels';
import { Alert, Avatar, EmptyState, Skeleton } from '../ui/Bits';

type Load = { status: 'loading' } | { status: 'error' } | { status: 'ready'; comments: Comment[] };

/** Comments of a task (FR11, US7): author and date from the server; no edit or delete (A14). */
export function CommentsSection({ taskId }: { taskId: number }) {
  const { t, i18n } = useTranslation();
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
      setError(t('comments.blank'));
      return;
    }
    setPending(true);
    setError(null);
    try {
      const comment = await addComment(taskId, body);
      // show the comment confirmed by the API, without duplicating it
      setLoad((current) =>
        current.status === 'ready' && !current.comments.some((c) => c.id === comment.id)
          ? { status: 'ready', comments: [...current.comments, comment] }
          : current,
      );
      setBody('');
    } catch (e) {
      // the text is kept so it can be sent again
      if (e instanceof ApiError && e.status === 401) return;
      if (e instanceof ApiError && e.fieldCode('body') === 'INVALID_CHARACTER') setError(t('common.invalidCharacters')); // D49
      else if (e instanceof ApiError && e.fieldCode('body') === 'TOO_LONG') setError(t('comments.tooLong', { max: COMMENT_MAX }));
      else if (e instanceof ApiError && e.fieldCode('body')) setError(t('comments.blank'));
      else if (e instanceof ApiError && e.status === 404) setError(t('comments.gone'));
      else setError(t('comments.error'));
    } finally {
      setPending(false);
    }
  }

  return (
    <section className="tile" aria-labelledby="comments-title" data-testid="comments-section">
      <h2 id="comments-title">
        {t('comments.title')} {load.status === 'ready' && <span className="eyebrow">{load.comments.length}</span>}
      </h2>
      {load.status === 'loading' && <Skeleton rows={2} label={t('common.loading')} testId="comments-loading" />}
      {load.status === 'error' && (
        <Alert kind="error" testId="comments-error">
          {t('comments.loadError')}{' '}
          <button type="button" className="link-button" onClick={() => void reload()}>
            {t('common.retry')}
          </button>
        </Alert>
      )}
      {load.status === 'ready' && load.comments.length === 0 && (
        <EmptyState icon={MessageSquare} title={t('comments.empty')} testId="comments-empty" />
      )}
      {load.status === 'ready' && load.comments.length > 0 && (
        <ul className="list" data-testid="comment-list">
          {load.comments.map((c) => (
            <li key={c.id} className="comment" data-testid="comment-item">
              <Avatar name={c.author.name} small />
              <div className="comment-bubble">
                <span style={{ fontSize: 13 }}>
                  <strong data-testid="comment-author">{userLabel(t, c.author)}</strong>{' '}
                  <span className="eyebrow" style={{ fontSize: 11 }} data-testid="comment-date">
                    · {formatDate(c.createdAt, i18n.language)} {formatTime(c.createdAt, i18n.language)}
                  </span>
                </span>
                <p className="pre-wrap" data-testid="comment-body">
                  {c.body}
                </p>
              </div>
            </li>
          ))}
        </ul>
      )}
      <form className="form" onSubmit={onSubmit} noValidate data-testid="comment-form">
        <label className="field">
          {t('comments.newLabel')}
          <textarea
            value={body}
            onChange={(e) => setBody(e.target.value)}
            maxLength={COMMENT_MAX}
            rows={3}
            placeholder={t('comments.placeholder')}
            data-testid="comment-input"
          />
        </label>
        {error && (
          <Alert kind="error" testId="comment-error">
            {error}
          </Alert>
        )}
        <div className="row-between">
          <span className="hint" data-testid="comment-counter">
            {t('common.chars', { count: body.length, max: COMMENT_MAX })}
          </span>
          <button type="submit" className="btn btn-primary" disabled={pending} data-testid="comment-submit">
            {pending ? t('comments.sending') : t('comments.submit')}
          </button>
        </div>
      </form>
    </section>
  );
}
