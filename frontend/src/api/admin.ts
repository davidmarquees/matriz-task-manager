import { api } from './client';
import type { AccountRole } from './auth';
import type { Member, UserSummary } from './projects';

export type AccountStatus = 'ACTIVE' | 'DEACTIVATED' | 'ANONYMISED';

/** AdminAccount schema of openapi.yaml. */
export interface AdminAccount {
  id: number;
  name: string | null;
  email: string;
  accountRole: AccountRole;
  status: AccountStatus;
  mustChangePassword: boolean;
  createdAt: string;
  deactivatedAt: string | null;
}

export interface AuditEntry {
  id: number;
  actor: UserSummary | null;
  targetUser: UserSummary;
  action: string;
  projectId: number | null;
  projectName: string | null;
  oldValue: Record<string, unknown> | null;
  newValue: Record<string, unknown> | null;
  createdAt: string;
}

export interface AuditPage {
  items: AuditEntry[];
  page: number;
  size: number;
  totalItems: number;
}

export const AUDIT_PAGE_SIZE = 20;

export function listAccounts(status: AccountStatus | '', q: string): Promise<AdminAccount[]> {
  const params = new URLSearchParams();
  if (status) params.set('status', status);
  if (q.trim()) params.set('q', q.trim());
  const query = params.toString();
  return api<AdminAccount[]>(`/admin/users${query ? `?${query}` : ''}`);
}

export function createAccount(body: {
  name: string;
  email: string;
  accountRole: 'MANAGER' | 'MEMBER';
  initialPassword: string;
}): Promise<AdminAccount> {
  return api<AdminAccount>('/admin/users', { method: 'POST', body });
}

export function deactivateAccount(id: number): Promise<AdminAccount> {
  return api<AdminAccount>(`/admin/users/${id}/deactivate`, { method: 'POST' });
}

export function reactivateAccount(id: number, initialPassword: string): Promise<AdminAccount> {
  return api<AdminAccount>(`/admin/users/${id}/reactivate`, { method: 'POST', body: { initialPassword } });
}

export function anonymiseAccount(id: number): Promise<AdminAccount> {
  return api<AdminAccount>(`/admin/users/${id}/anonymise`, { method: 'POST' });
}

export function listAudit(page: number, userId?: number): Promise<AuditPage> {
  const params = new URLSearchParams({ page: String(page), size: String(AUDIT_PAGE_SIZE) });
  if (userId !== undefined) params.set('userId', String(userId));
  return api<AuditPage>(`/admin/audit?${params.toString()}`);
}

export function appointManager(projectId: number, email: string): Promise<Member> {
  return api<Member>(`/projects/${projectId}/managers`, { method: 'POST', body: { email } });
}
