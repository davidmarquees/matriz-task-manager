import { useCallback, useEffect, useState, type FormEvent } from 'react';
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
import { AssigneesSection } from '../components/AssigneesSection';
import { CommentsSection } from '../components/CommentsSection';
import { LastChangeNote } from '../components/LastChangeNote';
import { taskErrorMessage } from '../components/TasksSection';
import { formatDate, taskPriorityLabel, taskStatusLabel, userLabel } from '../labels';

type Load =
  | { status: 'loading' }
  | { status: 'not-found' }
  | { status: 'error' }
  | { status: 'ready'; task: Task; project: Project };

/** Task detail (Worksheet 03, Ex. 5 and 6): data, edit, status, priority, delete, assignees and comments. */
export function TaskDetailPage() {
  const id = Number(useParams().taskId);
  const navigate = useNavigate();
  const { state } = useAuth();
  const [load, setLoad] = useState<Load>({ status: 'loading' });
  const [editing, setEditing] = useState(false);

  const reload = useCallback(async () => {
    try {
      const task = await getTask(id);
      const project = await getProject(task.projectId);
      setLoad({ status: 'ready', task, project });
    } catch (e) {
      if (e instanceof ApiError && (e.status === 404 || e.status === 400 || e.status === 403)) {
        setLoad({ status: 'not-found' });
      } else if (!(e instanceof ApiError && e.status === 401)) {
        setLoad({ status: 'error' });
      }
    }
  }, [id]);

  useEffect(() => {
    setLoad({ status: 'loading' });
    void reload();
  }, [reload]);

  if (load.status === 'loading') return <p className="page" data-testid="task-loading">A carregar…</p>;
  if (load.status === 'not-found') {
    return (
      <main className="page" data-testid="task-not-found">
        <p>Tarefa não encontrada.</p>
        <Link to="/projects">Voltar aos projetos</Link>
      </main>
    );
  }
  if (load.status === 'error') {
    return (
      <main className="page">
        <p role="alert" className="error" data-testid="task-error">
          Não foi possível carregar a tarefa.{' '}
          <button type="button" onClick={() => void reload()}>
            Tentar novamente
          </button>
        </p>
      </main>
    );
  }

  const { task, project } = load;
  const userId = state.status === 'authenticated' ? state.user.id : null;
  const canDelete = project.myRole === 'MANAGER' || task.createdBy.id === userId; // A10, D23

  async function onDelete() {
    if (!window.confirm(`Eliminar a tarefa "${task.title}"? Esta ação não pode ser desfeita.`)) return; // NFR6
    try {
      await deleteTask(task.id);
      navigate(`/projects/${task.projectId}`, { replace: true });
    } catch (e) {
      if (e instanceof ApiError && e.status === 403) window.alert('Só o Gestor do projeto ou quem criou a tarefa a pode eliminar.');
      else if (!(e instanceof ApiError && e.status === 401)) window.alert('Não foi possível eliminar a tarefa.');
    }
  }

  return (
    <main className="page" data-testid="task-detail">
      <p>
        <Link to={`/projects/${project.id}`}>← {project.name}</Link>
      </p>
      {editing ? (
        <EditTaskForm
          task={task}
          onSaved={(updated) => {
            setLoad({ ...load, task: updated });
            setEditing(false);
          }}
          onCancel={() => setEditing(false)}
          onReload={reload}
        />
      ) : (
        <>
          <h1 data-testid="task-title">{task.title}</h1>
          <LastChangeNote task={task} />
          <dl className="task-fields">
            <dt>Estado</dt>
            <dd data-testid="task-status">{taskStatusLabel[task.status]}</dd>
            <dt>Prioridade</dt>
            <dd data-testid="task-priority">{taskPriorityLabel[task.priority]}</dd>
            <dt>Data limite</dt>
            <dd data-testid="task-due-date">{task.dueDate ? formatDate(task.dueDate) : '—'}</dd>
            <dt>Criada por</dt>
            <dd>
              {userLabel(task.createdBy)} · {formatDate(task.createdAt)}
            </dd>
            <dt>Descrição</dt>
            <dd className="description" data-testid="task-description">
              {task.description ?? '—'}
            </dd>
          </dl>
          <div className="actions">
            <button type="button" onClick={() => setEditing(true)} data-testid="task-edit-button">
              Editar tarefa
            </button>
            {canDelete && (
              <button type="button" className="danger" onClick={() => void onDelete()} data-testid="task-delete-button">
                Eliminar tarefa
              </button>
            )}
          </div>
          <AssigneesSection task={task} onChanged={reload} />
          <CommentsSection taskId={task.id} />
        </>
      )}
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
      setError('O título é obrigatório e não pode ter só espaços.');
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
      setError(taskErrorMessage(e));
    } finally {
      setPending(false);
    }
  }

  return (
    <form className="panel" onSubmit={onSubmit} noValidate data-testid="task-edit-form">
      <h2>Editar tarefa</h2>
      <label>
        Título
        <input value={title} onChange={(e) => setTitle(e.target.value)} maxLength={TASK_TITLE_MAX} data-testid="task-edit-title" />
      </label>
      <label>
        Descrição
        <textarea
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          maxLength={TASK_DESCRIPTION_MAX}
          rows={4}
          data-testid="task-edit-description"
        />
      </label>
      <div className="filters">
        <label>
          Estado
          <select value={status} onChange={(e) => setStatus(e.target.value as TaskStatus)} data-testid="task-edit-status">
            {(['TODO', 'IN_PROGRESS', 'DONE'] as const).map((s) => (
              <option key={s} value={s}>
                {taskStatusLabel[s]}
              </option>
            ))}
          </select>
        </label>
        <label>
          Prioridade
          <select value={priority} onChange={(e) => setPriority(e.target.value as TaskPriority)} data-testid="task-edit-priority">
            {(['LOW', 'MEDIUM', 'HIGH'] as const).map((p) => (
              <option key={p} value={p}>
                {taskPriorityLabel[p]}
              </option>
            ))}
          </select>
        </label>
        <label>
          Data limite
          <input type="date" value={dueDate} onChange={(e) => setDueDate(e.target.value)} data-testid="task-edit-due-date" />
        </label>
      </div>
      {error && (
        <p role="alert" className="error" data-testid="task-edit-error">
          {error}{' '}
          {conflict && (
            <button type="button" onClick={() => void onReload().then(onCancel)}>
              Recarregar
            </button>
          )}
        </p>
      )}
      <div className="actions">
        <button type="submit" disabled={pending} data-testid="task-edit-submit">
          {pending ? 'A guardar…' : 'Guardar'}
        </button>
        <button type="button" className="secondary" onClick={onCancel} disabled={pending}>
          Cancelar
        </button>
      </div>
    </form>
  );
}
