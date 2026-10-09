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

test.beforeEach(async ({ page }) => {
  await page.route('**/api/v1/label-versions/*/derived-allergens', (route) =>
    route.fulfill({ json: { ...draft, facts: [], unresolvedComponents: [] } }),
  )
  await page.route('**/api/labels/*/declarations', (route) =>
    route.fulfill({ json: { ...draft, declarations: [] } }),
  )
})

test('shows a loading state and renders canonical allergens from the API', async ({ page }) => {
  // Create the gate before navigation: loading can render before the route runs.
  let releaseResponse!: () => void
  const responseGate = new Promise<void>((resolve) => {
    releaseResponse = resolve
  })
  await page.route('**/api/v1/allergens?*', async (route) => {
    await responseGate
    await route.fulfill({ json: allergens })
  })

  await page.goto('/labels')
  await expect(page.getByRole('status').filter({ hasText: 'Checking the canonical allergen endpoint' })).toBeVisible()
  releaseResponse()

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

test('keeps bound-task creation disabled while the declaration catalog is loading', async ({ page }) => {
  let releaseResponse!: () => void
  const responseGate = new Promise<void>((resolve) => {
    releaseResponse = resolve
  })
  let creates = 0
  await page.route('**/api/v1/allergens?*', async (route) => {
    await responseGate
    await route.fulfill({ json: allergens })
  })
  await page.route('**/api/labels/drafts', (route) => {
    creates++
    return route.fulfill({ status: 201, json: draft })
  })
  await page.goto('/labels?productId=' + draft.productId + '&reviewTaskId=task_catalog_loading')
  try {
    await page.getByRole('checkbox', { name: 'Use the connected identity to create this draft' }).check()
    await expect(page.getByRole('status').filter({ hasText: 'Checking the canonical allergen endpoint' })).toBeVisible()
    await expect(page.getByRole('button', { name: 'Create label draft' })).toBeDisabled()
    await expect(page.getByRole('checkbox', { name: 'Declare Soy (SOY)' })).toHaveCount(0)
    expect(creates).toBe(0)
  } finally {
    releaseResponse()
  }
  await expect(page.getByText('Canonical allergen endpoint connected')).toBeVisible()
  await expect(page.getByRole('button', { name: 'Create label draft' })).toBeEnabled()
  expect(creates).toBe(0)
})

test('keeps bound-task creation disabled after the declaration catalog fails', async ({ page }) => {
  let creates = 0
  await page.route('**/api/v1/allergens?*', (route) => route.fulfill({
    status: 503,
    json: { code: 'UNAVAILABLE', message: 'Declaration catalog unavailable.' },
  }))
  await page.route('**/api/labels/drafts', (route) => {
    creates++
    return route.fulfill({ status: 201, json: draft })
  })
  await page.goto('/labels?productId=' + draft.productId + '&reviewTaskId=task_catalog_failed')
  await page.getByRole('checkbox', { name: 'Use the connected identity to create this draft' }).check()
 await expect(page.getByRole('alert').filter({ hasText: 'Declaration catalog unavailable.' })).toHaveText('Declaration catalog unavailable.')
  await expect(page.getByRole('button', { name: 'Create label draft' })).toBeDisabled()
  await expect(page.getByRole('checkbox', { name: 'Declare Soy (SOY)' })).toHaveCount(0)
  expect(creates).toBe(0)
})

test('allows explicit empty declarations after the canonical catalog successfully returns empty', async ({ page }) => {
  let creates = 0
  await page.route('**/api/v1/allergens?*', (route) => route.fulfill({ json: [] }))
  await page.route('**/api/labels/drafts', async (route) => {
    creates++
    expect(route.request().postDataJSON()).toEqual({
      productId: draft.productId,
      jurisdictionCode: 'US',
      declarations: [],
      reviewTaskId: 'task_catalog_empty',
    })
    await route.fulfill({ status: 201, json: draft })
  })
  await page.goto('/labels?productId=' + draft.productId + '&reviewTaskId=task_catalog_empty')
  await expect(page.getByText('0 entries returned for US.')).toBeVisible()
  await page.getByRole('checkbox', { name: 'Use the connected identity to create this draft' }).check()
  await page.getByRole('button', { name: 'Create label draft' }).click()
  await expect(page.getByRole('region', { name: 'Label draft details' })).toContainText(draft.labelVersionId)
  expect(creates).toBe(1)
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
  await expect(page.getByText('Version-bound label inputs')).toBeVisible()

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
      declarations: [],
    })
    await route.fulfill({ status: 201, json: draft })
  })
  await page.goto('/labels')

  const create = page.getByRole('button', { name: 'Create label draft' })
  await expect(create).toBeDisabled()
  await page
    .getByRole('checkbox', { name: 'Use the connected identity to create this draft' })
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
    .getByRole('checkbox', { name: 'Use the connected identity to create this draft' })
    .check()
  await page.getByRole('button', { name: 'Create label draft' }).click()

  await expect(page.getByRole('alert')).toContainText('AUTHORIZATION_DENIED')
  await expect(page.getByRole('region', { name: 'Label draft details' })).toHaveCount(0)
})

