import { useId, useState } from 'react';
import type { FieldChange, Task } from '../api/tasks';
import { changedFieldLabel, formatDate, taskPriorityLabel, taskStatusLabel, userLabel } from '../labels';

function relativeDay(value: string): string {
  const date = new Date(value);
  const today = new Date();
  const startOf = (d: Date) => new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime();
  const days = Math.round((startOf(today) - startOf(date)) / 86_400_000);
  if (days === 0) return 'hoje';
  if (days === 1) return 'ontem';
  return `a ${formatDate(value)}`;
}

function valueLabel(change: FieldChange, value: string | null): string {
  if (value === null) return '—';
  if (change.field === 'status') return taskStatusLabel[value as keyof typeof taskStatusLabel] ?? value;
  if (change.field === 'priority') return taskPriorityLabel[value as keyof typeof taskPriorityLabel] ?? value;
  if (change.field === 'dueDate') return formatDate(value);
  return value;
}

function describe(change: FieldChange): string {
  const label = changedFieldLabel[change.field] ?? change.field;
  if (change.field === 'description' || change.field === 'assignees') {
    return `${label} alterad${change.field === 'description' ? 'a' : 'os'}`;
  }
  return `${label}: ${valueLabel(change, change.old)} → ${valueLabel(change, change.new)}`;
}

/**
 * "Alterada hoje / ontem / a dd/mm/aaaa" next to a task (extra feature, option A). Project Managers
 * also get who changed it and what, shown on hover, on keyboard focus and on tap (mobile).
 */
export function LastChangeNote({ task }: { task: Task }) {
  const [open, setOpen] = useState(false);
  const tooltipId = useId();
  const change = task.lastChange;

  if (!change) {
    return (
      <span className="last-change muted" data-testid="task-last-change">
        Criada {relativeDay(task.createdAt)}
      </span>
    );
  }
  const text = `Alterada ${relativeDay(change.changedAt)}`;
  if (!change.changedBy || !change.changes) {
    return (
      <span className="last-change muted" data-testid="task-last-change">
        {text}
      </span>
    );
  }
  return (
    <span className="last-change" onMouseEnter={() => setOpen(true)} onMouseLeave={() => setOpen(false)}>
      <button
        type="button"
        className="link-button muted"
        aria-describedby={tooltipId}
        aria-expanded={open}
        onClick={() => setOpen(!open)}
        onFocus={() => setOpen(true)}
        onBlur={() => setOpen(false)}
        data-testid="task-last-change"
      >
        {text}
      </button>
      <span role="tooltip" id={tooltipId} className="tooltip" hidden={!open} data-testid="task-last-change-details">
        <strong>{userLabel(change.changedBy)}</strong> · {formatDate(change.changedAt)}{' '}
        {new Date(change.changedAt).toLocaleTimeString('pt-PT', { hour: '2-digit', minute: '2-digit' })}
        <ul>
          {change.changes.map((c) => (
            <li key={c.field}>{describe(c)}</li>
          ))}
        </ul>
      </span>
    </span>
  );
}
