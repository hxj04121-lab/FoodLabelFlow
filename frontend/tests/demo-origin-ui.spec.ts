import { test, expect } from './catalog-fixture'
import type { Page } from '@playwright/test'

// Explicit API fixtures and a locally built production bundle; not cloud deployment evidence.
const approved = 'https://spectrace.52zzx.top'
const actors = {
  'dev-external-label-officer': { userId: 'user_label_officer', username: 'label.officer', displayName: 'Demo Label Officer', roles: ['LABEL_OFFICER'], permissions: ['LABEL.CREATE', 'LABEL.VALIDATE', 'LABEL.SUBMIT_REVIEW'] },
  'dev-external-qa-approver': { userId: 'user_approver', username: 'qa.approver', displayName: 'Demo Approver', roles: ['APPROVER'], permissions: ['LABEL.APPROVE', 'LABEL.REQUEST_CHANGES'] },
  'dev-external-publisher': { userId: 'user_publisher', username: 'publication.manager', displayName: 'Demo Publisher', roles: ['PUBLISHER'], permissions: ['LABEL.PUBLISH'] },
  'dev-external-admin': { userId: 'user_admin', username: 'data.admin', displayName: 'Demo Data Admin', roles: ['ADMIN_DATA_MAINTENANCE'], permissions: ['DATA.MAINTAIN', 'FORMULA.RELEASE', 'AUDIT.READ'] },
  'dev-external-change-manager': { userId: 'user_change_manager', username: 'change.manager', displayName: 'Demo Change Manager', roles: ['CHANGE_MANAGER'], permissions: ['CHANGE_REQUEST.CREATE', 'IMPACT.RUN', 'AUDIT.READ'] },
} as const
const identityKeys: Record<keyof typeof actors, string> = {
  'dev-external-label-officer': 'MAKER', 'dev-external-qa-approver': 'CHECKER',
  'dev-external-publisher': 'PUBLISHER', 'dev-external-admin': 'DATA_ADMIN',
  'dev-external-change-manager': 'CHANGE_MANAGER',
}
const draft = {
  labelVersionId: 'label_hosted_fixture_v2', productId: 'prod_usda_1106285',
  formulaVersionId: 'formula_1106285_v1', ruleSetVersionId: 'ruleset_us_falcpa_demo_v1', jurisdictionCode: 'US',
  versionNumber: 2, rawIngredientText: 'Course fixture', lifecycleStatus: 'DRAFT', isCurrentPublished: 'N',
  createdByUserId: 'user_label_officer', createdAt: '2026-10-24T00:00:00', dataProvenanceId: 'prov_project_seed',
}

async function interceptHostedOrigin(page: Page, origin: string, assetBase: string) {
  await page.routeWebSocket('**/*', socket => socket.close())
  await page.route('**/*', async route => {
    const url = new URL(route.request().url())
    if (url.origin !== origin) return route.abort('blockedbyclient')
    const localUrl = new URL(url.pathname + url.search, assetBase).href
    if (url.pathname.startsWith('/api/')) return route.fallback({ url: localUrl })
    const response = await route.fetch({ url: localUrl })
    return route.fulfill({ response })
  })
}

test.beforeEach(async ({ page }) => {
  await page.setExtraHTTPHeaders({})
  await page.route('**/api/identity/current', route => {
    const subject = route.request().headers()['x-external-subject']
    return subject && subject in actors
      ? route.fulfill({ json: actors[subject as keyof typeof actors] })
      : route.fulfill({ status: 401, json: { code: 'AUTHENTICATION_REQUIRED' } })
  })
  await page.route('**/api/identity/demo-options', route => route.fulfill({ json: Object.entries(actors).map(([subject, actor]) => ({
    key: identityKeys[subject as keyof typeof actors], subject, actor,
  })) }))
  await page.route('**/api/v1/allergens?*', route => route.fulfill({ json: [] }))
  await page.route('**/api/labels/' + draft.labelVersionId, route => route.fulfill({ json: draft }))
  await page.route('**/api/labels/*/declarations', route => route.fulfill({ json: { ...draft, declarations: [] } }))
  await page.route('**/api/v1/label-versions/*/derived-allergens', route => route.fulfill({ json: { ...draft, facts: [], unresolvedComponents: [] } }))
})

