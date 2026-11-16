import { api } from './client';
import type { UserSummary } from './projects';

type FieldValue = { old?: unknown; new?: unknown };

/** ActivityEntry schema of openapi.yaml (listProjectActivity, FR13). */
export interface ActivityEntry {
  id: number;
  actor: UserSummary;
  entityType: 'PROJECT' | 'MEMBER' | 'TASK' | 'ASSIGNEE' | 'COMMENT';
  entityId: number;
  action: 'CREATED' | 'UPDATED' | 'DELETED' | 'ADDED' | 'REMOVED';
  entityUser?: UserSummary;
  entityTitle: string | null;
  changes: Record<string, FieldValue | number> | null;
  createdAt: string;
}

export interface ActivityPage {
  items: ActivityEntry[];
  page: number;
  size: number;
  totalItems: number;
}

export const ACTIVITY_PAGE_SIZE = 10;

export function listActivity(projectId: number, page: number): Promise<ActivityPage> {
  return api<ActivityPage>(`/projects/${projectId}/activity?page=${page}&size=${ACTIVITY_PAGE_SIZE}`);
}
