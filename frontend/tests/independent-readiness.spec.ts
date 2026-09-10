import { test, expect } from '@playwright/test'

test('multiple items remain validated independently, optional values can stay empty', async ({
  page,
}) => {
  const writes: string[] = []
  page.on('request', (request) => {
    if (['POST', 'PUT', 'PATCH', 'DELETE'].includes(request.method()))
      writes.push(request.url())
  })
  await page.goto('/formulas')
  await page.getByRole('button', { name: '创建配方', exact: true }).click()
  await page
    .getByLabel('产品 *', { exact: true })
    .selectOption('prod_usda_1106285')
  await page.getByLabel('供应商物料 *').selectOption('mat_chocolate_base')
  await page.getByLabel('规格版本 *').selectOption('spec_chocolate_v1')
  await page.getByRole('button', { name: '添加物料' }).click()
  await page.getByRole('button', { name: '预览配方', exact: true }).click()
  await expect(page.locator('#material-2-error')).toBeVisible()
  await expect(page.locator('#material-1-error')).toHaveCount(0)
  await page.getByRole('button', { name: '删除物料 2' }).click()
  await expect(page.getByRole('alert')).toHaveCount(0)
  await page.getByRole('button', { name: '预览配方', exact: true }).click()
  await expect(page.getByText('数量：未填写 · 单位：未填写')).toBeVisible()
  expect(writes).toEqual([])
})

test('confirmation keeps keyboard focus inside and restores editable form', async ({
  page,
}) => {
  await page.goto('/formulas')
  await page.getByRole('button', { name: '创建配方', exact: true }).click()
  await page.getByLabel('数量（选填）').fill('10')
  await page.keyboard.press('Escape')
  const confirmation = page.getByRole('alertdialog')
  await expect(page.getByRole('button', { name: '继续编辑' })).toBeFocused()
  for (let n = 0; n < 5; n++) {
    await page.keyboard.press('Tab')
    expect(
      await confirmation.evaluate((el) => el.contains(document.activeElement)),
    ).toBeTruthy()
  }
  await page.keyboard.press('Escape')
  await expect(confirmation).toHaveCount(0)
  await expect(page.getByLabel('数量（选填）')).toHaveValue('10')
  await page.getByLabel('数量（选填）').fill('')
  await page.keyboard.press('Escape')
  await expect(page.getByRole('dialog')).toHaveCount(0)
})
