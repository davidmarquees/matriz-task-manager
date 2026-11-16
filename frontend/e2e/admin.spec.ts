import { expect, test } from '@playwright/test';
import { ADMIN, login, logout, unique } from './support';

/**
 * Account administration (US9, US10). Needs MATRIZ_E2E_ADMIN_PASSWORD: the Admin password after the
 * first-login change. Run it only against a test database, since accounts cannot be deleted.
 */
const adminPassword = process.env.MATRIZ_E2E_ADMIN_PASSWORD;

test.skip(!adminPassword, 'MATRIZ_E2E_ADMIN_PASSWORD not set');

test('the Admin creates an account that must change its password, then deactivates it', async ({ page }) => {
  const email = `${unique('e2e').replace(' ', '.')}@example.test`;
  await login(page, ADMIN, adminPassword!);
  await page.getByTestId('nav-admin').click();

  await page.getByTestId('account-name').fill('Conta E2E');
  await page.getByTestId('account-email').fill(email);
  await page.getByTestId('account-password').fill('initial-e2e-pass');
  await page.getByTestId('account-create-submit').click();
  const item = page.locator(`[data-testid="account-item"][data-account-email="${email}"]`);
  await expect(item).toBeVisible();
  await logout(page);

  await login(page, email, 'initial-e2e-pass');
  await expect(page.getByTestId('password-change-required')).toBeVisible();
  await logout(page);

  await login(page, ADMIN, adminPassword!);
  await page.goto('/admin');
  await item.getByTestId('account-deactivate-button').click();
  await page.getByTestId('confirm-ok').click();
  await expect(item.getByTestId('account-item-status')).toHaveText('Desativada');
  await expect(page.getByTestId('audit-item').first()).toContainText('desativou a conta');
});
