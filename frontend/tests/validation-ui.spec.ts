import { expect, test } from './catalog-fixture'

const draft = {
  labelVersionId: 'label_validation_target',
  productId: 'prod_usda_1106285',
  formulaVersionId: 'formula_1106285_v1',
  ruleSetVersionId: 'ruleset_us_falcpa_demo_v1',
  jurisdictionCode: 'US',
  versionNumber: 2,
  rawIngredientText: 'Wheat flour, soy lecithin',
  lifecycleStatus: 'DRAFT',
  isCurrentPublished: 'N',
  createdByUserId: 'user_label_officer',
  createdAt: '2026-09-19T08:00:00',
  dataProvenanceId: 'prov_project_seed',
}

const failedRun = {
  validationRunId: 'validation_failed_1',
  labelVersionId: draft.labelVersionId,
  ruleSetVersionId: draft.ruleSetVersionId,
  status: 'FAILED',
  ranAt: '2026-09-19T08:05:00Z',
  results: [
    {
      ruleDefinitionId: 'rule_v1_soy_ingredient',
      resultCode: 'INGREDIENT_SOY_FOUND',
      severity: 'INFO',
      passed: true,
      blocking: false,
      message: 'Soy evidence was derived from the current formula.',
    },
    {
      ruleDefinitionId: 'rule_v1_label_contains',
      resultCode: 'ALLERGEN_DECLARATION_MISSING',
      severity: 'ERROR',
      passed: false,
      blocking: true,
      message: 'The structured label declaration is missing soy.',
    },
  ],
}

const passedRun = {
  ...failedRun,
  validationRunId: 'validation_passed_1',
  status: 'PASSED',
  results: [
    {
      ruleDefinitionId: 'rule_v1_label_contains',
      resultCode: 'ALLERGEN_DECLARATION_MATCH',
      severity: 'INFO',
      passed: true,
      blocking: false,
      message: 'The structured declaration matches the derived allergens.',
    },
  ],
}

async function openDraft(page: import('@playwright/test').Page) {
  await page.route('**/api/v1/allergens?*', (route) => route.fulfill({ json: [] }))
  await page.route(`**/api/labels/${draft.labelVersionId}`, (route) =>
    route.fulfill({ json: draft }),
  )
  await page.goto('/labels')
  await page.getByLabel('Existing label version ID').fill(draft.labelVersionId)
  await page.getByRole('button', { name: 'Load label draft' }).click()
  await expect(page.getByRole('region', { name: 'Label draft details' })).toBeVisible()
}

test('runs exact-version validation and renders attributable PASS and blocking FAIL results', async ({ page }) => {
  await page.route('**/api/v1/label-versions/*/validation-runs', async (route) => {
    expect(route.request().method()).toBe('POST')
    expect(route.request().headers()['x-auth-provider']).toBe('DEV_EXTERNAL')
    expect(route.request().headers()['x-external-subject']).toBe(
      'dev-external-label-officer',
    )
    expect(route.request().postDataJSON()).toEqual({
      ruleSetVersionId: draft.ruleSetVersionId,
    })
    await route.fulfill({ status: 201, json: failedRun })
  })
  await openDraft(page)

  await page
    .getByRole('checkbox', {
      name: 'Enable the local demo label-officer identity to validate this exact version',
    })
    .check()
  await page.getByRole('button', { name: 'Run validation' }).click()

  const results = page.getByRole('region', { name: 'Validation run results' })
  await expect(results).toContainText('FAILED')
  await expect(results.getByText('PASS', { exact: true })).toBeVisible()
  await expect(results.getByText('FAIL', { exact: true })).toBeVisible()
  await expect(results).toContainText('Blocking')
  await expect(results).toContainText('ALLERGEN_DECLARATION_MISSING')
  await expect(results).toContainText('rule_v1_label_contains')
  await results.scrollIntoViewIfNeeded()
  await page.screenshot({
    path: 'test-results/scrum19-validation-feedback.png',
  })
})

test('loads a persisted validation run by exact ID', async ({ page }) => {
  await page.route(`**/api/v1/validation-runs/${passedRun.validationRunId}`, async (route) => {
    expect(route.request().headers()['x-external-subject']).toBe(
      'dev-external-label-officer',
    )
    await route.fulfill({ json: passedRun })
  })
  await openDraft(page)

  await page.getByLabel('Existing validation run ID').fill(passedRun.validationRunId)
  await page.getByRole('button', { name: 'Load validation run' }).click()

  const results = page.getByRole('region', { name: 'Validation run results' })
  await expect(results).toContainText('PASSED')
  await expect(results).toContainText('ALLERGEN_DECLARATION_MATCH')
  await expect(page.getByText(`Loaded validation run ${passedRun.validationRunId}.`)).toBeVisible()
})

