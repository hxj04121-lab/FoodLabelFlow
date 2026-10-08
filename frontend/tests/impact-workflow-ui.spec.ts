import { test, expect } from './catalog-fixture'

// UI-only HTTP fixtures. The S3 live suite uses the actual seeded backend.
const change = { changeRequestId: 'change_ui', changeType: 'INGREDIENT_SPEC', supplierMaterialId: 'mat_chocolate_base',
  previousSpecificationVersionId: 'spec_chocolate_v1', targetSpecificationVersionId: 'spec_chocolate_v2',
  description: 'Recorded soy change', status: 'SUBMITTED', createdAt: '2026-10-08T00:00:00Z' }
const finding = { impactFindingId: 'finding_ui', productId: 'prod_usda_1106285', outcome: 'REVIEW_REQUIRED',
  currentFormulaVersionId: 'formula_old', proposedFormulaVersionId: 'formula_new', currentLabelVersionId: 'label_old',
  missingAllergenCodes: ['SOY'], explanation: 'Soy must be declared.', reviewTask: { reviewTaskId: 'task_ui',
    impactFindingId: 'finding_ui', productId: 'prod_usda_1106285', currentFormulaVersionId: 'formula_old',
    currentLabelVersionId: 'label_old', draftLabelVersionId: null } }
const run = { impactAnalysisId: 'analysis_ui', changeRequestId: change.changeRequestId,
  ruleSetVersionId: 'ruleset_us_falcpa_demo_v1', status: 'COMPLETED', completedAt: '2026-10-08T00:01:00Z',
  relevantProductCount: 1, noActionCount: 0, reviewRequiredCount: 1, findings: [finding] }

test.beforeEach(async ({ page }) => {
  await page.setExtraHTTPHeaders({ 'X-Auth-Provider': 'DEV_EXTERNAL', 'X-External-Subject': 'dev-external-change-manager' })
  await page.route('**/api/identity/current', route => route.fulfill({ json: {
    userId: 'user_change_manager', username: 'change.manager', displayName: 'Demo Change Manager',
    roles: ['CHANGE_MANAGER'], permissions: ['CHANGE_REQUEST.CREATE', 'IMPACT.RUN'],
  } }))
  await page.route('**/api/v1/change-requests?*', route => route.fulfill({ json: [change] }))
})

test('renders the actual returned findings and exact task handoff, including an already bound draft', async ({ page }) => {
  await page.route('**/impact-analyses', route => route.fulfill({ status: 201, json: run }))
  await page.route('**/api/v1/impact-analyses/*', route => route.fulfill({ json: {
    ...run, findings: [{ ...finding, reviewTask: { ...finding.reviewTask, draftLabelVersionId: 'label_bound' } }],
  } }))
  await page.goto('/impact')
  await expect(page.getByLabel('Change request', { exact: true })).toHaveValue(change.changeRequestId)
  await page.getByRole('checkbox', { name: 'Use the connected identity for impact writes' }).check()
  await page.getByRole('button', { name: 'Run impact analysis', exact: true }).click()
  const results = page.getByRole('region', { name: 'Analysis results' })
  await expect(results.locator('article')).toHaveCount(1)
  const first = results.getByRole('link', { name: 'Prepare replacement label' })
  await expect(first).toHaveAttribute('href', '/labels?productId=prod_usda_1106285&reviewTaskId=task_ui')
  await page.getByRole('button', { name: 'Load impact analysis' }).click()
  await expect(results.getByRole('link', { name: 'Open bound label draft' })).toHaveAttribute('href',
    '/labels?productId=prod_usda_1106285&reviewTaskId=task_ui&labelVersionId=label_bound')
})

test('keeps the captured change and rule set after a malformed analysis response and permits only the same replay', async ({ page }) => {
  const requests: object[] = []
  await page.route('**/impact-analyses', async route => {
    requests.push({ path: new URL(route.request().url()).pathname, body: route.request().postDataJSON() })
    await route.fulfill({ json: { ...run, relevantProductCount: 40 } })
  })
  await page.goto('/impact')
  await expect(page.getByLabel('Change request', { exact: true })).toHaveValue(change.changeRequestId)
  await page.getByRole('checkbox', { name: 'Use the connected identity for impact writes' }).check()
  await page.getByRole('button', { name: 'Run impact analysis', exact: true }).click()
  await expect(page.getByRole('alert')).toContainText('INVALID_RESPONSE')
  await expect(page.getByLabel('Change request', { exact: true })).toBeDisabled()
  await expect(page.getByLabel('Impact rule set ID')).toBeDisabled()
  await page.getByText('Create a specification change request', { exact: true }).click()
  await expect(page.getByRole('button', { name: 'Create change request', exact: true })).toBeDisabled()
  await page.getByRole('button', { name: 'Retry the same impact analysis' }).click()
  expect(requests).toEqual([
    { path: '/api/v1/change-requests/change_ui/impact-analyses', body: { ruleSetVersionId: run.ruleSetVersionId } },
    { path: '/api/v1/change-requests/change_ui/impact-analyses', body: { ruleSetVersionId: run.ruleSetVersionId } },
  ])
  await expect(page.getByRole('region', { name: 'Analysis results' }).locator('article')).toHaveCount(0)
})

test('refreshes version choices from the live catalog instead of retaining earlier choices', async ({ page }) => {
  let includeFresh = false
  await page.route('**/api/catalog/specifications?*', route => {
    return route.fulfill({ json: [{ specification_version_id: 'spec_chocolate_v1', supplier_material_id: 'mat_chocolate_base' },
      ...(includeFresh ? [{ specification_version_id: 'spec_freshly_released', supplier_material_id: 'mat_chocolate_base' }] : [])] })
  })
  await page.goto('/impact')
  await page.getByText('Create a specification change request', { exact: true }).click()
  await page.getByLabel('Supplier material', { exact: true }).selectOption('mat_chocolate_base')
  await expect(page.getByLabel('Target specification', { exact: true }).locator('option[value="spec_freshly_released"]')).toHaveCount(0)
  includeFresh = true
  await page.getByRole('button', { name: 'Refresh specification versions' }).click()
  await expect(page.getByLabel('Target specification', { exact: true }).locator('option[value="spec_freshly_released"]')).toHaveCount(1)
})
