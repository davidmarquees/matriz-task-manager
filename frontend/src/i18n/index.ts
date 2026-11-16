import i18n from 'i18next';
import { initReactI18next } from 'react-i18next';
import { en } from './en';
import { ptPT } from './pt-PT';

export type Locale = 'pt-PT' | 'en';

/** Before login: the browser language if it is English, otherwise European Portuguese (A24). */
export function browserLocale(): Locale {
  const languages = typeof navigator !== 'undefined' ? navigator.languages ?? [navigator.language] : [];
  return languages.some((l) => l?.toLowerCase().startsWith('en')) ? 'en' : 'pt-PT';
}

void i18n.use(initReactI18next).init({
  resources: { 'pt-PT': { translation: ptPT }, en: { translation: en } },
  lng: browserLocale(),
  fallbackLng: 'pt-PT',
  interpolation: { escapeValue: false }, // React escapes
});

export default i18n;
