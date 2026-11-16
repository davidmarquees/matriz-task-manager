import { render } from '@testing-library/react';
import type { ReactElement } from 'react';
import { MemoryRouter } from 'react-router-dom';
import { vi } from 'vitest';
import { AuthProvider } from '../auth/AuthContext';
import { PreferencesProvider } from '../prefs/PreferencesContext';
import { FeedbackProvider } from '../ui/Feedback';

type Reply = { status: number; body?: unknown };

/**
 * Replaces fetch with fixed answers per "METHOD /path" (fictitious data only). Unknown requests get 404.
 * Returns the mock so tests can inspect the calls.
 */
export function mockApi(routes: Record<string, Reply | (() => Reply)>) {
  return vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
    const url = new URL(String(input), 'http://localhost');
    const key = `${init?.method ?? 'GET'} ${url.pathname.replace(/^\/api/, '')}`;
    const route = routes[key];
    const reply = typeof route === 'function' ? route() : route ?? { status: 404, body: { status: 404, title: 'Not Found' } };
    return new Response(reply.body === undefined ? null : JSON.stringify(reply.body), {
      status: reply.status,
      headers: { 'Content-Type': 'application/json' },
    });
  });
}

/** Renders a page with the same providers as the app. */
export function renderWithApp(ui: ReactElement, path = '/') {
  return render(
    <AuthProvider>
      <PreferencesProvider>
        <FeedbackProvider>
          <MemoryRouter initialEntries={[path]}>{ui}</MemoryRouter>
        </FeedbackProvider>
      </PreferencesProvider>
    </AuthProvider>,
  );
}

export const demoUser = {
  id: 1,
  name: 'Gestor Demo',
  email: 'manager@example.test',
  accountRole: 'MANAGER',
  mustChangePassword: false,
  locale: 'pt-PT',
  theme: 'SYSTEM',
} as const;
