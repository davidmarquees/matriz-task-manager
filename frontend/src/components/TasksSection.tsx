import { ClipboardList, Plus, Search, X } from 'lucide-react';
import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import type { TFunction } from 'i18next';
import { ApiError } from '../api/client';
import {
  createTask,
  listTasks,
  TASK_DESCRIPTION_MAX,
  TASK_PAGE_SIZE,
  TASK_TITLE_MAX,
  TASK_TITLE_MIN,
  updateTask,
  type Task,
  type TaskFilters,
  type TaskPage,
  type TaskPriority,
  type TaskStatus,
} from '../api/tasks';
import { formatDate, userLabel } from '../labels';
import { Alert, EmptyState, PriorityPill, Skeleton } from '../ui/Bits';
import { useFeedback } from '../ui/Feedback';
import { LastChangeNote } from './LastChangeNote';

const STATUSES: TaskStatus[] = ['TODO', 'IN_PROGRESS', 'DONE'];
const PRIORITIES: TaskPriority[] = ['LOW', 'MEDIUM', 'HIGH'];

type Load = { status: 'loading' } | { status: 'error' } | { status: 'ready'; data: TaskPage };

/** Message for an error on a task form, by status and field (D20, D25, D26). Null when the session ended. */
export function taskErrorMessage(t: TFunction, e: unknown): string | null {
  if (!(e instanceof ApiError)) return t('tasks.saveError');
  if (e.status === 401) return null;
  if (e.status === 409) return t('tasks.conflict');
  if (e.status === 404) return t('tasks.gone');
  const title = e.fieldCode('title');
  if (title === 'BLANK' || title === 'REQUIRED') return t('tasks.titleRequired');
  if (title === 'TOO_SHORT') return t('tasks.titleTooShort', { min: TASK_TITLE_MIN });
  if (title === 'TOO_LONG') return t('tasks.titleTooLong', { max: TASK_TITLE_MAX });
  if (e.fieldCode('description')) return t('tasks.descriptionTooLong', { max: TASK_DESCRIPTION_MAX });
  if (e.fieldCode('dueDate')) return t('tasks.invalidDate');
  if (e.fieldCode('status') || e.fieldCode('priority')) return t('tasks.invalidValue');
  return t('tasks.saveError');
}

/**
 * Tasks of a project (Ex. 5): search, filters by status and priority, pagination, creation and quick
 * status / priority changes. After every change the list and the project counts are refreshed without
 * reloading the page.
 */
