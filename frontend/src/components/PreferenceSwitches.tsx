import { Moon, Sun } from 'lucide-react';
import type { ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import type { Theme } from '../api/auth';
import type { Locale } from '../i18n';
import { usePreferences } from '../prefs/PreferencesContext';

/**
 * Language PT / EN and theme Light / Dark / Auto (wireframe elements 8 and 9).
 * Light and Dark show a sun and a moon; their names stay as aria-label and tooltip.
 * `inline` puts both groups on one row (login and password pages).
 */
export function PreferenceSwitches({ inline = false }: { inline?: boolean }) {
  const { t } = useTranslation();
  const { locale, theme, setLocale, setTheme } = usePreferences();
  const locales: { value: Locale; label: string }[] = [
    { value: 'pt-PT', label: 'PT' },
    { value: 'en', label: 'EN' },
  ];
  const themes: { value: Theme; label: string; icon?: ReactNode }[] = [
    { value: 'LIGHT', label: t('nav.themeLight'), icon: <Sun size={16} aria-hidden="true" /> },
    { value: 'DARK', label: t('nav.themeDark'), icon: <Moon size={16} aria-hidden="true" /> },
    { value: 'SYSTEM', label: t('nav.themeSystem') },
  ];
  return (
    <div className={inline ? 'prefs prefs-inline' : 'prefs'}>
      <div className="seg" role="group" aria-label={t('nav.language')} data-testid="language-switch">
        {locales.map((l) => (
          <button
            key={l.value}
            type="button"
            aria-pressed={locale === l.value}
            lang={l.value}
            onClick={() => setLocale(l.value)}
            data-testid={`language-${l.value}`}
          >
            {l.label}
          </button>
        ))}
      </div>
      <div className="seg" role="group" aria-label={t('nav.theme')} data-testid="theme-switch">
        {themes.map((th) => (
          <button
            key={th.value}
            type="button"
            aria-pressed={theme === th.value}
            onClick={() => setTheme(th.value)}
            data-testid={`theme-${th.value}`}
            aria-label={th.icon ? th.label : undefined}
            title={th.icon ? th.label : undefined}
          >
            {th.icon ?? th.label}
          </button>
        ))}
      </div>
    </div>
  );
}
