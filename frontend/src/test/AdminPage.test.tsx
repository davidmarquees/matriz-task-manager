import { fireEvent, screen, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { AdminPage } from '../pages/AdminPage';
import { demoUser, mockApi, renderWithApp } from './render';

const admin = { ...demoUser, id: 9, name: 'Administrador', email: 'admin@example.test', accountRole: 'ADMIN' } as const;
const account = (id: number, name: string, status: 'ACTIVE' | 'DEACTIVATED') => ({
  id,
  name,
  email: `user${id}@example.test`,
  accountRole: 'MANAGER',
  status,
  mustChangePassword: false,
  createdAt: '2026-11-01T10:00:00Z',
  deactivatedAt: status === 'DEACTIVATED' ? '2026-11-10T10:00:00Z' : null,
});
const emptyAudit = { status: 200, body: { items: [], page: 0, size: 20, totalItems: 0 } };

describe('AdminPage', () => {
  it('explains why the only Manager of a project cannot be deactivated (D5)', async () => {
    mockApi({
      'GET /auth/me': { status: 200, body: admin },
      'GET /admin/users': { status: 200, body: [account(9, 'Administrador', 'ACTIVE'), account(2, 'Rui Gestor', 'ACTIVE')] },
      'GET /admin/audit': emptyAudit,
      'POST /admin/users/2/deactivate': { status: 409, body: { status: 409, title: 'Conflict', code: 'ONLY_MANAGER' } },
    });
    renderWithApp(<AdminPage />, '/admin');

    const items = await screen.findAllByTestId('account-item');
    expect(within(items[0]).queryByTestId('account-deactivate-button')).toBeNull(); // own account (D4)
    fireEvent.click(within(items[1]).getByTestId('account-deactivate-button'));
    fireEvent.click(await screen.findByTestId('confirm-ok'));

    expect(await screen.findByTestId('account-action-error')).toHaveTextContent('única gestora de um projeto');
  });

  it('keeps the form data and explains a repeated email (D1)', async () => {
    mockApi({
      'GET /auth/me': { status: 200, body: admin },
      'GET /admin/users': { status: 200, body: [] },
      'GET /admin/audit': emptyAudit,
      'POST /admin/users': { status: 409, body: { status: 409, title: 'Conflict', code: 'EMAIL_IN_USE' } },
    });
    renderWithApp(<AdminPage />, '/admin');

    fireEvent.change(await screen.findByTestId('account-name'), { target: { value: 'Rita Nova' } });
    fireEvent.change(screen.getByTestId('account-email'), { target: { value: 'rita@example.test' } });
    fireEvent.change(screen.getByTestId('account-password'), { target: { value: 'initial-pass' } });
    fireEvent.click(screen.getByTestId('account-create-submit'));

    expect(await screen.findByTestId('account-create-error')).toHaveTextContent('Já existe uma conta com este email');
    expect(screen.getByTestId('account-email')).toHaveValue('rita@example.test');
  });
});