export function TasksSection({ projectId, onTasksChanged }: { projectId: number; onTasksChanged: () => void }) {
  const { t, i18n } = useTranslation();
  const { toast } = useFeedback();
  const [search, setSearch] = useState('');
  const [filters, setFilters] = useState<TaskFilters>({ q: '', status: '', priority: '' });
  const [page, setPage] = useState(0);
  const [load, setLoad] = useState<Load>({ status: 'loading' });
  const [rowError, setRowError] = useState<string | null>(null);
  const [savingId, setSavingId] = useState<number | null>(null);
  const [creating, setCreating] = useState(false);

  useEffect(() => {
    const timer = setTimeout(() => {
      setFilters((f) => (f.q === search ? f : { ...f, q: search }));
      setPage(0);
    }, 300);
    return () => clearTimeout(timer);
  }, [search]);

  const reload = useCallback(async () => {
    try {
      setLoad({ status: 'ready', data: await listTasks(projectId, filters, page) });
    } catch (e) {
      if (!(e instanceof ApiError && e.status === 401)) setLoad({ status: 'error' });
    }
  }, [projectId, filters, page]);

  useEffect(() => {
    setLoad({ status: 'loading' });
    void reload();
  }, [reload]);

  function changed() {
    void reload();
    onTasksChanged();
  }

  async function quickChange(task: Task, change: { status?: TaskStatus; priority?: TaskPriority }) {
    if (savingId !== null) return;
    setSavingId(task.id);
    setRowError(null);
    try {
      await updateTask(task.id, task.version, change);
      toast(t('tasks.updated'));
      changed();
    } catch (e) {
      setRowError(taskErrorMessage(t, e));
      void reload();
    } finally {
      setSavingId(null);
    }
  }

  const filtered = filters.q.trim() !== '' || filters.status !== '' || filters.priority !== '';
  const totalPages = load.status === 'ready' ? Math.max(1, Math.ceil(load.data.totalItems / TASK_PAGE_SIZE)) : 1;

  return (
    <section className="tile" aria-labelledby="tasks-title" data-testid="tasks-section">
      <div className="row-between">
        <h2 id="tasks-title">{t('tasks.title')}</h2>
        {!creating && (
          <button type="button" className="btn btn-primary btn-small" onClick={() => setCreating(true)} data-testid="task-create-open">
            <Plus size={16} aria-hidden="true" />
            {t('tasks.newTask')}
          </button>
        )}
      </div>

      {creating && (
        <CreateTaskForm
          projectId={projectId}
          onClose={() => setCreating(false)}
          onCreated={() => {
            if (page === 0) changed();
            else {
              setPage(0);
              onTasksChanged();
            }
          }}
        />
      )}

      <div className="field-row">
        <label className="field">
          {t('tasks.search')}
          <span style={{ position: 'relative', display: 'block' }}>
            <Search size={18} aria-hidden="true" className="search-icon" />
            <input
              type="search"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              maxLength={TASK_TITLE_MAX}
              data-testid="task-search"
            />
          </span>
        </label>
        <label className="field">
          {t('tasks.status')}
          <select
            value={filters.status}
            onChange={(e) => {
              setFilters({ ...filters, status: e.target.value as TaskStatus | '' });
              setPage(0);
            }}
            data-testid="task-filter-status"
          >
            <option value="">{t('tasks.allStatuses')}</option>
            {STATUSES.map((s) => (
              <option key={s} value={s}>
                {t(`status.${s}`)}
              </option>
            ))}
          </select>
        </label>
        <label className="field">
          {t('tasks.priority')}
          <select
            value={filters.priority}
            onChange={(e) => {
              setFilters({ ...filters, priority: e.target.value as TaskPriority | '' });
              setPage(0);
            }}
            data-testid="task-filter-priority"
          >
            <option value="">{t('tasks.allPriorities')}</option>
            {PRIORITIES.map((p) => (
              <option key={p} value={p}>
                {t(`priority.${p}`)}
              </option>
            ))}
          </select>
        </label>
      </div>

      {rowError && (
        <Alert kind="error" testId="task-row-error">
          {rowError}
        </Alert>
      )}
      {load.status === 'loading' && <Skeleton rows={3} label={t('common.loading')} testId="tasks-loading" />}
      {load.status === 'error' && (
        <Alert kind="error" testId="tasks-error">
          {t('tasks.loadError')}{' '}
          <button type="button" className="link-button" onClick={() => void reload()}>
            {t('common.retry')}
          </button>
        </Alert>
      )}
      {load.status === 'ready' && load.data.items.length === 0 && (
        <EmptyState icon={ClipboardList} title={filtered ? t('tasks.noResults') : t('tasks.none')} testId="tasks-empty" />
      )}
      {load.status === 'ready' && load.data.items.length > 0 && (
        <>
          <p className="eyebrow" data-testid="tasks-total">
            {t('tasks.total', { count: load.data.totalItems })}
          </p>
          <ul className="list" data-testid="task-list">
            {load.data.items.map((task) => (
              <li key={task.id} className="task-row" data-testid="task-item">
                <div className="stack" style={{ gap: 6 }}>
                  <Link className="task-title" to={`/tasks/${task.id}`} data-testid="task-link">
                    {task.title}
                  </Link>
                  <span className="row" style={{ gap: 8 }}>
                    <PriorityPill priority={task.priority} />
                    <span className="muted" style={{ fontSize: 13 }}>
                      {task.dueDate && <>{t('tasks.dueOn', { date: formatDate(task.dueDate, i18n.language) })} · </>}
                      {task.assignees.length > 0 ? task.assignees.map((a) => userLabel(t, a)).join(', ') : t('tasks.noAssignees')}
                    </span>
                  </span>
                  <LastChangeNote task={task} />
                </div>
                <label>
                  <span className="visually-hidden">{t('tasks.statusOf', { title: task.title })}</span>
                  <select
                    className="select-compact"
                    value={task.status}
                    disabled={savingId !== null}
                    onChange={(e) => void quickChange(task, { status: e.target.value as TaskStatus })}
                    data-testid="task-status-select"
                  >
                    {STATUSES.map((s) => (
                      <option key={s} value={s}>
                        {t(`status.${s}`)}
                      </option>
                    ))}
                  </select>
                </label>
                <label>
                  <span className="visually-hidden">{t('tasks.priorityOf', { title: task.title })}</span>
                  <select
                    className="select-compact"
                    value={task.priority}
                    disabled={savingId !== null}
                    onChange={(e) => void quickChange(task, { priority: e.target.value as TaskPriority })}
                    data-testid="task-priority-select"
                  >
                    {PRIORITIES.map((p) => (
                      <option key={p} value={p}>
                        {t(`priority.${p}`)}
                      </option>
                    ))}
                  </select>
                </label>
              </li>
            ))}
          </ul>
          {totalPages > 1 && (
            <nav className="row" aria-label={t('tasks.pagination')}>
              <button type="button" className="btn btn-small" onClick={() => setPage(page - 1)} disabled={page === 0} data-testid="tasks-prev">
                {t('projects.previous')}
              </button>
              <span className="eyebrow">{t('projects.page', { page: page + 1, total: totalPages })}</span>
              <button
                type="button"
                className="btn btn-small"
                onClick={() => setPage(page + 1)}
                disabled={page + 1 >= totalPages}
                data-testid="tasks-next"
              >
                {t('projects.next')}
              </button>
            </nav>
          )}
        </>
      )}
    </section>
  );
}

