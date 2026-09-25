import { expect, test } from './catalog-fixture'
import type { Page } from '@playwright/test'
import type { LabelAllergenFacts, LabelDraft } from '../src/api/labels'

const draft: LabelDraft = {
  labelVersionId: 'label_facts_v1', productId: 'prod_usda_1106285',
  formulaVersionId: 'formula_1106285_v1', ruleSetVersionId: 'ruleset_us_falcpa_demo_v1',
  jurisdictionCode: 'US', versionNumber: 1, rawIngredientText: 'Soy lecithin',
  lifecycleStatus: 'SUPERSEDED', isCurrentPublished: 'N', createdByUserId: 'user_label_officer',
  createdAt: '2026-09-19T08:00:00', dataProvenanceId: 'prov_project_seed',
}
const facts: LabelAllergenFacts = {
  labelVersionId: draft.labelVersionId, formulaVersionId: draft.formulaVersionId,
  ruleSetVersionId: draft.ruleSetVersionId, jurisdictionCode: draft.jurisdictionCode,
  facts: [{ allergenId: 'all_soy', allergenCode: 'SOY', derivationEvidence: [{
    formulaItemId: 'item_soy', specificationVersionId: 'spec_soy_v1', specComponentId: 'component_soy',
    ingredientId: 'ingredient_soy', ingredientAllergenId: 'mapping_soy',
    evidenceRule: 'EXACT_SYNONYM', dataProvenanceId: 'prov_soy',
  }] }],
  unresolvedComponents: [{ formulaItemId: 'item_unknown', specificationVersionId: 'spec_unknown_v1',
    specComponentId: 'component_unknown', ingredientId: 'ingredient_unknown', rawPhrase: 'Unknown flavour',
    matchRule: 'MANUAL_REVIEW', matchStatus: 'AMBIGUOUS' }],
}
const endpoint = '**/api/v1/label-versions/*/derived-allergens'
const panel = (page: Page) => page.getByRole('region', { name: 'Derived allergen facts', exact: true })
async function loadDraft(page: Page, id = draft.labelVersionId) {
  await page.getByLabel('Existing label version ID').fill(id)
  await page.getByRole('button', { name: 'Load label draft' }).click()
  await expect(page.getByRole('region', { name: 'Label draft details' })).toContainText(id)
  await expect(panel(page)).toHaveCount(1)
}
test.beforeEach(async ({ page }) => {
  await page.route('**/api/labels/*/declarations', (route) => route.fulfill({ json: {
    ...draft, labelVersionId: new URL(route.request().url()).pathname.split('/')[3], declarations: [],
  } }))
  await page.route('**/api/v1/allergens?*', async (route) => {
    expect(route.request().headers()['x-auth-provider']).toBe('DEV_EXTERNAL')
    await route.fulfill({ json: [] })
  })
  await page.route('**/api/labels/*', (route) => route.fulfill({ json: {
    ...draft, labelVersionId: new URL(route.request().url()).pathname.split('/').pop(),
  } }))
})

test('reads historical label-bound facts with evidence and unresolved warnings', async ({ page }) => {
  let release!: () => void
  const gate = new Promise<void>((resolve) => { release = resolve })
  await page.route(endpoint, async (route) => {
    expect(route.request().method()).toBe('GET')
    expect(new URL(route.request().url()).search).toBe('')
    expect(route.request().headers()['x-external-subject']).toBe('dev-external-label-officer')
    await gate
    await route.fulfill({ json: facts })
  })
  await page.goto('/labels')
  await expect(panel(page)).toContainText('Create or load a label version')
  await loadDraft(page)
  await expect(panel(page).getByRole('status')).toHaveText('Loading derived allergens…')
  release()
  await expect(panel(page)).toContainText('SOY')
  await panel(page).getByText('Derivation evidence for SOY (1)', { exact: true }).click()
  for (const id of Object.values(facts.facts[0].derivationEvidence[0])) {
    await expect(panel(page).getByText(id, { exact: true })).toBeVisible()
  }
  await expect(panel(page)).toContainText('AMBIGUOUS')
  await expect(panel(page)).toContainText('Unknown flavour')
  await expect(panel(page)).toContainText('not a saved validation result')
  await expect(page.getByRole('region', { name: 'Structured label declarations' })).toContainText('0 declaration(s)')
  await panel(page).screenshot({ path: 'test-results/derived-allergens-desktop.png' })
  await page.setViewportSize({ width: 390, height: 844 })
  await page.reload()
  await loadDraft(page)
  await panel(page).getByText('Derivation evidence for SOY (1)', { exact: true }).click()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBeTruthy()
  await page.screenshot({ path: 'test-results/derived-allergens-mobile.png', fullPage: true })
})

