import { expect, test } from '@playwright/test';
import { createProject, deleteProject, demoPassword, login, MANAGER, unique } from './support';

/**
 * Worksheet 04, Ex. 4 (docs/test-plan.md T7): log in → open project → create task → see it in the list.
 * Expected results come from US3 (defaults, mandatory title), NFR1 (labels, never codes) and US15
 * ("Criada" until the task changes). Each test creates its own project and deletes it afterwards.
 * No fixed waits: every check uses Playwright's auto-waiting assertions.
 */
let projectId: number | undefined;

test.beforeEach(async ({ page }) => {
  await login(page, MANAGER, demoPassword());
  // createProject opens the new project from the project list ("abrir projeto")
  projectId = await createProject(page, unique('E2E Ex4 projeto'));
  await expect(page.getByTestId('tasks-empty')).toBeVisible();
});

test.afterEach(async ({ page }) => {
  if (projectId !== undefined) await deleteProject(page, projectId);
  projectId = undefined;
});

test('a Manager creates a task and sees it in the project list', async ({ page }) => {
  const title = unique('E2E Ex4 tarefa');

  await page.getByTestId('task-create-open').click();
  await page.getByTestId('task-title-input').fill(title);
  await page.getByTestId('task-priority-input').selectOption('HIGH');
  await page.getByTestId('task-create-submit').click();

  const item = page.getByTestId('task-list').getByTestId('task-item').filter({ hasText: title });
  await expect(item).toHaveCount(1);
  await expect(item.getByTestId('task-link')).toHaveText(title);
  await expect(item.getByTestId('task-status-select')).toHaveValue('TODO'); // default status (US3)
  await expect(item.getByTestId('task-status-select').locator('option:checked')).toHaveText('A fazer'); // NFR1
  await expect(item.getByTestId('task-priority-select')).toHaveValue('HIGH');
  await expect(item.getByTestId('task-priority-select').locator('option:checked')).toHaveText('Alta');
  await expect(item).not.toContainText(/\b(TODO|IN_PROGRESS|HIGH)\b/); // never API codes (NFR1)
  await expect(item.getByTestId('task-last-change')).toContainText('Criada hoje'); // US15
  await expect(page.getByTestId('tasks-total')).toContainText('1');

  // still there after a reload: it came from the server, not only from the page state
  await page.reload();
  await expect(page.getByTestId('task-item').filter({ hasText: title })).toHaveCount(1);
});

test('a task without a title is not created and the title is flagged as mandatory', async ({ page }) => {
  await page.getByTestId('task-create-open').click();
  await page.getByTestId('task-title-input').fill('   ');
  await page.getByTestId('task-create-submit').click();

  await expect(page.getByTestId('task-create-error')).toContainText('O título é obrigatório'); // US3
  await expect(page.getByTestId('task-create-form')).toBeVisible();
  await page.reload();
  await expect(page.getByTestId('tasks-empty')).toBeVisible(); // nothing was saved
});
