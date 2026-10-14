import { expect, test } from './catalog-fixture'
import type { Page } from '@playwright/test'
import seed from '../src/data/seed-preview.json' with { type: 'json' }

const ruleSet = 'ruleset_us_falcpa_demo_v1'
const changeId = 'change_s4_ui'
const analysisId = 'impact_s4_ui'

function productName(productId: string) {
  return seed.product.find(row => row.product_id === productId)?.product_description ?? productId
}

async function currentIdentity(page: Page) {
  await page.setExtraHTTPHeaders({})
  await page.route('**/api/identity/current', route => {
    const subject = route.request().headers()['x-external-subject']
    if (!subject) return route.fulfill({ status: 401, json: { code: 'AUTHENTICATION_REQUIRED', message: 'Identity required' } })
    const actors: Record<string, { userId: string; username: string; displayName: string; roles: string[]; permissions: string[] }> = {
      'dev-external-label-officer': { userId: 'user_label_officer', username: 'label.officer', displayName: 'Demo Label Officer',
        roles: ['LABEL_OFFICER'], permissions: ['LABEL.CREATE', 'LABEL.VALIDATE', 'LABEL.SUBMIT_REVIEW'] },
      'dev-external-change-manager': { userId: 'user_change_manager', username: 'change.manager', displayName: 'Demo Change Manager',
        roles: ['CHANGE_MANAGER'], permissions: ['CHANGE_REQUEST.CREATE', 'IMPACT.RUN', 'AUDIT.READ'] },
      'dev-external-admin': { userId: 'user_admin', username: 'data.admin', displayName: 'Demo Admin/Data Maintenance',
        roles: ['ADMIN_DATA_MAINTENANCE'], permissions: ['DATA.MAINTAIN', 'FORMULA.RELEASE', 'AUDIT.READ'] },
    }
    return route.fulfill({ json: actors[subject] ?? actors['dev-external-label-officer'] })
  })
  await page.route('**/api/v1/change-requests?*', route => route.fulfill({ json: [] }))
}

test('impact results use catalog product names and filter by persisted outcome', async ({ page }) => {
  const reviewProduct = seed.product[0]
  const noActionProduct = seed.product[1]
  await currentIdentity(page)
  await page.route(`**/api/v1/change-requests/${changeId}`, route => route.fulfill({ json: {
    changeRequestId: changeId, changeType: 'INGREDIENT_SPEC', supplierMaterialId: 'mat_chocolate_base',
    previousSpecificationVersionId: 'spec_chocolate_v1', targetSpecificationVersionId: 'spec_chocolate_v2',
    description: 'S4 UI fixture', status: 'ANALYZED', createdAt: '2026-10-14T00:00:00Z',
  } }))
  await page.route(`**/api/v1/impact-analyses/${analysisId}`, route => route.fulfill({ json: {
    impactAnalysisId: analysisId, changeRequestId: changeId, ruleSetVersionId: ruleSet,
    status: 'COMPLETED', completedAt: '2026-10-14T00:05:00Z', relevantProductCount: 2,
    noActionCount: 1, reviewRequiredCount: 1,
    findings: [
      { impactFindingId: 'finding_review', productId: reviewProduct.product_id, productDisplayName: productName(reviewProduct.product_id), outcome: 'REVIEW_REQUIRED',
        currentFormulaVersionId: 'formula_current_review', proposedFormulaVersionId: 'formula_new_review',
        currentLabelVersionId: 'label_current_review', missingAllergenCodes: ['SOY'], explanation: 'Add the missing soy declaration.',
        reviewTask: { reviewTaskId: 'task_s4_ui', impactFindingId: 'finding_review', productId: reviewProduct.product_id,
          currentFormulaVersionId: 'formula_current_review', currentLabelVersionId: 'label_current_review', draftLabelVersionId: null } },
      { impactFindingId: 'finding_none', productId: noActionProduct.product_id, productDisplayName: productName(noActionProduct.product_id), outcome: 'NO_ACTION',
        currentFormulaVersionId: 'formula_current_none', proposedFormulaVersionId: 'formula_new_none',
        currentLabelVersionId: 'label_current_none', missingAllergenCodes: [], explanation: 'The declarations cover this change.' },
    ],
  } }))

  await page.goto(`/impact?changeRequestId=${changeId}&impactAnalysisId=${analysisId}&ruleSetVersionId=${ruleSet}`)
  const results = page.getByRole('region', { name: 'Analysis results' })
  await expect(results).toContainText(productName(reviewProduct.product_id))
  await expect(results).toContainText(productName(noActionProduct.product_id))
  await expect(results.getByText('2 findings · 1 no action · 1 review required')).toBeVisible()
  await results.getByLabel('Show findings').selectOption('REVIEW_REQUIRED')
  await expect(results).toContainText(productName(reviewProduct.product_id))
  await expect(results).not.toContainText(productName(noActionProduct.product_id))
  await expect(results).toContainText('Showing 1 of 2 findings')
  await expect(results.getByRole('link', { name: 'Prepare replacement label' })).toHaveAttribute('href', /reviewTaskId=task_s4_ui/)
})

