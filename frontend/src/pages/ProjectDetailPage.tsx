import { ArrowLeft, Pencil, SearchX, Trash2, UserPlus } from 'lucide-react';
import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom';
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
import { appointManager } from '../api/admin';
import { ActivitySection } from '../components/ActivitySection';
import { CountsBar } from '../components/CountsBar';
import { TasksSection } from '../components/TasksSection';
import { userLabel } from '../labels';
import { Alert, Avatar, EmptyState, Skeleton } from '../ui/Bits';
import { useFeedback } from '../ui/Feedback';

type Load =
  | { status: 'loading' }
  | { status: 'not-found' }
  | { status: 'error' }
  | { status: 'ready'; project: Project; members: Member[] };

/** Project detail (Ex. 4 to 7): data, counts, tasks and members; edit, delete and members for Managers. */
export function ProjectDetailPage() {
  const { t } = useTranslation();
  const id = Number(useParams().projectId);
  const [searchParams] = useSearchParams();
  const statusParam = searchParams.get('status');
  const initialStatus = statusParam === 'TODO' || statusParam === 'IN_PROGRESS' || statusParam === 'DONE' ? statusParam : '';
  const navigate = useNavigate();
  const { state } = useAuth();
  const { confirm, toast } = useFeedback();
  const currentUserId = state.status === 'authenticated' ? state.user.id : null;
  const [load, setLoad] = useState<Load>({ status: 'loading' });
  const [editing, setEditing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [activityKey, setActivityKey] = useState(0); // refreshes the history after a change

  const reload = useCallback(async () => {
    try {
      const [project, members] = await Promise.all([getProject(id), listMembers(id)]);
      setLoad({ status: 'ready', project, members });
      setActivityKey((k) => k + 1);
    } catch (e) {
      if (e instanceof ApiError && (e.status === 404 || e.status === 400)) setLoad({ status: 'not-found' });
      else if (!(e instanceof ApiError && e.status === 401)) setLoad({ status: 'error' });
    }
  }, [id]);

  // after a task change: refresh the counts only
  const refreshCounts = useCallback(async () => {
    try {
      const project = await getProject(id);
      setLoad((current) => (current.status === 'ready' ? { ...current, project } : current));
      setActivityKey((k) => k + 1);
    } catch {
      // the next full reload shows the error
    }
  }, [id]);

  useEffect(() => {
    setLoad({ status: 'loading' });
    void reload();
  }, [reload]);

  if (load.status === 'loading') {
    return <Skeleton rows={5} label={t('common.loading')} testId="project-loading" />;
  }
  if (load.status === 'not-found') {
    return (
      <EmptyState icon={SearchX} title={t('project.notFound')} testId="project-not-found">
        <Link className="btn" to="/projects">
          {t('project.back')}
        </Link>
      </EmptyState>
    );
  }
  if (load.status === 'error') {
    return (
      <Alert kind="error" testId="project-error">
        {t('project.loadError')}{' '}
        <button type="button" className="link-button" onClick={() => void reload()}>
          {t('common.retry')}
        </button>
      </Alert>
    );
  }

  const { project, members } = load;
  const isManager = project.myRole === 'MANAGER';
  const isAdmin = state.status === 'authenticated' && state.user.accountRole === 'ADMIN';

  async function onDelete() {
    const ok = await confirm({
      title: t('project.deleteTitle'),
      message: t('project.deleteText', { name: project.name }),
      confirmLabel: t('project.delete'),
      danger: true,
    });
    if (!ok) return;
    setError(null);
    try {
      await deleteProject(project.id);
      toast(t('project.deleted'));
      navigate('/projects', { replace: true });
    } catch (e) {
      if (!(e instanceof ApiError && e.status === 401)) setError(t('project.deleteError'));
    }
  }

  return (
    <main className="stack" data-testid="project-detail">
      <Link to="/projects" className="row" style={{ gap: 6, fontWeight: 600, textDecoration: 'none' }}>
        <ArrowLeft size={16} aria-hidden="true" />
        {t('project.back')}
      </Link>
      <div className="page-head">
        <div className="page-head-text">
          <span className="eyebrow">
            {project.myRole && t('project.yourRole', { role: t(`roles.${project.myRole}`) })} ·{' '}
            {t('project.createdBy', { name: userLabel(t, project.createdBy) })}
          </span>
          <h1 className="page-title" data-testid="project-name">
            {project.name}
          </h1>
          {project.description && (
            <p className="muted pre-wrap" data-testid="project-description">
              {project.description}
            </p>
          )}
        </div>
        {isManager && !editing && (
          <div className="row">
            <button type="button" className="btn" onClick={() => setEditing(true)} data-testid="project-edit-button">
              <Pencil size={16} aria-hidden="true" />
              {t('project.edit')}
            </button>
            <button type="button" className="btn btn-danger" onClick={() => void onDelete()} data-testid="project-delete-button">
              <Trash2 size={16} aria-hidden="true" />
              {t('project.delete')}
            </button>
          </div>
        )}
      </div>
      {error && (
        <Alert kind="error" testId="project-delete-error">
          {error}
        </Alert>
      )}
      {project.taskCounts && (
        <section className="tile">
          <CountsBar counts={project.taskCounts} testId="project-counts" />
        </section>
      )}

      {isManager && editing && (
        <EditProjectForm
          project={project}
          onSaved={(updated) => {
            setLoad({ ...load, project: updated });
            setEditing(false);
            toast(t('project.saved'));
          }}
          onCancel={() => setEditing(false)}
          onReload={async () => {
            await reload();
            setEditing(false);
          }}
        />
      )}

      {isAdmin && (
        <Alert kind="info" testId="project-admin-note">
          {t('admin.adminProjectNote')}
        </Alert>
      )}
      <div className="grid-main">
        {isAdmin ? (
          <AppointManagerForm projectId={project.id} onAppointed={reload} />
        ) : (
          <TasksSection projectId={project.id} initialStatus={initialStatus} onTasksChanged={() => void refreshCounts()} />
        )}
        <MembersSection projectId={project.id} members={members} canManage={isManager} currentUserId={currentUserId} onChanged={reload} />
      </div>

      {isManager && <ActivitySection projectId={project.id} refreshKey={activityKey} />}
    </main>
  );
}

function EditProjectForm({
  project,
  onSaved,
  onCancel,
  onReload,
}: {
  project: Project;
  onSaved: (project: Project) => void;
  onCancel: () => void;
  onReload: () => Promise<void>;
}) {
  const { t } = useTranslation();
  const [name, setName] = useState(project.name);
  const [description, setDescription] = useState(project.description ?? '');
  const [error, setError] = useState<string | null>(null);
  const [conflict, setConflict] = useState(false);
  const [pending, setPending] = useState(false);

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    if (pending) return;
    if (!name.trim()) {
      setError(t('projects.nameRequired'));
      return;
    }
    setPending(true);
    setError(null);
    setConflict(false);
    try {
      onSaved(await updateProject(project.id, project.version, { name, description: description.trim() ? description : null }));
    } catch (e) {
      if (e instanceof ApiError && e.status === 401) return;
      setConflict(e instanceof ApiError && e.status === 409);
      if (e instanceof ApiError && e.status === 409) setError(t('project.conflict'));
      else if (e instanceof ApiError && e.fieldCode('name') === 'TOO_LONG') setError(t('projects.nameTooLong', { max: PROJECT_NAME_MAX }));
      else if (e instanceof ApiError && e.fieldCode('name')) setError(t('projects.nameRequired'));
      else if (e instanceof ApiError && e.fieldCode('description')) setError(t('projects.descriptionTooLong', { max: PROJECT_DESCRIPTION_MAX }));
      else setError(t('common.genericError'));
    } finally {
      setPending(false);
    }
  }

  return (
    <section className="tile" aria-labelledby="edit-project-title">
      <h2 id="edit-project-title">{t('project.editTitle')}</h2>
      <form className="form" onSubmit={onSubmit} noValidate data-testid="project-edit-form">
        <label className="field">
          {t('projects.name')}
          <input value={name} onChange={(e) => setName(e.target.value)} maxLength={PROJECT_NAME_MAX} data-testid="project-edit-name" />
        </label>
        <label className="field">
          <span>{t('projects.description')} <span className="hint">({t('common.optional')})</span></span>
          <textarea
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            maxLength={PROJECT_DESCRIPTION_MAX}
            rows={3}
            data-testid="project-edit-description"
          />
        </label>
        {error && (
          <Alert kind="error" testId="project-edit-error">
            {error}{' '}
            {conflict && (
              <button type="button" className="link-button" onClick={() => void onReload()}>
                {t('project.reload')}
              </button>
            )}
          </Alert>
        )}
        <div className="row">
          <button type="submit" className="btn btn-primary" disabled={pending} data-testid="project-edit-submit">
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
  const { t } = useTranslation();
  const { confirm, toast } = useFeedback();
  const [email, setEmail] = useState('');
  const [role, setRole] = useState<ProjectRole>('MEMBER');
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);

  function messageFor(e: unknown): string | null {
    if (e instanceof ApiError) {
      if (e.status === 401) return null;
      if (e.problem?.code === 'ALREADY_MEMBER') return t('members.already');
      if (e.problem?.code === 'LAST_MANAGER') return t('members.lastManager');
      if (e.fieldCode('email') === 'USER_NOT_ELIGIBLE') return t('members.notEligible');
      if (e.fieldCode('email')) return t('members.emailRequired');
      if (e.status === 403) return t('members.noPermission');
      if (e.status === 404) return t('members.gone');
    }
    return t('common.genericError');
  }

  async function run(action: () => Promise<unknown>, success?: string, after?: () => void) {
    if (pending) return;
    setPending(true);
    setError(null);
    try {
      await action();
      after?.();
      if (success) toast(success);
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
      setError(t('members.emailRequired'));
      return;
    }
    void run(() => addMember(projectId, email.trim(), role), t('members.added'), () => setEmail(''));
  }

  async function onRemove(member: Member) {
    const self = member.user.id === currentUserId;
    const ok = await confirm({
      title: self ? t('members.leaveTitle') : t('members.removeTitle'),
      message: self ? t('members.leaveText') : t('members.removeText', { name: userLabel(t, member.user) }),
      confirmLabel: t('common.remove'),
      danger: true,
    });
    if (ok) void run(() => removeMember(projectId, member.user.id));
  }

  async function onRoleChange(member: Member, projectRole: ProjectRole) {
    if (projectRole === 'MEMBER') {
      const ok = await confirm({
        title: t('members.demoteTitle'),
        message: t('members.demoteText', { name: userLabel(t, member.user) }),
        danger: true,
      });
      if (!ok) return;
    }
    void run(() => changeMemberRole(projectId, member.user.id, projectRole));
  }

  return (
    <section className="tile" aria-labelledby="members-title" data-testid="members-section" style={{ alignSelf: 'start' }}>
      <h2 id="members-title">
        {t('members.title')} <span className="eyebrow">{members.length}</span>
      </h2>
      <ul className="list" data-testid="member-list">
        {members.map((member) => (
          <li key={member.user.id} data-testid="member-item" className="row-between">
            <span className="user-chip" style={{ padding: 0 }}>
              <Avatar name={member.user.name} small tone={member.projectRole === 'MANAGER' ? 'brick' : 'pine'} />
              <span data-testid="member-name">{userLabel(t, member.user)}</span>
            </span>
            {canManage ? (
              <span className="row" style={{ gap: 6 }}>
                <select
                  className="select-compact"
                  value={member.projectRole}
                  onChange={(e) => void onRoleChange(member, e.target.value as ProjectRole)}
                  disabled={pending}
                  aria-label={t('members.roleOf', { name: userLabel(t, member.user) })}
                  data-testid="member-role-select"
                >
                  <option value="MANAGER">{t('roles.MANAGER')}</option>
                  <option value="MEMBER">{t('roles.MEMBER')}</option>
                </select>
                <button
                  type="button"
                  className="btn btn-small btn-ghost"
                  onClick={() => void onRemove(member)}
                  disabled={pending}
                  aria-label={`${t('common.remove')}: ${userLabel(t, member.user)}`}
                  data-testid="member-remove-button"
                >
                  <Trash2 size={16} aria-hidden="true" />
                </button>
              </span>
            ) : (
              <span className="pill pill-role">{t(`roles.${member.projectRole}`)}</span>
            )}
          </li>
        ))}
      </ul>
      {error && (
        <Alert kind="error" testId="members-error">
          {error}
        </Alert>
      )}
      {canManage && (
        <form className="form" onSubmit={onAdd} noValidate data-testid="member-add-form">
          <label className="field">
            {t('members.emailLabel')}
            <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} data-testid="member-email-input" />
          </label>
          <div className="row">
            <label className="field" style={{ flex: 1 }}>
              {t('members.role')}
              <select value={role} onChange={(e) => setRole(e.target.value as ProjectRole)} data-testid="member-role-input">
                <option value="MEMBER">{t('roles.MEMBER')}</option>
                <option value="MANAGER">{t('roles.MANAGER')}</option>
              </select>
            </label>
            <button type="submit" className="btn btn-primary" disabled={pending} data-testid="member-add-submit" style={{ alignSelf: 'flex-end' }}>
              <UserPlus size={16} aria-hidden="true" />
              {t('members.add')}
            </button>
          </div>
        </form>
      )}
    </section>
  );
}

