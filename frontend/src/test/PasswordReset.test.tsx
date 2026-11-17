import { fireEvent, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { ForgotPasswordPage, ResetPasswordPage } from '../pages/PasswordResetPages';
import { mockApi, renderWithApp } from './render';

const anonymous = { status: 401, body: { status: 401, title: 'Unauthorized' } };

describe('Password reset pages', () => {
  it('gives the same answer for any email (D30)', async () => {
    mockApi({ 'GET /auth/me': anonymous, 'POST /auth/password-reset': { status: 202 } });
    renderWithApp(<ForgotPasswordPage />, '/forgot-password');

    fireEvent.change(screen.getByTestId('reset-email'), { target: { value: 'nobody@example.test' } });
    fireEvent.click(screen.getByTestId('reset-request-submit'));

    expect(await screen.findByTestId('reset-request-sent')).toHaveTextContent('Se o email pertencer a uma conta ativa');
  });

  it('offers a new link when the token is invalid, expired or used (D32)', async () => {
    mockApi({
      'GET /auth/me': anonymous,
      'POST /auth/password-reset/confirm': {
        status: 422,
        body: { status: 422, title: 'Unprocessable Content', errors: [{ field: 'token', code: 'INVALID_TOKEN' }] },
      },
    });
    renderWithApp(<ResetPasswordPage />, '/reset-password?token=old-token');

    fireEvent.change(screen.getByTestId('reset-new-password'), { target: { value: 'a-new-password' } });
    fireEvent.change(screen.getByTestId('reset-confirm-password'), { target: { value: 'a-new-password' } });
    fireEvent.click(screen.getByTestId('reset-confirm-submit'));

    expect(await screen.findByTestId('reset-invalid-token')).toHaveTextContent('O link é inválido, expirou ou já foi usado');
    expect(screen.getByTestId('reset-new-link')).toHaveAttribute('href', '/forgot-password');
  });
});
