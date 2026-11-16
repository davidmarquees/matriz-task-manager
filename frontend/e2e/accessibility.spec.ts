import AxeBuilder from '@axe-core/playwright';
import { expect, test, type Page } from '@playwright/test';
import { createProject, deleteProject, demoPassword, login, MANAGER, unique } from './support';

/**
 * Automatic accessibility audit (NFR6, WCAG 2.1 AA rules of axe-core) of the main pages in both themes.
 * Serious and critical violations fail the test; the full list is attached to the report.
 */
async function audit(page: Page, name: string) {
  const result = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
  await test.info().attach(`axe-${name}.json`, { body: JSON.stringify(result.violations, null, 2), contentType: 'application/json' });
  const blocking = result.violations
    .filter((v) => v.impact === 'serious' || v.impact === 'critical')
    .map((v) => `${v.id} (${v.impact}): ${v.nodes.map((n) => n.target.join(' ')).join(', ')}`);
  expect(blocking, `${name}: ${blocking.join('; ')}`).toEqual([]);
}

for (const colorScheme of ['light', 'dark'] as const) {
  test(`main pages have no serious accessibility problems (${colorScheme})`, async ({ page }) => {
    await page.emulateMedia({ colorScheme });
    await page.goto('/login');
    await expect(page.getByTestId('login-form')).toBeVisible();
    await audit(page, `login-${colorScheme}`);
    await page.goto('/forgot-password');
    await audit(page, `forgot-password-${colorScheme}`);

    await login(page, MANAGER, demoPassword());
    await expect(page.getByTestId('home-page')).toBeVisible();
    await audit(page, `dashboard-${colorScheme}`);

    const projectId = await createProject(page, unique('E2E a11y'));
    await page.getByTestId('task-create-open').click();
    await page.getByTestId('task-title-input').fill('Rever contraste');
    await page.getByTestId('task-create-submit').click();
    await expect(page.getByTestId('task-item')).toHaveCount(1);
    await audit(page, `project-${colorScheme}`);

    await page.getByTestId('task-link').first().click();
    await expect(page.getByTestId('task-detail')).toBeVisible();
    await audit(page, `task-${colorScheme}`);

    await page.goto('/projects');
    await expect(page.getByTestId('project-list')).toBeVisible();
    await audit(page, `projects-${colorScheme}`);

    await deleteProject(page, projectId);
  });
}
