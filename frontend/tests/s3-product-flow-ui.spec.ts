import { expect, test } from './catalog-fixture'
import type { Page } from '@playwright/test'

// HTTP fixtures exercise UI state and request construction only. The separate live
// S3 suite proves database validation, maker-checker and immutable publication.
const initial = {
  labelVersionId: 'label_s3_ui', productId: 'prod_usda_1106285',
  formulaVersionId: 'formula_1106285_v1', ruleSetVersionId: 'ruleset_us_falcpa_demo_v1',
  jurisdictionCode: 'US', versionNumber: 2, rawIngredientText: 'Wheat flour, soy lecithin',
  lifecycleStatus: 'DRAFT', isCurrentPublished: 'N', createdByUserId: 'user_label_officer',
  createdAt: '2026-10-08T00:00:00', dataProvenanceId: 'prov_project_seed',
}
const allergens = [
  { allergenId: 'all_soy', allergenCode: 'SOY', displayName: 'Soy', jurisdictionCode: 'US' },
  { allergenId: 'all_wheat', allergenCode: 'WHEAT', displayName: 'Wheat', jurisdictionCode: 'US' },
]
const passed = {
  validationRunId: 'validation_s3_ui', labelVersionId: initial.labelVersionId,
  ruleSetVersionId: initial.ruleSetVersionId, status: 'PASSED', ranAt: '2026-10-08T00:01:00Z', results: [],
}
const task = {
  reviewTaskId: 'task_s3_ui', productId: initial.productId, currentLabelVersionId: 'label_old',
  draftLabelVersionId: initial.labelVersionId, targetLabelVersionId: initial.labelVersionId,
  status: 'IN_PROGRESS', decision: null, resolvedAt: null,
}
async function reads(page: Page, label = initial, actorUserId?: string) {
  await page.route('**/api/identity/current', route => route.fulfill({ json: {
    userId: actorUserId ?? (label.lifecycleStatus === 'DRAFT' ? 'user_label_officer' : 'ui_fixture_checker'),
    username: 'ui.fixture', displayName: 'UI workflow fixture', roles: ['UI_FIXTURE'],
    permissions: ['LABEL.CREATE', 'LABEL.VALIDATE', 'LABEL.SUBMIT_REVIEW', 'LABEL.APPROVE', 'LABEL.REQUEST_CHANGES', 'LABEL.REJECT', 'LABEL.PUBLISH'],
  } }))
  await page.route('**/api/v1/allergens?*', route => route.fulfill({ json: allergens }))
  await page.route('**/api/v1/label-versions/*/derived-allergens', route => route.fulfill({ json: { ...label, facts: [], unresolvedComponents: [] } }))
  await page.route('**/api/labels/*/declarations', route => route.fulfill({ json: { ...label, declarations: [] } }))
  await page.route('**/api/v1/validation-runs/*', route => route.fulfill({ json: passed }))
  await page.route('**/api/review-tasks/*', route => route.fulfill({ json: task }))
  await page.route('**/api/labels/' + label.labelVersionId, route => route.fulfill({ json: label }))
}
async function open(page: Page, label = initial, actorUserId?: string) {
  await reads(page, label, actorUserId)
  await page.goto('/labels?productId=' + label.productId + '&reviewTaskId=' + task.reviewTaskId + '&labelVersionId=' + label.labelVersionId)
  await expect(page.getByRole('region', { name: 'Label draft details' })).toContainText(label.labelVersionId)
}
async function pass(page: Page) {
  await page.getByLabel('Existing validation run ID').fill(passed.validationRunId)
  await page.getByRole('button', { name: 'Load validation run' }).click()
  await expect(page.getByRole('region', { name: 'Validation run results' })).toContainText('PASSED')
}
async function consent(page: Page) {
  await page.getByRole('checkbox', { name: 'Use the connected identity for review and publication' }).check()
}

test('records only explicit declarations with the first matching task and current server formula', async ({ page }) => {
  await reads(page)
  await page.route('**/api/labels/drafts', async route => {
    expect(route.request().postDataJSON()).toEqual({ productId: initial.productId, jurisdictionCode: 'US',
      declarations: [{ allergenId: 'all_soy', declarationType: 'CONTAINS', displayText: 'Contains Soy' },
        { allergenId: 'all_wheat', declarationType: 'CONTAINS', displayText: 'Contains Wheat' }], reviewTaskId: task.reviewTaskId })
    await route.fulfill({ status: 201, json: initial })
  })
  await page.goto('/labels?productId=' + initial.productId + '&reviewTaskId=' + task.reviewTaskId)
  await page.getByLabel('Declare Soy (SOY)').check()
  await page.getByLabel('Declare Wheat (WHEAT)').check()
  await page.getByRole('checkbox', { name: 'Use the connected identity to create this draft' }).check()
  await page.getByRole('button', { name: 'Create label draft' }).click()
  await expect(page.getByRole('region', { name: 'Label draft details' })).toContainText(initial.labelVersionId)
  await expect(page.getByText('Declarations are fixed at draft creation.', { exact: false })).toBeVisible()
})

