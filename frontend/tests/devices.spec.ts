import { test, expect } from '@playwright/test';

test.beforeEach(async ({ page }) => {
  // 1. Go to the app, which will redirect to Keycloak
  await page.goto('/');
  await page.waitForURL(/.*openid-connect\/auth.*/, { timeout: 15000 });

  // 2. Perform the login
  const usernameField = page.locator('#username');
  await usernameField.waitFor({ state: 'visible', timeout: 10000 });
  await usernameField.fill('ronikavb.ua@gmail.com');
  await page.locator('#password').fill('12345678');
  await page.locator('#kc-login').click();

  // 3. Wait until we are successfully redirected back to the app
  await page.waitForURL('http://localhost:5173/', { timeout: 15000 });
});

test('Should display devices list', async ({ page }) => {
  // We are already authenticated and on the home page thanks to beforeEach

  // Navigate to devices
  await page.goto('/devices');

  // Wait for the heading to appear (increased timeout to account for API loading)
  await expect(page.getByRole('heading', { name: 'Devices' })).toBeVisible({ timeout: 15000 });
});