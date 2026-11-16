import {
  ArrowDown,
  ArrowUp,
  CircleCheck,
  CircleDashed,
  CircleDot,
  Info,
  Minus,
  TriangleAlert,
  type LucideIcon,
} from 'lucide-react';
import type { ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import type { TaskPriority, TaskStatus } from '../api/tasks';

/** Status always as icon + translated word, never colour alone (NFR1, NFR8). */
export function StatusPill({ status, testId }: { status: TaskStatus; testId?: string }) {
  const { t } = useTranslation();
  const Icon = { TODO: CircleDashed, IN_PROGRESS: CircleDot, DONE: CircleCheck }[status];
  const tone = { TODO: 'pill-todo', IN_PROGRESS: 'pill-progress', DONE: 'pill-done' }[status];
  return (
    <span className={`pill ${tone}`} data-testid={testId}>
      <Icon size={15} aria-hidden="true" />
      {t(`status.${status}`)}
    </span>
  );
}

export function PriorityPill({ priority, testId }: { priority: TaskPriority; testId?: string }) {
  const { t } = useTranslation();
  const Icon = { LOW: ArrowDown, MEDIUM: Minus, HIGH: ArrowUp }[priority];
  const tone = { LOW: 'pill-low', MEDIUM: 'pill-medium', HIGH: 'pill-high' }[priority];
  return (
    <span className={`pill ${tone}`} data-testid={testId}>
      <Icon size={15} aria-hidden="true" />
      {t(`priority.${priority}`)}
    </span>
  );
}

export function Alert({
  kind,
  children,
  testId,
}: {
  kind: 'error' | 'info' | 'success';
  children: ReactNode;
  testId?: string;
}) {
  const Icon: LucideIcon = kind === 'error' ? TriangleAlert : kind === 'success' ? CircleCheck : Info;
  return (
    <p role={kind === 'error' ? 'alert' : 'status'} className={`alert alert-${kind}`} data-testid={testId}>
      <Icon size={18} aria-hidden="true" />
      <span>{children}</span>
    </p>
  );
}

export function EmptyState({
  icon: Icon,
  title,
  hint,
  testId,
  children,
}: {
  icon: LucideIcon;
  title: string;
  hint?: string;
  testId?: string;
  children?: ReactNode;
}) {
  return (
    <div className="empty" data-testid={testId}>
      <span className="empty-art" aria-hidden="true">
        <Icon size={24} />
      </span>
      <strong>{title}</strong>
      {hint && <span className="muted">{hint}</span>}
      {children}
    </div>
  );
}

/** Placeholder rows while data loads; hidden from screen readers, which hear the label instead. */
export function Skeleton({ rows = 3, label, testId }: { rows?: number; label: string; testId?: string }) {
  return (
    <div className="stack" style={{ gap: 10 }} data-testid={testId}>
      <span className="visually-hidden">{label}</span>
      {Array.from({ length: rows }, (_, i) => (
        <div key={i} className="skeleton" style={{ height: 44 }} aria-hidden="true" />
      ))}
    </div>
  );
}

export function Avatar({ name, small, tone }: { name: string | null; small?: boolean; tone?: 'brick' | 'pine' }) {
  const initials = (name ?? '?')
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((part) => part[0]?.toUpperCase())
    .join('');
  return (
    <span
      className={small ? 'avatar small' : 'avatar'}
      aria-hidden="true"
      style={tone === 'brick' ? { background: 'var(--brick)', color: 'var(--on-brick)' } : undefined}
    >
      {initials || '?'}
    </span>
  );
}