test('Data Admin can call the permission-gated current-product batch from the impact page', async ({ page }) => {
  await currentIdentity(page)
  await page.route('**/api/identity/demo-options', route => route.fulfill({ json: [
    { key: 'MAKER', subject: 'dev-external-label-officer', actor: { userId: 'user_label_officer', permissions: ['LABEL.CREATE'] } },
    { key: 'CHECKER', subject: 'dev-external-qa-approver', actor: { userId: 'user_approver', permissions: ['LABEL.APPROVE'] } },
    { key: 'PUBLISHER', subject: 'dev-external-publisher', actor: { userId: 'user_publisher', permissions: ['LABEL.PUBLISH'] } },
    { key: 'CHANGE_MANAGER', subject: 'dev-external-change-manager', actor: { userId: 'user_change_manager', permissions: ['CHANGE_REQUEST.CREATE'] } },
    { key: 'DATA_ADMIN', subject: 'dev-external-admin', actor: { userId: 'user_admin', permissions: ['DATA.MAINTAIN', 'FORMULA.RELEASE', 'AUDIT.READ'] } },
  ] }))
  let batchCalls = 0
  const target = 'spec_chocolate_v1'
  const firstProduct = seed.product[0]
  await page.route(`**/api/catalog/specifications/${target}/formula-adoptions`, async route => {
    batchCalls++
    expect(route.request().method()).toBe('POST')
    expect(route.request().headers()['x-external-subject']).toBe('dev-external-admin')
    await route.fulfill({ json: {
      targetSpecificationVersionId: target, candidateCount: 1, adoptedCount: 1, skippedCount: 0, failedCount: 0,
      results: [{ productId: firstProduct.product_id, status: 'ADOPTED', formulaVersionId: 'formula_s4_adopted',
        replayed: false, errorCode: null, message: null }],
    } })
  })

  await page.goto('/impact')
  await page.getByLabel('Demo identity').selectOption('DATA_ADMIN')
  await expect(page.getByRole('region', { name: 'Connected identity' })).toContainText('user_admin')
  await page.getByRole('checkbox', { name: 'Use the connected identity for impact writes' }).check()
  await page.getByText('Create a specification change request').click()
  await page.getByLabel('Target specification').selectOption(target)
  await page.getByRole('button', { name: 'Adopt for current products' }).click()
  await expect(page.getByRole('region', { name: 'Batch adoption summary' })).toContainText('1 adopted')
  await expect(page.getByRole('region', { name: 'Batch adoption summary' })).toContainText(productName(firstProduct.product_id))
  expect(batchCalls).toBe(1)
})

