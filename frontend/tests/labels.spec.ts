import { expect, test } from './catalog-fixture'

const allergens = [
  {
    allergenId: 'all_milk',
    allergenCode: 'MILK',
    displayName: 'Milk',
    jurisdictionCode: 'US',
  },
  {
    allergenId: 'all_soy',
    allergenCode: 'SOY',
    displayName: 'Soy',
    jurisdictionCode: 'US',
  },
]

const draft = {
  labelVersionId: 'label_test_v2',
  productId: 'prod_usda_1106285',
  formulaVersionId: 'formula_1106285_v1',
  ruleSetVersionId: 'ruleset_us_falcpa_demo_v1',
  jurisdictionCode: 'US',
  versionNumber: 2,
  rawIngredientText: 'Wheat flour, soy lecithin',
  lifecycleStatus: 'DRAFT',
  isCurrentPublished: 'N',
  createdByUserId: 'user_label_officer',
  createdAt: '2026-09-15T08:00:00',
  dataProvenanceId: 'prov_project_seed',
}

test('shows a loading state and renders canonical allergens from the API', async ({ page }) => {
  let releaseResponse: (() => void) | undefined
  await page.route('**/api/v1/allergens?*', async (route) => {
    await new Promise<void>((resolve) => {
      releaseResponse = resolve
    })
    await route.fulfill({ json: allergens })
  })

  await page.goto('/labels')
  await expect(page.getByRole('status')).toContainText('Checking the canonical allergen endpoint')
  releaseResponse?.()

  await expect(page.getByText('Canonical allergen endpoint connected')).toBeVisible()
  await expect(page.getByText('2 entries returned for US.')).toBeVisible()
  await page.screenshot({ path: 'test-results/scrum17-labels-desktop.png', fullPage: true })
})

test('shows an honest empty state', async ({ page }) => {
  await page.route('**/api/v1/allergens?*', (route) => route.fulfill({ json: [] }))
  await page.goto('/labels')

  await expect(page.getByText('Canonical allergen endpoint connected')).toBeVisible()
  await expect(page.getByText('0 entries returned for US.')).toBeVisible()
})

test('shows API errors without seed fallback and retries the request', async ({ page }) => {
  let serviceAvailable = false
  await page.route('**/api/v1/allergens?*', (route) => {
    if (!serviceAvailable) {
      return route.fulfill({
        status: 503,
        json: {
          code: 'INTERNAL_ERROR',
          message: 'Validation service is unavailable.',
          traceId: 'trace-test-1',
          evidenceId: null,
        },
      })
    }
    return route.fulfill({ json: allergens })
  })

  await page.goto('/labels')
  await expect(page.getByRole('alert')).toHaveText('Validation service is unavailable.')
  await expect(page.getByText('Derived facts and declarations pending')).toBeVisible()

  serviceAvailable = true
  await page.getByRole('button', { name: 'Retry check' }).click()
  await expect(page.getByText('Canonical allergen endpoint connected')).toBeVisible()
})

test('rejects a successful response that does not match the frozen contract', async ({ page }) => {
  await page.route('**/api/v1/allergens?*', (route) =>
    route.fulfill({ json: [{ allergen_id: 'all_milk', display_name: 'Milk' }] }),
  )
  await page.goto('/labels')

  await expect(page.getByRole('alert')).toHaveText(
    'The label API returned an invalid allergen list.',
  )
})

test('keeps the label foundation readable without mobile overflow', async ({ page }) => {
  await page.route('**/api/v1/allergens?*', (route) => route.fulfill({ json: allergens }))
  await page.setViewportSize({ width: 390, height: 844 })
  await page.goto('/labels')

  await expect(page.getByRole('heading', { name: 'Label draft context' })).toBeVisible()
  expect(
    await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth),
  ).toBeTruthy()
  await page.screenshot({ path: 'test-results/scrum17-labels-mobile.png', fullPage: true })
})

test('creates a label draft with the approved local identity and renders server context', async ({ page }) => {
  await page.route('**/api/v1/allergens?*', (route) => route.fulfill({ json: allergens }))
  await page.route('**/api/labels/drafts', async (route) => {
    expect(route.request().method()).toBe('POST')
    expect(route.request().headers()['x-auth-provider']).toBe('DEV_EXTERNAL')
    expect(route.request().headers()['x-external-subject']).toBe(
      'dev-external-label-officer',
    )
    expect(route.request().postDataJSON()).toEqual({
      productId: draft.productId,
      jurisdictionCode: 'US',
    })
    await route.fulfill({ status: 201, json: draft })
  })
  await page.goto('/labels')

  const create = page.getByRole('button', { name: 'Create label draft' })
  await expect(create).toBeDisabled()
  await page
    .getByRole('checkbox', { name: 'Enable the local demo label-officer identity' })
    .check()
  await create.click()

  await expect(page.getByRole('region', { name: 'Label draft details' })).toContainText(
    draft.labelVersionId,
  )
  await expect(page.getByRole('region', { name: 'Label draft details' })).toContainText(
    draft.ruleSetVersionId,
  )
  await expect(page.getByText('Label draft V2 created and read from the server.')).toBeVisible()
  await page.screenshot({ path: 'test-results/scrum18-draft-context.png', fullPage: true })
})

test('reopens an existing label draft by exact server ID', async ({ page }) => {
  await page.route('**/api/v1/allergens?*', (route) => route.fulfill({ json: allergens }))
  await page.route(`**/api/labels/${draft.labelVersionId}`, async (route) => {
    expect(route.request().headers()['x-auth-provider']).toBe('DEV_EXTERNAL')
    await route.fulfill({ json: draft })
  })
  await page.goto('/labels')

  await page.getByLabel('Existing label version ID').fill(draft.labelVersionId)
  await page.getByRole('button', { name: 'Load label draft' }).click()

  await expect(page.getByText(`Loaded ${draft.labelVersionId} from the server.`)).toBeVisible()
  await expect(page.getByRole('region', { name: 'Label draft details' })).toContainText(
    draft.rawIngredientText,
  )
})

test('keeps authorization errors visible and does not claim a created draft', async ({ page }) => {
  await page.route('**/api/v1/allergens?*', (route) => route.fulfill({ json: allergens }))
  await page.route('**/api/labels/drafts', (route) =>
    route.fulfill({
      status: 403,
      json: {
        code: 'AUTHORIZATION_DENIED',
        message: 'The actor is not permitted to create label drafts.',
        traceId: null,
        evidenceId: null,
      },
    }),
  )
  await page.goto('/labels')
  await page
    .getByRole('checkbox', { name: 'Enable the local demo label-officer identity' })
    .check()
  await page.getByRole('button', { name: 'Create label draft' }).click()

  await expect(page.getByRole('alert')).toContainText('AUTHORIZATION_DENIED')
  await expect(page.getByRole('region', { name: 'Label draft details' })).toHaveCount(0)
})
