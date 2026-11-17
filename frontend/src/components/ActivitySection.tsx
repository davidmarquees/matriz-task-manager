import { History } from 'lucide-react';
import { useCallback, useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import type { TFunction } from 'i18next';
import { listActivity, type ActivityEntry } from '../api/activity';
import { ApiError } from '../api/client';
import { formatDate, formatTime, userLabel } from '../labels';
import { Alert, Avatar, EmptyState, Skeleton } from '../ui/Bits';

/** One readable line per changed field; description values and lists are not repeated. */
function describeChanges(t: TFunction, entry: ActivityEntry, locale: string): string[] {
  if (!entry.changes || entry.action !== 'UPDATED') return [];
  return Object.entries(entry.changes).flatMap(([field, value]) => {
    if (typeof value !== 'object' || value === null) return [];
    const label =
      field === 'name' || field === 'projectRole' || (entry.entityType === 'PROJECT' && field === 'description')
        ? t(`activity.fields.${field}`)
        : t(`lastChange.fields.${field}`, { defaultValue: field });
    if (field === 'description') return [t('lastChange.changedText', { field: label })];
    if (field === 'assignees') return [t('lastChange.changedList', { field: label })];
    const show = (v: unknown) => {
      if (v === null || v === undefined) return '—';
      const text = String(v);
      if (field === 'status') return t(`status.${text}`);
      if (field === 'priority') return t(`priority.${text}`);
      if (field === 'projectRole') return t(`roles.${text}`);
      if (field === 'dueDate') return formatDate(text, locale);
      return text;
    };
    return [`${label}: ${show(value.old)} → ${show(value.new)}`];
  });
}

/** Project history for its Managers (FR13, US12): who changed what and when, newest first. */
export function ActivitySection({ projectId, refreshKey }: { projectId: number; refreshKey: number }) {
  const { t, i18n } = useTranslation();
  const [items, setItems] = useState<ActivityEntry[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const [status, setStatus] = useState<'loading' | 'ready' | 'error'>('loading');

  const load = useCallback(
    async (nextPage: number) => {
      try {
        const result = await listActivity(projectId, nextPage);
        setItems((current) => (nextPage === 0 ? result.items : [...current, ...result.items]));
        setTotal(result.totalItems);
        setPage(nextPage);
        setStatus('ready');
      } catch (e) {
        if (!(e instanceof ApiError && e.status === 401)) setStatus('error');
      }
    },
    [projectId],
  );

  useEffect(() => {
    void load(0);
  }, [load, refreshKey]);

  return (
    <section className="tile" aria-labelledby="activity-title" data-testid="activity-section">
      <div className="stack" style={{ gap: 4 }}>
        <h2 id="activity-title">{t('activity.title')}</h2>
        <p className="muted" style={{ fontSize: 13 }}>
          {t('activity.subtitle')}
        </p>
      </div>
      {status === 'loading' && <Skeleton rows={3} label={t('activity.loading')} testId="activity-loading" />}
      {status === 'error' && (
        <Alert kind="error" testId="activity-error">
          {t('activity.loadError')}{' '}
          <button type="button" className="link-button" onClick={() => void load(0)}>
            {t('common.retry')}
          </button>
        </Alert>
      )}
      {status === 'ready' && items.length === 0 && <EmptyState icon={History} title={t('activity.empty')} testId="activity-empty" />}
      {status === 'ready' && items.length > 0 && (
        <ol className="list" data-testid="activity-list">
          {items.map((entry) => {
            const details = describeChanges(t, entry, i18n.language);
            return (
              <li key={entry.id} className="comment" data-testid="activity-item">
                <Avatar name={entry.actor.name} small />
                <div className="stack" style={{ gap: 2, minWidth: 0 }}>
                  <span>
                    <strong>{userLabel(t, entry.actor)}</strong>{' '}
                    {t(`activity.${entry.entityType}_${entry.action}`, {
                      user: entry.entityUser ? userLabel(t, entry.entityUser) : '',
                      title: entry.entityTitle ?? t('activity.unknownTask'),
                    })}
                  </span>
                  {details.length > 0 && (
                    <span className="muted" style={{ fontSize: 13 }}>
                      {details.join(' · ')}
                    </span>
                  )}
                  <span className="eyebrow" style={{ fontSize: 11 }}>
                    {formatDate(entry.createdAt, i18n.language)} {formatTime(entry.createdAt, i18n.language)}
                  </span>
                </div>
              </li>
            );
          })}
        </ol>
      )}
      {status === 'ready' && items.length < total && (
        <button type="button" className="btn btn-small" onClick={() => void load(page + 1)} data-testid="activity-more" style={{ alignSelf: 'flex-start' }}>
          {t('activity.more')}
        </button>
      )}
    </section>
  );
}
