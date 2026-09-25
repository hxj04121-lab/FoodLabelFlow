import { expect, test } from './catalog-fixture'
import type { Page } from '@playwright/test'
import type { LabelDeclarations, LabelDraft } from '../src/api/labels'

const draft: LabelDraft = {
  labelVersionId: 'label_decl_v1', productId: 'prod_usda_1106285',
  formulaVersionId: 'formula_1106285_v1', ruleSetVersionId: 'ruleset_us_falcpa_demo_v1',
  jurisdictionCode: 'US', versionNumber: 1, rawIngredientText: 'Soy lecithin, wheat flour',
  lifecycleStatus: 'SUPERSEDED', isCurrentPublished: 'N', createdByUserId: 'user_label_officer',
  createdAt: '2026-09-19T08:00:00', dataProvenanceId: 'prov_project_seed',
}
const result: LabelDeclarations = {
  labelVersionId: draft.labelVersionId, formulaVersionId: draft.formulaVersionId,
  ruleSetVersionId: draft.ruleSetVersionId, jurisdictionCode: draft.jurisdictionCode,
  declarations: [
    { allergenId: 'all_soy', declarationType: 'CONTAINS',
      declarationSource: 'FORMULA_DERIVED', displayText: 'Contains: Soy' },
    { allergenId: 'all_wheat', declarationType: 'CONTAINS',
      declarationSource: 'USER_ENTERED', displayText: null },
  ],
}
const endpoint = '**/api/labels/*/declarations'
const panel = (page: Page) => page.getByRole('region', { name: 'Structured label declarations' })
async function loadDraft(page: Page, id = draft.labelVersionId) {
  await page.getByLabel('Existing label version ID').fill(id)
  await page.getByRole('button', { name: 'Load label draft' }).click()
  await expect(page.getByRole('region', { name: 'Label draft details' })).toContainText(id)
  await expect(page.getByRole('region', { name: 'Derived allergen facts', exact: true })).toHaveCount(1)
  await expect(panel(page)).toHaveCount(1)
}
test.beforeEach(async ({ page }) => {
  await page.route('**/api/v1/allergens?*', (route) => route.fulfill({ json: [] }))
  await page.route('**/api/v1/label-versions/*/derived-allergens', (route) => route.fulfill({ json: {
    ...draft,
    labelVersionId: new URL(route.request().url()).pathname.split('/')[4],
    facts: [], unresolvedComponents: [],
  } }))
  await page.route('**/api/labels/*', (route) => route.fulfill({ json: {
    ...draft, labelVersionId: new URL(route.request().url()).pathname.split('/').pop(),
  } }))
})

test('reads an exact historical label and renders distinct declarations, source and null display text', async ({ page }) => {
  let release!: () => void
  const gate = new Promise<void>((resolve) => { release = resolve })
  await page.route(endpoint, async (route) => {
    expect(route.request().method()).toBe('GET')
    expect(route.request().headers()['x-auth-provider']).toBe('DEV_EXTERNAL')
    expect(route.request().headers()['x-external-subject']).toBe('dev-external-label-officer')
    expect(new URL(route.request().url()).search).toBe('')
    await gate
    await route.fulfill({ json: result })
  })
  await page.goto('/labels')
  await expect(panel(page)).toContainText('Create or load a label version')
  await loadDraft(page)
  await expect(panel(page).getByRole('status')).toHaveText('Loading label declarations…')
  release()
  await expect(panel(page)).toContainText('2 declaration(s)')
  await expect(panel(page)).toContainText('Contains: Soy')
  await expect(panel(page)).toContainText('Source: FORMULA_DERIVED')
  await expect(panel(page)).toContainText('Source: USER_ENTERED')
  await expect(panel(page)).toContainText('No display text provided.')
  await expect(page.getByRole('region', { name: 'Derived allergen facts' })).toContainText('0 derived allergen(s)')
  await page.setViewportSize({ width: 390, height: 844 })
  await page.reload()
  await loadDraft(page)
  await expect(panel(page)).toContainText('2 declaration(s)')
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBeTruthy()
  await page.screenshot({ path: 'test-results/label-declarations-mobile.png', fullPage: true })
})

