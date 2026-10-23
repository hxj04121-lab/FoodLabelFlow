import { test, expect } from './catalog-fixture'
import seed from '../src/data/seed-preview.json' with { type: 'json' }

test('Data Admin creates a specification draft with a new allergen ingredient and releases it explicitly', async ({ page }) => {
  const writes: Array<{ method: string; path: string; body?: unknown }> = []
  await page.route('**/api/identity/current', route => route.fulfill({ json: {
    userId: 'user_admin', username: 'data.admin', displayName: 'Demo Admin/Data Maintenance',
    roles: ['ADMIN_DATA_MAINTENANCE'], permissions: ['DATA.MAINTAIN', 'FORMULA.RELEASE', 'RULE_SET.MAINTAIN'],
  } }))
  await page.route('**/api/catalog/specifications', async route => {
    if (route.request().method() !== 'POST') return route.fallback()
    writes.push({ method: 'POST', path: '/api/catalog/specifications', body: route.request().postDataJSON() })
    await route.fulfill({ status: 201, json: {
      specification_version_id: 'spec_test_soy_v2', supplier_material_id: 'mat_chocolate_base',
      version_number: 2, lifecycle_status: 'DRAFT', effective_date: '2026-10-23',
    } })
  })
  await page.route('**/api/catalog/specifications/spec_test_soy_v2/release', async route => {
    if (route.request().method() !== 'POST') return route.fallback()
    writes.push({ method: 'POST', path: '/api/catalog/specifications/spec_test_soy_v2/release' })
    await route.fulfill({ status: 200, json: {
      specification_version_id: 'spec_test_soy_v2', supplier_material_id: 'mat_chocolate_base',
      version_number: 2, lifecycle_status: 'RELEASED', effective_date: '2026-10-23',
    } })
  })

  await page.goto('/materials')
  await page.getByRole('button', { name: 'Create supplier specification' }).click()
  await page.getByLabel('Supplier material *').selectOption('mat_chocolate_base')
  await expect(page.getByLabel('Ingredient *').first()).toHaveValue('ing_cocoa')
  await page.getByRole('button', { name: 'Add ingredient' }).click()
  await page.getByLabel('Ingredient *').nth(2).selectOption('ing_soy_lecithin')
  await page.getByLabel('Supplier wording *').nth(2).fill('Soy lecithin')
  await page.getByLabel('Matching evidence *').nth(2).fill('FOOD-S4-UI-20261023 supplier COA')

  const createResponse = page.waitForResponse(response => response.request().method() === 'POST' && new URL(response.url()).pathname === '/api/catalog/specifications')
  await page.getByRole('button', { name: 'Save specification draft' }).click()
  expect((await createResponse).status()).toBe(201)
  await expect(page.getByRole('heading', { name: 'Specification V2 · DRAFT' })).toBeVisible()
  await expect(page.getByText('spec_test_soy_v2')).toBeVisible()
  expect(writes[0]).toMatchObject({ method: 'POST', path: '/api/catalog/specifications', body: {
    materialId: 'mat_chocolate_base', effectiveDate: expect.any(String), provenanceId: 'prov_project_seed',
    components: expect.arrayContaining([expect.objectContaining({ ingredientId: 'ing_soy_lecithin', rawPhrase: 'Soy lecithin' })]),
  } })
  await page.getByRole('button', { name: 'Release specification' }).click()
  await expect(page.getByRole('heading', { name: 'Specification V2 · RELEASED' })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Release specification' })).toHaveCount(0)
  expect(writes.map(write => write.path)).toEqual([
    '/api/catalog/specifications', '/api/catalog/specifications/spec_test_soy_v2/release',
  ])

  // Closing a released result any way (not only Done) starts the next specification from an empty form.
  await page.keyboard.press('Escape')
  await page.getByRole('button', { name: 'Create supplier specification' }).click()
  await expect(page.getByRole('heading', { name: 'Specification V2 · RELEASED' })).toHaveCount(0)
  await expect(page.getByRole('button', { name: 'Save specification draft' })).toBeEnabled()
  await expect(page.getByLabel('Supplier material *')).toHaveValue('')
})

test('catalog identities without DATA.MAINTAIN cannot create a supplier specification', async ({ page }) => {
  await page.goto('/materials')
  const create = page.getByRole('button', { name: 'Create supplier specification' })
  await expect(create).toBeDisabled()
  await expect(page.getByRole('status')).toContainText('requires DATA.MAINTAIN')
})

test('materials without specification history get unique editable rows that delete independently', async ({ page }) => {
  await page.route('**/api/identity/current', route => route.fulfill({ json: {
    userId: 'user_admin', username: 'data.admin', displayName: 'Demo Admin/Data Maintenance',
    roles: ['ADMIN_DATA_MAINTENANCE'], permissions: ['DATA.MAINTAIN'],
  } }))
  await page.route('**/api/catalog/materials**', async route => {
    const materials = [
      ...seed.supplier_material,
      {
        supplier_material_id: 'mat_creator_no_history',
        supplier_id: seed.supplier[0].supplier_id,
        material_name: 'No history regression material',
      },
    ]
    await route.fulfill({ json: materials })
  })

  await page.goto('/materials')
  await page.getByRole('button', { name: 'Create supplier specification' }).click()
  await page.getByLabel('Supplier material *').selectOption('mat_creator_no_history')

  const ingredients = page.locator('select[id^="specification-ingredient-"]')
  await expect(ingredients).toHaveCount(1)
  await expect(ingredients.first()).toHaveValue('')
  await page.getByRole('button', { name: 'Add ingredient' }).click()
  await expect(ingredients).toHaveCount(2)

  const ids = await ingredients.evaluateAll(nodes => nodes.map(node => (node as HTMLSelectElement).id))
  expect(new Set(ids).size).toBe(2)

  await ingredients.nth(0).selectOption('ing_cocoa')
  await ingredients.nth(1).selectOption('ing_soy_lecithin')
  const phrases = page.locator('input[id^="specification-phrase-"]')
  const matches = page.locator('input[id^="specification-match-"]')
  await phrases.nth(0).fill('first supplier wording')
  await phrases.nth(1).fill('second supplier wording')
  await matches.nth(0).fill('first component evidence')
  await matches.nth(1).fill('second component evidence')

  await expect(ingredients.nth(0)).toHaveValue('ing_cocoa')
  await expect(ingredients.nth(1)).toHaveValue('ing_soy_lecithin')
  await expect(phrases.nth(0)).toHaveValue('first supplier wording')
  await expect(phrases.nth(1)).toHaveValue('second supplier wording')
  await expect(matches.nth(0)).toHaveValue('first component evidence')
  await expect(matches.nth(1)).toHaveValue('second component evidence')

  await page.getByRole('button', { name: 'Remove ingredient 1' }).click()
  await expect(ingredients).toHaveCount(1)
  await expect(ingredients.first()).toHaveValue('ing_soy_lecithin')
  await expect(phrases).toHaveCount(1)
  await expect(phrases.first()).toHaveValue('second supplier wording')
  await expect(matches).toHaveCount(1)
  await expect(matches.first()).toHaveValue('second component evidence')
})
