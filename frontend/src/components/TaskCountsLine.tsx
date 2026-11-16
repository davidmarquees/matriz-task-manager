import type { TaskCounts } from '../api/projects';
import { statusCountLabel } from '../labels';

/** Counts per status, always with a text label (never colour alone, NFR8). */
export function TaskCountsLine({ counts, testId }: { counts: TaskCounts; testId?: string }) {
  return (
    <span className="counts" data-testid={testId}>
      {(['TODO', 'IN_PROGRESS', 'DONE'] as const).map((status) => (
        <span key={status} data-testid={testId ? `${testId}-${status}` : undefined}>
          {statusCountLabel[status]}: {counts[status]}
        </span>
      ))}
    </span>
  );
}
