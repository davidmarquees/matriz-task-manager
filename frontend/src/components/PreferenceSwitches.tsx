import { useTranslation } from 'react-i18next';
import type { Theme } from '../api/auth';
import type { Locale } from '../i18n';
import { usePreferences } from '../prefs/PreferencesContext';

/** Language PT / EN and theme Light / Dark / Auto (wireframe elements 8 and 9). */
export function PreferenceSwitches() {
  const { t } = useTranslation();
  const { locale, theme, setLocale, setTheme } = usePreferences();
  const locales: { value: Locale; label: string }[] = [
    { value: 'pt-PT', label: 'PT' },
    { value: 'en', label: 'EN' },
  ];
  const themes: { value: Theme; label: string }[] = [
    { value: 'LIGHT', label: t('nav.themeLight') },
    { value: 'DARK', label: t('nav.themeDark') },
    { value: 'SYSTEM', label: t('nav.themeSystem') },
  ];
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
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
          >
            {th.label}
          </button>
        ))}
      </div>
    </div>
  );
}
