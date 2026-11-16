import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { updatePreferences, type Theme } from '../api/auth';
import { useAuth } from '../auth/AuthContext';
import { browserLocale, type Locale } from '../i18n';

interface PreferencesValue {
  locale: Locale;
  theme: Theme;
  setLocale(locale: Locale): void;
  setTheme(theme: Theme): void;
}

const PreferencesContext = createContext<PreferencesValue | null>(null);
const STORAGE_KEY = 'matriz.preferences'; // only for the login page, before the profile is known

function readStored(): { locale?: Locale; theme?: Theme } {
  try {
    return JSON.parse(localStorage.getItem(STORAGE_KEY) ?? '{}');
  } catch {
    return {};
  }
}

function store(value: { locale: Locale; theme: Theme }) {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(value));
  } catch {
    // storage unavailable: the choice lasts until the page is closed
  }
}

function systemDark(): boolean {
  return typeof window !== 'undefined' && window.matchMedia?.('(prefers-color-scheme: dark)').matches === true;
}

/**
 * Language and theme (NFR1, NFR8, A24). After login they come from the user's profile and every change
 * is saved there; before login, the browser language and the system theme (or the last choice made on
 * this device).
 */
export function PreferencesProvider({ children }: { children: ReactNode }) {
  const { state, setUser } = useAuth();
  const { i18n } = useTranslation();
  const stored = readStored();
  const [anonymous, setAnonymous] = useState<{ locale: Locale; theme: Theme }>({
    locale: stored.locale ?? browserLocale(),
    theme: stored.theme ?? 'SYSTEM',
  });
  const [prefersDark, setPrefersDark] = useState(systemDark);

  const user = state.status === 'authenticated' ? state.user : null;
  const locale: Locale = user ? user.locale : anonymous.locale;
  const theme: Theme = user ? user.theme : anonymous.theme;

  useEffect(() => {
    const media = window.matchMedia?.('(prefers-color-scheme: dark)');
    if (!media) return;
    const onChange = () => setPrefersDark(media.matches);
    media.addEventListener('change', onChange);
    return () => media.removeEventListener('change', onChange);
  }, []);

  useEffect(() => {
    const resolved = theme === 'SYSTEM' ? (prefersDark ? 'dark' : 'light') : theme === 'DARK' ? 'dark' : 'light';
    document.documentElement.dataset.theme = resolved;
  }, [theme, prefersDark]);

  useEffect(() => {
    if (i18n.language !== locale) void i18n.changeLanguage(locale);
    document.documentElement.lang = locale;
  }, [locale, i18n]);

  const save = useCallback(
    (changes: { locale?: Locale; theme?: Theme }) => {
      const next = { locale: changes.locale ?? locale, theme: changes.theme ?? theme };
      store(next);
      if (!user) {
        setAnonymous(next);
        return;
      }
      // optimistic: show the choice at once, then keep the user confirmed by the API
      setUser({ ...user, ...next });
      updatePreferences(changes)
        .then(setUser)
        .catch(() => setUser(user));
    },
    [locale, theme, user, setUser],
  );

  const value = useMemo<PreferencesValue>(
    () => ({
      locale,
      theme,
      setLocale: (l) => save({ locale: l }),
      setTheme: (t) => save({ theme: t }),
    }),
    [locale, theme, save],
  );

  return <PreferencesContext.Provider value={value}>{children}</PreferencesContext.Provider>;
}

export function usePreferences(): PreferencesValue {
  const value = useContext(PreferencesContext);
  if (!value) throw new Error('usePreferences must be used inside PreferencesProvider');
  return value;
}
