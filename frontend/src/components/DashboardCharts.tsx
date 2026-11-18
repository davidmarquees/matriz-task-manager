import { useTranslation } from 'react-i18next';
import type { PriorityCounts } from '../api/dashboard';
import type { TaskCounts } from '../api/projects';

type Row = { key: string; label: string; value: number; tone: string };

/**
 * Horizontal bar chart with the value and its share written next to each bar, so the data never
 * depends on colour alone (NFR8). Bars are scaled to the largest value; they grow once on load.
 */
function BarChart({ titleId, title, rows, empty, testId }: { titleId: string; title: string; rows: Row[]; empty: string; testId: string }) {
  const total = rows.reduce((sum, r) => sum + r.value, 0);
  const max = Math.max(...rows.map((r) => r.value));
  return (
    <section className="tile" aria-labelledby={titleId} data-testid={testId}>
      <h2 id={titleId}>{title}</h2>
      {total === 0 ? (
        <p className="muted chart-empty" data-testid={`${testId}-empty`}>
          {empty}
        </p>
      ) : (
        <ul className="hbars">
          {rows.map((r, i) => (
            <li key={r.key} className="hbar" data-testid={`${testId}-${r.key}`}>
              <span>{r.label}</span>
              <span className="hbar-track" aria-hidden="true">
                <span className={`hbar-fill grow ${r.tone}`} style={{ width: `${(r.value / max) * 100}%`, animationDelay: `${i * 0.12}s` }} />
              </span>
              <span className="hbar-value">
                {r.value} <small>{Math.round((r.value / total) * 100)}%</small>
              </span>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

/** Tasks per status over every project of the user (totals of GET /dashboard). */
export function StatusChart({ counts }: { counts: TaskCounts }) {
  const { t } = useTranslation();
  const rows: Row[] = [
    { key: 'TODO', label: t('statusCount.TODO'), value: counts.TODO, tone: 'hatch hbar-todo' },
    { key: 'IN_PROGRESS', label: t('statusCount.IN_PROGRESS'), value: counts.IN_PROGRESS, tone: 'bar-progress' },
    { key: 'DONE', label: t('statusCount.DONE'), value: counts.DONE, tone: 'bar-done' },
  ];
  return <BarChart titleId="chart-status" title={t('dashboard.byStatus')} rows={rows} empty={t('dashboard.chartEmpty')} testId="dashboard-chart-status" />;
}

/** Open tasks (not done) per priority over every project of the user (GET /dashboard). */
export function PriorityChart({ counts }: { counts: PriorityCounts }) {
  const { t } = useTranslation();
  const rows: Row[] = [
    { key: 'HIGH', label: t('priority.HIGH'), value: counts.HIGH, tone: 'bar-high' },
    { key: 'MEDIUM', label: t('priority.MEDIUM'), value: counts.MEDIUM, tone: 'bar-progress' },
    { key: 'LOW', label: t('priority.LOW'), value: counts.LOW, tone: 'bar-low' },
  ];
  return <BarChart titleId="chart-priority" title={t('dashboard.byPriority')} rows={rows} empty={t('dashboard.priorityEmpty')} testId="dashboard-chart-priority" />;
}