for (const unresolved of [false, true]) {
  test(`empty facts never claim allergen-free (unresolved=${unresolved})`, async ({ page }) => {
    await page.route(endpoint, (route) => route.fulfill({ json: {
      ...facts, facts: [], unresolvedComponents: unresolved
        ? [{ ...facts.unresolvedComponents[0], matchStatus: 'UNMAPPED' }] : [],
    } }))
    await page.goto('/labels')
    await loadDraft(page)
    await expect(panel(page)).toContainText('This does not establish that the label is allergen-free.')
    if (unresolved) await expect(panel(page)).toContainText('UNMAPPED')
    else await expect(panel(page).getByRole('region', { name: 'Unresolved components' })).toHaveCount(0)
  })
}

for (const status of [401, 404, 422, 500]) {
  test(`shows ${status} read error and supports an explicit retry`, async ({ page }) => {
    let available = false
    await page.route(endpoint, (route) => route.fulfill(available ? { json: facts } : {
      status, json: { code: `ERROR_${status}`, message: 'Derived inputs unavailable.', traceId: null, evidenceId: null },
    }))
    await page.goto('/labels')
    await loadDraft(page)
    await expect(panel(page).getByRole('alert')).toContainText(`ERROR_${status}`)
    await expect(panel(page).getByRole('heading', { name: 'SOY', exact: true })).toHaveCount(0)
    available = true
    await panel(page).getByRole('button', { name: 'Retry derived allergens' }).click()
    await expect(panel(page)).toContainText('SOY')
  })
}

for (const field of ['labelVersionId', 'formulaVersionId', 'ruleSetVersionId', 'jurisdictionCode']) {
  test(`rejects derived facts with mismatched ${field}`, async ({ page }) => {
    await page.route(endpoint, (route) => route.fulfill({ json: { ...facts, [field]: 'other' } }))
    await page.goto('/labels')
    await loadDraft(page)
    await expect(panel(page).getByRole('alert')).toContainText('DERIVATION_TARGET_MISMATCH')
    await expect(panel(page).getByRole('heading', { name: 'SOY', exact: true })).toHaveCount(0)
  })
}

for (const [name, invalid] of Object.entries({
  'missing evidence': { ...facts, facts: [{ ...facts.facts[0], derivationEvidence: [] }] },
  'unknown match status': { ...facts, unresolvedComponents: [{ ...facts.unresolvedComponents[0], matchStatus: 'UNKNOWN' }] },
  'null unresolved components': { ...facts, unresolvedComponents: null },
})) {
  test(`rejects malformed data: ${name}`, async ({ page }) => {
    await page.route(endpoint, (route) => route.fulfill({ json: invalid }))
    await page.goto('/labels')
    await loadDraft(page)
    await expect(panel(page).getByRole('alert')).toContainText('INVALID_RESPONSE')
  })
}

test('clears data on product change and ignores a late response from the previous draft', async ({ page }) => {
  let release!: () => void
  const gate = new Promise<void>((resolve) => { release = resolve })
  await page.route(endpoint, async (route) => {
    if (route.request().url().includes(draft.labelVersionId)) await gate
    const id = new URL(route.request().url()).pathname.split('/')[4]
    await route.fulfill({ json: { ...facts, labelVersionId: id, facts: id === draft.labelVersionId ? facts.facts : [] } })
  })
  await page.goto('/labels')
  await loadDraft(page)
  await expect(panel(page)).toContainText('Loading derived allergens')
  await loadDraft(page, 'label_facts_v2')
  await expect(panel(page)).toContainText('0 derived allergen(s)')
  release()
  await expect(panel(page)).not.toContainText('SOY')
  await page.getByLabel('Product').selectOption({ index: 1 })
  await expect(panel(page)).toContainText('Create or load a label version')
  await expect(panel(page)).not.toContainText('label_facts_v2')
})

test('shows connection and timeout errors', async ({ page }) => {
  await page.route(endpoint, (route) => route.abort('failed'))
  await page.goto('/labels')
  await loadDraft(page)
  await expect(panel(page).getByRole('alert')).toContainText('Unable to connect')
  await page.clock.install()
  await page.route(endpoint, () => {})
  await panel(page).getByRole('button', { name: 'Retry derived allergens' }).click()
  await expect(panel(page)).toContainText('Loading derived allergens')
  await page.clock.fastForward(15_001)
  await expect(panel(page).getByRole('alert')).toContainText('timed out')
})