test('Data Admin rejects inconsistent batch adoption rows without claiming success', async ({ page }) => {
  await currentIdentity(page)
  await page.route('**/api/identity/demo-options', route => route.fulfill({ json: [
    { key: 'DATA_ADMIN', subject: 'dev-external-admin', actor: { userId: 'user_admin', permissions: ['DATA.MAINTAIN', 'FORMULA.RELEASE'] } },
  ] }))
  let batchCalls = 0
  await page.route('**/api/catalog/specifications/spec_chocolate_v1/formula-adoptions', route => {
    batchCalls++
    return route.fulfill({ json: batchCalls === 1 ? {
      targetSpecificationVersionId: 'spec_chocolate_v1', candidateCount: 1, adoptedCount: 0, skippedCount: 0, failedCount: 0,
      results: [null],
    } : batchCalls === 2 ? {
      targetSpecificationVersionId: 'spec_chocolate_v1', candidateCount: 1, adoptedCount: 0, skippedCount: 0, failedCount: 0,
      results: [{ productId: 'prod_s4', status: 'UNKNOWN', formulaVersionId: null, replayed: false, errorCode: null, message: null }],
    } : {
      targetSpecificationVersionId: 'spec_chocolate_v1', candidateCount: 0, adoptedCount: 0, skippedCount: 0, failedCount: 0,
      results: [],
    } })
  })

  await page.goto('/impact')
  await page.getByLabel('Demo identity').selectOption('DATA_ADMIN')
  await expect(page.getByRole('region', { name: 'Connected identity' })).toContainText('user_admin')
  await page.getByRole('checkbox', { name: 'Use the connected identity for impact writes' }).check()
  await page.getByText('Create a specification change request', { exact: true }).click()
  await page.getByLabel('Target specification', { exact: true }).selectOption('spec_chocolate_v1')

  for (let attempt = 0; attempt < 2; attempt++) {
    await page.getByRole('button', { name: 'Adopt for current products' }).click()
    await expect(page.getByRole('alert')).toContainText('inconsistent product results')
  }
  await page.getByRole('button', { name: 'Adopt for current products' }).click()
  await expect(page.getByRole('region', { name: 'Batch adoption summary' })).toContainText('0 adopted')
  expect(batchCalls).toBe(3)
})

test('Maker cannot invoke the Data Admin-only batch adoption endpoint', async ({ page }) => {
  let batchCalls = 0
  await currentIdentity(page)
  await page.route('**/api/catalog/specifications/spec_chocolate_v1/formula-adoptions', route => {
    batchCalls++
    return route.fulfill({ json: {} })
  })
  await page.goto('/impact')
  await page.getByText('Create a specification change request', { exact: true }).click()
  await page.getByLabel('Target specification', { exact: true }).selectOption('spec_chocolate_v1')
  await expect(page.getByRole('button', { name: 'Adopt for current products' })).toBeDisabled()
  expect(batchCalls).toBe(0)
})