/** The Admin account appoints a Manager, e.g. before deactivating the only one (FR3, D12, D14). */
function AppointManagerForm({ projectId, onAppointed }: { projectId: number; onAppointed: () => Promise<void> }) {
  const { t } = useTranslation();
  const { toast } = useFeedback();
  const [email, setEmail] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    if (pending) return;
    if (!email.trim()) {
      setError(t('members.emailRequired'));
      return;
    }
    setPending(true);
    setError(null);
    try {
      await appointManager(projectId, email.trim());
      toast(t('admin.appointed'));
      setEmail('');
      await onAppointed();
    } catch (e) {
      if (e instanceof ApiError && e.status === 401) return;
      if (e instanceof ApiError && e.fieldCode('email') === 'USER_NOT_ELIGIBLE') setError(t('admin.appointNotEligible'));
      else if (e instanceof ApiError && e.fieldCode('email')) setError(t('members.emailRequired'));
      else setError(t('common.genericError'));
    } finally {
      setPending(false);
    }
  }

  return (
    <section className="tile" aria-labelledby="appoint-title" data-testid="appoint-manager-section" style={{ alignSelf: 'start' }}>
      <div className="stack" style={{ gap: 4 }}>
        <h2 id="appoint-title">{t('admin.appointTitle')}</h2>
        <p className="muted" style={{ fontSize: 13 }}>
          {t('admin.appointText')}
        </p>
      </div>
      <form className="form" onSubmit={onSubmit} noValidate data-testid="appoint-manager-form">
        <label className="field">
          {t('admin.appointEmail')}
          <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} data-testid="appoint-manager-email" />
        </label>
        {error && (
          <Alert kind="error" testId="appoint-manager-error">
            {error}
          </Alert>
        )}
        <button type="submit" className="btn btn-primary" disabled={pending} data-testid="appoint-manager-submit" style={{ alignSelf: 'flex-start' }}>
          <UserPlus size={16} aria-hidden="true" />
          {t('admin.appoint')}
        </button>
      </form>
    </section>
  );
}
