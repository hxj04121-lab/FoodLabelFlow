import { expect, test } from '@playwright/test'

test('validates a seeded PASS and a new blocking FAIL through the browser and persisted API', async ({
  page,
}) => {
  test.skip(
    process.env.LIVE_VALIDATION !== '1',
    'Explicit opt-in required for writes to local course validation data',
  )

  await page.goto('/labels')
  await expect(page.getByText('Connected to the M1 read-only API')).toBeVisible({
    timeout: 30_000,
  })

  // Canonical published label with matching SOY and WHEAT declarations.
  await page.getByLabel('Existing label version ID').fill('label_1106285_v1')
  await page.getByRole('button', { name: 'Load label draft' }).click()
  await expect(page.getByRole('region', { name: 'Label draft details' })).toContainText(
    'label_1106285_v1',
  )
  await page
    .getByRole('checkbox', {
      name: 'Enable the local demo label-officer identity to validate this exact version',
    })
    .check()
  await page.getByRole('button', { name: 'Run validation' }).click()

  const passResults = page.getByRole('region', { name: 'Validation run results' })
  await expect(passResults).toContainText('PASSED')
  await expect(passResults.getByText('FAIL', { exact: true })).toHaveCount(0)
  const persistedRunId = await page.getByLabel('Existing validation run ID').inputValue()
  expect(persistedRunId).not.toBe('')
  await passResults.scrollIntoViewIfNeeded()
  await page.screenshot({ path: 'test-results/validation-live-pass.png' })

  // Re-read the committed run rather than re-evaluating current inputs.
  await page.reload()
  await expect(page.getByText('Connected to the M1 read-only API')).toBeVisible({
    timeout: 30_000,
  })
  await page.getByLabel('Existing label version ID').fill('label_1106285_v1')
  await page.getByRole('button', { name: 'Load label draft' }).click()
  await page.getByLabel('Existing validation run ID').fill(persistedRunId)
  await page.getByRole('button', { name: 'Load validation run' }).click()
  await expect(page.getByRole('region', { name: 'Validation run results' })).toContainText(
    'PASSED',
  )

  // A new draft has no structured declarations, so the real evaluator must block it.
  await page.getByLabel('Product').selectOption('prod_usda_1106285')
  await page
    .getByRole('checkbox', {
      name: 'Enable the local demo label-officer identity for this form',
    })
    .check()
  await page.getByRole('button', { name: 'Create label draft' }).click()
  await expect(page.getByText(/Label draft V\d+ created and read from the server/)).toBeVisible()
  await page
    .getByRole('checkbox', {
      name: 'Enable the local demo label-officer identity to validate this exact version',
    })
    .check()
  await page.getByRole('button', { name: 'Run validation' }).click()

  const failResults = page.getByRole('region', { name: 'Validation run results' })
  await expect(failResults).toContainText('FAILED')
  await expect(failResults).toContainText('ALLERGEN_DECLARATION_MISSING')
  await expect(failResults).toContainText('Blocking')
  await failResults.scrollIntoViewIfNeeded()
  await page.screenshot({ path: 'test-results/validation-live-fail.png' })
})
