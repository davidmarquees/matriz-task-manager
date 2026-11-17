import { ArrowLeft, Pencil, SearchX, Trash2 } from 'lucide-react';
import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { ApiError } from '../api/client';
import { getProject, type Project } from '../api/projects';
import {
  deleteTask,
  getTask,
  TASK_DESCRIPTION_MAX,
  TASK_TITLE_MAX,
  updateTask,
  type Task,
  type TaskPriority,
  type TaskStatus,
} from '../api/tasks';
import { useAuth } from '../auth/AuthContext';
import { limitChars } from '../text';
import { AssigneesSection } from '../components/AssigneesSection';
import { CommentsSection } from '../components/CommentsSection';
import { LastChangeNote } from '../components/LastChangeNote';
import { taskErrorMessage } from '../components/TasksSection';
import { formatDate, userLabel } from '../labels';
import { Alert, EmptyState, PriorityPill, Skeleton, StatusPill } from '../ui/Bits';
import { useFeedback } from '../ui/Feedback';

type Load =
  | { status: 'loading' }
  | { status: 'not-found' }
  | { status: 'error' }
  | { status: 'ready'; task: Task; project: Project };

/** Task page (Ex. 5 to 7): data, edit, status, priority, delete, assignees and comments. */
export function TaskDetailPage() {
  const { t, i18n } = useTranslation();
  const id = Number(useParams().taskId);
  const navigate = useNavigate();
  const { state } = useAuth();
  const { confirm, toast } = useFeedback();
  const [load, setLoad] = useState<Load>({ status: 'loading' });
  const [editing, setEditing] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const reload = useCallback(async () => {
    try {
      const task = await getTask(id);
      const project = await getProject(task.projectId);
      setLoad({ status: 'ready', task, project });
    } catch (e) {
      if (e instanceof ApiError && (e.status === 404 || e.status === 400 || e.status === 403)) setLoad({ status: 'not-found' });
      else if (!(e instanceof ApiError && e.status === 401)) setLoad({ status: 'error' });
    }
  }, [id]);

  useEffect(() => {
    setLoad({ status: 'loading' });
    void reload();
  }, [reload]);

  if (load.status === 'loading') return <Skeleton rows={5} label={t('common.loading')} testId="task-loading" />;
  if (load.status === 'not-found') {
    return (
      <EmptyState icon={SearchX} title={t('task.notFound')} testId="task-not-found">
        <Link className="btn" to="/projects">
          {t('project.back')}
        </Link>
      </EmptyState>
    );
  }
  if (load.status === 'error') {
    return (
      <Alert kind="error" testId="task-error">
        {t('task.loadError')}{' '}
        <button type="button" className="link-button" onClick={() => void reload()}>
          {t('common.retry')}
        </button>
      </Alert>
    );
  }

  const { task, project } = load;
  const userId = state.status === 'authenticated' ? state.user.id : null;
  const canDelete = project.myRole === 'MANAGER' || task.createdBy.id === userId; // A10, D23

  async function onDelete() {
    const ok = await confirm({
      title: t('task.deleteTitle'),
      message: t('task.deleteText', { title: task.title }),
      confirmLabel: t('task.delete'),
      danger: true,
    });
    if (!ok) return;
    setError(null);
    try {
      await deleteTask(task.id);
      toast(t('task.deleted'));
      navigate(`/projects/${task.projectId}`, { replace: true });
    } catch (e) {
      if (e instanceof ApiError && e.status === 403) setError(t('task.deleteForbidden'));
      else if (!(e instanceof ApiError && e.status === 401)) setError(t('task.deleteError'));
    }
  }

  return (
    <main className="stack" data-testid="task-detail">
      <Link to={`/projects/${project.id}`} className="row" style={{ gap: 6, fontWeight: 600, textDecoration: 'none' }}>
        <ArrowLeft size={16} aria-hidden="true" />
        {project.name}
      </Link>
      <div className="page-head">
        <div className="page-head-text">
          <span className="eyebrow">
            {t('task.number', { id: task.id })} · {t('task.createdBy', { name: userLabel(t, task.createdBy), date: formatDate(task.createdAt, i18n.language) })}
          </span>
          <h1 className="page-title" style={{ fontSize: 'clamp(2.2rem, 5vw, 3.6rem)' }} data-testid="task-title">
            {task.title}
          </h1>
          <div className="row">
            <StatusPill status={task.status} testId="task-status" />
            <PriorityPill priority={task.priority} testId="task-priority" />
            <LastChangeNote task={task} />
          </div>
        </div>
      </div>
      {error && (
        <Alert kind="error" testId="task-delete-error">
          {error}
        </Alert>
      )}

      <div className="grid-main">
        <div className="stack">
          {editing ? (
            <EditTaskForm
              task={task}
              onSaved={(updated) => {
                setLoad({ ...load, task: updated });
                setEditing(false);
                toast(t('task.saved'));
              }}
              onCancel={() => setEditing(false)}
              onReload={async () => {
                await reload();
                setEditing(false);
              }}
            />
          ) : (
            <section className="tile" aria-labelledby="description-title">
              <h2 id="description-title">{t('tasks.description')}</h2>
              <p className="pre-wrap" data-testid="task-description">
                {task.description ?? <span className="muted">{t('task.noDescription')}</span>}
              </p>
            </section>
          )}
          <CommentsSection taskId={task.id} />
        </div>
        <div className="stack">
          <section className="tile" aria-labelledby="details-title">
            <h2 id="details-title">{t('task.details')}</h2>
            <dl className="details-list">
              <dt>{t('tasks.status')}</dt>
              <dd>{t(`status.${task.status}`)}</dd>
              <dt>{t('tasks.priority')}</dt>
              <dd>{t(`priority.${task.priority}`)}</dd>
              <dt>{t('tasks.dueDate')}</dt>
              <dd className="mono" data-testid="task-due-date">
                {task.dueDate ? formatDate(task.dueDate, i18n.language) : '—'}
              </dd>
            </dl>
            {!editing && (
              <div className="row">
                <button type="button" className="btn btn-small" onClick={() => setEditing(true)} data-testid="task-edit-button">
                  <Pencil size={16} aria-hidden="true" />
                  {t('task.edit')}
                </button>
                {canDelete && (
                  <button type="button" className="btn btn-small btn-danger" onClick={() => void onDelete()} data-testid="task-delete-button">
                    <Trash2 size={16} aria-hidden="true" />
                    {t('task.delete')}
                  </button>
                )}
              </div>
            )}
          </section>
          <AssigneesSection task={task} onChanged={reload} />
        </div>
      </div>
    </main>
  );
}

