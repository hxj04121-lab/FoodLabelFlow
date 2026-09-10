import { test, expect } from './catalog-fixture'
test('formula validation, dependent options, preview and discard', async ({
  page,
}) => {
  await page.goto('/formulas')
  await page.getByRole('button', { name: 'Create formula preview' }).click()
  await page.getByRole('button', { name: 'Preview formula', exact: true }).click()
  await expect(page.getByRole('alert')).toBeVisible()
  await page
    .getByLabel('Product *', { exact: true })
    .selectOption('prod_usda_1106285')
  await page.getByLabel('Supplier material *').selectOption('mat_chocolate_base')
  await page.getByLabel('Specification version *').selectOption('spec_chocolate_v1')
  await page.getByLabel('Quantity (optional)').fill('-1')
  await page.getByRole('button', { name: 'Preview formula', exact: true }).click()
  await expect(page.locator('#quantity-1-error')).toContainText('Enter a positive number')
  await page.getByLabel('Quantity (optional)').fill('12.5')
  await page.getByLabel('Unit (optional)').fill('kg')
  await page.getByRole('button', { name: 'Add material' }).click()
  await page.getByRole('button', { name: 'Remove material 2' }).click()
  await page.getByRole('button', { name: 'Preview formula', exact: true }).click()
  await expect(
    page.getByRole('heading', { name: 'Formula preview' }),
  ).toBeVisible()
  await expect(page.getByText('Quantity: 12.5 · Unit: kg')).toBeVisible()
  await page.getByRole('button', { name: 'Back to editor' }).click()
  await page.getByLabel('Supplier material *').selectOption('mat_soy_carrier')
  await expect(page.getByLabel('Specification version *')).toHaveValue('')
  await page.getByRole('button', { name: 'Cancel', exact: true }).click()
  await expect(page.getByText('Discard unsaved changes?')).toBeVisible()
  await page.getByRole('button', { name: 'Keep editing' }).click()
  await expect(page.getByLabel('Quantity (optional)')).toHaveValue('12.5')
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
  await page.getByRole('button', { name: 'Cancel', exact: true }).click()
  await page.getByRole('button', { name: 'Discard changes' }).click()
  await expect(page.getByRole('dialog')).toHaveCount(0)
})

test('errors persist independently and storage limits are enforced', async ({
  page,
}) => {
  await page.goto('/formulas')
  await page.getByRole('button', { name: 'Create formula preview' }).click()
  await page.getByRole('button', { name: 'Preview formula', exact: true }).click()
  await page
    .getByLabel('Product *', { exact: true })
    .selectOption('prod_usda_1106285')
  await expect(page.locator('#product-error')).toHaveCount(0)
  await expect(page.locator('#material-1-error')).toBeVisible()
  await page.getByLabel('Supplier material *').selectOption('mat_chocolate_base')
  await page.getByLabel('Specification version *').selectOption('spec_chocolate_v1')
  await page.getByLabel('Quantity (optional)').fill('1.12345')
  await expect(page.locator('#quantity-1-error')).toContainText('4 decimal places')
  await page.getByLabel('Unit (optional)').fill('x'.repeat(41))
  await expect(page.locator('#unit-1-error')).toBeVisible()
  await page.getByLabel('Quantity (optional)').fill('99999999.9999')
  await expect(page.locator('#quantity-1-error')).toHaveCount(0)
  await expect(page.locator('#unit-1-error')).toBeVisible()
  await page.getByRole('button', { name: /Item 1: Unit must be at most/ }).click()
  await expect(page.getByLabel('Unit (optional)')).toBeFocused()
  await page.getByLabel('Unit (optional)').fill('kg')
  await page.getByRole('button', { name: 'Preview formula', exact: true }).click()
  await expect(page.getByRole('status')).toContainText('Local checks passed')
  await page.getByRole('button', { name: 'Back to editor' }).click()
  await expect(page.getByLabel('Quantity (optional)')).toHaveValue('99999999.9999')
})