test('Change Manager can create a specification change and run its exact impact analysis', async ({ page }) => {
  const change = {
    changeRequestId: 'change_s4_manager', changeType: 'INGREDIENT_SPEC', supplierMaterialId: 'mat_chocolate_base',
    previousSpecificationVersionId: 'spec_chocolate_v1', targetSpecificationVersionId: 'spec_chocolate_v2',
    description: 'S4 Change Manager browser fixture', status: 'SUBMITTED', createdAt: '2026-10-14T00:00:00Z',
  }
  const product = seed.product[0]
  const analysis = {
    impactAnalysisId: 'impact_s4_manager', changeRequestId: change.changeRequestId,
    ruleSetVersionId: ruleSet, status: 'COMPLETED', completedAt: '2026-10-14T00:05:00Z',
    relevantProductCount: 1, noActionCount: 1, reviewRequiredCount: 0,
    findings: [{ impactFindingId: 'finding_s4_manager', productId: product.product_id, productDisplayName: product.product_description, outcome: 'NO_ACTION',
      currentFormulaVersionId: 'formula_current_manager', proposedFormulaVersionId: 'formula_next_manager',
      currentLabelVersionId: 'label_current_manager', missingAllergenCodes: [], explanation: 'No declaration change required.' }],
  }
  await currentIdentity(page)
  await page.route('**/api/identity/demo-options', route => route.fulfill({ json: [
    { key: 'MAKER', subject: 'dev-external-label-officer', actor: { userId: 'user_label_officer', permissions: ['LABEL.CREATE'] } },
    { key: 'CHECKER', subject: 'dev-external-qa-approver', actor: { userId: 'user_approver', permissions: ['LABEL.APPROVE'] } },
    { key: 'PUBLISHER', subject: 'dev-external-publisher', actor: { userId: 'user_publisher', permissions: ['LABEL.PUBLISH'] } },
    { key: 'CHANGE_MANAGER', subject: 'dev-external-change-manager', actor: { userId: 'user_change_manager', permissions: ['CHANGE_REQUEST.CREATE', 'IMPACT.RUN', 'AUDIT.READ'] } },
    { key: 'DATA_ADMIN', subject: 'dev-external-admin', actor: { userId: 'user_admin', permissions: ['DATA.MAINTAIN', 'FORMULA.RELEASE', 'AUDIT.READ'] } },
  ] }))
  await page.route('**/api/catalog/specifications?*', route => route.fulfill({ json: [
    { specification_version_id: 'spec_chocolate_v1', supplier_material_id: 'mat_chocolate_base' },
    { specification_version_id: 'spec_chocolate_v2', supplier_material_id: 'mat_chocolate_base' },
  ] }))
  await page.route('**/api/v1/change-requests', async route => {
    expect(route.request().method()).toBe('POST')
    expect(route.request().headers()['x-external-subject']).toBe('dev-external-change-manager')
    expect(route.request().postDataJSON()).toEqual({
      changeType: 'INGREDIENT_SPEC', supplierMaterialId: 'mat_chocolate_base',
      previousSpecificationVersionId: 'spec_chocolate_v1', targetSpecificationVersionId: 'spec_chocolate_v2',
      description: change.description,
    })
    await route.fulfill({ status: 201, json: change })
  })
  await page.route(`**/api/v1/change-requests/${change.changeRequestId}`, route => route.fulfill({ json: change }))
  await page.route(`**/api/v1/change-requests/${change.changeRequestId}/impact-analyses`, async route => {
    expect(route.request().method()).toBe('POST')
    expect(route.request().headers()['x-external-subject']).toBe('dev-external-change-manager')
    await route.fulfill({ status: 201, json: analysis })
  })

  await page.goto('/impact')
  await page.getByLabel('Demo identity').selectOption('CHANGE_MANAGER')
  await expect(page.getByRole('region', { name: 'Connected identity' })).toContainText('user_change_manager')
  await page.getByRole('checkbox', { name: 'Use the connected identity for impact writes' }).check()
  await page.getByText('Create a specification change request', { exact: true }).click()
  await page.getByLabel('Supplier material', { exact: true }).selectOption('mat_chocolate_base')
  await page.getByLabel('Previous specification', { exact: true }).selectOption('spec_chocolate_v1')
  await page.getByLabel('Target specification', { exact: true }).selectOption('spec_chocolate_v2')
  await page.getByLabel('Change description').fill(change.description)
  await page.getByRole('button', { name: 'Create change request', exact: true }).click()
  await expect(page).toHaveURL(url => url.searchParams.get('changeRequestId') === change.changeRequestId)
  await page.getByRole('checkbox', { name: 'Use the connected identity for impact writes' }).check()
  await page.getByRole('button', { name: 'Run impact analysis', exact: true }).click()
  await expect(page.getByRole('region', { name: 'Analysis results' })).toContainText('1 findings · 1 no action · 0 review required')
})

test('Maker cannot read product label history', async ({ page }) => {
  let historyCalls = 0
  await currentIdentity(page)
  await page.route('**/api/audit/products/*/label-history', route => {
    historyCalls++
    return route.fulfill({ json: {} })
  })
  await page.goto('/label-history')
  await expect(page.getByRole('alert')).toContainText('This identity cannot read audit history')
  expect(historyCalls).toBe(0)
})

