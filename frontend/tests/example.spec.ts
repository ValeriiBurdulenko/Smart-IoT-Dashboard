import { test, expect } from '@playwright/test';

test('App should load and redirect to Keycloak login', async ({ page }) => {
  await page.goto('/');

  await expect(page).toHaveURL(/.*auth.*/); 

  const usernameInput = page.getByLabel('Username') || page.getByLabel('Email');
  await expect(usernameInput).toBeVisible();
});
