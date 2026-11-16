import { Clock } from 'lucide-react';
import { useId, useState } from 'react';
import { useTranslation } from 'react-i18next';
import type { TFunction } from 'i18next';
import type { FieldChange, Task } from '../api/tasks';
import { formatDate, formatTime, userLabel } from '../labels';

function relativeDay(t: TFunction, value: string, locale: string): string {
  const date = new Date(value);
  const today = new Date();
  const startOf = (d: Date) => new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime();
  const days = Math.round((startOf(today) - startOf(date)) / 86_400_000);
  if (days === 0) return t('lastChange.today');
  if (days === 1) return t('lastChange.yesterday');
  return t('lastChange.onDate', { date: formatDate(value, locale) });
}

function describe(t: TFunction, change: FieldChange, locale: string): string {
  const field = t(`lastChange.fields.${change.field}`);
  if (change.field === 'description') return t('lastChange.changedText', { field });
  if (change.field === 'assignees') return t('lastChange.changedList', { field });
  const value = (v: string | null) => {
    if (v === null) return '—';
    if (change.field === 'status') return t(`status.${v}`);
    if (change.field === 'priority') return t(`priority.${v}`);
    if (change.field === 'dueDate') return formatDate(v, locale);
    return v;
  };
  return `${field}: ${value(change.old)} → ${value(change.new)}`;
}

/**
 * "Changed today / yesterday / on dd/mm/yyyy" next to a task (FR14, option A). Project Managers also
 * get who changed it and what, on hover, keyboard focus and tap (mobile).
 */
export function LastChangeNote({ task }: { task: Task }) {
  const { t, i18n } = useTranslation();
  const [open, setOpen] = useState(false);
  const tooltipId = useId();
  const change = task.lastChange;
  const locale = i18n.language;

  if (!change) {
    return (
      <span className="last-change" data-testid="task-last-change">
        <span className="last-change-button">
          <Clock size={13} aria-hidden="true" />
          {t('lastChange.created', { when: relativeDay(t, task.createdAt, locale) })}
        </span>
      </span>
    );
  }
  const text = t('lastChange.changed', { when: relativeDay(t, change.changedAt, locale) });
  if (!change.changedBy || !change.changes) {
    return (
      <span className="last-change" data-testid="task-last-change">
        <span className="last-change-button">
          <Clock size={13} aria-hidden="true" />
          {text}
        </span>
      </span>
    );
  }
  return (
    <span className="last-change" onMouseEnter={() => setOpen(true)} onMouseLeave={() => setOpen(false)}>
      <button
        type="button"
        className="last-change-button"
        aria-describedby={tooltipId}
        aria-expanded={open}
        onClick={() => setOpen(!open)}
        onFocus={() => setOpen(true)}
        onBlur={() => setOpen(false)}
        onKeyDown={(e) => e.key === 'Escape' && setOpen(false)}
        data-testid="task-last-change"
      >
        <Clock size={13} aria-hidden="true" />
        {text}
      </button>
      <span role="tooltip" id={tooltipId} className="tooltip" hidden={!open} data-testid="task-last-change-details">
        <span className="eyebrow" style={{ color: 'var(--on-dark-2)', fontSize: 11 }}>
          {t('lastChange.heading', { date: formatDate(change.changedAt, locale), time: formatTime(change.changedAt, locale) })}
        </span>
        <br />
        <strong>{userLabel(t, change.changedBy)}</strong>
        <ul>
          {change.changes.map((c) => (
            <li key={c.field}>{describe(t, c, locale)}</li>
          ))}
        </ul>
      </span>
    </span>
  );
}