test('configured HTTPS origin enables verified role switching while preserving consent and permissions', async ({ page, baseURL }) => {
  await interceptHostedOrigin(page, approved, baseURL!)
  await page.goto(approved + '/labels?productId=' + draft.productId)
  const switcher = page.getByRole('combobox', { name: 'Demo identity' })
  await expect(switcher).toBeVisible()
  await expect(page.getByRole('button', { name: 'Create label draft', exact: true })).toBeDisabled()
  await page.getByRole('checkbox', { name: 'Use the connected identity to create this draft' }).check()
  await expect(page.getByRole('button', { name: 'Create label draft', exact: true })).toBeEnabled()
  await switcher.selectOption('CHECKER')
  await expect(page.getByRole('region', { name: 'Connected identity' })).toContainText('Demo Approver')
  await expect(page.getByRole('checkbox', { name: 'Use the connected identity to create this draft' })).toBeDisabled()
  await expect(page.getByRole('button', { name: 'Create label draft', exact: true })).toBeDisabled()
})

test('coverage builds instrument the production origin policy', async ({ page, baseURL }) => {
  test.skip(process.env.VITE_COVERAGE !== 'true', 'Only the explicit coverage build should be instrumented.')
  await interceptHostedOrigin(page, approved, baseURL!)
  await page.goto(approved + '/labels?productId=' + draft.productId)
  const files = await page.evaluate(() => Object.keys(
    (window as Window & { __coverage__?: Record<string, unknown> }).__coverage__ ?? {},
  ))
  expect(files.some(file => file.replaceAll('\\', '/').endsWith('/src/lib/local-demo.ts'))).toBe(true)
})

test('configured HTTPS origin can create a label and enable its validation and workflow controls', async ({ page, baseURL }) => {
  let writes = 0
  await page.route('**/api/labels/drafts', route => {
    writes++
    expect(route.request().headers()['x-external-subject']).toBe('dev-external-label-officer')
    return route.fulfill({ status: 201, json: draft })
  })
  await interceptHostedOrigin(page, approved, baseURL!)
  await page.goto(approved + '/labels?productId=' + draft.productId)
  await page.getByRole('checkbox', { name: 'Use the connected identity to create this draft' }).check()
  await page.getByRole('button', { name: 'Create label draft', exact: true }).click()
  await expect(page.getByText('Label draft V2 created and read from the server.')).toBeVisible()
  expect(writes).toBe(1)
  await expect(page.getByRole('checkbox', { name: 'Use the connected identity to validate this exact version' })).toBeEnabled()
  await expect(page.getByRole('checkbox', { name: 'Use the connected identity for review and publication' })).toBeEnabled()
  await expect(page.getByRole('button', { name: 'Submit for review', exact: true })).toBeDisabled()
  await expect(page.getByRole('button', { name: 'Approve label', exact: true })).toBeDisabled()
  await expect(page.getByRole('button', { name: 'Publish label', exact: true })).toBeDisabled()
})

test('configured HTTPS formula writes retain explicit Data Admin consent and the actual command identity', async ({ page, baseURL }) => {
  let writes = 0
  await page.route('**/api/catalog/formulas', route => {
    writes++
    expect(route.request().headers()['x-external-subject']).toBe('dev-external-admin')
    return route.fulfill({ status: 201, json: { formula_version_id: 'formula_hosted_fixture_v2', product_id: draft.productId, version_number: 2, lifecycle_status: 'DRAFT', is_current_released: 'N' } })
  })
  await interceptHostedOrigin(page, approved, baseURL!)
  await page.goto(approved + '/formulas')
  await page.getByRole('button', { name: 'Create formula preview' }).click()
  await page.getByLabel('Product *', { exact: true }).selectOption(draft.productId)
  await page.getByLabel('Supplier material *').selectOption('mat_chocolate_base')
  await page.getByLabel('Specification version *').selectOption('spec_chocolate_v1')
  await page.getByRole('button', { name: 'Preview formula', exact: true }).click()
  await expect(page.getByRole('button', { name: 'Save draft', exact: true })).toBeDisabled()
  await page.getByRole('checkbox', { name: 'Enable course Data Admin identity for this form' }).check()
  await page.getByRole('button', { name: 'Save draft', exact: true }).click()
  await expect(page.getByText('Draft V2 saved to the server.')).toBeVisible()
  expect(writes).toBe(1)
})

