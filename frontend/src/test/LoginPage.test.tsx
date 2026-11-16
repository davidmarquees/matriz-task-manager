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
});
