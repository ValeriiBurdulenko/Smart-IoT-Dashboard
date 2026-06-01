import { test, expect } from '@playwright/test';

test('Should display devices list', async ({ page }) => {
  await page.goto('/devices');

  await page.waitForURL(/.*openid-connect\/auth.*/, { timeout: 15000 });

  const usernameField = page.locator('#username');
  await usernameField.waitFor({ state: 'visible', timeout: 10000 });
  await usernameField.fill('ronikavb.ua@gmail.com');
  await page.locator('#password').fill('12345678');
  await page.locator('#kc-login').click();

  await expect(page.getByRole('heading', { name: 'Devices' })).toBeVisible({ timeout: 15000 });
});