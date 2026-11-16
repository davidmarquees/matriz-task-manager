import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import { AuthProvider, useAuth } from '../auth/AuthContext';
import { RequireAuth } from '../auth/RequireAuth';
import { LoginPage } from '../pages/LoginPage';
import { PreferencesProvider } from '../prefs/PreferencesContext';
import { FeedbackProvider } from '../ui/Feedback';
import { demoUser, mockApi } from './render';

const memberUser = { ...demoUser, id: 2, name: 'Membro Demo', email: 'member@example.test', accountRole: 'MEMBER' } as const;

/**
 * Logout without the shell's own navigate('/login'): in the browser the RequireAuth redirect happens first,
 * which is how the bug showed up (docs/verification.md, Ex. 8).
 */
function ProjectPage() {
  const { logout } = useAuth();
  return (
    <button type="button" data-testid="plain-logout" onClick={() => void logout()}>
      project page
    </button>
  );
}

function app(path: string) {
  return render(
    <AuthProvider>
      <PreferencesProvider>
        <FeedbackProvider>
          <MemoryRouter initialEntries={[path]}>
            <Routes>
              <Route path="/login" element={<LoginPage />} />
              <Route path="/" element={<RequireAuth><p data-testid="home-marker">home</p></RequireAuth>} />
              <Route path="/projects/:id" element={<RequireAuth><ProjectPage /></RequireAuth>} />
            </Routes>
          </MemoryRouter>
        </FeedbackProvider>
      </PreferencesProvider>
    </AuthProvider>,
  );
}

async function logInAsManager() {
  fireEvent.change(await screen.findByTestId('login-email'), { target: { value: 'manager@example.test' } });
  fireEvent.change(screen.getByTestId('login-password'), { target: { value: 'demo-password' } });
  fireEvent.click(screen.getByTestId('login-submit'));
}

describe('Redirect after login', () => {
  it('after an explicit logout, the next login opens the dashboard, not the previous user page', async () => {
    let me: { status: number; body?: unknown } = { status: 200, body: memberUser };
    mockApi({
      'GET /auth/me': () => me,
      'POST /auth/logout': () => {
        me = { status: 401, body: { status: 401, title: 'Unauthorized' } };
        return { status: 204 };
      },
      'POST /auth/login': { status: 200, body: demoUser },
      'PATCH /auth/me/preferences': { status: 200, body: demoUser },
    });
    app('/projects/7');

    fireEvent.click(await screen.findByTestId('plain-logout'));
    await logInAsManager();

    expect(await screen.findByTestId('home-marker')).toBeInTheDocument();
    expect(screen.queryByTestId('plain-logout')).toBeNull();
  });

  it('without a session, the login still returns to the page that asked for it', async () => {
    mockApi({
      'GET /auth/me': { status: 401, body: { status: 401, title: 'Unauthorized' } },
      'POST /auth/login': { status: 200, body: demoUser },
      'PATCH /auth/me/preferences': { status: 200, body: demoUser },
    });
    app('/projects/7');

    await logInAsManager();

    expect(await screen.findByTestId('plain-logout')).toBeInTheDocument();
  });
});
