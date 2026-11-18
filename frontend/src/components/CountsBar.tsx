import { useTranslation } from 'react-i18next';
import type { TaskCounts } from '../api/projects';

const pct = (n: number, of: number) => (of === 0 ? '0%' : `${Math.round((n / of) * 100)}%`);

/**
 * Counts per status as a pill bar (done, in progress, hatched "to do") with a text legend, so the
 * information is never conveyed by colour alone (NFR8). On the dashboard (`live`) the bar grows once on
 * load, the "to do" stripes keep moving forward, and the share done is written next to the bar.
 */
export function CountsBar({ counts, testId, live = false }: { counts: TaskCounts; testId?: string; live?: boolean }) {
  const { t } = useTranslation();
  const all = counts.TODO + counts.IN_PROGRESS + counts.DONE;
  return (
    <div className="stack" style={{ gap: 8 }} data-testid={testId}>
      <div className={live ? 'bar-line' : undefined}>
        <div
          className={live ? 'bar grow' : 'bar'}
          role="img"
          aria-label={all === 0 ? t('dashboard.noTasks') : t('dashboard.doneOf', { done: counts.DONE, total: all })}
        >
          <span className="bar-done" style={{ width: pct(counts.DONE, all) }} />
          <span className="bar-progress" style={{ width: pct(counts.IN_PROGRESS, all) }} />
          <span className={live ? 'hatch hatch-run' : 'hatch'} style={{ width: pct(counts.TODO, all) }} />
        </div>
        {live && (
          <span className="bar-pct" aria-hidden="true" data-testid={testId ? `${testId}-pct` : undefined}>
            {pct(counts.DONE, all)}
          </span>
        )}
      </div>
      <div className="legend">
        <span data-testid={testId ? `${testId}-TODO` : undefined}>
          <span className="dot dot-todo hatch" aria-hidden="true" />
          {t('dashboard.legendTodo', { count: counts.TODO })}
        </span>
        <span data-testid={testId ? `${testId}-IN_PROGRESS` : undefined}>
          <span className="dot dot-progress" aria-hidden="true" />
          {t('dashboard.legendProgress', { count: counts.IN_PROGRESS })}
        </span>
        <span data-testid={testId ? `${testId}-DONE` : undefined}>
          <span className="dot dot-done" aria-hidden="true" />
          {t('dashboard.legendDone', { count: counts.DONE })}
        </span>
      </div>
    </div>
  );
}
