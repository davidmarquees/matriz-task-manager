import { expect, test } from '@playwright/test';
import { createProject, deleteProject, demoPassword, login, logout, MANAGER, MEMBER, unique } from './support';

/**
 * Main flow of the MVP with two users (US1 to US8): the Manager creates a project, adds the Member
 * and creates a task; the Member moves it to Done; the Manager follows it from the dashboard.
 */
test('a Manager and a Member work on the same task', async ({ page }) => {
  const projectName = unique('E2E projeto');
  const taskTitle = unique('E2E tarefa');

  await login(page, MANAGER, demoPassword());
  const projectId = await createProject(page, projectName);

  await page.getByTestId('member-email-input').fill(MEMBER);
  await page.getByTestId('member-add-submit').click();
  await expect(page.getByTestId('member-item')).toHaveCount(2);

  await page.getByTestId('task-create-open').click();
  await page.getByTestId('task-title-input').fill(taskTitle);
  await page.getByTestId('task-create-submit').click();
  await expect(page.getByTestId('task-item').filter({ hasText: taskTitle })).toBeVisible();
  await logout(page);

  await login(page, MEMBER, demoPassword());
  await page.goto(`/projects/${projectId}`);
  const task = page.getByTestId('task-item').filter({ hasText: taskTitle });
  await task.getByTestId('task-status-select').selectOption('DONE');
  await expect(task.getByTestId('task-status-select')).toHaveValue('DONE');
  await logout(page);

  await login(page, MANAGER, demoPassword());
  await expect(page.getByTestId('home-page')).toBeVisible(); // not the Member's last page (bug fixed in Ex. 8)
  await page.getByTestId('dashboard-kpi-DONE-link').click(); // ↗ opens the projects with Done tasks
  await expect(page.getByTestId('projects-status-hint')).toBeVisible();
  await page.goto(`/projects/${projectId}?status=DONE`);
  await expect(page.getByTestId('task-item').filter({ hasText: taskTitle })).toBeVisible();
  await expect(page.getByTestId('activity-item').first()).toBeVisible(); // project history (FR13)

  await deleteProject(page, projectId);
});

test('a Member cannot open a project they are not in, even by changing the id (D9)', async ({ page }) => {
  await login(page, MANAGER, demoPassword());
  const projectId = await createProject(page, unique('E2E privado'));
  await logout(page);

  await login(page, MEMBER, demoPassword());
  await page.goto(`/projects/${projectId}`);
  await expect(page.getByTestId('project-not-found')).toBeVisible();
  await logout(page);

  await login(page, MANAGER, demoPassword());
  await deleteProject(page, projectId);
});
