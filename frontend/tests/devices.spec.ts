import { test, expect } from '@playwright/test';

test('Should display devices list', async ({ page }) => {
  await page.goto('http://localhost:5173/devices');

  await expect(page.getByRole('heading', { name: 'Devices' })).toBeVisible();
  
  // await expect(page.locator('.device-card')).toHaveCountGreaterThan(0);
});