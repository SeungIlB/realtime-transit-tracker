import { expect, test } from '@playwright/test'

test('renders the application shell', async ({ page }) => {
  await page.goto('/')

  await expect(page.getByRole('heading', { name: '첫 차와 다음 차를 한눈에' })).toBeVisible()
})
