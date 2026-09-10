import { expect, test } from '@playwright/test'

test('creates, publishes and reads formula history through the full stack', async ({ page }) => {
  await page.goto('/formulas')
  await page.getByRole('button', { name: '创建配方', exact: true }).click()
  await page.getByLabel('产品 *', { exact: true }).selectOption('prod_usda_1106285')
  await page.getByLabel('供应商物料 *').selectOption('mat_chocolate_base')
  await page.getByLabel('规格版本 *').selectOption('spec_chocolate_v1')
  await page.getByLabel('数量（选填）').fill('12.5')
  await page.getByLabel('单位（选填）').fill('kg')
  await page.getByRole('button', { name: '预览配方', exact: true }).click()

  await page.getByRole('button', { name: '保存草稿' }).click()
  await expect(page.getByRole('status')).toContainText('已保存到服务器')
  await page.getByRole('button', { name: '发布配方' }).click()

  await expect(page.getByRole('status')).toContainText('已发布')
  await expect(page.getByRole('region', { name: '配方历史' })).toBeVisible()
  await expect(page.getByRole('region', { name: '配方历史' }).getByText(/V1 · RELEASED/)).toBeVisible()
  expect(await page.getByRole('region', { name: '配方历史' }).getByText(/V\d+ · RELEASED/).count()).toBeGreaterThanOrEqual(2)
})
