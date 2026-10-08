import { test, expect } from './catalog-fixture'

// Read-model and affordance fixtures only; live S3 tests prove actual identity
// permissions, self-review denial and publication in separate actor contexts.
const task = { reviewTaskId: 'task_read_ui', productId: 'prod_usda_1106285',
  currentLabelVersionId: 'label_old', draftLabelVersionId: 'label_read_ui', targetLabelVersionId: 'label_read_ui',
  status: 'OPEN', decision: null, resolvedAt: null }
const label = { labelVersionId: task.draftLabelVersionId, productId: task.productId,
  formulaVersionId: 'formula_1106285_v1', ruleSetVersionId: 'ruleset_us_falcpa_demo_v1', jurisdictionCode: 'US',
  versionNumber: 2, rawIngredientText: 'Wheat and soy', lifecycleStatus: 'DRAFT', isCurrentPublished: 'N',
  createdByUserId: 'user_admin', createdAt: '2026-10-08T00:00:00', dataProvenanceId: 'prov_project_seed' }

test('reads a bounded actual-shaped task page and exact detail without presenting the page as a total', async ({ page }) => {
  await page.route('**/api/review-tasks?*', async route => {
    const query = new URL(route.request().url()).searchParams
    expect(query.get('limit')).toBe('20'); expect(query.get('offset')).toBe('0'); expect(query.get('status')).toBe('OPEN')
    await route.fulfill({ json: [task] })
  })
  await page.route('**/api/review-tasks/' + task.reviewTaskId, route => route.fulfill({ json: task }))
  await page.goto('/reviews')
  const list = page.getByRole('region', { name: 'Review task list' })
  await expect(list.locator('article')).toHaveCount(1)
  await expect(list).toContainText('1 rows on this page')
  await list.getByRole('button', { name: 'View task details' }).click()
  const detail = page.getByRole('region', { name: 'Review task details' })
  await expect(detail).toContainText(task.reviewTaskId)
  await expect(detail).toContainText('label_read_ui')
  await expect(detail.getByRole('link', { name: 'Open bound label version' })).toHaveAttribute('href',
    '/labels?productId=prod_usda_1106285&reviewTaskId=task_read_ui&labelVersionId=label_read_ui')
  await expect(list.getByRole('button', { name: 'Next tasks' })).toBeDisabled()
})

test('rejects duplicate or incorrectly filtered task pages and clears previous detail on an exact-ID mismatch', async ({ page }) => {
  await page.route('**/api/review-tasks?*', route => route.fulfill({ json: [task, task] }))
  await page.route('**/api/review-tasks/wanted-task', route => route.fulfill({ json: { ...task, reviewTaskId: 'other-task' } }))
  await page.goto('/reviews')
  await expect(page.getByRole('region', { name: 'Review task list' }).getByRole('alert')).toContainText('duplicate tasks')
  await expect(page.getByRole('region', { name: 'Review task list' }).locator('article')).toHaveCount(0)
  await page.getByLabel('Existing review task ID').fill('wanted-task')
  await page.getByRole('button', { name: 'Load review task', exact: true }).click()
  const detail = page.getByRole('region', { name: 'Review task details' })
  await expect(detail.getByRole('alert')).toContainText('invalid review task')
  await expect(detail.getByRole('link')).toHaveCount(0)
})

test('keeps create and validation disabled when the actual connected profile read fails', async ({ page }) => {
  let writes = 0
  await page.route('**/api/identity/current', route => route.fulfill({ status: 503, json: { code: 'UNAVAILABLE', message: 'Identity service unavailable.' } }))
  await page.route('**/api/v1/allergens?*', route => route.fulfill({ json: [] }))
  await page.route('**/api/labels/' + label.labelVersionId, route => route.fulfill({ json: label }))
  await page.route('**/api/labels/*/declarations', route => route.fulfill({ json: { ...label, declarations: [] } }))
  await page.route('**/api/v1/label-versions/*/derived-allergens', route => route.fulfill({ json: { ...label, facts: [], unresolvedComponents: [] } }))
  await page.route('**/api/labels/drafts', route => { writes++; return route.fulfill({ json: label }) })
  await page.goto('/labels?productId=' + label.productId + '&labelVersionId=' + label.labelVersionId)
  await expect(page.getByRole('region', { name: 'Connected identity' })).toContainText('Identity service unavailable')
  await expect(page.getByRole('checkbox', { name: 'Use the connected identity to create this draft' })).toBeDisabled()
  await expect(page.getByRole('button', { name: 'Create label draft' })).toBeDisabled()
  await expect(page.getByRole('checkbox', { name: 'Use the connected identity to validate this exact version' })).toBeDisabled()
  await expect(page.getByRole('button', { name: 'Run validation' })).toBeDisabled()
  await expect(page.getByRole('button', { name: 'Submit for review' })).toBeDisabled()
  expect(writes).toBe(0)
})

test('shows actual read-only permissions and disables writes despite explicit connected consent', async ({ page }) => {
  await page.route('**/api/identity/current', route => route.fulfill({ json: {
    userId: 'user_auditor', username: 'audit.viewer', displayName: 'Demo Auditor', roles: ['AUDITOR'], permissions: ['AUDIT.READ'],
  } }))
  await page.route('**/api/v1/allergens?*', route => route.fulfill({ json: [] }))
  await page.route('**/api/labels/' + label.labelVersionId, route => route.fulfill({ json: label }))
  await page.route('**/api/labels/*/declarations', route => route.fulfill({ json: { ...label, declarations: [] } }))
  await page.route('**/api/v1/label-versions/*/derived-allergens', route => route.fulfill({ json: { ...label, facts: [], unresolvedComponents: [] } }))
  await page.goto('/labels?productId=' + label.productId + '&labelVersionId=' + label.labelVersionId)
  await expect(page.getByRole('region', { name: 'Connected identity' })).toContainText('Demo Auditor')
  await page.getByRole('checkbox', { name: 'Use the connected identity for review and publication' }).check()
  for (const name of ['Create label draft', 'Run validation', 'Submit for review', 'Approve label', 'Publish label']) {
    await expect(page.getByRole('button', { name, exact: true })).toBeDisabled()
  }
})
