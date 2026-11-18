import { ChevronLeft, ChevronRight } from 'lucide-react';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Link } from 'react-router-dom';
import { getDueTasks, type DueTask } from '../api/dashboard';
import { Alert, Skeleton } from '../ui/Bits';

type Load = { status: 'loading' } | { status: 'error' } | { status: 'ready'; tasks: DueTask[] };
type Kind = 'todo' | 'progress' | 'done' | 'overdue';

/** Local calendar date as YYYY-MM-DD (the API's date format). */
export function isoDate(d: Date): string {
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

function addDays(d: Date, days: number): Date {
  const copy = new Date(d);
  copy.setDate(copy.getDate() + days);
  return copy;
}

/** Monday of the week that contains `d`. */
export function mondayOf(d: Date): Date {
  const start = new Date(d.getFullYear(), d.getMonth(), d.getDate());
  return addDays(start, -((start.getDay() + 6) % 7));
}

/** [A] Overdue: due before today and not done (shown by the interface; the API does not send it). */
export function kindOf(task: DueTask, today: string): Kind {
  if (task.status === 'DONE') return 'done';
  if (task.dueDate < today) return 'overdue';
  return task.status === 'IN_PROGRESS' ? 'progress' : 'todo';
}

/**
 * Weekly due-date widget (UI polish, 2026-11-18): the seven days of a week with a dot per task due
 * (colour by state, plus a text list), and the tasks of the selected day underneath.
 * Data: GET /dashboard/due-tasks for the week shown.
 */
export function WeekDue({ now = new Date() }: { now?: Date }) {
  const { t, i18n } = useTranslation();
  const today = isoDate(now);
  const [weekStart, setWeekStart] = useState(() => mondayOf(now));
  const [selected, setSelected] = useState(today);
  const [load, setLoad] = useState<Load>({ status: 'loading' });

  const days = useMemo(() => Array.from({ length: 7 }, (_, i) => addDays(weekStart, i)), [weekStart]);
  const from = isoDate(days[0]);
  const to = isoDate(days[6]);

  const reload = useCallback(async () => {
    setLoad({ status: 'loading' });
    try {
      setLoad({ status: 'ready', tasks: await getDueTasks(from, to) });
    } catch {
      setLoad({ status: 'error' });
    }
  }, [from, to]);

  useEffect(() => {
    void reload();
  }, [reload]);

  const weekday = new Intl.DateTimeFormat(i18n.language, { weekday: 'short' });
  const longDay = new Intl.DateTimeFormat(i18n.language, { weekday: 'long', day: 'numeric', month: 'short' });
  const shortDay = new Intl.DateTimeFormat(i18n.language, { day: 'numeric', month: 'short' });

  function moveWeek(weeks: number) {
    const next = addDays(weekStart, weeks * 7);
    setWeekStart(next);
    setSelected(weeks === 0 ? today : isoDate(next));
  }

  function goToday() {
    setWeekStart(mondayOf(now));
    setSelected(today);
  }

  const tasks = load.status === 'ready' ? load.tasks : [];
  const ofDay = (iso: string) => tasks.filter((task) => task.dueDate === iso);
  const selectedTasks = ofDay(selected);
  const selectedDate = new Date(`${selected}T12:00:00`);

  return (
    <section className="tile week" aria-labelledby="week-title" data-testid="dashboard-week">
      <div className="week-head">
        <div className="stack" style={{ gap: 2 }}>
          <h2 id="week-title">{t('dashboard.week.title')}</h2>
          <span className="eyebrow" data-testid="dashboard-week-range">
            {shortDay.format(days[0])} – {shortDay.format(days[6])}
          </span>
        </div>
        <div className="week-nav">
          <button type="button" className="week-today" onClick={goToday} data-testid="dashboard-week-today">
            {t('dashboard.week.today')}
          </button>
          <button type="button" onClick={() => moveWeek(-1)} aria-label={t('dashboard.week.previous')} data-testid="dashboard-week-prev">
            <ChevronLeft size={16} aria-hidden="true" />
          </button>
          <button type="button" onClick={() => moveWeek(1)} aria-label={t('dashboard.week.next')} data-testid="dashboard-week-next">
            <ChevronRight size={16} aria-hidden="true" />
          </button>
        </div>
      </div>

      {load.status === 'loading' && <Skeleton rows={3} label={t('common.loading')} testId="dashboard-week-loading" />}
      {load.status === 'error' && (
        <Alert kind="error" testId="dashboard-week-error">
          {t('dashboard.week.error')}{' '}
          <button type="button" className="link-button" onClick={() => void reload()}>
            {t('common.retry')}
          </button>
        </Alert>
      )}
      {load.status === 'ready' && (
        <>
          <div className="week-days" role="group" aria-label={t('dashboard.week.days')}>
            {days.map((d) => {
              const iso = isoDate(d);
              const due = ofDay(iso);
              return (
                <button
                  key={iso}
                  type="button"
                  className={iso === today ? 'week-day today' : 'week-day'}
                  aria-pressed={iso === selected}
                  onClick={() => setSelected(iso)}
                  onMouseEnter={() => setSelected(iso)}
                  onFocus={() => setSelected(iso)}
                  data-testid={`dashboard-week-day-${iso}`}
                >
                  <span className="week-wd">{weekday.format(d).replace('.', '').slice(0, 3)}</span>
                  <span>{d.getDate()}</span>
                  {/* the visible text starts the accessible name (WCAG 2.5.3); the rest is for screen readers */}
                  <span className="visually-hidden">
                    {`, ${longDay.format(d)}: ${due.length === 0 ? t('dashboard.week.none') : t('dashboard.tasks', { count: due.length })}`}
                  </span>
                  <span className="week-dots" aria-hidden="true">
                    {due.slice(0, 3).map((task) => (
                      <i key={task.id} className={`week-dot ${kindOf(task, today)}`} />
                    ))}
                  </span>
                </button>
              );
            })}
          </div>

          <div className="week-list" aria-live="polite" data-testid="dashboard-week-list">
            <strong>
              {longDay.format(selectedDate)}
              {selectedTasks.length > 0 && ` · ${t('dashboard.tasks', { count: selectedTasks.length })}`}
            </strong>
            {selectedTasks.length === 0 ? (
              <span className="muted">{tasks.length === 0 ? t('dashboard.week.noneWeek') : t('dashboard.week.none')}</span>
            ) : (
              <ul>
                {selectedTasks.map((task) => {
                  const kind = kindOf(task, today);
                  return (
                    <li key={task.id} data-testid="dashboard-week-task">
                      <i className={`week-dot ${kind}`} aria-hidden="true" />
                      <span className="stack" style={{ gap: 0 }}>
                        <Link to={`/tasks/${task.id}`} className="week-task">
                          {task.title}
                        </Link>
                        <span className="muted week-meta">
                          {task.projectName} · {t(`status.${task.status}`)}
                          {kind === 'overdue' && ` · ${t('dashboard.week.overdue')}`} · {t('dashboard.week.priority', { priority: t(`priority.${task.priority}`).toLowerCase() })}
                        </span>
                      </span>
                    </li>
                  );
                })}
              </ul>
            )}
          </div>

          <div className="week-legend" aria-hidden="true">
            <span><i className="week-dot todo" />{t('status.TODO')}</span>
            <span><i className="week-dot progress" />{t('status.IN_PROGRESS')}</span>
            <span><i className="week-dot done" />{t('status.DONE')}</span>
            <span><i className="week-dot overdue" />{t('dashboard.week.overdueLegend')}</span>
          </div>
        </>
      )}
    </section>
  );
}
