import { expect, test } from '@playwright/test'

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

test('shows a loading state and renders canonical allergens from the API', async ({ page }) => {
  let releaseResponse: (() => void) | undefined
  await page.route('**/api/v1/allergens?*', async (route) => {
    await new Promise<void>((resolve) => {
      releaseResponse = resolve
    })
    await route.fulfill({ json: allergens })
  })

  await page.goto('/labels')
  await expect(page.getByRole('status')).toContainText('Connecting to the label API')
  releaseResponse?.()

  await expect(page.getByRole('list', { name: 'Canonical allergens' })).toBeVisible()
  await expect(page.getByText('Milk', { exact: true })).toBeVisible()
  await expect(page.getByText('Soy', { exact: true })).toBeVisible()
  await expect(page.getByText('2 entries returned for US')).toBeVisible()
  await page.screenshot({ path: 'test-results/scrum17-labels-desktop.png', fullPage: true })
})

test('shows an honest empty state', async ({ page }) => {
  await page.route('**/api/v1/allergens?*', (route) => route.fulfill({ json: [] }))
  await page.goto('/labels')

  await expect(page.getByRole('heading', { name: 'No canonical allergens returned' })).toBeVisible()
  await expect(page.getByText('the US jurisdiction has no allergen entries')).toBeVisible()
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
  await expect(page.getByText('No preview or seed result has been shown in its place.')).toBeVisible()
  await expect(page.getByText('Milk', { exact: true })).toHaveCount(0)

  serviceAvailable = true
  await page.getByRole('button', { name: 'Retry connection' }).click()
  await expect(page.getByText('Milk', { exact: true })).toBeVisible()
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

  await expect(page.getByRole('heading', { name: 'Canonical allergens' })).toBeVisible()
  expect(
    await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth),
  ).toBeTruthy()
  await page.screenshot({ path: 'test-results/scrum17-labels-mobile.png', fullPage: true })
})