test('Change Manager reads published and draft label snapshots, approvals and attributable events', async ({ page }) => {
  await currentIdentity(page)
  await page.route('**/api/identity/demo-options', route => route.fulfill({ json: [
    { key: 'CHANGE_MANAGER', subject: 'dev-external-change-manager', actor: { userId: 'user_change_manager', permissions: ['AUDIT.READ'] } },
  ] }))
  await page.route('**/api/catalog/products?*', route => route.fulfill({ json: [
    { product_id: 'prod_history', product_description: 'Chocolate product' },
    { product_id: 'prod_empty_history', product_description: 'Empty history product' },
    { product_id: 7, product_description: 'Invalid product row' },
    { product_id: 'prod_missing_name' },
  ] }))
  await page.route('**/api/audit/products/*/label-history', route => {
    const empty = route.request().url().includes('prod_empty_history')
    return route.fulfill({ json: empty ? {
      productId: 'prod_empty_history', productDisplayName: 'Empty history product',
      versions: [], approvals: [], auditEvents: [],
    } : {
      productId: 'prod_history', productDisplayName: 'Chocolate product',
      versions: [
        { labelVersionId: 'label-history-v2', versionNumber: 2, jurisdictionCode: 'US', lifecycleStatus: 'PUBLISHED',
          formulaVersionId: 'formula-v2', ruleSetVersionId: 'rules-us-v1', createdAt: '2026-10-13T10:00:00Z',
          createdByUserId: 'maker-1', createdByDisplayName: 'Maker', publishedAt: '2026-10-13T11:00:00Z',
          publishedByUserId: 'publisher-1', publishedByDisplayName: 'Publisher', publicationChannel: 'WEB',
          declarations: [{ allergenCode: 'SOY', declarationType: 'CONTAINS', source: 'USER_ENTERED', displayText: 'Contains soy' }] },
        { labelVersionId: 'label-history-draft', versionNumber: 3, jurisdictionCode: 'US', lifecycleStatus: 'DRAFT',
          formulaVersionId: 'formula-v2', ruleSetVersionId: 'rules-us-v1', createdAt: '2026-10-14T10:00:00Z',
          createdByUserId: 'maker-2', createdByDisplayName: 'Second Maker', publishedAt: null,
          publishedByUserId: null, publishedByDisplayName: null, publicationChannel: null, declarations: [] },
      ],
      approvals: [{ approvalRecordId: 'approval-1', labelVersionId: 'label-history-v2', reviewTaskId: 'task-1',
        decision: 'APPROVE', decidedByUserId: 'checker-1', decidedByDisplayName: 'Checker',
        decidedAt: '2026-10-13T10:30:00Z', comments: 'Independent review complete' }],
      auditEvents: [{ auditEventId: 'audit-1', eventType: 'LABEL_PUBLISHED', entityType: 'LABEL_VERSION',
        entityId: 'label-history-v2', eventAt: '2026-10-13T11:00:00Z', actorUserId: 'publisher-1',
        actorDisplayName: 'Publisher', eventPayload: '{"channel":"WEB"}' }],
    } })
  })

  await page.goto('/label-history')
  await page.getByLabel('Demo identity').selectOption('CHANGE_MANAGER')
  await expect(page.getByRole('region', { name: 'Connected identity' })).toContainText('user_change_manager')
  await expect(page.getByLabel('Product', { exact: true })).toHaveValue('prod_history')
  const history = page.getByRole('region', { name: 'Product label history' })
  await expect(history).toContainText('Chocolate product')
  await expect(page.getByText('2 label versions · 1 decisions · 1 audit events')).toBeVisible()
  await expect(history).toContainText('Published')
  await expect(history).toContainText('Contains soy [USER_ENTERED]')
  await expect(history).toContainText('Not published')
  await expect(history).toContainText('Independent review complete')
  await expect(history).toContainText('LABEL_PUBLISHED')
  await expect(history).toContainText('{"channel":"WEB"}')

  const emptyHistory = page.waitForResponse(response => response.url().includes('/api/audit/products/prod_empty_history/label-history'))
  await page.getByLabel('Product', { exact: true }).selectOption('prod_empty_history')
  await emptyHistory
  await expect(history).toContainText('No label versions exist for this product.')
  await expect(history).toContainText('No approval decisions recorded.')
  await expect(history).toContainText('No attributable label audit events recorded.')
})

test('label history reports a malformed read and refreshes the exact selected product', async ({ page }) => {
  let historyCalls = 0
  await currentIdentity(page)
  await page.route('**/api/identity/demo-options', route => route.fulfill({ json: [
    { key: 'DATA_ADMIN', subject: 'dev-external-admin', actor: { userId: 'user_admin', permissions: ['AUDIT.READ'] } },
  ] }))
  await page.route('**/api/catalog/products?*', route => route.fulfill({ json: [
    { product_id: 'prod_history', product_description: 'Chocolate product' },
  ] }))
  await page.route('**/api/audit/products/prod_history/label-history', route => {
    historyCalls++
    return route.fulfill({ json: historyCalls === 1 ? {
      productId: 'another-product', productDisplayName: 'Wrong product', versions: [], approvals: [], auditEvents: [],
    } : {
      productId: 'prod_history', productDisplayName: 'Chocolate product', versions: [], approvals: [], auditEvents: [],
    } })
  })

  await page.goto('/label-history')
  await page.getByLabel('Demo identity').selectOption('DATA_ADMIN')
  await expect(page.getByRole('alert')).toContainText('incomplete product history')
  await page.getByRole('button', { name: 'Refresh history' }).click()
  await expect(page.getByRole('region', { name: 'Product label history' }))
    .toContainText('No label versions exist for this product.')
  expect(historyCalls).toBe(2)
})
