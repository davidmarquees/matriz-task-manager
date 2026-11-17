import { api } from './client';

export type ProjectRole = 'MANAGER' | 'MEMBER';

export interface UserSummary {
  id: number;
  name: string | null;
  active: boolean;
}

export interface TaskCounts {
  TODO: number;
  IN_PROGRESS: number;
  DONE: number;
}

export interface ProjectSummary {
  id: number;
  name: string;
  description: string | null;
  myRole: ProjectRole | null;
  memberCount: number;
  taskCounts?: TaskCounts;
}

export interface Project extends ProjectSummary {
  createdBy: UserSummary;
  createdAt: string;
  updatedAt: string;
  version: number;
}

export interface ProjectPage {
  items: ProjectSummary[];
  page: number;
  size: number;
  totalItems: number;
}

export interface Member {
  user: UserSummary;
  projectRole: ProjectRole;
  addedAt: string;
}

export const PROJECT_NAME_MAX = 100;
export const PROJECT_DESCRIPTION_MAX = 2000;
export const PAGE_SIZE = 20;

export function listProjects(q: string, page: number): Promise<ProjectPage> {
  const params = new URLSearchParams({ page: String(page), size: String(PAGE_SIZE) });
  if (q.trim()) {
    params.set('q', q.trim());
  }
  return api<ProjectPage>(`/projects?${params.toString()}`);
}

export function createProject(name: string, description: string): Promise<Project> {
  return api<Project>('/projects', {
    method: 'POST',
    body: { name, description: description.trim() ? description : null },
  });
}

export function getProject(id: number): Promise<Project> {
  return api<Project>(`/projects/${id}`);
}

export function updateProject(
  id: number,
  version: number,
  changes: { name?: string; description?: string | null },
): Promise<Project> {
  return api<Project>(`/projects/${id}`, { method: 'PATCH', body: { version, ...changes } });
}

export function deleteProject(id: number): Promise<void> {
  return api<void>(`/projects/${id}`, { method: 'DELETE' });
}

export function listMembers(projectId: number): Promise<Member[]> {
  return api<Member[]>(`/projects/${projectId}/members`);
}

export function addMember(projectId: number, email: string, projectRole: ProjectRole): Promise<Member> {
  return api<Member>(`/projects/${projectId}/members`, { method: 'POST', body: { email, projectRole } });
}

export function changeMemberRole(projectId: number, userId: number, projectRole: ProjectRole): Promise<Member> {
  return api<Member>(`/projects/${projectId}/members/${userId}`, { method: 'PATCH', body: { projectRole } });
}

export function removeMember(projectId: number, userId: number): Promise<void> {
  return api<void>(`/projects/${projectId}/members/${userId}`, { method: 'DELETE' });
}
