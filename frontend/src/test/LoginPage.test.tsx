import { fireEvent, screen, waitFor } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { LoginPage } from '../pages/LoginPage';
import { mockApi, renderWithApp } from './render';

describe('LoginPage', () => {
  it('shows the same generic error for wrong credentials and keeps what was typed (D6)', async () => {
    mockApi({
      'GET /auth/me': { status: 401 },
      'POST /auth/login': { status: 401, body: { status: 401, title: 'Unauthorized', detail: 'Invalid email or password.' } },
    });
    renderWithApp(<LoginPage />, '/login');

    fireEvent.change(await screen.findByTestId('login-email'), { target: { value: 'member@example.test' } });
    fireEvent.change(screen.getByTestId('login-password'), { target: { value: 'wrong-password' } });
    fireEvent.click(screen.getByTestId('login-submit'));

    expect(await screen.findByTestId('login-error')).toHaveTextContent('Email ou palavra-passe inválidos.');
    expect(screen.getByTestId('login-email')).toHaveValue('member@example.test');
  });

  it('asks for both fields before calling the API', async () => {
    const api = mockApi({ 'GET /auth/me': { status: 401 } });
    renderWithApp(<LoginPage />, '/login');

    fireEvent.click(await screen.findByTestId('login-submit'));

    expect(await screen.findByTestId('login-error')).toHaveTextContent('Indique o email e a palavra-passe.');
    expect(api.mock.calls.some(([, init]) => init?.method === 'POST')).toBe(false);
  });

  it('tells the user to wait after too many attempts (429)', async () => {
    mockApi({ 'GET /auth/me': { status: 401 }, 'POST /auth/login': { status: 429, body: { status: 429, title: 'Too Many Requests' } } });
    renderWithApp(<LoginPage />, '/login');

    fireEvent.change(await screen.findByTestId('login-email'), { target: { value: 'a@example.test' } });
    fireEvent.change(screen.getByTestId('login-password'), { target: { value: 'whatever-1' } });
    fireEvent.click(screen.getByTestId('login-submit'));

    await waitFor(() => expect(screen.getByTestId('login-error')).toHaveTextContent('Demasiadas tentativas'));
  });

  it('shows and hides the password without losing it', async () => {
    mockApi({ 'GET /auth/me': { status: 401 } });
    renderWithApp(<LoginPage />, '/login');

    const password = await screen.findByTestId('login-password');
    fireEvent.change(password, { target: { value: 'secret-1' } });
    expect(password).toHaveAttribute('type', 'password');

    fireEvent.click(screen.getByTestId('login-password-toggle'));
    expect(password).toHaveAttribute('type', 'text');
    expect(screen.getByTestId('login-password-toggle')).toHaveAttribute('aria-pressed', 'true');
    expect(screen.getByTestId('login-password-toggle')).toHaveAccessibleName('Ocultar palavra-passe');

    fireEvent.click(screen.getByTestId('login-password-toggle'));
    expect(password).toHaveAttribute('type', 'password');
    expect(password).toHaveValue('secret-1');
  });

  it('warns when Caps Lock is on while typing the password', async () => {
    mockApi({ 'GET /auth/me': { status: 401 } });
    renderWithApp(<LoginPage />, '/login');

    const password = await screen.findByTestId('login-password');
    fireEvent.keyUp(password, { key: 'A', modifierCapsLock: true });
    expect(await screen.findByTestId('login-caps-lock')).toHaveTextContent('Caps Lock está ativo.');

    fireEvent.blur(password);
    expect(screen.queryByTestId('login-caps-lock')).not.toBeInTheDocument();
  });

  it('describes the platform below the login board, in four parts', async () => {
    mockApi({ 'GET /auth/me': { status: 401 } });
    renderWithApp(<LoginPage />, '/login');

    const about = await screen.findByTestId('login-about');
    expect(about).toHaveTextContent('Conheça o Matriz');
    expect(about.querySelectorAll('.info-card')).toHaveLength(4);
    expect(screen.getByTestId('login-about-link')).toHaveAttribute('href', '#about-matriz');
  });
});
