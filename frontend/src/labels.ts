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

export const taskStatusLabel = {
  TODO: 'A fazer',
  IN_PROGRESS: 'Em curso',
  DONE: 'Concluída',
} as const;

export const taskPriorityLabel = {
  LOW: 'Baixa',
  MEDIUM: 'Média',
  HIGH: 'Alta',
} as const;

export const changedFieldLabel = {
  title: 'Título',
  description: 'Descrição',
  status: 'Estado',
  priority: 'Prioridade',
  dueDate: 'Data limite',
  assignees: 'Responsáveis',
} as const;

/** dd/mm/aaaa for a date-time or a date (YYYY-MM-DD). */
export function formatDate(value: string): string {
  const date = /^\d{4}-\d{2}-\d{2}$/.test(value) ? new Date(`${value}T00:00:00`) : new Date(value);
  return date.toLocaleDateString('pt-PT', { day: '2-digit', month: '2-digit', year: 'numeric' });
}
