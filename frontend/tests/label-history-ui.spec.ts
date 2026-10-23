import { expect, test } from './catalog-fixture'
import type { Page } from '@playwright/test'

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
}

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
