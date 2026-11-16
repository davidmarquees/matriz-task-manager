import type { AccountRole } from './api/auth';

// Translated labels for enumerated values (docs/ui/wireframe.md). pt-PT only until the
// translation files are added in Ex. 7 (react-i18next).
export const accountRoleLabel: Record<AccountRole, string> = {
  ADMIN: 'Administrador',
  MANAGER: 'Gestor',
  MEMBER: 'Membro',
};