test('an empty declaration list is visible and does not imply allergen-free', async ({ page }) => {
  await page.route(endpoint, (route) => route.fulfill({ json: { ...result, declarations: [] } }))
  await page.goto('/labels')
  await loadDraft(page)
  await expect(panel(page)).toContainText('0 declaration(s)')
  await expect(panel(page)).toContainText('This does not mean the product is allergen-free')
})

for (const status of [401, 404, 500]) {
  test(`shows ${status} error and succeeds only after an explicit retry`, async ({ page }) => {
    let available = false
    await page.route(endpoint, (route) => route.fulfill(available ? { json: result } : {
      status, json: { code: `ERROR_${status}`, message: 'Declarations unavailable.', traceId: null, evidenceId: null },
    }))
    await page.goto('/labels')
    await loadDraft(page)
    await expect(panel(page).getByRole('alert')).toContainText(`ERROR_${status}`)
    await expect(panel(page)).not.toContainText('Contains: Soy')
    available = true
    await panel(page).getByRole('button', { name: 'Retry declarations' }).click()
    await expect(panel(page)).toContainText('Contains: Soy')
  })
}

for (const field of ['labelVersionId', 'formulaVersionId', 'ruleSetVersionId', 'jurisdictionCode']) {
  test(`rejects declarations with mismatched ${field}`, async ({ page }) => {
    await page.route(endpoint, (route) => route.fulfill({ json: { ...result, [field]: 'wrong' } }))
    await page.goto('/labels')
    await loadDraft(page)
    await expect(panel(page).getByRole('alert')).toContainText('DECLARATION_TARGET_MISMATCH')
    await expect(panel(page)).not.toContainText('Contains: Soy')
  })
}

for (const [name, invalid] of Object.entries({
  'missing jurisdiction': { ...result, jurisdictionCode: undefined },
  'unknown source': { ...result, declarations: [{ ...result.declarations[0], declarationSource: 'LABEL' }] },
  'missing displayText': { ...result, declarations: [{ ...result.declarations[0], displayText: undefined }] },
  'not an array': { ...result, declarations: null },
})) {
  test(`rejects malformed declaration response: ${name}`, async ({ page }) => {
    await page.route(endpoint, (route) => route.fulfill({ json: invalid }))
    await page.goto('/labels')
    await loadDraft(page)
    await expect(panel(page).getByRole('alert')).toContainText('INVALID_RESPONSE')
  })
}

test('switching label or product clears old data and ignores a late response', async ({ page }) => {
  let release!: () => void
  const gate = new Promise<void>((resolve) => { release = resolve })
  await page.route(endpoint, async (route) => {
    if (route.request().url().includes(draft.labelVersionId)) await gate
    const id = new URL(route.request().url()).pathname.split('/')[3]
    await route.fulfill({ json: {
      ...result, labelVersionId: id, declarations: id === draft.labelVersionId ? result.declarations : [],
    } })
  })
  await page.goto('/labels')
  await loadDraft(page)
  await expect(panel(page)).toContainText('Loading label declarations')
  await loadDraft(page, 'label_decl_v2')
  await expect(panel(page)).toContainText('0 declaration(s)')
  release()
  await expect(panel(page)).not.toContainText('Contains: Soy')
  await page.getByLabel('Product').selectOption({ index: 1 })
  await expect(panel(page)).toContainText('Create or load a label version')
  await expect(panel(page)).not.toContainText('label_decl_v2')
})

test('reports network and timeout errors', async ({ page }) => {
  await page.route(endpoint, (route) => route.abort('failed'))
  await page.goto('/labels')
  await loadDraft(page)
  await expect(panel(page).getByRole('alert')).toContainText('Unable to connect')
  await page.clock.install()
  await page.route(endpoint, () => {})
  await panel(page).getByRole('button', { name: 'Retry declarations' }).click()
  await expect(panel(page)).toContainText('Loading label declarations')
  await page.clock.fastForward(15_001)
  await expect(panel(page).getByRole('alert')).toContainText('timed out')
})
