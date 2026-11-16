import { api } from './client';
import type { UserSummary } from './projects';

export type TaskStatus = 'TODO' | 'IN_PROGRESS' | 'DONE';
export type TaskPriority = 'LOW' | 'MEDIUM' | 'HIGH';
export type ChangedField = 'title' | 'description' | 'status' | 'priority' | 'dueDate' | 'assignees';

export interface FieldChange {
  field: ChangedField;
  old: string | null;
  new: string | null;
}

/** Last change (extra feature): who and what only for project Managers. */
export interface LastChange {
  changedAt: string;
  changedBy?: UserSummary;
  changes?: FieldChange[];
}

export interface Task {
  id: number;
  projectId: number;
  title: string;
  description: string | null;
  status: TaskStatus;
  priority: TaskPriority;
  dueDate: string | null;
  assignees: UserSummary[];
  createdBy: UserSummary;
  createdAt: string;
  updatedAt: string;
  version: number;
  lastChange: LastChange | null;
}

export interface TaskPage {
  items: Task[];
  page: number;
  size: number;
  totalItems: number;
}

export interface TaskFilters {
  q: string;
  status: TaskStatus | '';
  priority: TaskPriority | '';
}

export const TASK_TITLE_MIN = 3;
export const TASK_TITLE_MAX = 150;
export const TASK_DESCRIPTION_MAX = 2000;
export const TASK_PAGE_SIZE = 10;

export function listTasks(projectId: number, filters: TaskFilters, page: number): Promise<TaskPage> {
  const params = new URLSearchParams({ page: String(page), size: String(TASK_PAGE_SIZE) });
  if (filters.q.trim()) params.set('q', filters.q.trim());
  if (filters.status) params.set('status', filters.status);
  if (filters.priority) params.set('priority', filters.priority);
  return api<TaskPage>(`/projects/${projectId}/tasks?${params.toString()}`);
}

export interface NewTask {
  title: string;
  description: string | null;
  status: TaskStatus;
  priority: TaskPriority;
  dueDate: string | null;
}

export function createTask(projectId: number, task: NewTask): Promise<Task> {
  return api<Task>(`/projects/${projectId}/tasks`, { method: 'POST', body: task });
}

export function getTask(id: number): Promise<Task> {
  return api<Task>(`/tasks/${id}`);
}

export function updateTask(id: number, version: number, changes: Partial<NewTask>): Promise<Task> {
  return api<Task>(`/tasks/${id}`, { method: 'PATCH', body: { version, ...changes } });
}

export function deleteTask(id: number): Promise<void> {
  return api<void>(`/tasks/${id}`, { method: 'DELETE' });
}

export function addAssignee(taskId: number, userId: number): Promise<Task> {
  return api<Task>(`/tasks/${taskId}/assignees`, { method: 'POST', body: { userId } });
}

export function removeAssignee(taskId: number, userId: number): Promise<void> {
  return api<void>(`/tasks/${taskId}/assignees/${userId}`, { method: 'DELETE' });
}

export const COMMENT_MAX = 2000;

/** Comment schema of openapi.yaml; author and date come from the server. */
export interface Comment {
  id: number;
  taskId: number;
  author: UserSummary;
  body: string;
  createdAt: string;
}

export function listComments(taskId: number): Promise<Comment[]> {
  return api<Comment[]>(`/tasks/${taskId}/comments`);
}

export function addComment(taskId: number, body: string): Promise<Comment> {
  return api<Comment>(`/tasks/${taskId}/comments`, { method: 'POST', body: { body } });
}
