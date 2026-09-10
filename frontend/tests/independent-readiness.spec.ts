import { test, expect } from './catalog-fixture'

test('multiple items remain validated independently, optional values can stay empty', async ({
  page,
}) => {
  const writes: string[] = []
  page.on('request', (request) => {
    if (['POST', 'PUT', 'PATCH', 'DELETE'].includes(request.method()))
      writes.push(request.url())
  })
  await page.goto('/formulas')
  await page.getByRole('button', { name: 'Create formula preview' }).click()
  await page
    .getByLabel('Product *', { exact: true })
    .selectOption('prod_usda_1106285')
  await page.getByLabel('Supplier material *').selectOption('mat_chocolate_base')
  await page.getByLabel('Specification version *').selectOption('spec_chocolate_v1')
  await page.getByRole('button', { name: 'Add material' }).click()
  await page.getByRole('button', { name: 'Preview formula', exact: true }).click()
  await expect(page.locator('#material-2-error')).toBeVisible()
  await expect(page.locator('#material-1-error')).toHaveCount(0)
  await page.getByRole('button', { name: 'Remove material 2' }).click()
  await expect(page.getByRole('alert')).toHaveCount(0)
  await page.getByRole('button', { name: 'Preview formula', exact: true }).click()
  await expect(page.getByText('Quantity: Not provided · Unit: Not provided')).toBeVisible()
  expect(writes).toEqual([])
})

test('confirmation keeps keyboard focus inside and restores editable form', async ({
  page,
}) => {
  await page.goto('/formulas')
  await page.getByRole('button', { name: 'Create formula preview' }).click()
  await page.getByLabel('Quantity (optional)').fill('10')
  await page.keyboard.press('Escape')
  const confirmation = page.getByRole('alertdialog')
  await expect(page.getByRole('button', { name: 'Keep editing' })).toBeFocused()
  for (let n = 0; n < 5; n++) {
    await page.keyboard.press('Tab')
    expect(
      await confirmation.evaluate((el) => el.contains(document.activeElement)),
    ).toBeTruthy()
  }
  await page.keyboard.press('Escape')
  await expect(confirmation).toHaveCount(0)
  await expect(page.getByLabel('Quantity (optional)')).toHaveValue('10')
  await page.getByLabel('Quantity (optional)').fill('')
  await page.keyboard.press('Escape')
  await expect(page.getByRole('dialog')).toHaveCount(0)
})
