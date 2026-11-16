import { ArrowUpRight, FolderOpen, Plus, Search, X } from 'lucide-react';
import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { Link, useLocation } from 'react-router-dom';
import { ApiError } from '../api/client';
import {
  createProject,
  listProjects,
  PAGE_SIZE,
  PROJECT_DESCRIPTION_MAX,
  PROJECT_NAME_MAX,
  type ProjectPage,
} from '../api/projects';
import { useAuth } from '../auth/AuthContext';
import { Alert, EmptyState, Skeleton } from '../ui/Bits';
import { useFeedback } from '../ui/Feedback';
import { CountsBar } from '../components/CountsBar';

type Load = { status: 'loading' } | { status: 'error' } | { status: 'ready'; data: ProjectPage };

/** Projects (Worksheet 03, Ex. 4 and 7): search by name, pagination, creation for Manager accounts. */
export function ProjectsPage() {
  const { t } = useTranslation();
  const { state } = useAuth();
  const location = useLocation();
  const canCreate = state.status === 'authenticated' && state.user.accountRole === 'MANAGER';

  const [search, setSearch] = useState('');
  const [query, setQuery] = useState('');
  const [page, setPage] = useState(0);
  const [load, setLoad] = useState<Load>({ status: 'loading' });
  const [creating, setCreating] = useState(location.hash === '#new');

  useEffect(() => {
    const timer = setTimeout(() => {
      setQuery(search);
      setPage(0);
    }, 300);
    return () => clearTimeout(timer);
  }, [search]);

  useEffect(() => {
    if (location.hash === '#new') setCreating(true);
  }, [location.hash]);

  const reload = useCallback(async () => {
    try {
      setLoad({ status: 'ready', data: await listProjects(query, page) });
    } catch (e) {
      if (!(e instanceof ApiError && e.status === 401)) setLoad({ status: 'error' });
    }
  }, [query, page]);

  useEffect(() => {
    setLoad({ status: 'loading' });
    void reload();
  }, [reload]);

  const totalPages = load.status === 'ready' ? Math.max(1, Math.ceil(load.data.totalItems / PAGE_SIZE)) : 1;

  return (
    <main className="stack" data-testid="projects-page">
      <div className="page-head">
        <div className="page-head-text">
          <span className="eyebrow">{t('projects.subtitle')}</span>
          <h1 className="page-title">{t('projects.title')}</h1>
        </div>
        {canCreate && !creating && (
          <button type="button" className="btn btn-primary" onClick={() => setCreating(true)} data-testid="project-create-open">
            <Plus size={18} aria-hidden="true" />
            {t('projects.createTitle')}
          </button>
        )}
      </div>

      {canCreate && creating && (
        <CreateProjectForm
          onCreated={() => (page === 0 ? void reload() : setPage(0))}
          onClose={() => setCreating(false)}
        />
      )}

      <section className="tile">
        <label className="field" style={{ maxWidth: 420 }}>
          {t('projects.search')}
          <span style={{ position: 'relative', display: 'block' }}>
            <Search size={18} aria-hidden="true" className="search-icon" />
            <input
              type="search"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              maxLength={PROJECT_NAME_MAX}
              data-testid="project-search"
            />
          </span>
        </label>

        {load.status === 'loading' && <Skeleton rows={3} label={t('common.loading')} testId="projects-loading" />}
        {load.status === 'error' && (
          <Alert kind="error" testId="projects-error">
            {t('projects.loadError')}{' '}
            <button type="button" className="link-button" onClick={() => void reload()}>
              {t('common.retry')}
            </button>
          </Alert>
        )}
        {load.status === 'ready' && load.data.items.length === 0 && (
          <EmptyState icon={FolderOpen} title={query.trim() ? t('projects.noResults') : t('projects.none')} testId="projects-empty" />
        )}
        {load.status === 'ready' && load.data.items.length > 0 && (
          <>
            <p className="eyebrow" data-testid="projects-total">
              {t('projects.total', { count: load.data.totalItems })}
            </p>
            <ul className="list" data-testid="project-list">
              {load.data.items.map((project) => (
                <li key={project.id} className="project-row" data-testid="project-item">
                  <div style={{ display: 'flex', flexDirection: 'column', gap: 6, minWidth: 0 }}>
                    <Link className="project-name" to={`/projects/${project.id}`} data-testid="project-link">
                      {project.name}
                    </Link>
                    <span className="row" style={{ gap: 8 }}>
                      {project.myRole && <span className="pill pill-role">{t(`roles.${project.myRole}`)}</span>}
                      <span className="muted" style={{ fontSize: 13 }}>
                        {t('dashboard.members', { count: project.memberCount })}
                      </span>
                    </span>
                  </div>
                  <div className="bars">{project.taskCounts && <CountsBar counts={project.taskCounts} />}</div>
                  <Link className="arrow-link" to={`/projects/${project.id}`} aria-label={t('dashboard.openProject', { name: project.name })}>
                    <ArrowUpRight size={18} aria-hidden="true" />
                  </Link>
                </li>
              ))}
            </ul>
            {totalPages > 1 && (
              <nav className="row" aria-label={t('projects.pagination')}>
                <button type="button" className="btn btn-small" onClick={() => setPage(page - 1)} disabled={page === 0} data-testid="pagination-prev">
                  {t('projects.previous')}
                </button>
                <span className="eyebrow" data-testid="pagination-info">
                  {t('projects.page', { page: page + 1, total: totalPages })}
                </span>
                <button
                  type="button"
                  className="btn btn-small"
                  onClick={() => setPage(page + 1)}
                  disabled={page + 1 >= totalPages}
                  data-testid="pagination-next"
                >
                  {t('projects.next')}
                </button>
              </nav>
            )}
          </>
        )}
      </section>
    </main>
  );
}

