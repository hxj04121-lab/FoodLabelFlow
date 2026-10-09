import { expect, test } from './catalog-fixture'

// UI contract test with explicit HTTP fixtures; NOT a live MySQL E2E.
const v1 = {
  labelVersionId: 'label_returned_v1', productId: 'prod_usda_1106285',
  formulaVersionId: 'formula_1106285_v1', ruleSetVersionId: 'ruleset_us_falcpa_demo_v1',
  jurisdictionCode: 'US', versionNumber: 1, rawIngredientText: 'Soy lecithin',
  lifecycleStatus: 'DRAFT', isCurrentPublished: 'N', createdByUserId: 'user_label_officer',
  createdAt: '2026-10-08T08:00:00', dataProvenanceId: 'prov_project_seed',
}
const v2 = { ...v1, labelVersionId: 'label_returned_v2', versionNumber: 2, createdAt: '2026-10-08T09:00:00' }
const task = (revision: boolean) => ({
  reviewTaskId: 'task_returned', productId: v1.productId,
  currentLabelVersionId: v1.labelVersionId,
  draftLabelVersionId: revision ? v2.labelVersionId : v1.labelVersionId,
  targetLabelVersionId: revision ? v2.labelVersionId : v1.labelVersionId,
  status: 'OPEN', decision: revision ? null : 'REQUEST_CHANGES', resolvedAt: null,
})

test('Maker saves a returned task as V2 without mutating V1 or inheriting validation', async ({ page }) => {
  let revised = false
  let revisionWrites = 0
  let validationWrites = 0
  const oldDeclarations = [{ allergenId: 'all_soy', declarationType: 'CONTAINS', declarationSource: 'USER_ENTERED', displayText: 'Contains Soy' }]
  await page.route('**/api/v1/allergens?*', route => route.fulfill({ json: [
    { allergenId: 'all_soy', allergenCode: 'SOY', displayName: 'Soy', jurisdictionCode: 'US' },
  ] }))
  await page.route('**/api/review-tasks/task_returned', route => route.fulfill({ json: task(revised) }))
  await page.route('**/api/labels/label_returned_v1', route => route.fulfill({ json: v1 }))
  await page.route('**/api/labels/label_returned_v2', route => route.fulfill({ json: v2 }))
  await page.route('**/api/labels/*/declarations', route => {
    const id = route.request().url().includes('label_returned_v2') ? v2.labelVersionId : v1.labelVersionId
    return route.fulfill({ json: {
      labelVersionId: id, formulaVersionId: v1.formulaVersionId,
      ruleSetVersionId: v1.ruleSetVersionId, jurisdictionCode: 'US',
      declarations: id === v1.labelVersionId ? oldDeclarations : [{ ...oldDeclarations[0], displayText: 'Contains Soy (revised)' }],
    } })
  })
  await page.route('**/api/v1/label-versions/*/derived-allergens', route => {
    const id = route.request().url().includes('label_returned_v2') ? v2.labelVersionId : v1.labelVersionId
    return route.fulfill({ json: { ...(id === v1.labelVersionId ? v1 : v2), facts: [], unresolvedComponents: [] } })
  })
  await page.route('**/api/review-tasks/task_returned/draft-revisions', async route => {
    revisionWrites++
    expect(route.request().headers()['x-external-subject']).toBe('dev-external-label-officer')
    expect(route.request().postDataJSON()).toEqual({
      expectedLabelVersionId: v1.labelVersionId,
      declarations: [{ allergenId: 'all_soy', declarationType: 'CONTAINS', displayText: 'Contains Soy (revised)' }],
    })
    revised = true
    await route.fulfill({ status: 201, json: v2 })
  })
  await page.route('**/api/labels/*/validations', route => {
    validationWrites++
    return route.fulfill({ status: 500, json: { code: 'UNEXPECTED_VALIDATION', message: 'Validation must be initiated explicitly' } })
  })

  await page.goto('/labels?productId=prod_usda_1106285&reviewTaskId=task_returned')
  const editor = page.getByRole('region', { name: 'Structured label declarations' })
  await expect(editor.getByRole('button', { name: 'Save changes as new version' })).toBeVisible()
  await editor.getByRole('textbox', { name: 'Soy declaration text' }).fill('Contains Soy (revised)')
  await editor.getByRole('button', { name: 'Save changes as new version' }).click()
  await expect(page.getByRole('region', { name: 'Label draft details' })).toContainText(v2.labelVersionId)
  await expect(page.getByText(/Run validation for this exact version before resubmitting/)).toBeVisible()
  await expect(editor.getByRole('button', { name: 'Save changes as new version' })).toHaveCount(0)
  expect(revisionWrites).toBe(1)
  expect(validationWrites).toBe(0)
})
