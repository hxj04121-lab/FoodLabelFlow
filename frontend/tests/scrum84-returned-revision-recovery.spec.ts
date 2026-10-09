import { expect, test } from './catalog-fixture'
import type { Page } from '@playwright/test'

// Explicit HTTP fixtures in a real browser; these are not live MySQL evidence.
const taskId = 'task_revision_recovery'
const v1 = {
  labelVersionId: 'label_recovery_v1', productId: 'prod_usda_1106285',
  formulaVersionId: 'formula_1106285_v1', ruleSetVersionId: 'ruleset_us_falcpa_demo_v1',
  jurisdictionCode: 'US', versionNumber: 1, rawIngredientText: 'Soy lecithin',
  lifecycleStatus: 'DRAFT', isCurrentPublished: 'N', createdByUserId: 'user_label_officer',
  createdAt: '2026-10-08T08:00:00', dataProvenanceId: 'prov_project_seed',
}
const v2 = { ...v1, labelVersionId: 'label_recovery_v2', versionNumber: 2 }
const submittedText = 'Contains Soy (submitted correction)'

async function recoveryFixture(page: Page, response: 'invalid-json' | 'invalid-draft' | 'timeout' | 'conflict', committedInitially = true, matching = true) {
  const state = { committed: committedInitially, writes: 0, validationWrites: 0, taskReads: [] as string[] }
  const declaration = (text: string) => ({ allergenId: 'all_soy', declarationType: 'CONTAINS', declarationSource: 'USER_ENTERED', displayText: text })
  await page.route('**/api/v1/allergens?*', route => route.fulfill({ json: [
    { allergenId: 'all_soy', allergenCode: 'SOY', displayName: 'Soy', jurisdictionCode: 'US' },
  ] }))
  // The first page read must show returned V1, before the attempted command.
  await page.route('**/api/review-tasks/*', route => {
    const id = new URL(route.request().url()).pathname.split('/').pop()!
    state.taskReads.push(id)
    const revised = state.writes > 0 && state.committed
    return route.fulfill({ json: {
      reviewTaskId: id, productId: v1.productId, currentLabelVersionId: v1.labelVersionId,
      draftLabelVersionId: revised ? v2.labelVersionId : v1.labelVersionId,
      targetLabelVersionId: revised ? v2.labelVersionId : v1.labelVersionId,
      status: 'OPEN', decision: revised ? null : 'REQUEST_CHANGES', resolvedAt: null,
    } })
  })
  await page.route('**/api/labels/label_recovery_*', route => route.fulfill({
    json: route.request().url().endsWith(v2.labelVersionId) ? v2 : v1,
  }))
  await page.route('**/api/labels/*/declarations', route => {
    const revised = route.request().url().includes(v2.labelVersionId)
    const label = revised ? v2 : v1
    return route.fulfill({ json: {
      labelVersionId: label.labelVersionId, formulaVersionId: label.formulaVersionId,
      ruleSetVersionId: label.ruleSetVersionId, jurisdictionCode: label.jurisdictionCode,
      declarations: [declaration(revised ? (matching ? submittedText : 'Contains Soy (different stored correction)') : 'Contains Soy')],
    } })
  })
  await page.route('**/api/v1/label-versions/*/derived-allergens', route => {
    const label = route.request().url().includes(v2.labelVersionId) ? v2 : v1
    return route.fulfill({ json: { ...label, facts: [], unresolvedComponents: [] } })
  })
  await page.route('**/api/v1/validation-runs/validation_old_pass', route => route.fulfill({ json: {
    validationRunId: 'validation_old_pass', labelVersionId: v1.labelVersionId,
    ruleSetVersionId: v1.ruleSetVersionId, status: 'PASSED', ranAt: '2026-10-08T08:00:00', results: [],
  } }))
  await page.route('**/api/v1/label-versions/*/validation-runs', route => {
    state.validationWrites++
    return route.fulfill({ status: 500, json: { code: 'UNEXPECTED_VALIDATION', message: 'Validation requires explicit consent' } })
  })
  await page.route(`**/api/review-tasks/${taskId}/draft-revisions`, async route => {
    state.writes++
    expect(route.request().postDataJSON()).toEqual({
      expectedLabelVersionId: v1.labelVersionId,
      declarations: [{ allergenId: 'all_soy', declarationType: 'CONTAINS', displayText: submittedText }],
    })
    if (response === 'timeout') return route.abort('timedout')
    if (response === 'conflict') return route.fulfill({ status: 409, json: { code: 'LABEL_VERSION_CONFLICT', message: 'Task target changed' } })
    if (response === 'invalid-json') return route.fulfill({ status: 201, contentType: 'application/json', body: '{' })
    return route.fulfill({ status: 201, json: { labelVersionId: v2.labelVersionId } })
  })
  await page.goto(`/labels?productId=${v1.productId}&reviewTaskId=${taskId}`)
  const editor = page.getByRole('region', { name: 'Structured label declarations' })
  await expect(editor.getByRole('button', { name: 'Save changes as new version' })).toBeEnabled()
  await editor.getByRole('textbox', { name: 'Soy declaration text' }).fill(submittedText)
  return { state, editor }
}

