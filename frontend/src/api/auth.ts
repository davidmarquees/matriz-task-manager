import { api } from './client';

export type AccountRole = 'ADMIN' | 'MANAGER' | 'MEMBER';

/** CurrentUser schema of openapi.yaml. */
export interface CurrentUser {
  id: number;
  name: string;
  email: string;
  accountRole: AccountRole;
  mustChangePassword: boolean;
  locale: 'pt-PT' | 'en';
  theme: 'LIGHT' | 'DARK' | 'SYSTEM';
}

/** getMe. 401 without a session; that answer also sets the XSRF-TOKEN cookie needed to log in. */
export function getMe(): Promise<CurrentUser> {
  return api<CurrentUser>('/auth/me', { notifySessionExpired: false });
}

export function login(email: string, password: string): Promise<CurrentUser> {
  return api<CurrentUser>('/auth/login', {
    method: 'POST',
    body: { email, password },
    notifySessionExpired: false,
  });
}

export function logout(): Promise<void> {
  return api<void>('/auth/logout', { method: 'POST', notifySessionExpired: false });
}

export function changeOwnPassword(currentPassword: string, newPassword: string): Promise<void> {
  return api<void>('/auth/password', { method: 'POST', body: { currentPassword, newPassword } });
}

export type Theme = CurrentUser['theme'];

/** updatePreferences: language and theme saved in the profile (NFR1, NFR8). */
export function updatePreferences(changes: { locale?: CurrentUser['locale']; theme?: Theme }): Promise<CurrentUser> {
  return api<CurrentUser>('/auth/me/preferences', { method: 'PATCH', body: changes });
}

/** requestPasswordReset: always 202, whether or not the email exists (D30). */
export function requestPasswordReset(email: string): Promise<void> {
  return api<void>('/auth/password-reset', { method: 'POST', body: { email }, notifySessionExpired: false });
}

export function confirmPasswordReset(token: string, newPassword: string): Promise<void> {
  return api<void>('/auth/password-reset/confirm', { method: 'POST', body: { token, newPassword }, notifySessionExpired: false });
}
