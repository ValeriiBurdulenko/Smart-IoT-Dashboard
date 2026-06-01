import { test as setup, expect } from '@playwright/test';

const authFile = 'playwright/.auth/user.json';

setup('authenticate', async ({ page }) => {
  await page.goto('/');

  await page.waitForURL(/.*openid-connect\/auth.*/);

  await page.locator('#username').fill('ronikavb.ua@gmail.com');
  await page.locator('#password').fill('12345678');
  await page.locator('#kc-login').click();

  await expect(page.getByRole('heading', { name: 'Dashboard' })).toBeVisible();

  await page.context().storageState({ path: authFile });
});