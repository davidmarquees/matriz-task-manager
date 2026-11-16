import { api } from './client';
import type { ProjectSummary, TaskCounts } from './projects';

/** Dashboard schema of openapi.yaml (getDashboard, FR9). */
export interface Dashboard {
  totals: { projects: number; taskCounts: TaskCounts };
  projects: ProjectSummary[];
}

export function getDashboard(): Promise<Dashboard> {
  return api<Dashboard>('/dashboard');
}