test('hosted Data Admin creates and explicitly releases a supplier specification', async ({ page, baseURL }) => {
  const subjects: string[] = []
  await page.route('**/api/catalog/specifications', route => {
    if (route.request().method() !== 'POST') return route.fallback()
    subjects.push(route.request().headers()['x-external-subject'])
    expect(route.request().postDataJSON()).toMatchObject({ materialId: 'mat_chocolate_base',
      components: expect.arrayContaining([expect.objectContaining({ ingredientId: 'ing_soy_lecithin', rawPhrase: 'Soy lecithin' })]) })
    return route.fulfill({ status: 201, json: { specification_version_id: 'spec_hosted_v2', supplier_material_id: 'mat_chocolate_base',
      version_number: 2, lifecycle_status: 'DRAFT', effective_date: route.request().postDataJSON().effectiveDate } })
  })
  await page.route('**/api/catalog/specifications/spec_hosted_v2/release', route => {
    subjects.push(route.request().headers()['x-external-subject'])
    return route.fulfill({ json: { specification_version_id: 'spec_hosted_v2', supplier_material_id: 'mat_chocolate_base',
      version_number: 2, lifecycle_status: 'RELEASED', effective_date: '2026-01-01' } })
  })
  await interceptHostedOrigin(page, approved, baseURL!)
  await page.goto(approved + '/materials')
  await page.getByRole('combobox', { name: 'Demo identity' }).selectOption('DATA_ADMIN')
  await page.getByRole('button', { name: 'Create supplier specification' }).click()
  await page.getByLabel('Supplier material *').selectOption('mat_chocolate_base')
  await page.getByRole('button', { name: 'Add ingredient' }).click()
  await page.getByLabel('Ingredient *').nth(2).selectOption('ing_soy_lecithin')
  await page.getByLabel('Supplier wording *').nth(2).fill('Soy lecithin')
  await page.getByLabel('Matching evidence *').nth(2).fill('Hosted course supplier evidence')
  await page.getByRole('button', { name: 'Save specification draft' }).click()
  await expect(page.getByRole('heading', { name: 'Specification V2 · DRAFT' })).toBeVisible()
  await page.getByRole('button', { name: 'Release specification' }).click()
  await expect(page.getByRole('heading', { name: 'Specification V2 · RELEASED' })).toBeVisible()
  expect(subjects).toEqual(['dev-external-admin', 'dev-external-admin'])
})

test('hosted batch adoption permits Data Admin and stays disabled for Change Manager', async ({ page, baseURL }) => {
  const target = { specification_version_id: 'spec_hosted_v2', supplier_material_id: 'mat_chocolate_base',
    version_number: 2, lifecycle_status: 'RELEASED', effective_date: '2026-01-01' }
  let posts = 0
  await page.route('**/api/v1/change-requests?*', route => route.fulfill({ json: [] }))
  await page.route('**/api/catalog/specifications?*', route => route.fulfill({ json: [target] }))
  await page.route('**/api/catalog/specifications/spec_hosted_v2/formula-adoptions', route => {
    posts++
    expect(route.request().headers()['x-external-subject']).toBe('dev-external-admin')
    return route.fulfill({ json: { targetSpecificationVersionId: target.specification_version_id,
      candidateCount: 1, adoptedCount: 1, skippedCount: 0, failedCount: 0,
      results: [{ productId: draft.productId, status: 'ADOPTED', formulaVersionId: 'formula_hosted_adopted_v2', replayed: false, errorCode: null, message: null }] } })
  })
  await interceptHostedOrigin(page, approved, baseURL!)
  await page.goto(approved + '/impact')
  const switcher = page.getByRole('combobox', { name: 'Demo identity' })
  await switcher.selectOption('DATA_ADMIN')
  const panel = page.getByRole('region', { name: 'Batch specification adoption' })
  await panel.getByRole('combobox', { name: 'Released specification' }).selectOption(target.specification_version_id)
  await panel.getByRole('button', { name: 'Adopt for current products' }).click()
  await expect(panel).toContainText('1 adopted, 0 skipped, 0 failed')
  await switcher.selectOption('CHANGE_MANAGER')
  await panel.getByRole('combobox', { name: 'Released specification' }).selectOption(target.specification_version_id)
  await expect(panel.getByRole('button', { name: 'Adopt for current products' })).toBeDisabled()
  expect(posts).toBe(1)
})

