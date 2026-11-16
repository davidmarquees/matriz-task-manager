import { useTranslation } from 'react-i18next';
import type { TaskCounts } from '../api/projects';

const pct = (n: number, of: number) => (of === 0 ? '0%' : `${Math.round((n / of) * 100)}%`);

/**
 * Counts per status as a pill bar (done, in progress, hatched "to do") with a text legend, so the
 * information is never conveyed by colour alone (NFR8).
 */
export function CountsBar({ counts, testId }: { counts: TaskCounts; testId?: string }) {
  const { t } = useTranslation();
  const all = counts.TODO + counts.IN_PROGRESS + counts.DONE;
  return (
    <div className="stack" style={{ gap: 8 }} data-testid={testId}>
      <div className="bar" role="img" aria-label={all === 0 ? t('dashboard.noTasks') : t('dashboard.doneOf', { done: counts.DONE, total: all })}>
        <span className="bar-done" style={{ width: pct(counts.DONE, all) }} />
        <span className="bar-progress" style={{ width: pct(counts.IN_PROGRESS, all) }} />
        <span className="hatch" style={{ width: pct(counts.TODO, all) }} />
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
