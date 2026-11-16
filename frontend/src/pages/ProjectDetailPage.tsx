import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { ApiError } from '../api/client';
import {
  addMember,
  changeMemberRole,
  deleteProject,
  getProject,
  listMembers,
  PROJECT_DESCRIPTION_MAX,
  PROJECT_NAME_MAX,
  removeMember,
  updateProject,
  type Member,
  type Project,
  type ProjectRole,
} from '../api/projects';
import { useAuth } from '../auth/AuthContext';
import { TaskCountsLine } from '../components/TaskCountsLine';
import { TasksSection } from '../components/TasksSection';
import { projectRoleLabel, userLabel } from '../labels';

type Load =
  | { status: 'loading' }
  | { status: 'not-found' }
  | { status: 'error' }
  | { status: 'ready'; project: Project; members: Member[] };

/** Project detail (Worksheet 03, Ex. 4): data, counts, members; edit, delete and members for Managers. */
export function ProjectDetailPage() {
  const id = Number(useParams().projectId);
  const navigate = useNavigate();
  const { state } = useAuth();
  const currentUserId = state.status === 'authenticated' ? state.user.id : null;
  const [load, setLoad] = useState<Load>({ status: 'loading' });

  // After a task change: refresh the project (counts) without touching the rest of the page.
  const refreshCounts = useCallback(async () => {
    try {
      const project = await getProject(id);
      setLoad((current) => (current.status === 'ready' ? { ...current, project } : current));
    } catch {
      // the next full reload shows the error
    }
  }, [id]);

  const reload = useCallback(async () => {
    try {
      const [project, members] = await Promise.all([getProject(id), listMembers(id)]);
      setLoad({ status: 'ready', project, members });
    } catch (e) {
      if (e instanceof ApiError && (e.status === 404 || e.status === 400)) {
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

  if (load.status === 'loading') return <p className="page" data-testid="project-loading">A carregar…</p>;
  if (load.status === 'not-found') {
    return (
      <main className="page" data-testid="project-not-found">
        <p>Projeto não encontrado.</p>
        <Link to="/projects">Voltar aos projetos</Link>
      </main>
    );
  }
  if (load.status === 'error') {
    return (
      <main className="page">
        <p role="alert" className="error" data-testid="project-error">
          Não foi possível carregar o projeto.{' '}
          <button type="button" onClick={() => void reload()}>
            Tentar novamente
          </button>
        </p>
      </main>
    );
  }

  const { project, members } = load;
  const isManager = project.myRole === 'MANAGER';

  return (
    <main className="page" data-testid="project-detail">
      <p>
        <Link to="/projects">← Projetos</Link>
      </p>
      <h1 data-testid="project-name">{project.name}</h1>
      {project.description && <p data-testid="project-description">{project.description}</p>}
      <p className="muted">
        {project.myRole && <>O seu papel: {projectRoleLabel[project.myRole]} · </>}
        Criado por {userLabel(project.createdBy)}
      </p>
      {project.taskCounts && <TaskCountsLine counts={project.taskCounts} testId="project-counts" />}

      {isManager && (
        <EditProjectForm
          project={project}
          onSaved={(updated) => setLoad({ ...load, project: updated })}
          onReload={reload}
          onDeleted={() => navigate('/projects', { replace: true })}
        />
      )}

      <TasksSection projectId={project.id} onTasksChanged={() => void refreshCounts()} />

      <MembersSection
        projectId={project.id}
        members={members}
        canManage={isManager}
        currentUserId={currentUserId}
        onChanged={reload}
      />
    </main>
  );
}

function EditProjectForm({
  project,
  onSaved,
  onReload,
  onDeleted,
}: {
  project: Project;
  onSaved: (project: Project) => void;
  onReload: () => Promise<void>;
  onDeleted: () => void;
}) {
  const [editing, setEditing] = useState(false);
  const [name, setName] = useState(project.name);
  const [description, setDescription] = useState(project.description ?? '');
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [pending, setPending] = useState(false);

  function startEditing() {
    setName(project.name);
    setDescription(project.description ?? '');
    setError(null);
    setSuccess(null);
    setEditing(true);
  }

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    if (pending) return;
    if (!name.trim()) {
      setError('O nome é obrigatório.');
      return;
    }
    setPending(true);
    setError(null);
    try {
      const updated = await updateProject(project.id, project.version, {
        name,
        description: description.trim() ? description : null,
      });
      onSaved(updated);
      setEditing(false);
      setSuccess('Alterações guardadas.');
    } catch (e) {
      if (e instanceof ApiError && e.status === 409) {
        setError('O projeto foi alterado por outro utilizador. Recarregue os dados e tente novamente.');
      } else if (e instanceof ApiError && e.fieldCode('name') === 'TOO_LONG') {
        setError(`O nome pode ter no máximo ${PROJECT_NAME_MAX} caracteres.`);
      } else if (e instanceof ApiError && e.fieldCode('name')) {
        setError('O nome é obrigatório.');
      } else if (e instanceof ApiError && e.fieldCode('description')) {
        setError(`A descrição pode ter no máximo ${PROJECT_DESCRIPTION_MAX} caracteres.`);
      } else if (!(e instanceof ApiError && e.status === 401)) {
        setError('Não foi possível guardar. Tente novamente.');
      }
    } finally {
      setPending(false);
    }
  }

  async function onDelete() {
    // NFR6: destructive actions ask for confirmation
    if (!window.confirm(`Eliminar o projeto "${project.name}"? As tarefas e os comentários também são eliminados.`)) {
      return;
    }
    setPending(true);
    setError(null);
    try {
      await deleteProject(project.id);
      onDeleted();
    } catch (e) {
      if (!(e instanceof ApiError && e.status === 401)) {
        setError('Não foi possível eliminar o projeto.');
      }
      setPending(false);
    }
  }

  if (!editing) {
    return (
      <div className="actions">
        <button type="button" onClick={startEditing} data-testid="project-edit-button">
          Editar projeto
        </button>
        <button type="button" className="danger" onClick={onDelete} disabled={pending} data-testid="project-delete-button">
          Eliminar projeto
        </button>
        {success && (
          <span role="status" className="success" data-testid="project-edit-success">
            {success}
          </span>
        )}
        {error && (
          <p role="alert" className="error" data-testid="project-delete-error">
            {error}
          </p>
        )}
      </div>
    );
  }

  return (
    <form className="panel" onSubmit={onSubmit} noValidate data-testid="project-edit-form">
      <h2>Editar projeto</h2>
      <label>
        Nome
        <input
          value={name}
          onChange={(e) => setName(e.target.value)}
          maxLength={PROJECT_NAME_MAX}
          data-testid="project-edit-name"
        />
      </label>
      <label>
        Descrição (opcional)
        <textarea
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          maxLength={PROJECT_DESCRIPTION_MAX}
          rows={3}
          data-testid="project-edit-description"
        />
      </label>
      {error && (
        <p role="alert" className="error" data-testid="project-edit-error">
          {error}{' '}
          {error.startsWith('O projeto foi alterado') && (
            <button type="button" onClick={() => void onReload().then(() => setEditing(false))}>
              Recarregar
            </button>
          )}
        </p>
      )}
      <div className="actions">
        <button type="submit" disabled={pending} data-testid="project-edit-submit">
          {pending ? 'A guardar…' : 'Guardar'}
        </button>
        <button type="button" className="secondary" onClick={() => setEditing(false)} disabled={pending}>
          Cancelar
        </button>
      </div>
    </form>
  );
}

function MembersSection({
  projectId,
  members,
  canManage,
  currentUserId,
  onChanged,
}: {
  projectId: number;
  members: Member[];
  canManage: boolean;
  currentUserId: number | null;
  onChanged: () => Promise<void>;
}) {
  const [email, setEmail] = useState('');
  const [role, setRole] = useState<ProjectRole>('MEMBER');
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);

  function messageFor(e: unknown): string | null {
    if (e instanceof ApiError) {
      if (e.status === 401) return null;
      if (e.problem?.code === 'ALREADY_MEMBER') return 'Esse utilizador já é membro do projeto.';
      if (e.problem?.code === 'LAST_MANAGER') return 'O projeto tem de ter pelo menos um Gestor.';
      if (e.fieldCode('email') === 'USER_NOT_ELIGIBLE') return 'Não há nenhuma conta ativa que possa ser adicionada com esse email.';
      if (e.fieldCode('email')) return 'Indique o email.';
      if (e.status === 403) return 'Não tem permissão para gerir os membros.';
      if (e.status === 404) return 'O membro ou o projeto já não existe.';
    }
    return 'Não foi possível concluir a operação. Tente novamente.';
  }

  async function run(action: () => Promise<unknown>, after?: () => void) {
    if (pending) return;
    setPending(true);
    setError(null);
    try {
      await action();
      after?.();
      await onChanged();
    } catch (e) {
      setError(messageFor(e));
    } finally {
      setPending(false);
    }
  }

  function onAdd(event: FormEvent) {
    event.preventDefault();
    if (!email.trim()) {
      setError('Indique o email.');
      return;
    }
    void run(() => addMember(projectId, email.trim(), role), () => setEmail(''));
  }

  function onRemove(member: Member) {
    const who = member.user.id === currentUserId ? 'sair deste projeto' : `remover ${userLabel(member.user)} do projeto`;
    // NFR6: confirmation before removing a member
    if (window.confirm(`Tem a certeza de que quer ${who}? As tarefas atribuídas deixam de lhe estar atribuídas.`)) {
      void run(() => removeMember(projectId, member.user.id));
    }
  }

  function onRoleChange(member: Member, projectRole: ProjectRole) {
    if (projectRole === 'MEMBER' && !window.confirm(`Despromover ${userLabel(member.user)} a Membro?`)) {
      return;
    }
    void run(() => changeMemberRole(projectId, member.user.id, projectRole));
  }

  return (
    <section className="panel" data-testid="members-section">
      <h2>Membros ({members.length})</h2>
      <ul className="members" data-testid="member-list">
        {members.map((member) => (
          <li key={member.user.id} data-testid="member-item">
            <span data-testid="member-name">{userLabel(member.user)}</span>
            {canManage ? (
              <>
                <select
                  value={member.projectRole}
                  onChange={(e) => onRoleChange(member, e.target.value as ProjectRole)}
                  disabled={pending}
                  aria-label={`Papel de ${userLabel(member.user)}`}
                  data-testid="member-role-select"
                >
                  <option value="MANAGER">{projectRoleLabel.MANAGER}</option>
                  <option value="MEMBER">{projectRoleLabel.MEMBER}</option>
                </select>
                <button
                  type="button"
                  className="secondary"
                  onClick={() => onRemove(member)}
                  disabled={pending}
                  data-testid="member-remove-button"
                >
                  Remover
                </button>
              </>
            ) : (
              <span className="tag">{projectRoleLabel[member.projectRole]}</span>
            )}
          </li>
        ))}
      </ul>
      {error && (
        <p role="alert" className="error" data-testid="members-error">
          {error}
        </p>
      )}
      {canManage && (
        <form className="inline-form" onSubmit={onAdd} noValidate data-testid="member-add-form">
          <label>
            Email do novo membro
            <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} data-testid="member-email-input" />
          </label>
          <label>
            Papel
            <select value={role} onChange={(e) => setRole(e.target.value as ProjectRole)} data-testid="member-role-input">
              <option value="MEMBER">{projectRoleLabel.MEMBER}</option>
              <option value="MANAGER">{projectRoleLabel.MANAGER}</option>
            </select>
          </label>
          <button type="submit" disabled={pending} data-testid="member-add-submit">
            Adicionar
          </button>
        </form>
      )}
    </section>
  );
}
