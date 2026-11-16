import { fireEvent, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { ChangePasswordPage } from '../pages/ChangePasswordPage';
import { demoUser, mockApi, renderWithApp } from './render';

describe('ChangePasswordPage', () => {
  it('refuses a new password equal to the current one before calling the API (D47)', async () => {
    const api = mockApi({ 'GET /auth/me': { status: 200, body: demoUser } });
    renderWithApp(<ChangePasswordPage />, '/change-password');

    fireEvent.change(await screen.findByTestId('current-password'), { target: { value: 'same-password' } });
    fireEvent.change(screen.getByTestId('new-password'), { target: { value: 'same-password' } });
    fireEvent.change(screen.getByTestId('confirm-password'), { target: { value: 'same-password' } });
    fireEvent.click(screen.getByTestId('change-password-submit'));

    expect(await screen.findByTestId('change-password-error')).toHaveTextContent('A nova palavra-passe tem de ser diferente da atual.');
    expect(api.mock.calls.some(([url]) => String(url).includes('/auth/password'))).toBe(false);
  });

  it('shows the server answer for a wrong current password (D46)', async () => {
    mockApi({
      'GET /auth/me': { status: 200, body: demoUser },
      'POST /auth/password': {
        status: 422,
        body: { status: 422, title: 'Unprocessable Content', errors: [{ field: 'currentPassword', code: 'WRONG_PASSWORD' }] },
      },
    });
    renderWithApp(<ChangePasswordPage />, '/change-password');

    fireEvent.change(await screen.findByTestId('current-password'), { target: { value: 'not-the-one' } });
    fireEvent.change(screen.getByTestId('new-password'), { target: { value: 'a-new-password' } });
    fireEvent.change(screen.getByTestId('confirm-password'), { target: { value: 'a-new-password' } });
    fireEvent.click(screen.getByTestId('change-password-submit'));

    expect(await screen.findByTestId('change-password-error')).toHaveTextContent('A palavra-passe atual está incorreta.');
    expect(screen.getByTestId('new-password')).toHaveValue('a-new-password'); // data kept on error
  });
});
