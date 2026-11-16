import { expect, type Page } from '@playwright/test';

/** Demo accounts of the dev profile (fictitious); the password comes from the environment. */
export const MANAGER = 'manager@example.test';
export const MEMBER = 'member@example.test';
export const ADMIN = 'admin@example.test';

export function demoPassword(): string {
  const value = process.env.MATRIZ_DEMO_PASSWORD;
  if (!value) throw new Error('Set MATRIZ_DEMO_PASSWORD (same value as in .env) to run the E2E tests.');
  return value;
}

export async function login(page: Page, email: string, password: string) {
  await page.goto('/login');
  await page.getByTestId('login-email').fill(email);
  await page.getByTestId('login-password').fill(password);
  await page.getByTestId('login-submit').click();
  await expect(page.getByTestId('current-user-name')).toBeVisible();
}

export async function logout(page: Page) {
  await page.getByTestId('logout-button').click();
  await expect(page.getByTestId('login-form')).toBeVisible();
}

/** Unique name so runs do not clash with existing data. */
export function unique(prefix: string): string {
  return `${prefix} ${Date.now().toString(36)}`;
}

/** Creates a project as the logged-in Manager and opens it; returns its id. */
export async function createProject(page: Page, name: string): Promise<number> {
  await page.goto('/projects');
  await page.getByTestId('project-create-open').click();
  await page.getByTestId('project-name-input').fill(name);
  await page.getByTestId('project-create-submit').click();
  await page.getByTestId('project-search').fill(name);
  await page.getByTestId('project-link').filter({ hasText: name }).click();
  await expect(page.getByTestId('project-name')).toHaveText(name);
  return Number(new URL(page.url()).pathname.split('/').pop());
}

/** Deletes a project through the interface (clean-up at the end of a test). */
export async function deleteProject(page: Page, id: number) {
  await page.goto(`/projects/${id}`);
  await page.getByTestId('project-delete-button').click();
  await page.getByTestId('confirm-ok').click();
  await expect(page.getByTestId('projects-page')).toBeVisible();
}