for (const response of ['malformed body', 'invalid JSON']) {
  test(`keeps a successful draft creation with ${response} uncertain`, async ({ page }) => {
    let writes = 0
    await page.route('**/api/v1/allergens?*', (route) => route.fulfill({ json: allergens }))
    await page.route('**/api/labels/drafts', (route) => {
      writes += 1
      return response === 'malformed body'
        ? route.fulfill({ status: 201, json: { labelVersionId: 'incomplete-draft' } })
        : route.fulfill({ status: 201, contentType: 'text/html', body: '<html>Response lost</html>' })
    })
    await page.goto('/labels')
    await page.getByRole('checkbox', { name: 'Use the connected identity to create this draft' }).check()
    await page.getByRole('button', { name: 'Create label draft' }).click()

    await expect(page.getByRole('alert')).toContainText('INVALID_RESPONSE')
    await expect(page.getByText('The write outcome may be unknown.')).toBeVisible()
    await expect(page.getByRole('button', { name: 'Create label draft' })).toBeDisabled()
    await expect(page.getByRole('region', { name: 'Label draft details' })).toHaveCount(0)
    expect(writes).toBe(1)
  })
}

test('reading an existing draft does not confirm uncertain draft creation', async ({ page }) => {
  let writes = 0
  await page.route('**/api/v1/allergens?*', (route) => route.fulfill({ json: allergens }))
  await page.route('**/api/labels/drafts', (route) => {
    writes += 1
    return route.abort('failed')
  })
  await page.route(`**/api/labels/${draft.labelVersionId}`, (route) => route.fulfill({ json: draft }))
  await page.goto('/labels')
  await page.getByRole('checkbox', { name: 'Use the connected identity to create this draft' }).check()
  await page.getByRole('button', { name: 'Create label draft' }).click()
  await expect(page.getByText('The write outcome may be unknown.')).toBeVisible()

  await page.getByLabel('Existing label version ID').fill(draft.labelVersionId)
  await page.getByRole('button', { name: 'Load label draft' }).click()
  await expect(page.getByRole('region', { name: 'Label draft details' })).toContainText(draft.labelVersionId)
  await expect(page.getByText('The earlier draft creation is still unconfirmed.')).toBeVisible()
  await expect(page.getByRole('button', { name: 'Create label draft' })).toBeDisabled()
  expect(writes).toBe(1)
})

test('changing products does not unlock uncertain draft creation', async ({ page }) => {
  await page.route('**/api/v1/allergens?*', (route) => route.fulfill({ json: allergens }))
  await page.route('**/api/labels/drafts', (route) => route.abort('failed'))
  await page.goto('/labels')
  await page.getByRole('checkbox', { name: 'Use the connected identity to create this draft' }).check()
  await page.getByRole('button', { name: 'Create label draft' }).click()
  await expect(page.getByText('The write outcome may be unknown.')).toBeVisible()

  await page.getByLabel('Product', { exact: true }).selectOption({ index: 1 })
  await expect(page.getByText('The earlier draft creation is still unconfirmed.')).toBeVisible()
  await expect(page.getByRole('button', { name: 'Create label draft' })).toBeDisabled()
  await page.getByLabel('Product', { exact: true }).selectOption(draft.productId)
  await expect(page.getByRole('button', { name: 'Create label draft' })).toBeDisabled()
})

test('rejects a different label version returned for an exact draft read', async ({ page }) => {
  await page.route('**/api/v1/allergens?*', (route) => route.fulfill({ json: allergens }))
  await page.route('**/api/labels/requested-label', (route) => route.fulfill({ json: draft }))
  await page.route(`**/api/labels/${draft.labelVersionId}`, (route) => route.fulfill({ json: draft }))
  const boundReads: string[] = []
  page.on('request', (request) => {
    if (/derived-allergens|declarations|validation-runs/.test(request.url())) boundReads.push(request.url())
  })
  await page.goto('/labels')
  await page.getByLabel('Existing label version ID').fill('requested-label')
  await page.getByRole('button', { name: 'Load label draft' }).click()

  await expect(page.getByRole('alert')).toContainText('LABEL_VERSION_MISMATCH')
  await expect(page.getByRole('region', { name: 'Label draft details' })).toHaveCount(0)
  expect(boundReads).toEqual([])

  await page.getByLabel('Existing label version ID').fill(draft.labelVersionId)
  await page.getByRole('button', { name: 'Load label draft' }).click()
  await expect(page.getByRole('region', { name: 'Label draft details' })).toContainText(draft.labelVersionId)
  await expect(page.getByRole('alert')).toHaveCount(0)
})
