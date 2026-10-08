import { test, expect } from './catalog-fixture'

// HTTP fixture checks only. These do not claim live S3 integration.
test('unavailable impact reads never fabricate findings or send write commands', async ({ page }) => {
  const requests: string[] = []
  await page.route('**/api/v1/change-requests?*', async (route) => {
    requests.push(`${route.request().method()} ${new URL(route.request().url()).pathname}`)
    await route.fulfill({ status: 503, json: { code: 'UNAVAILABLE', message: 'Unavailable' } })
  })
  await page.goto('/impact')
  for (let visit = 0; visit < 2; visit++) {
    await expect(page.getByRole('heading', { name: 'Change impact', exact: true })).toBeVisible()
    await expect(page.getByRole('alert')).toContainText('Unavailable')
    await expect(page.getByRole('button', { name: 'Run impact analysis' })).toBeDisabled()
    const results = page.getByRole('region', { name: 'Analysis results' })
    await expect(results).toContainText('No analysis loaded')
    await expect(results).toContainText('This empty state does not mean no products are affected')
    await expect(results.getByRole('row')).toHaveCount(0)
    await expect(results).not.toContainText(/NO_ACTION|REVIEW_REQUIRED|PUBLISHED/)
    if (visit === 0) await page.reload()
  }
  // Development StrictMode may start and abort the initial read twice.
  expect(requests.length).toBeGreaterThanOrEqual(2)
  expect(requests.every(request => request === 'GET /api/v1/change-requests')).toBeTruthy()
})

test('impact preparation link opens the existing materials workflow by keyboard', async ({ page }) => {
  await page.route('**/api/v1/change-requests?*', route => route.fulfill({ json: [] }))
  await page.goto('/impact')
  const browse = page.getByRole('link', { name: 'Browse materials & specs' })
  await browse.focus()
  await expect(browse).toBeFocused()
  await page.keyboard.press('Enter')
  await expect(page).toHaveURL(/\/materials$/)
  await expect(page.getByRole('heading', { name: 'Materials & specs', exact: true })).toBeVisible()
  await expect(page.getByText('Connected to the M1 read-only API')).toBeVisible()
})

test('impact foundation fits desktop and mobile navigation', async ({ page }) => {
  await page.route('**/api/v1/change-requests?*', route => route.fulfill({ json: [] }))
  for (const width of [1440, 390]) {
    await page.setViewportSize({ width, height: 900 })
    await page.goto('/reviews')
    if (width === 390) await page.getByRole('button', { name: 'Open navigation' }).click()
    await page.getByRole('link', { name: 'Change impact', exact: true }).click()
    await expect(page.getByRole('heading', { name: 'Change impact', exact: true })).toBeVisible()
    await expect(page.getByRole('button', { name: 'Dismiss navigation overlay' })).toHaveCount(0)
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBeTruthy()
    await page.screenshot({ path: `test-results/impact-${width}.png`, fullPage: true })
  }
})