test('hosted Change Manager creates a change request and runs impact analysis', async ({ page, baseURL }) => {
  const change = { changeRequestId: 'change_hosted', changeType: 'INGREDIENT_SPEC', supplierMaterialId: 'mat_chocolate_base',
    previousSpecificationVersionId: 'spec_chocolate_v1', targetSpecificationVersionId: 'spec_hosted_v2',
    description: 'Hosted supplier change', status: 'SUBMITTED', createdAt: '2026-10-24T00:00:00Z' }
  const analysis = { impactAnalysisId: 'impact_hosted', changeRequestId: change.changeRequestId,
    ruleSetVersionId: draft.ruleSetVersionId, status: 'COMPLETED', completedAt: '2026-10-24T00:01:00Z',
    relevantProductCount: 0, noActionCount: 0, reviewRequiredCount: 0, findings: [] }
  const subjects: string[] = []
  await page.route('**/api/catalog/specifications?*', route => route.fulfill({ json: [
    { specification_version_id: 'spec_chocolate_v1', supplier_material_id: 'mat_chocolate_base', version_number: 1, lifecycle_status: 'RELEASED', effective_date: '2026-01-01' },
    { specification_version_id: 'spec_hosted_v2', supplier_material_id: 'mat_chocolate_base', version_number: 2, lifecycle_status: 'RELEASED', effective_date: '2026-01-01' },
  ] }))
  await page.route('**/api/v1/change-requests?*', route => route.fulfill({ json: [] }))
  await page.route('**/api/v1/change-requests', route => {
    subjects.push(route.request().headers()['x-external-subject'])
    expect(route.request().postDataJSON()).toMatchObject({ supplierMaterialId: change.supplierMaterialId,
      previousSpecificationVersionId: change.previousSpecificationVersionId, targetSpecificationVersionId: change.targetSpecificationVersionId, description: change.description })
    return route.fulfill({ status: 201, json: change })
  })
  await page.route('**/api/v1/change-requests/change_hosted', route => route.fulfill({ json: change }))
  await page.route('**/api/v1/change-requests/change_hosted/impact-analyses', route => {
    subjects.push(route.request().headers()['x-external-subject'])
    expect(route.request().postDataJSON()).toEqual({ ruleSetVersionId: draft.ruleSetVersionId })
    return route.fulfill({ status: 201, json: analysis })
  })
  await page.route('**/api/v1/impact-analyses/impact_hosted', route => route.fulfill({ json: analysis }))
  await interceptHostedOrigin(page, approved, baseURL!)
  await page.goto(approved + '/impact')
  await page.getByRole('combobox', { name: 'Demo identity' }).selectOption('CHANGE_MANAGER')
  await page.getByText('Create a specification change request', { exact: true }).click()
  await page.getByLabel('Supplier material', { exact: true }).selectOption(change.supplierMaterialId)
  await page.getByLabel('Previous specification', { exact: true }).selectOption(change.previousSpecificationVersionId)
  await page.getByLabel('Target specification', { exact: true }).selectOption(change.targetSpecificationVersionId)
  await page.getByLabel('Change description').fill(change.description)
  await page.getByRole('checkbox', { name: 'Use the connected identity for impact writes' }).check()
  await page.getByRole('button', { name: 'Create change request', exact: true }).click()
  await expect(page.getByRole('combobox', { name: 'Change request', exact: true })).toHaveValue(change.changeRequestId)
  await page.getByRole('checkbox', { name: 'Use the connected identity for impact writes' }).check()
  await page.getByRole('button', { name: 'Run impact analysis', exact: true }).click()
  await expect(page.getByRole('region', { name: 'Analysis results' })).toContainText(analysis.impactAnalysisId)
  expect(subjects).toEqual(['dev-external-change-manager', 'dev-external-change-manager'])
})

for (const origin of ['http://spectrace.52zzx.top', 'https://spectrace.52zzx.top:8443', 'https://spectrace.52zzx.top.example.invalid', 'https://*.52zzx.top']) {
  test(`unapproved origin keeps role switching and label writes blocked: ${origin}`, async ({ page, baseURL }) => {
    await interceptHostedOrigin(page, origin, baseURL!)
    await page.goto(origin + '/labels?productId=' + draft.productId)
    await expect(page.getByRole('combobox', { name: 'Demo identity' })).toHaveCount(0)
    await expect(page.getByRole('checkbox', { name: 'Use the connected identity to create this draft' })).toBeDisabled()
    await expect(page.getByRole('button', { name: 'Create label draft', exact: true })).toBeDisabled()
  })
}

test('unapproved origin cannot edit a returned-label revision through its separate save path', async ({ page, baseURL }) => {
  const origin = 'https://other.52zzx.top'
  const task = { reviewTaskId: 'task_hosted_returned', impactFindingId: 'finding_hosted', productId: draft.productId,
    currentLabelVersionId: 'label_previous', draftLabelVersionId: draft.labelVersionId, targetLabelVersionId: draft.labelVersionId,
    status: 'OPEN', decision: 'REQUEST_CHANGES', resolvedAt: null }
  await page.route('**/api/review-tasks/' + task.reviewTaskId, route => route.fulfill({ json: task }))
  await page.route('**/api/v1/allergens?*', route => route.fulfill({ json: [
    { allergenId: 'all_soy', allergenCode: 'SOY', displayName: 'Soy', jurisdictionCode: 'US' },
  ] }))
  await interceptHostedOrigin(page, origin, baseURL!)
  await page.goto(`${origin}/labels?productId=${draft.productId}&reviewTaskId=${task.reviewTaskId}&labelVersionId=${draft.labelVersionId}`)
  await expect(page.getByRole('region', { name: 'Structured label declarations' })).toContainText('0 declaration(s)')
  await expect(page.getByRole('button', { name: 'Save changes as new version' })).toHaveCount(0)
})
