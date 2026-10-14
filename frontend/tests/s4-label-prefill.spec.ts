import { expect, test } from './catalog-fixture'

const productId = 'prod_usda_1106285'
const previousLabel = {
  labelVersionId: 'label_s4_previous', productId, formulaVersionId: 'formula_1106285_v1',
  ruleSetVersionId: 'ruleset_us_falcpa_demo_v1', jurisdictionCode: 'US', versionNumber: 2,
  rawIngredientText: 'Wheat flour, soy lecithin', lifecycleStatus: 'PUBLISHED', isCurrentPublished: 'Y',
  createdByUserId: 'user_label_officer', createdAt: '2026-10-01T08:00:00', dataProvenanceId: 'prov_project_seed',
}

test('prefills only user-entered declarations from the exact review-task source and keeps them editable', async ({ page }) => {
  const submitted: unknown[] = []
  await page.route('**/api/v1/allergens?*', route => route.fulfill({ json: [
    { allergenId: 'all_milk', allergenCode: 'MILK', displayName: 'Milk', jurisdictionCode: 'US' },
    { allergenId: 'all_soy', allergenCode: 'SOY', displayName: 'Soy', jurisdictionCode: 'US' },
  ] }))
  await page.route('**/api/labels/*/declarations', route => {
    const labelVersionId = new URL(route.request().url()).pathname.split('/').at(-2)!
    return route.fulfill({ json: { labelVersionId, formulaVersionId: previousLabel.formulaVersionId,
      ruleSetVersionId: previousLabel.ruleSetVersionId, jurisdictionCode: 'US', declarations: [] } })
  })
  await page.route('**/api/v1/label-versions/*/derived-allergens', route => {
    const labelVersionId = new URL(route.request().url()).pathname.split('/')[4]
    return route.fulfill({ json: { labelVersionId, formulaVersionId: previousLabel.formulaVersionId,
      ruleSetVersionId: previousLabel.ruleSetVersionId, jurisdictionCode: 'US', facts: [], unresolvedComponents: [] } })
  })
  await page.route('**/api/review-tasks/task_s4_prefill', route => route.fulfill({ json: {
    reviewTaskId: 'task_s4_prefill', productId, currentLabelVersionId: previousLabel.labelVersionId,
    draftLabelVersionId: null, targetLabelVersionId: null, status: 'OPEN', decision: null, resolvedAt: null,
  } }))
  await page.route(`**/api/labels/${previousLabel.labelVersionId}`, route => route.fulfill({ json: previousLabel }))
  await page.route(`**/api/labels/${previousLabel.labelVersionId}/declarations`, route => route.fulfill({ json: {
    labelVersionId: previousLabel.labelVersionId, formulaVersionId: previousLabel.formulaVersionId,
    ruleSetVersionId: previousLabel.ruleSetVersionId, jurisdictionCode: 'US', declarations: [
      { allergenId: 'all_soy', declarationType: 'CONTAINS', declarationSource: 'USER_ENTERED', displayText: 'Contains soy' },
      { allergenId: 'all_milk', declarationType: 'CONTAINS', declarationSource: 'FORMULA_DERIVED', displayText: 'Contains milk' },
    ],
  } }))
  await page.route('**/api/labels/drafts', async route => {
    submitted.push(route.request().postDataJSON())
    await route.fulfill({ status: 201, json: {
      ...previousLabel, labelVersionId: 'label_s4_replacement', versionNumber: 3,
      lifecycleStatus: 'DRAFT', isCurrentPublished: 'N', createdAt: '2026-10-14T08:00:00',
    } })
  })

  await page.goto(`/labels?productId=${productId}&reviewTaskId=task_s4_prefill`)
  const soy = page.getByRole('checkbox', { name: 'Declare Soy (SOY)' })
  const milk = page.getByRole('checkbox', { name: 'Declare Milk (MILK)' })
  await expect(soy).toBeChecked()
  await expect(milk).not.toBeChecked()
  await expect(page.getByText(`Prefilled 1 declarations from published label ${previousLabel.labelVersionId}.`)).toBeVisible()

  await milk.check()
  await page.getByRole('checkbox', { name: 'Use the connected identity to create this draft' }).check()
  await page.getByRole('button', { name: 'Create label draft' }).click()
  await expect(page.getByRole('region', { name: 'Label draft details' })).toContainText('label_s4_replacement')
  expect(submitted).toEqual([{
    productId, jurisdictionCode: 'US', declarations: [
      { allergenId: 'all_milk', declarationType: 'CONTAINS', displayText: 'Contains Milk' },
      { allergenId: 'all_soy', declarationType: 'CONTAINS', displayText: 'Contains Soy' },
    ], reviewTaskId: 'task_s4_prefill',
  }])
})
