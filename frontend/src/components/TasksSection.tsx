import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
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
import { formatDate, taskPriorityLabel, taskStatusLabel, userLabel } from '../labels';
import { LastChangeNote } from './LastChangeNote';

const STATUSES: TaskStatus[] = ['TODO', 'IN_PROGRESS', 'DONE'];
const PRIORITIES: TaskPriority[] = ['LOW', 'MEDIUM', 'HIGH'];

type Load = { status: 'loading' } | { status: 'error' } | { status: 'ready'; data: TaskPage };

/** Message for a 422 on a task form, by field and code (D20). */
export function taskErrorMessage(e: unknown): string | null {
  if (!(e instanceof ApiError)) return 'Não foi possível guardar a tarefa. Tente novamente.';
  if (e.status === 401) return null;
  if (e.status === 409) return 'A tarefa foi alterada por outro utilizador. Recarregue e tente novamente.';
  if (e.status === 404) return 'A tarefa ou o projeto já não existe.';
  const title = e.fieldCode('title');
  if (title === 'BLANK' || title === 'REQUIRED') return 'O título é obrigatório e não pode ter só espaços.';
  if (title === 'TOO_SHORT') return `O título tem de ter pelo menos ${TASK_TITLE_MIN} caracteres.`;
  if (title === 'TOO_LONG') return `O título pode ter no máximo ${TASK_TITLE_MAX} caracteres.`;
  if (e.fieldCode('description')) return `A descrição pode ter no máximo ${TASK_DESCRIPTION_MAX} caracteres.`;
  if (e.fieldCode('dueDate')) return 'A data limite não é válida.';
  if (e.fieldCode('status') || e.fieldCode('priority')) return 'Estado ou prioridade inválidos.';
  return 'Não foi possível guardar a tarefa. Tente novamente.';
}

/**
 * Tasks of a project (Worksheet 03, Ex. 5): search, filters by status and priority, pagination,
 * creation, and quick status / priority changes. After every change the list and the project counts
 * are refreshed without reloading the page.
 */
export function TasksSection({ projectId, onTasksChanged }: { projectId: number; onTasksChanged: () => void }) {
  const [search, setSearch] = useState('');
  const [filters, setFilters] = useState<TaskFilters>({ q: '', status: '', priority: '' });
  const [page, setPage] = useState(0);
  const [load, setLoad] = useState<Load>({ status: 'loading' });
  const [rowError, setRowError] = useState<string | null>(null);
  const [savingId, setSavingId] = useState<number | null>(null);

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
      changed();
    } catch (e) {
      setRowError(taskErrorMessage(e));
      void reload();
    } finally {
      setSavingId(null);
    }
  }

  const filtered = filters.q.trim() !== '' || filters.status !== '' || filters.priority !== '';
  const totalPages = load.status === 'ready' ? Math.max(1, Math.ceil(load.data.totalItems / TASK_PAGE_SIZE)) : 1;

  return (
    <section className="panel" data-testid="tasks-section">
      <h2>Tarefas</h2>
      <CreateTaskForm projectId={projectId} onCreated={() => (page === 0 ? changed() : (setPage(0), onTasksChanged()))} />

      <div className="filters">
        <label>
          Pesquisar
          <input
            type="search"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            maxLength={TASK_TITLE_MAX}
            data-testid="task-search"
          />
        </label>
        <label>
          Estado
          <select
            value={filters.status}
            onChange={(e) => {
              setFilters({ ...filters, status: e.target.value as TaskStatus | '' });
              setPage(0);
            }}
            data-testid="task-filter-status"
          >
            <option value="">Todos</option>
            {STATUSES.map((s) => (
              <option key={s} value={s}>
                {taskStatusLabel[s]}
              </option>
            ))}
          </select>
        </label>
        <label>
          Prioridade
          <select
            value={filters.priority}
            onChange={(e) => {
              setFilters({ ...filters, priority: e.target.value as TaskPriority | '' });
              setPage(0);
            }}
            data-testid="task-filter-priority"
          >
            <option value="">Todas</option>
            {PRIORITIES.map((p) => (
              <option key={p} value={p}>
                {taskPriorityLabel[p]}
              </option>
            ))}
          </select>
        </label>
      </div>

      {rowError && (
        <p role="alert" className="error" data-testid="task-row-error">
          {rowError}
        </p>
      )}
      {load.status === 'loading' && <p data-testid="tasks-loading">A carregar…</p>}
      {load.status === 'error' && (
        <p role="alert" className="error" data-testid="tasks-error">
          Não foi possível carregar as tarefas.{' '}
          <button type="button" onClick={() => void reload()}>
            Tentar novamente
          </button>
        </p>
      )}
      {load.status === 'ready' && load.data.items.length === 0 && (
        <p data-testid="tasks-empty">{filtered ? 'Nenhuma tarefa encontrada.' : 'Este projeto ainda não tem tarefas.'}</p>
      )}
      {load.status === 'ready' && load.data.items.length > 0 && (
        <>
          <p className="muted" data-testid="tasks-total">
            {load.data.totalItems} {load.data.totalItems === 1 ? 'tarefa' : 'tarefas'}
          </p>
          <ul className="task-list" data-testid="task-list">
            {load.data.items.map((task) => (
              <li key={task.id} data-testid="task-item">
                <div className="task-main">
                  <Link to={`/tasks/${task.id}`} data-testid="task-link">
                    {task.title}
                  </Link>
                  <span className="muted">
                    {task.dueDate && <>Data limite: {formatDate(task.dueDate)} · </>}
                    {task.assignees.length > 0 ? task.assignees.map(userLabel).join(', ') : 'Sem responsáveis'}
                  </span>
                  <LastChangeNote task={task} />
                </div>
                <label className="compact">
                  <span className="visually-hidden">Estado de {task.title}</span>
                  <select
                    value={task.status}
                    disabled={savingId !== null}
                    onChange={(e) => void quickChange(task, { status: e.target.value as TaskStatus })}
                    data-testid="task-status-select"
                  >
                    {STATUSES.map((s) => (
                      <option key={s} value={s}>
                        {taskStatusLabel[s]}
                      </option>
                    ))}
                  </select>
                </label>
                <label className="compact">
                  <span className="visually-hidden">Prioridade de {task.title}</span>
                  <select
                    value={task.priority}
                    disabled={savingId !== null}
                    onChange={(e) => void quickChange(task, { priority: e.target.value as TaskPriority })}
                    data-testid="task-priority-select"
                  >
                    {PRIORITIES.map((p) => (
                      <option key={p} value={p}>
                        {taskPriorityLabel[p]}
                      </option>
                    ))}
                  </select>
                </label>
              </li>
            ))}
          </ul>
          {totalPages > 1 && (
            <nav className="pagination" aria-label="Páginas de tarefas">
              <button type="button" onClick={() => setPage(page - 1)} disabled={page === 0} data-testid="tasks-prev">
                Anterior
              </button>
              <span>
                Página {page + 1} de {totalPages}
              </span>
              <button
                type="button"
                onClick={() => setPage(page + 1)}
                disabled={page + 1 >= totalPages}
                data-testid="tasks-next"
              >
                Seguinte
              </button>
            </nav>
          )}
        </>
      )}
    </section>
  );
}

