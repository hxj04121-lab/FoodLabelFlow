import { test, expect } from '@playwright/test'

test('clean close, escape confirmation, preserve input and discard reset', async ({
  page,
}) => {
  await page.goto('/formulas')
  await page.getByRole('button', { name: '创建配方预览' }).click()
  await page.keyboard.press('Escape')
  await expect(page.getByRole('dialog')).toHaveCount(0)
  await page.getByRole('button', { name: '创建配方预览' }).click()
  await page.getByLabel('数量（选填）').fill('25')
  await page.keyboard.press('Escape')
  await expect(page.getByRole('alertdialog')).toBeVisible()
  await expect(page.getByRole('button', { name: '继续编辑' })).toBeFocused()
  await page.keyboard.press('Escape')
  await expect(page.getByRole('alertdialog')).toHaveCount(0)
  await expect(page.getByLabel('数量（选填）')).toHaveValue('25')
  await page
    .locator('[data-slot="dialog-overlay"]')
    .click({ position: { x: 5, y: 5 } })
  await expect(page.getByRole('alertdialog')).toBeVisible()
  await page.getByRole('button', { name: '继续编辑' }).click()
  await page.getByRole('button', { name: '关闭弹窗' }).click()
  await expect(page.getByRole('alertdialog')).toBeVisible()
  await page.screenshot({ path: 'test-results/unsaved-confirmation.png' })
  await page.getByRole('button', { name: '放弃并关闭' }).click()
  await page.getByRole('button', { name: '创建配方预览' }).click()
  await expect(page.getByLabel('数量（选填）')).toHaveValue('')
})

test('browser back is blocked and can be cancelled or confirmed', async ({
  page,
}) => {
  await page.goto('/products')
  await page.getByRole('link', { name: '配方版本', exact: true }).click()
  await page.getByRole('button', { name: '创建配方预览' }).click()
  await page.getByLabel('数量（选填）').fill('8')
  await page.evaluate(() => history.back())
  await expect(page.getByRole('alertdialog')).toBeVisible()
  await page.getByRole('button', { name: '继续编辑' }).click()
  await expect(page).toHaveURL(/\/formulas$/)
  await expect(page.getByLabel('数量（选填）')).toHaveValue('8')
  await page.evaluate(() => history.back())
  await page.getByRole('button', { name: '放弃并关闭' }).click()
  await expect(page).toHaveURL(/\/products$/)
})

test('refresh native prompt can keep the unsaved form', async ({ page }) => {
  await page.goto('/formulas')
  await page.getByRole('button', { name: '创建配方预览' }).click()
  await page.getByLabel('数量（选填）').fill('9')
  const dialogPromise = page.waitForEvent('dialog')
  const reload = page.reload({ timeout: 3000 }).catch(() => null)
  const dialog = await dialogPromise
  expect(dialog.type()).toBe('beforeunload')
  await dialog.dismiss()
  await expect(page.getByLabel('数量（选填）')).toHaveValue('9')
  await reload
})
