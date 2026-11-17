import '@testing-library/jest-dom/vitest';
import { cleanup } from '@testing-library/react';
import { afterEach, vi } from 'vitest';
import i18n from '../i18n';

// Tests run in European Portuguese (the default language) unless a test changes it: the browser
// language decides before login (A24), so the test browser says pt-PT.
Object.defineProperty(navigator, 'languages', { value: ['pt-PT'], configurable: true });
void i18n.changeLanguage('pt-PT');

// jsdom has no matchMedia; the theme code asks for prefers-color-scheme.
if (!window.matchMedia) {
  Object.defineProperty(window, 'matchMedia', {
    value: (query: string) => ({
      matches: false,
      media: query,
      addEventListener: () => undefined,
      removeEventListener: () => undefined,
    }),
  });
}

afterEach(() => {
  cleanup();
  localStorage.clear();
  vi.restoreAllMocks();
  void i18n.changeLanguage('pt-PT');
});