function CreateTaskForm({ projectId, onCreated, onClose }: { projectId: number; onCreated: () => void; onClose: () => void }) {
  const { t } = useTranslation();
  const { toast } = useFeedback();
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [status, setStatus] = useState<TaskStatus>('TODO');
  const [priority, setPriority] = useState<TaskPriority>('MEDIUM');
  const [dueDate, setDueDate] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);
  const titleRef = useRef<HTMLInputElement>(null);

  useEffect(() => titleRef.current?.focus(), []);

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    if (pending) return;
    const trimmed = title.trim();
    if (!trimmed) {
      setError(t('tasks.titleRequired'));
      return;
    }
    if (trimmed.length < TASK_TITLE_MIN) {
      setError(t('tasks.titleTooShort', { min: TASK_TITLE_MIN }));
      return;
    }
    setPending(true);
    setError(null);
    try {
      const task = await createTask(projectId, {
        title,
        description: description.trim() ? description : null,
        status,
        priority,
        dueDate: dueDate || null,
      });
      setTitle('');
      setDescription('');
      setStatus('TODO');
      setPriority('MEDIUM');
      setDueDate('');
      toast(t('tasks.created', { title: task.title }));
      onCreated();
      titleRef.current?.focus();
    } catch (e) {
      setError(taskErrorMessage(t, e)); // the form keeps what was typed
    } finally {
      setPending(false);
    }
  }

  return (
    <form className="form" onSubmit={onSubmit} noValidate data-testid="task-create-form" style={{ paddingBottom: 12, borderBottom: '1px solid var(--line)' }}>
      <div className="row-between">
        <strong>{t('tasks.newTask')}</strong>
        <button type="button" className="btn btn-small btn-ghost" onClick={onClose} aria-label={t('common.cancel')}>
          <X size={18} aria-hidden="true" />
        </button>
      </div>
      <label className="field">
        {t('tasks.titleLabel')}
        <input ref={titleRef} value={title} onChange={(e) => setTitle(e.target.value)} maxLength={TASK_TITLE_MAX} data-testid="task-title-input" />
      </label>
      <label className="field">
        <span>{t('tasks.description')} <span className="hint">({t('common.optional')})</span></span>
        <textarea
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          maxLength={TASK_DESCRIPTION_MAX}
          rows={2}
          data-testid="task-description-input"
        />
      </label>
      <div className="field-row">
        <label className="field">
          {t('tasks.status')}
          <select value={status} onChange={(e) => setStatus(e.target.value as TaskStatus)} data-testid="task-status-input">
            {STATUSES.map((s) => (
              <option key={s} value={s}>
                {t(`status.${s}`)}
              </option>
            ))}
          </select>
        </label>
        <label className="field">
          {t('tasks.priority')}
          <select value={priority} onChange={(e) => setPriority(e.target.value as TaskPriority)} data-testid="task-priority-input">
            {PRIORITIES.map((p) => (
              <option key={p} value={p}>
                {t(`priority.${p}`)}
              </option>
            ))}
          </select>
        </label>
        <label className="field">
          <span>{t('tasks.dueDate')} <span className="hint">({t('common.optional')})</span></span>
          <input type="date" value={dueDate} onChange={(e) => setDueDate(e.target.value)} data-testid="task-due-date-input" />
        </label>
      </div>
      {error && (
        <Alert kind="error" testId="task-create-error">
          {error}
        </Alert>
      )}
      <div className="row">
        <button type="submit" className="btn btn-primary" disabled={pending} data-testid="task-create-submit">
          {pending ? t('tasks.creating') : t('tasks.create')}
        </button>
      </div>
    </form>
  );
}