function CreateTaskForm({ projectId, onCreated }: { projectId: number; onCreated: () => void }) {
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [status, setStatus] = useState<TaskStatus>('TODO');
  const [priority, setPriority] = useState<TaskPriority>('MEDIUM');
  const [dueDate, setDueDate] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [pending, setPending] = useState(false);

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    if (pending) return;
    setSuccess(null);
    const trimmed = title.trim();
    if (!trimmed) {
      setError('O título é obrigatório e não pode ter só espaços.');
      return;
    }
    if (trimmed.length < TASK_TITLE_MIN) {
      setError(`O título tem de ter pelo menos ${TASK_TITLE_MIN} caracteres.`);
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
      setSuccess(`Tarefa "${task.title}" criada.`);
      onCreated();
    } catch (e) {
      setError(taskErrorMessage(e)); // the form keeps what was typed
    } finally {
      setPending(false);
    }
  }

  return (
    <form className="task-form" onSubmit={onSubmit} noValidate data-testid="task-create-form">
      <h3>Nova tarefa</h3>
      <label>
        Título
        <input value={title} onChange={(e) => setTitle(e.target.value)} maxLength={TASK_TITLE_MAX} data-testid="task-title-input" />
      </label>
      <label>
        Descrição (opcional)
        <textarea
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          maxLength={TASK_DESCRIPTION_MAX}
          rows={2}
          data-testid="task-description-input"
        />
      </label>
      <div className="filters">
        <label>
          Estado
          <select value={status} onChange={(e) => setStatus(e.target.value as TaskStatus)} data-testid="task-status-input">
            {STATUSES.map((s) => (
              <option key={s} value={s}>
                {taskStatusLabel[s]}
              </option>
            ))}
          </select>
        </label>
        <label>
          Prioridade
          <select value={priority} onChange={(e) => setPriority(e.target.value as TaskPriority)} data-testid="task-priority-input">
            {PRIORITIES.map((p) => (
              <option key={p} value={p}>
                {taskPriorityLabel[p]}
              </option>
            ))}
          </select>
        </label>
        <label>
          Data limite (opcional)
          <input type="date" value={dueDate} onChange={(e) => setDueDate(e.target.value)} data-testid="task-due-date-input" />
        </label>
      </div>
      {error && (
        <p role="alert" className="error" data-testid="task-create-error">
          {error}
        </p>
      )}
      {success && (
        <p role="status" className="success" data-testid="task-create-success">
          {success}
        </p>
      )}
      <button type="submit" disabled={pending} data-testid="task-create-submit">
        {pending ? 'A criar…' : 'Criar tarefa'}
      </button>
    </form>
  );
}
