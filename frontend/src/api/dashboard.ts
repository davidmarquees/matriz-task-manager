import { api } from './client';
import type { ProjectSummary, TaskCounts } from './projects';
import type { TaskPriority, TaskStatus } from './tasks';

/** PriorityCounts schema of openapi.yaml: open tasks (TODO or IN_PROGRESS) per priority. */
export interface PriorityCounts {
  LOW: number;
  MEDIUM: number;
  HIGH: number;
}

/** Dashboard schema of openapi.yaml (getDashboard, FR9). */
export interface Dashboard {
  totals: { projects: number; taskCounts: TaskCounts; priorityCounts: PriorityCounts };
  projects: ProjectSummary[];
}

/** DueTask schema of openapi.yaml (listDashboardDueTasks). */
export interface DueTask {
  id: number;
  projectId: number;
  projectName: string;
  title: string;
  status: TaskStatus;
  priority: TaskPriority;
  dueDate: string;
}

export function getDashboard(): Promise<Dashboard> {
  return api<Dashboard>('/dashboard');
}

/** Tasks due between `from` and `to` (YYYY-MM-DD, both included, at most 42 days). */
export async function getDueTasks(from: string, to: string): Promise<DueTask[]> {
  const params = new URLSearchParams({ from, to });
  return (await api<{ items: DueTask[] }>(`/dashboard/due-tasks?${params}`)).items;
}