for (const response of ['invalid-json', 'invalid-draft', 'timeout'] as const) {
  test(`${response} after committed revision adopts only matching stored state and clears old PASS`, async ({ page }) => {
    const { state, editor } = await recoveryFixture(page, response)
    await page.getByLabel('Existing validation run ID').fill('validation_old_pass')
    await page.getByRole('button', { name: 'Load validation run', exact: true }).click()
    await expect(page.getByRole('region', { name: 'Validation run results' })).toContainText('PASSED')
    await editor.getByRole('button', { name: 'Save changes as new version' }).click()
    await expect(page.getByRole('region', { name: 'Revision recovery' })).toContainText(`Adopted the current stored version ${v2.labelVersionId}`)
    await expect(page.getByRole('region', { name: 'Revision recovery' })).toContainText('does not independently confirm the original command outcome')
    await expect(page.getByRole('region', { name: 'Label draft details' })).toContainText(v2.labelVersionId)
    await expect(page.getByRole('region', { name: 'Validation run results' })).toHaveCount(0)
    const workflow = page.getByRole('region', { name: 'Label review and publication' })
    await workflow.getByRole('checkbox', { name: 'Use the connected identity for review and publication' }).check()
    await expect(workflow.getByRole('button', { name: 'Submit for review', exact: true })).toBeDisabled()
    expect(state.writes).toBe(1)
    expect(state.validationWrites).toBe(0)
    expect(state.taskReads.every(id => id === taskId)).toBe(true)
  })
}

test('timeout without visible commit retains the original snapshot and blocks another POST until matching state is read', async ({ page }) => {
  const { state, editor } = await recoveryFixture(page, 'timeout', false)
  await editor.getByRole('button', { name: 'Save changes as new version' }).click()
  const recovery = page.getByRole('region', { name: 'Revision recovery' })
  await expect(recovery).toContainText('still unconfirmed')
  await expect(recovery).toContainText(submittedText)
  await expect(editor.getByRole('button', { name: 'Save changes as new version' })).toHaveCount(0)
  await page.getByRole('button', { name: 'Load review task draft', exact: true }).click()
  await expect(recovery).toContainText('still unconfirmed')
  expect(state.writes).toBe(1)
  state.committed = true
  await page.getByRole('button', { name: 'Load review task draft', exact: true }).click()
  await expect(recovery).toContainText(`Adopted the current stored version ${v2.labelVersionId}`)
  expect(state.writes).toBe(1)
  expect(state.validationWrites).toBe(0)
})

test('409 reads the original task despite task ID edits and preserves conflicting submitted declarations', async ({ page }) => {
  const { state, editor } = await recoveryFixture(page, 'conflict', true, false)
  await editor.getByRole('button', { name: 'Save changes as new version' }).click()
  const recovery = page.getByRole('region', { name: 'Revision recovery' })
  await expect(recovery).toContainText('still unconfirmed')
  await expect(recovery).toContainText(submittedText)
  await expect(editor).toContainText('Contains Soy (different stored correction)')
  await page.getByLabel('Review task ID', { exact: true }).fill('unrelated_task')
  await page.getByRole('button', { name: 'Load review task draft', exact: true }).click()
  await expect(page.getByLabel('Review task ID', { exact: true })).toHaveValue(taskId)
  await expect(recovery).toContainText(submittedText)
  await expect(editor.getByRole('button', { name: 'Save changes as new version' })).toHaveCount(0)
  expect(state.taskReads.length).toBeGreaterThan(1)
  expect(state.taskReads.every(id => id === taskId)).toBe(true)
  expect(state.writes).toBe(1)
  expect(state.validationWrites).toBe(0)
})