function CreateProjectForm({ onCreated, onClose }: { onCreated: () => void; onClose: () => void }) {
  const { t } = useTranslation();
  const { toast } = useFeedback();
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [pending, setPending] = useState(false);
  const nameRef = useRef<HTMLInputElement>(null);

  useEffect(() => nameRef.current?.focus(), []);

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    if (pending) return;
    if (!name.trim()) {
      setError(t('projects.nameRequired'));
      return;
    }
    setPending(true);
    setError(null);
    try {
      const project = await createProject(name, description);
      setName('');
      setDescription('');
      toast(t('projects.created', { name: project.name }));
      onCreated();
    } catch (e) {
      // the form keeps what was typed
      if (e instanceof ApiError && e.status === 401) return;
      if (e instanceof ApiError && e.fieldCode('name') === 'TOO_LONG') setError(t('projects.nameTooLong', { max: PROJECT_NAME_MAX }));
      else if (e instanceof ApiError && e.fieldCode('name')) setError(t('projects.nameRequired'));
      else if (e instanceof ApiError && e.fieldCode('description')) setError(t('projects.descriptionTooLong', { max: PROJECT_DESCRIPTION_MAX }));
      else if (e instanceof ApiError && e.status === 403) setError(t('projects.noPermission'));
      else setError(t('projects.createError'));
    } finally {
      setPending(false);
    }
  }

  return (
    <section className="tile" id="new" aria-labelledby="new-project-title">
      <div className="row-between">
        <h2 id="new-project-title">{t('projects.createTitle')}</h2>
        <button type="button" className="btn btn-small btn-ghost" onClick={onClose} aria-label={t('common.cancel')}>
          <X size={18} aria-hidden="true" />
        </button>
      </div>
      <form className="form" onSubmit={onSubmit} noValidate data-testid="project-create-form">
        <label className="field">
          {t('projects.name')}
          <input
            ref={nameRef}
            value={name}
            onChange={(e) => setName(e.target.value)}
            maxLength={PROJECT_NAME_MAX}
            required
            data-testid="project-name-input"
          />
        </label>
        <label className="field">
          <span>{t('projects.description')} <span className="hint">({t('common.optional')})</span></span>
          <textarea
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            maxLength={PROJECT_DESCRIPTION_MAX}
            rows={3}
            data-testid="project-description-input"
          />
          <span className="hint">{t('common.chars', { count: description.length, max: PROJECT_DESCRIPTION_MAX })}</span>
        </label>
        {error && (
          <Alert kind="error" testId="project-create-error">
            {error}
          </Alert>
        )}
        <div className="row">
          <button type="submit" className="btn btn-primary" disabled={pending} data-testid="project-create-submit">
            {pending ? t('projects.creating') : t('projects.create')}
          </button>
        </div>
      </form>
    </section>
  );
}
