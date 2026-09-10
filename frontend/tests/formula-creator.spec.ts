import { test, expect } from '@playwright/test'
test('formula validation, dependent options, preview and discard', async ({
  page,
}) => {
  await page.goto('/formulas')
  await page.getByRole('button', { name: '创建配方', exact: true }).click()
  await page.getByRole('button', { name: '预览配方', exact: true }).click()
  await expect(page.getByRole('alert')).toBeVisible()
  await page
    .getByLabel('产品 *', { exact: true })
    .selectOption('prod_usda_1106285')
  await page.getByLabel('供应商物料 *').selectOption('mat_chocolate_base')
  await page.getByLabel('规格版本 *').selectOption('spec_chocolate_v1')
  await page.getByLabel('数量（选填）').fill('-1')
  await page.getByRole('button', { name: '预览配方', exact: true }).click()
  await expect(page.locator('#quantity-1-error')).toContainText('请输入非负数')
  await page.getByLabel('数量（选填）').fill('12.5')
  await page.getByLabel('单位（选填）').fill('kg')
  await page.getByRole('button', { name: '添加物料' }).click()
  await page.getByRole('button', { name: '删除物料 2' }).click()
  await page.getByRole('button', { name: '预览配方', exact: true }).click()
  await expect(
    page.getByRole('heading', { name: '配方内容预览' }),
  ).toBeVisible()
  await expect(page.getByText('数量：12.5 · 单位：kg')).toBeVisible()
  await page.getByRole('button', { name: '返回编辑' }).click()
  await page.getByLabel('供应商物料 *').selectOption('mat_soy_carrier')
  await expect(page.getByLabel('规格版本 *')).toHaveValue('')
  await page.getByRole('button', { name: '取消', exact: true }).click()
  await expect(page.getByText('放弃尚未保存的内容？')).toBeVisible()
  await page.getByRole('button', { name: '继续编辑' }).click()
  await expect(page.getByLabel('数量（选填）')).toHaveValue('12.5')
  await page.setViewportSize({ width: 390, height: 844 })
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBeTruthy()
  await page.screenshot({
    path: 'test-results/formula-creator-mobile.png',
    fullPage: true,
  })
  await page.getByRole('button', { name: '取消', exact: true }).click()
  await page.getByRole('button', { name: '放弃并关闭' }).click()
  await expect(page.getByRole('dialog')).toHaveCount(0)
})

test('errors persist independently and storage limits are enforced', async ({
  page,
}) => {
  await page.goto('/formulas')
  await page.getByRole('button', { name: '创建配方', exact: true }).click()
  await page.getByRole('button', { name: '预览配方', exact: true }).click()
  await page
    .getByLabel('产品 *', { exact: true })
    .selectOption('prod_usda_1106285')
  await expect(page.locator('#product-error')).toHaveCount(0)
  await expect(page.locator('#material-1-error')).toBeVisible()
  await page.getByLabel('供应商物料 *').selectOption('mat_chocolate_base')
  await page.getByLabel('规格版本 *').selectOption('spec_chocolate_v1')
  await page.getByLabel('数量（选填）').fill('1.12345')
  await expect(page.locator('#quantity-1-error')).toContainText('4 位小数')
  await page.getByLabel('单位（选填）').fill('x'.repeat(41))
  await expect(page.locator('#unit-1-error')).toBeVisible()
  await page.getByLabel('数量（选填）').fill('99999999.9999')
  await expect(page.locator('#quantity-1-error')).toHaveCount(0)
  await expect(page.locator('#unit-1-error')).toBeVisible()
  await page.getByRole('button', { name: /物料 1：单位最多/ }).click()
  await expect(page.getByLabel('单位（选填）')).toBeFocused()
  await page.getByLabel('单位（选填）').fill('kg')
  await page.getByRole('button', { name: '预览配方', exact: true }).click()
  await expect(page.getByRole('status')).toContainText('本地校验通过')
  await page.getByRole('button', { name: '返回编辑' }).click()
  await expect(page.getByLabel('数量（选填）')).toHaveValue('99999999.9999')
})
