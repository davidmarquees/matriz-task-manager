import type { AccountRole } from './api/auth';
import type { ProjectRole, UserSummary } from './api/projects';

// Translated labels for enumerated values (docs/ui/wireframe.md). pt-PT only until the
// translation files are added in Ex. 7 (react-i18next).
export const accountRoleLabel: Record<AccountRole, string> = {
  ADMIN: 'Administrador',
  MANAGER: 'Gestor',
  MEMBER: 'Membro',
};

export const projectRoleLabel: Record<ProjectRole, string> = {
  MANAGER: 'Gestor',
  MEMBER: 'Membro',
};

/** Plural labels used next to the counters (wireframe element 4). */
export const statusCountLabel = {
  TODO: 'A fazer',
  IN_PROGRESS: 'Em curso',
  DONE: 'Concluídas',
} as const;

/** Name of a user as shown next to their work (docs/database.md, section 5). */
export function userLabel(user: UserSummary): string {
  if (user.name === null) return 'Utilizador removido';
  return user.active ? user.name : `${user.name} (saiu da empresa)`;
}
