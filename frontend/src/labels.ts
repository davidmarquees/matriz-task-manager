import type { TFunction } from 'i18next';
import type { UserSummary } from './api/projects';

/** Name of a user next to their work (docs/database.md, section 5), in the interface language. */
export function userLabel(t: TFunction, user: UserSummary): string {
  if (user.name === null) return t('common.removedUser');
  return user.active ? user.name : t('common.leftCompany', { name: user.name });
}

/** dd/mm/yyyy (pt-PT) or the English equivalent, for a date-time or a date (YYYY-MM-DD). */
export function formatDate(value: string, locale: string): string {
  const date = /^\d{4}-\d{2}-\d{2}$/.test(value) ? new Date(`${value}T00:00:00`) : new Date(value);
  return date.toLocaleDateString(locale === 'en' ? 'en-GB' : 'pt-PT', { day: '2-digit', month: '2-digit', year: 'numeric' });
}

export function formatTime(value: string, locale: string): string {
  return new Date(value).toLocaleTimeString(locale === 'en' ? 'en-GB' : 'pt-PT', { hour: '2-digit', minute: '2-digit' });
}

export function formatLongDate(date: Date, locale: string): string {
  return date.toLocaleDateString(locale === 'en' ? 'en-GB' : 'pt-PT', { weekday: 'long', day: 'numeric', month: 'long' });
}
