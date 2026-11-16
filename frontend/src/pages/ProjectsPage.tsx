import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
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
import { TaskCountsLine } from '../components/TaskCountsLine';
import { projectRoleLabel } from '../labels';

type Load = { status: 'loading' } | { status: 'error' } | { status: 'ready'; data: ProjectPage };

/** Projects screen (Worksheet 03, Ex. 4): list, search by name, pagination and creation. */
export function ProjectsPage() {
  const { state } = useAuth();
  const canCreate = state.status === 'authenticated' && state.user.accountRole === 'MANAGER';

  const [search, setSearch] = useState('');
  const [query, setQuery] = useState('');
  const [page, setPage] = useState(0);
  const [load, setLoad] = useState<Load>({ status: 'loading' });

  // Search after the user stops typing for a moment.
  useEffect(() => {
    const timer = setTimeout(() => {
      setQuery(search);
      setPage(0);
    }, 300);
    return () => clearTimeout(timer);
  }, [search]);

  const reload = useCallback(async () => {
    setLoad({ status: 'loading' });
    try {
      setLoad({ status: 'ready', data: await listProjects(query, page) });
    } catch (e) {
      if (!(e instanceof ApiError && e.status === 401)) {
        setLoad({ status: 'error' });
      }
    }
  }, [query, page]);

  useEffect(() => {
    void reload();
  }, [reload]);

  const totalPages = load.status === 'ready' ? Math.max(1, Math.ceil(load.data.totalItems / PAGE_SIZE)) : 1;

  return (
    <main className="page" data-testid="projects-page">
      <h1>Projetos</h1>

      {canCreate && <CreateProjectForm onCreated={() => (page === 0 ? void reload() : setPage(0))} />}

      <label className="search">
        Pesquisar por nome
        <input
          type="search"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          maxLength={PROJECT_NAME_MAX}
          data-testid="project-search"
        />
      </label>

      {load.status === 'loading' && <p data-testid="projects-loading">A carregar…</p>}
      {load.status === 'error' && (
        <p role="alert" className="error" data-testid="projects-error">
          Não foi possível carregar os projetos.{' '}
          <button type="button" onClick={() => void reload()}>
            Tentar novamente
          </button>
        </p>
      )}
      {load.status === 'ready' && load.data.items.length === 0 && (
        <p data-testid="projects-empty">
          {query.trim() ? 'Nenhum projeto encontrado.' : 'Ainda não pertence a nenhum projeto.'}
        </p>
      )}
      {load.status === 'ready' && load.data.items.length > 0 && (
        <>
          <p data-testid="projects-total">
            {load.data.totalItems} {load.data.totalItems === 1 ? 'projeto' : 'projetos'}
          </p>
          <ul className="cards" data-testid="project-list">
            {load.data.items.map((project) => (
              <li key={project.id} className="card" data-testid="project-item">
                <Link to={`/projects/${project.id}`} data-testid="project-link">
                  <strong>{project.name}</strong>
                </Link>
                {project.myRole && <span className="tag">{projectRoleLabel[project.myRole]}</span>}
                {project.taskCounts && <TaskCountsLine counts={project.taskCounts} />}
                <span className="muted">
                  {project.memberCount} {project.memberCount === 1 ? 'membro' : 'membros'}
                </span>
              </li>
            ))}
          </ul>
          {totalPages > 1 && (
            <nav className="pagination" aria-label="Páginas">
              <button
                type="button"
                onClick={() => setPage(page - 1)}
                disabled={page === 0}
                data-testid="pagination-prev"
              >
                Anterior
              </button>
              <span data-testid="pagination-info">
                Página {page + 1} de {totalPages}
              </span>
              <button
                type="button"
                onClick={() => setPage(page + 1)}
                disabled={page + 1 >= totalPages}
                data-testid="pagination-next"
              >
                Seguinte
              </button>
            </nav>
          )}
        </>
      )}
    </main>
  );
}

function CreateProjectForm({ onCreated }: { onCreated: () => void }) {
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [pending, setPending] = useState(false);

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    if (pending) return;
    setSuccess(null);
    if (!name.trim()) {
      setError('O nome é obrigatório.');
      return;
    }
    setPending(true);
    setError(null);
    try {
      const project = await createProject(name, description);
      setName('');
      setDescription('');
      setSuccess(`Projeto "${project.name}" criado.`);
      onCreated();
    } catch (e) {
      // The form keeps what was typed (Worksheet 03, Ex. 7).
      if (e instanceof ApiError && e.fieldCode('name') === 'TOO_LONG') {
        setError(`O nome pode ter no máximo ${PROJECT_NAME_MAX} caracteres.`);
      } else if (e instanceof ApiError && e.fieldCode('name')) {
        setError('O nome é obrigatório.');
      } else if (e instanceof ApiError && e.fieldCode('description')) {
        setError(`A descrição pode ter no máximo ${PROJECT_DESCRIPTION_MAX} caracteres.`);
      } else if (e instanceof ApiError && e.status === 403) {
        setError('Não tem permissão para criar projetos.');
      } else if (!(e instanceof ApiError && e.status === 401)) {
        setError('Não foi possível criar o projeto. Tente novamente.');
      }
    } finally {
      setPending(false);
    }
  }

  return (
    <form className="panel" onSubmit={onSubmit} noValidate data-testid="project-create-form">
      <h2>Novo projeto</h2>
      <label>
        Nome
        <input
          value={name}
          onChange={(e) => setName(e.target.value)}
          maxLength={PROJECT_NAME_MAX}
          required
          data-testid="project-name-input"
        />
      </label>
      <label>
        Descrição (opcional)
        <textarea
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          maxLength={PROJECT_DESCRIPTION_MAX}
          rows={3}
          data-testid="project-description-input"
        />
      </label>
      {error && (
        <p role="alert" className="error" data-testid="project-create-error">
          {error}
        </p>
      )}
      {success && (
        <p role="status" className="success" data-testid="project-create-success">
          {success}
        </p>
      )}
      <button type="submit" disabled={pending} data-testid="project-create-submit">
        {pending ? 'A criar…' : 'Criar projeto'}
      </button>
    </form>
  );
}
