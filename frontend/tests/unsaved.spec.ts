import { test, expect } from './catalog-fixture'

test('clean close, escape confirmation, preserve input and discard reset', async ({
  page,
}) => {
  await page.goto('/formulas')
  await page.getByRole('button', { name: 'Create formula preview' }).click()
  await page.keyboard.press('Escape')
  await expect(page.getByRole('dialog')).toHaveCount(0)
  await page.getByRole('button', { name: 'Create formula preview' }).click()
  await page.getByLabel('Quantity (optional)').fill('25')
  await page.keyboard.press('Escape')
  await expect(page.getByRole('alertdialog')).toBeVisible()
  await expect(page.getByRole('button', { name: 'Keep editing' })).toBeFocused()
  await page.keyboard.press('Escape')
  await expect(page.getByRole('alertdialog')).toHaveCount(0)
  await expect(page.getByLabel('Quantity (optional)')).toHaveValue('25')
  await page
    .locator('[data-slot="dialog-overlay"]')
    .click({ position: { x: 5, y: 5 } })
  await expect(page.getByRole('alertdialog')).toBeVisible()
  await page.getByRole('button', { name: 'Keep editing' }).click()
  await page.getByRole('button', { name: 'Close dialog' }).click()
  await expect(page.getByRole('alertdialog')).toBeVisible()
  await page.screenshot({ path: 'test-results/unsaved-confirmation.png' })
  await page.getByRole('button', { name: 'Discard changes' }).click()
  await page.getByRole('button', { name: 'Create formula preview' }).click()
  await expect(page.getByLabel('Quantity (optional)')).toHaveValue('')
})

test('browser back is blocked and can be cancelled or confirmed', async ({
  page,
}) => {
  await page.goto('/products')
  await page.getByRole('link', { name: 'Formula versions', exact: true }).click()
  await page.getByRole('button', { name: 'Create formula preview' }).click()
  await page.getByLabel('Quantity (optional)').fill('8')
  await page.evaluate(() => history.back())
  await expect(page.getByRole('alertdialog')).toBeVisible()
  await page.getByRole('button', { name: 'Keep editing' }).click()
  await expect(page).toHaveURL(/\/formulas$/)
  await expect(page.getByLabel('Quantity (optional)')).toHaveValue('8')
  await page.evaluate(() => history.back())
  await page.getByRole('button', { name: 'Discard changes' }).click()
  await expect(page).toHaveURL(/\/products$/)
})

test('refresh native prompt can keep the unsaved form', async ({ page }) => {
  await page.goto('/formulas')
  await page.getByRole('button', { name: 'Create formula preview' }).click()
  await page.getByLabel('Quantity (optional)').fill('9')
  const dialogPromise = page.waitForEvent('dialog')
  const reload = page.reload({ timeout: 3000 }).catch(() => null)
  const dialog = await dialogPromise
  expect(dialog.type()).toBe('beforeunload')
  await dialog.dismiss()
  await expect(page.getByLabel('Quantity (optional)')).toHaveValue('9')
  await reload
})
