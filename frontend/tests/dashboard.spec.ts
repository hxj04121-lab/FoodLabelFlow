import { test, expect } from './catalog-fixture'

test('overview, chart switching and CSV export', async ({ page }) => {
  const errors: string[] = []
  page.on('pageerror', (e) => errors.push(e.message))
  await page.goto('/')
  await expect(
    page.getByRole('heading', { name: 'Overview', exact: true }),
  ).toBeVisible()
  await expect(page.getByText('Connected to the M1 read-only API', { exact: false })).toBeVisible()
  await page.getByRole('button', { name: 'By group', exact: true }).click()
  await expect(page.locator('.bar-chart')).toHaveAttribute('aria-label', /Soy declared: 20/)
  await page.getByRole('button', { name: 'By material', exact: true }).click()
  await expect(page.getByRole('img', { name: /Chocolate: 40/ })).toBeVisible()
  const downloading = page.waitForEvent('download')
  await page.getByRole('button', { name: 'Export data', exact: true }).click()
  expect((await downloading).suggestedFilename()).toBe(
    'spectrace-seed-preview.csv',
  )
  expect(errors).toEqual([])
  await page.screenshot({ path: 'test-results/overview.png', fullPage: true })
})

test('product search, group filter, pagination and traceability tabs', async ({
  page,
}) => {
  await page.goto('/products')
  await page.getByRole('button', { name: 'Next page' }).click()
  await expect(page.getByText('Page 2 / 8', { exact: false })).toBeVisible()
  await page
    .getByRole('combobox', { name: 'Filter fixture group' })
    .selectOption('REVIEW_REQUIRED_BASELINE_NO_SOY')
  await expect(page.getByText('20 products', { exact: true })).toBeVisible()
  await page.getByRole('combobox').selectOption('all')
  await page.getByRole('textbox', { name: 'Search products' }).fill('1106285')
  await expect(page.locator('tbody tr')).toHaveCount(1)
  await page
    .getByRole('button', {
      name: 'View PLAIN BREAD CRUMBS, PLAIN',
      exact: true,
    })
    .click()
  await expect(page.getByRole('dialog')).toBeVisible()
  await expect(
    page.getByText('spec_chocolate_v1', { exact: true }),
  ).toBeVisible()
  await page.getByRole('tab', { name: 'Version history' }).click()
  await expect(page.getByRole('tabpanel')).toContainText('RELEASED')
  await page.getByRole('tab', { name: 'Data sources' }).click()
  await expect(
    page.getByText('not the actual supply chain of the brand', { exact: false }),
  ).toBeVisible()
  await page.keyboard.press('Escape')
  await expect(page.getByRole('dialog')).toHaveCount(0)
  await page
    .getByRole('textbox', { name: 'Search products' })
    .fill('no-such-product-xyz')
  await expect(
    page.getByRole('heading', { name: 'No matching products' }),
  ).toBeVisible()
})

test('all routes, material details and honest unavailable states', async ({
  page,
}) => {
  for (const [route, heading] of [
    ['/suppliers', 'Suppliers'],
    ['/materials', 'Materials & specs'],
    ['/formulas', 'Formula versions'],
    ['/labels', 'Labels'],
    ['/impact', 'Change impact'],
    ['/reviews', 'Review workspace'],
  ]) {
    await page.goto(route)
    await expect(
      page.getByRole('heading', { name: heading, exact: true }),
    ).toBeVisible()
  }
  await page.goto('/materials')
  await page.getByRole('textbox', { name: 'Search materials' }).fill('CHOC_BASE')
  await expect(page.locator('tbody tr')).toHaveCount(1)
  await page
    .getByRole('button', { name: 'View Chocolate Base', exact: true })
    .click()
  await expect(page.getByText('Cocoa', { exact: true })).toBeVisible()
  await page.getByRole('button', { name: 'Close dialog' }).click()
  await page.goto('/formulas')
  await expect(page.getByText('Local demo saving and publishing')).toBeVisible()
  await page.screenshot({ path: 'test-results/formulas.png', fullPage: true })
})

test('mobile navigation and no page-wide overflow', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await page.goto('/')
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBeTruthy()
  await page.getByRole('button', { name: 'Open navigation' }).click()
  await page.getByRole('link', { name: 'Products', exact: true }).click()
  await expect(
    page.getByRole('heading', { name: 'Products', exact: true }),
  ).toBeVisible()
  await expect(page.getByRole('button', { name: 'Dismiss navigation overlay' })).toHaveCount(
    0,
  )
  expect(
    await page.evaluate(
      () => document.documentElement.scrollWidth <= innerWidth,
    ),
  ).toBeTruthy()
  await page.goto('/')
  await page.screenshot({ path: 'test-results/mobile.png', fullPage: true })
})

test('health success, error and retry states', async ({ page }) => {
  // Explicit HTTP test fixtures, not live-backend acceptance evidence.
  await page.route('**/api/health', (route) =>
    route.fulfill({ json: { status: 'ok', database: 'ok' } }),
  )
  await page.goto('/health')
  await expect(page.getByText('Healthy')).toHaveCount(2)
  await page.unroute('**/api/health')
  await page.route('**/api/health', (route) =>
    route.fulfill({ status: 503, body: 'Unavailable' }),
  )
  await page.getByRole('button', { name: 'Check again' }).click()
  await expect(page.getByRole('alert')).toContainText('503')
})