test('requires exact PASSED validation and preserves the connected workflow identity', async ({ page }) => {
  await page.setExtraHTTPHeaders({ 'X-Auth-Provider': 'DEV_EXTERNAL', 'X-External-Subject': 'fixture-connected-maker' })
  await page.route('**/review-submissions', async route => {
    expect(route.request().headers()['x-external-subject']).toBe('fixture-connected-maker')
    expect(route.request().postDataJSON()).toEqual({})
    await route.fulfill({ json: { ...initial, lifecycleStatus: 'PENDING_REVIEW' } })
  })
  await open(page)
  await consent(page)
  await expect(page.getByRole('button', { name: 'Submit for review' })).toBeDisabled()
  await pass(page)
  await page.getByRole('button', { name: 'Submit for review' }).click()
  await expect(page.getByRole('region', { name: 'Label draft details' })).toContainText('PENDING_REVIEW')
  await expect(page.getByRole('button', { name: 'Approve label' })).toBeDisabled()
})

test('prevents a permitted maker from approving while connected consent is checked', async ({ page }) => {
  let approvalWrites = 0
  await page.route('**/review-decisions', route => {
    approvalWrites++
    return route.fulfill({ json: { ...initial, lifecycleStatus: 'APPROVED' } })
  })
  const pending = { ...initial, lifecycleStatus: 'PENDING_REVIEW', createdByUserId: 'ui_fixture_compound_maker' }
  await open(page, pending, pending.createdByUserId)
  await consent(page)
  const identity = page.getByRole('region', { name: 'Connected identity' })
  await expect(identity).toContainText(pending.createdByUserId)
  await expect(identity).toContainText('LABEL.CREATE')
  await expect(identity).toContainText('LABEL.APPROVE')
  await expect(page.getByRole('checkbox', { name: 'Use the connected identity for review and publication' })).toBeChecked()
  await expect(page.getByText('Your connected identity created this label. An independent reviewer must make the decision.')).toBeVisible()
  await expect(page.getByRole('button', { name: 'Approve label', exact: true })).toBeDisabled()
  expect(approvalWrites).toBe(0)
})
test('shows denied independent review without inventing success or switching identity', async ({ page }) => {
  await page.route('**/review-decisions', route => route.fulfill({ status: 403,
    json: { code: 'AUTHORIZATION_DENIED', message: 'Approval permission is required.' } }))
  await open(page, { ...initial, lifecycleStatus: 'PENDING_REVIEW' })
  await consent(page)
  await page.getByRole('button', { name: 'Approve label' }).click()
  await expect(page.getByRole('alert')).toContainText('AUTHORIZATION_DENIED')
  await expect(page.getByRole('region', { name: 'Label draft details' })).toContainText('PENDING_REVIEW')
  await expect(page.getByRole('button', { name: 'Publish label' })).toBeDisabled()
})

test('retains an uncertain workflow guard across label selection and unrelated reads', async ({ page }) => {
  let writes = 0
  await page.route('**/review-submissions', route => { writes++; return route.fulfill({ status: 200, json: { labelVersionId: 'malformed' } }) })
  await open(page)
  await pass(page)
  await consent(page)
  await page.getByRole('button', { name: 'Submit for review' }).click()
  await expect(page.getByRole('alert')).toContainText('INVALID_RESPONSE')
  await expect(page.getByRole('button', { name: 'Submit for review' })).toBeDisabled()
  await page.getByLabel('Product', { exact: true }).selectOption({ index: 1 })
  await expect(page.getByRole('status')).toContainText('Unconfirmed command')
  await page.getByRole('button', { name: 'Refresh workflow state' }).click()
  await expect(page.getByRole('status')).toContainText(initial.labelVersionId)
  expect(writes).toBe(1)
})

test('refuses publication when the actual task is bound to another label', async ({ page }) => {
  let publications = 0
  await open(page, { ...initial, lifecycleStatus: 'APPROVED' })
  await page.route('**/api/review-tasks/' + task.reviewTaskId, route => route.fulfill({ json: { ...task, targetLabelVersionId: 'another_label' } }))
  await page.route('**/publications', route => { publications++; return route.fulfill({ json: { ...initial, lifecycleStatus: 'PUBLISHED', isCurrentPublished: 'Y' } }) })
  await consent(page)
  await page.getByRole('button', { name: 'Publish label' }).click()
  await expect(page.getByRole('alert')).toContainText('REVIEW_TASK_TARGET_MISMATCH')
  expect(publications).toBe(0)
})

test('uses a bound-task read to recover exact declared draft creation without another POST', async ({ page }) => {
  await reads(page)
  let creates = 0
  await page.route('**/api/labels/drafts', route => { creates++; return route.fulfill({ status: 503, json: { code: 'UNAVAILABLE', message: 'Response lost.' } }) })
  await page.route('**/api/labels/*/declarations', route => route.fulfill({ json: { ...initial,
    declarations: [{ allergenId: 'all_soy', declarationType: 'CONTAINS', declarationSource: 'USER_ENTERED', displayText: 'Contains Soy' }] } }))
  await page.goto('/labels?productId=' + initial.productId + '&reviewTaskId=' + task.reviewTaskId)
  await page.getByLabel('Declare Soy (SOY)').check()
  await page.getByRole('checkbox', { name: 'Use the connected identity to create this draft' }).check()
  await page.getByRole('button', { name: 'Create label draft' }).click()
  await expect(page.getByRole('button', { name: 'Create label draft' })).toBeDisabled()
  await page.getByRole('button', { name: 'Load review task draft' }).click()
  await expect(page.getByRole('region', { name: 'Label draft details' })).toContainText(initial.labelVersionId)
  await expect(page.getByText('The earlier draft creation is still unconfirmed.', { exact: false })).toHaveCount(0)
  expect(creates).toBe(1)
})