function EditTaskForm({
  task,
  onSaved,
  onCancel,
  onReload,
}: {
  task: Task;
  onSaved: (task: Task) => void;
  onCancel: () => void;
  onReload: () => Promise<void>;
}) {
  const { t } = useTranslation();
  const [title, setTitle] = useState(task.title);
  const [description, setDescription] = useState(task.description ?? '');
  const [status, setStatus] = useState<TaskStatus>(task.status);
  const [priority, setPriority] = useState<TaskPriority>(task.priority);
  const [dueDate, setDueDate] = useState(task.dueDate ?? '');
  const [error, setError] = useState<string | null>(null);
  const [conflict, setConflict] = useState(false);
  const [pending, setPending] = useState(false);

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    if (pending) return;
    if (!title.trim()) {
      setError(t('tasks.titleRequired'));
      return;
    }
    setPending(true);
    setError(null);
    setConflict(false);
    try {
      onSaved(
        await updateTask(task.id, task.version, {
          title,
          description: description.trim() ? description : null,
          status,
          priority,
          dueDate: dueDate || null,
        }),
      );
    } catch (e) {
      setConflict(e instanceof ApiError && e.status === 409);
      setError(taskErrorMessage(t, e));
    } finally {
      setPending(false);
    }
  }

  return (
    <section className="tile" aria-labelledby="edit-task-title">
      <h2 id="edit-task-title">{t('task.editTitle')}</h2>
      <form className="form" onSubmit={onSubmit} noValidate data-testid="task-edit-form">
        <label className="field">
          {t('tasks.titleLabel')}
          <input
            value={title}
            onChange={(e) => setTitle(limitChars(e.target.value, TASK_TITLE_MAX))} // not maxLength: it counts UTF-16 units (Q-3)
            data-testid="task-edit-title"
          />
        </label>
        <label className="field">
          {t('tasks.description')}
          <textarea
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            maxLength={TASK_DESCRIPTION_MAX}
            rows={4}
            data-testid="task-edit-description"
          />
          <span className="hint">{t('common.chars', { count: description.length, max: TASK_DESCRIPTION_MAX })}</span>
        </label>
        <div className="field-row">
          <label className="field">
            {t('tasks.status')}
            <select value={status} onChange={(e) => setStatus(e.target.value as TaskStatus)} data-testid="task-edit-status">
              {(['TODO', 'IN_PROGRESS', 'DONE'] as const).map((s) => (
                <option key={s} value={s}>
                  {t(`status.${s}`)}
                </option>
              ))}
            </select>
          </label>
          <label className="field">
            {t('tasks.priority')}
            <select value={priority} onChange={(e) => setPriority(e.target.value as TaskPriority)} data-testid="task-edit-priority">
              {(['LOW', 'MEDIUM', 'HIGH'] as const).map((p) => (
                <option key={p} value={p}>
                  {t(`priority.${p}`)}
                </option>
              ))}
            </select>
          </label>
          <label className="field">
            {t('tasks.dueDate')}
            <input type="date" value={dueDate} onChange={(e) => setDueDate(e.target.value)} data-testid="task-edit-due-date" />
          </label>
        </div>
        {error && (
          <Alert kind="error" testId="task-edit-error">
            {error}{' '}
            {conflict && (
              <button type="button" className="link-button" onClick={() => void onReload()}>
                {t('project.reload')}
              </button>
            )}
          </Alert>
        )}
        <div className="row">
          <button type="submit" className="btn btn-primary" disabled={pending} data-testid="task-edit-submit">
            {pending ? t('common.saving') : t('common.save')}
          </button>
          <button type="button" className="btn btn-ghost" onClick={onCancel} disabled={pending}>
            {t('common.cancel')}
          </button>
        </div>
      </form>
    </section>
  );
}