test('clears validation feedback when the selected draft or product changes', async ({ page }) => {
  const otherDraft = { ...draft, labelVersionId: 'label_other_version' }
  await page.route(`**/api/v1/validation-runs/${passedRun.validationRunId}`, (route) =>
    route.fulfill({ json: passedRun }),
  )
  await page.route(`**/api/labels/${otherDraft.labelVersionId}`, (route) =>
    route.fulfill({ json: otherDraft }),
  )
  await openDraft(page)

  async function showRun() {
    await page.getByLabel('Existing validation run ID').fill(passedRun.validationRunId)
    await page.getByRole('button', { name: 'Load validation run' }).click()
    await expect(page.getByRole('region', { name: 'Validation run results' })).toContainText('PASSED')
  }

  await showRun()
  await page.getByLabel('Existing label version ID').fill(otherDraft.labelVersionId)
  await page.getByRole('button', { name: 'Load label draft' }).click()
  await expect(page.getByRole('region', { name: 'Label draft details' })).toContainText(otherDraft.labelVersionId)
  await expect(page.getByRole('region', { name: 'Validation run results' })).toHaveCount(0)
  await expect(page.getByLabel('Existing validation run ID')).toHaveValue('')

  await page.getByLabel('Existing label version ID').fill(draft.labelVersionId)
  await page.getByRole('button', { name: 'Load label draft' }).click()
  await expect(page.getByRole('region', { name: 'Label draft details' })).toContainText(draft.labelVersionId)
  await showRun()
  await page.getByLabel('Product').selectOption({ index: 1 })
  await expect(page.getByRole('region', { name: 'Validation run results' })).toHaveCount(0)
  await expect(page.getByLabel('Existing validation run ID')).toHaveCount(0)
})

test('rejects a validation run for a different label version or rule set', async ({ page }) => {
  const wrongLabel = { ...passedRun, labelVersionId: 'label_other_version' }
  const wrongRuleSet = { ...passedRun, ruleSetVersionId: 'ruleset_other' }
  await page.route('**/api/v1/validation-runs/*', (route) =>
    route.fulfill({ json: route.request().url().endsWith('wrong-label') ? wrongLabel : wrongRuleSet }),
  )
  await openDraft(page)

  for (const runId of ['wrong-label', 'wrong-rule-set']) {
    await page.getByLabel('Existing validation run ID').fill(runId)
    await page.getByRole('button', { name: 'Load validation run' }).click()
    await expect(page.getByRole('alert')).toContainText('VALIDATION_TARGET_MISMATCH')
    await expect(page.getByRole('region', { name: 'Validation run results' })).toHaveCount(0)
  }
})

test('rejects a validation write response for a different target', async ({ page }) => {
  await page.route('**/api/v1/label-versions/*/validation-runs', (route) =>
    route.fulfill({ status: 201, json: { ...passedRun, ruleSetVersionId: 'ruleset_other' } }),
  )
  await openDraft(page)
  await page
    .getByRole('checkbox', {
      name: 'Enable the local demo label-officer identity to validate this exact version',
    })
    .check()
  await page.getByRole('button', { name: 'Run validation' }).click()

  await expect(page.getByRole('alert')).toContainText('VALIDATION_TARGET_MISMATCH')
  await expect(page.getByRole('region', { name: 'Validation run results' })).toHaveCount(0)
  await expect(page.getByText('The validation write outcome may be unknown.')).toBeVisible()
  await expect(page.getByRole('button', { name: 'Run validation' })).toBeDisabled()
})

test('shows stable validation errors without inventing results', async ({ page }) => {
  await page.route('**/api/v1/label-versions/*/validation-runs', (route) =>
    route.fulfill({
      status: 409,
      json: {
        code: 'LABEL_VERSION_NOT_CURRENT',
        message: 'Validation is permitted only for the current label version.',
        traceId: null,
        evidenceId: null,
      },
    }),
  )
  await openDraft(page)
  await page
    .getByRole('checkbox', {
      name: 'Enable the local demo label-officer identity to validate this exact version',
    })
    .check()
  await page.getByRole('button', { name: 'Run validation' }).click()

  await expect(page.getByRole('alert')).toContainText('LABEL_VERSION_NOT_CURRENT')
  await expect(page.getByRole('region', { name: 'Validation run results' })).toHaveCount(0)
})

test('rejects validation responses that do not match the frozen contract', async ({ page }) => {
  await page.route('**/api/v1/label-versions/*/validation-runs', (route) =>
    route.fulfill({ status: 201, json: { validationRunId: 'incomplete' } }),
  )
  await openDraft(page)
  await page
    .getByRole('checkbox', {
      name: 'Enable the local demo label-officer identity to validate this exact version',
    })
    .check()
  await page.getByRole('button', { name: 'Run validation' }).click()

  await expect(page.getByRole('alert')).toContainText(
    'INVALID_RESPONSE: The label API returned an invalid validation run.',
  )
})

test('keeps validation feedback readable without mobile overflow', async ({ page }) => {
  await page.route(`**/api/v1/validation-runs/${failedRun.validationRunId}`, (route) =>
    route.fulfill({ json: failedRun }),
  )
  await page.setViewportSize({ width: 390, height: 844 })
  await openDraft(page)
  await page.getByLabel('Existing validation run ID').fill(failedRun.validationRunId)
  await page.getByRole('button', { name: 'Load validation run' }).click()

  await expect(page.getByRole('region', { name: 'Validation run results' })).toBeVisible()
  expect(
    await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth),
  ).toBeTruthy()
})
