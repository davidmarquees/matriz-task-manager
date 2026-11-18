import { ArrowUpRight, FolderOpen, Plus } from 'lucide-react';
import { useCallback, useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import { ApiError } from '../api/client';
import { getDashboard, type Dashboard } from '../api/dashboard';
import type { ProjectSummary, TaskCounts } from '../api/projects';
import { useAuth } from '../auth/AuthContext';
import { CountsBar } from '../components/CountsBar';
import { PriorityChart, StatusChart } from '../components/DashboardCharts';
import { WeekDue } from '../components/WeekDue';
import { formatLongDate } from '../labels';
import { Alert, EmptyState, Skeleton } from '../ui/Bits';

type Load = { status: 'loading' } | { status: 'error' } | { status: 'admin' } | { status: 'ready'; data: Dashboard };

const total = (c: TaskCounts) => c.TODO + c.IN_PROGRESS + c.DONE;

const reducedMotion = () => typeof window.matchMedia === 'function' && window.matchMedia('(prefers-reduced-motion: reduce)').matches;

/** Counts from 0 up to `value` once, on load (about 0.9 s); shows the value at once with reduced motion. */
function useCountUp(value: number): number {
  const [shown, setShown] = useState(() => (reducedMotion() || typeof requestAnimationFrame !== 'function' ? value : 0));
  useEffect(() => {
    if (reducedMotion() || typeof requestAnimationFrame !== 'function') {
      setShown(value);
      return;
    }
    const start = performance.now();
    let frame = 0;
    const step = (now: number) => {
      const k = Math.min(1, (now - start) / 900);
      setShown(Math.round(value * (1 - Math.pow(1 - k, 3))));
      if (k < 1) frame = requestAnimationFrame(step);
    };
    frame = requestAnimationFrame(step);
    return () => cancelAnimationFrame(frame);
  }, [value]);
  return shown;
}

function CountUp({ value }: { value: number }) {
  return <>{useCountUp(value)}</>;
}

/** Greeting by the time of day on the user's device. */
function greetingKey(hour: number): string {
  if (hour < 12) return 'dashboard.greetingMorning';
  if (hour < 20) return 'dashboard.greetingAfternoon';
  return 'dashboard.greetingEvening';
}

/** Dashboard (FR9, wireframe): totals, the user's projects with counts per status, progress, shortcuts. */
export function DashboardPage() {
  const { t, i18n } = useTranslation();
  const { state } = useAuth();
  const [load, setLoad] = useState<Load>({ status: 'loading' });

  const reload = useCallback(async () => {
    try {
      setLoad({ status: 'ready', data: await getDashboard() });
    } catch (e) {
      if (e instanceof ApiError && e.status === 403) setLoad({ status: 'admin' });
      else if (!(e instanceof ApiError && e.status === 401)) setLoad({ status: 'error' });
    }
  }, []);

  useEffect(() => {
    void reload();
  }, [reload]);

  if (state.status !== 'authenticated') return null;
  const user = state.user;
  const canCreate = user.accountRole === 'MANAGER';
  const firstName = (user.name ?? '').split(/\s+/)[0];

  return (
    <main className="stack dash" data-testid="home-page">
      <div className="dash-backdrop" aria-hidden="true">
        <span />
        <span />
        <span />
      </div>
      <div className="page-head">
        <div className="page-head-text">
          <span className="eyebrow">{formatLongDate(new Date(), i18n.language)}</span>
          <h1 className="page-title">{t('dashboard.title')}</h1>
          <p className="muted">
            {t(greetingKey(new Date().getHours()), { name: firstName })} {t('dashboard.subtitle')}
          </p>
        </div>
        <div className="row">
          {canCreate && (
            <Link className="btn btn-primary" to="/projects#new" data-testid="dashboard-new-project">
              <Plus size={18} aria-hidden="true" />
              {t('dashboard.newProject')}
            </Link>
          )}
          <Link className="btn" to="/projects" data-testid="home-projects-link">
            {t('dashboard.seeProjects')}
          </Link>
        </div>
      </div>

      {load.status === 'loading' && <Skeleton rows={4} label={t('common.loading')} testId="dashboard-loading" />}
      {load.status === 'error' && (
        <Alert kind="error" testId="dashboard-error">
          {t('dashboard.error')}{' '}
          <button type="button" className="link-button" onClick={() => void reload()}>
            {t('common.retry')}
          </button>
        </Alert>
      )}
      {load.status === 'admin' && (
        <section className="tile" data-testid="dashboard-admin">
          <h2>{t('dashboard.adminTitle')}</h2>
          <p className="muted">{t('dashboard.adminText')}</p>
          <Link className="btn" to="/projects" style={{ alignSelf: 'flex-start' }}>
            {t('dashboard.seeProjects')}
          </Link>
        </section>
      )}
      {load.status === 'ready' && <DashboardContent data={load.data} canCreate={canCreate} />}
    </main>
  );
}

function DashboardContent({ data, canCreate }: { data: Dashboard; canCreate: boolean }) {
  const { t } = useTranslation();
  const counts = data.totals.taskCounts;
  const all = total(counts);
  const managerCount = data.projects.filter((p) => p.myRole === 'MANAGER').length;
  const memberCount = data.projects.length - managerCount;
  const withStatus = (key: keyof TaskCounts) => data.projects.filter((p) => (p.taskCounts?.[key] ?? 0) > 0).length;
  const donePct = all === 0 ? 0 : Math.round((counts.DONE / all) * 100);
  const progressPct = all === 0 ? 0 : Math.round((counts.IN_PROGRESS / all) * 100);

  if (data.projects.length === 0) {
    return (
      <EmptyState icon={FolderOpen} title={t('dashboard.empty')} hint={t('dashboard.emptyHint')} testId="dashboard-empty">
        {canCreate && (
          <Link className="btn btn-primary" to="/projects#new">
            <Plus size={18} aria-hidden="true" />
            {t('dashboard.newProject')}
          </Link>
        )}
      </EmptyState>
    );
  }

  return (
    <>
      <section className="grid-kpi lift" aria-label={t('dashboard.totalsLabel')}>
        <div className="tile tile-brick" data-testid="dashboard-kpi-projects">
          <div className="kpi-top">
            {t('dashboard.projects')}
            <Link className="arrow-link" to="/projects" aria-label={t('dashboard.seeProjects')}>
              <ArrowUpRight size={18} aria-hidden="true" />
            </Link>
          </div>
          <span className="kpi-value">
            <CountUp value={data.totals.projects} />
          </span>
          <span style={{ fontSize: 13 }}>
            {[managerCount && t('dashboard.asManager', { count: managerCount }), memberCount && t('dashboard.asMember', { count: memberCount })]
              .filter(Boolean)
              .join(' · ')}
          </span>
        </div>
        {(['TODO', 'IN_PROGRESS', 'DONE'] as const).map((key) => (
          <div key={key} className="tile" data-testid={`dashboard-kpi-${key}`}>
            <div className="kpi-top">
              <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
                <span className={`dot ${key === 'TODO' ? 'dot-todo hatch' : key === 'DONE' ? 'dot-done' : 'dot-progress'}`} aria-hidden="true" />
                {t(`statusCount.${key}`)}
              </span>
              <Link
                className="arrow-link"
                to={`/projects?status=${key}`}
                aria-label={`${t('dashboard.seeProjects')}: ${t(`statusCount.${key}`)}`}
                data-testid={`dashboard-kpi-${key}-link`}
              >
                <ArrowUpRight size={18} aria-hidden="true" />
              </Link>
            </div>
            <span
              className="kpi-value"
              style={{ color: key === 'DONE' ? 'var(--pine)' : key === 'IN_PROGRESS' ? 'var(--ochre)' : undefined }}
            >
              <CountUp value={counts[key]} />
            </span>
            <span className="muted" style={{ fontSize: 13 }}>
              {t('dashboard.inProjects', { count: withStatus(key) })}
            </span>
          </div>
        ))}
      </section>

      <div className="grid-charts">
        <StatusChart counts={counts} />
        <PriorityChart counts={data.totals.priorityCounts} />
      </div>

      <div className="grid-main">
        <section className="tile projects-tile" aria-labelledby="my-projects">
          <div className="row-between">
            <h2 id="my-projects">{t('dashboard.myProjects')}</h2>
            <span className="eyebrow">{t('dashboard.byName')}</span>
          </div>
          <div className="projects-scroll">
            <ul className="list" data-testid="dashboard-projects">
              {data.projects.map((p) => (
                <ProjectRow key={p.id} project={p} />
              ))}
            </ul>
          </div>
        </section>
        <WeekDue />
      </div>

      <div className="grid-pair">
        <section className="tile gauge" aria-labelledby="progress-title">
          <div className="row-between">
            <h2 id="progress-title">{t('dashboard.progress')}</h2>
            <span className="eyebrow">{all === 0 ? t('dashboard.noTasks') : t('dashboard.doneOf', { done: counts.DONE, total: all })}</span>
          </div>
          <div className="gauge-fig">
            <svg width="260" height="146" viewBox="0 0 220 124" role="img" aria-label={t('dashboard.doneOf', { done: counts.DONE, total: all })}>
              <path d="M20 112 A90 90 0 0 1 200 112" fill="none" stroke="var(--soft)" strokeWidth="22" strokeLinecap="round" />
              {progressPct + donePct > 0 && (
                <path
                  className="gauge-arc"
                  d="M20 112 A90 90 0 0 1 200 112"
                  fill="none"
                  stroke="var(--ochre)"
                  strokeWidth="22"
                  strokeLinecap="round"
                  pathLength={100}
                  strokeDasharray={`${donePct + progressPct} 100`}
                  style={{ ['--arc' as string]: `${donePct + progressPct}px` }}
                />
              )}
              {donePct > 0 && (
                <path
                  className="gauge-arc"
                  d="M20 112 A90 90 0 0 1 200 112"
                  fill="none"
                  stroke="var(--pine)"
                  strokeWidth="22"
                  strokeLinecap="round"
                  pathLength={100}
                  strokeDasharray={`${donePct} 100`}
                  style={{ ['--arc' as string]: `${donePct}px` }}
                />
              )}
            </svg>
            <span className="gauge-value" data-testid="dashboard-progress">
              {donePct}%
            </span>
          </div>
          <div className="legend gauge-legend">
            <span>
              <span className="dot dot-done" aria-hidden="true" />
              {t('dashboard.pctDone', { pct: donePct })}
            </span>
            <span>
              <span className="dot dot-progress" aria-hidden="true" />
              {t('dashboard.pctProgress', { pct: progressPct })}
            </span>
            <span>
              <span className="dot dot-todo hatch" aria-hidden="true" />
              {t('dashboard.pctTodo', { pct: all === 0 ? 0 : 100 - donePct - progressPct })}
            </span>
          </div>
        </section>

        <section className="tile tile-dark" aria-labelledby="shortcuts">
          <h2 id="shortcuts">{t('dashboard.shortcuts')}</h2>
          <p className="muted" style={{ fontSize: 13 }}>
            {t('dashboard.shortcutsText')}
          </p>
          {canCreate && (
            <Link className="btn btn-primary btn-arrow" to="/projects#new">
              {t('dashboard.newProject')}
              <span className="arrow-dot" aria-hidden="true">
                <ArrowUpRight size={18} />
              </span>
            </Link>
          )}
          <div className="shortcut-row">
            {data.projects.slice(0, 2).map((p) => (
              <Link key={p.id} className="btn btn-arrow shortcut" to={`/projects/${p.id}`}>
                <span style={{ overflow: 'hidden', textOverflow: 'ellipsis' }}>{p.name}</span>
                <ArrowUpRight size={18} aria-hidden="true" style={{ marginRight: 12 }} />
              </Link>
            ))}
          </div>
        </section>
      </div>
    </>
  );
}

function ProjectRow({ project }: { project: ProjectSummary }) {
  const { t } = useTranslation();
  const c = project.taskCounts ?? { TODO: 0, IN_PROGRESS: 0, DONE: 0 };
  const all = total(c);
  return (
    <li className="project-row" data-testid="dashboard-project-item">
      <div style={{ display: 'flex', flexDirection: 'column', gap: 4, minWidth: 0 }}>
        <Link className="project-name" to={`/projects/${project.id}`}>
          {project.name}
        </Link>
        <span className="muted" style={{ fontSize: 13 }}>
          {project.myRole && t(`roles.${project.myRole}`)} · {all === 0 ? t('dashboard.noTasks') : t('dashboard.tasks', { count: all })} ·{' '}
          {t('dashboard.members', { count: project.memberCount })}
        </span>
      </div>
      <div className="bars">
        <CountsBar counts={c} live />
      </div>
      <Link className="arrow-link" to={`/projects/${project.id}`} aria-label={t('dashboard.openProject', { name: project.name })}>
        <ArrowUpRight size={18} aria-hidden="true" />
      </Link>
    </li>
  );
}

