import { expect, test } from '@playwright/test'

const dataAdmin = { userId: 'user_admin', username: 'data.admin', displayName: 'Demo Admin/Data Maintenance',
  roles: ['ADMIN_DATA_MAINTENANCE'], permissions: ['DATA.MAINTAIN', 'FORMULA.RELEASE', 'RULE_SET.MAINTAIN'] }
const changeManager = { userId: 'user_change_manager', username: 'change.manager', displayName: 'Demo Change Manager',
  roles: ['CHANGE_MANAGER'], permissions: ['CHANGE_REQUEST.CREATE', 'IMPACT.RUN'] }
const target = { specification_version_id: 'spec_chocolate_v2', supplier_material_id: 'mat_chocolate_base',
  version_number: 2, lifecycle_status: 'RELEASED', effective_date: '2026-01-01' }
const summary = { targetSpecificationVersionId: target.specification_version_id, candidateCount: 2,
  adoptedCount: 1, skippedCount: 1, failedCount: 0, results: [
    { productId: 'product-one', status: 'ADOPTED', formulaVersionId: 'formula-one-v2', replayed: false, errorCode: null, message: null },
    { productId: 'product-two', status: 'SKIPPED', formulaVersionId: null, replayed: false, errorCode: 'NO_CURRENT_FORMULA', message: 'No current formula.' },
  ] }

async function setup(page: import('@playwright/test').Page, actor: typeof dataAdmin | typeof changeManager) {
  const subject = actor === dataAdmin ? 'dev-external-admin' : 'dev-external-change-manager'
  await page.setExtraHTTPHeaders({ 'X-Auth-Provider': 'DEV_EXTERNAL', 'X-External-Subject': subject })
  await page.route('**/api/identity/current', route => route.fulfill({ json: actor }))
  await page.route('**/api/identity/demo-options', route => route.fulfill({ json: [
    { key: 'DATA_ADMIN', subject: 'dev-external-admin', actor: dataAdmin },
    { key: 'CHANGE_MANAGER', subject: 'dev-external-change-manager', actor: changeManager },
  ] }))
  await page.route('**/api/catalog/specifications?*', route => route.fulfill({ json: [
    target,
    { ...target, specification_version_id: 'spec-draft', version_number: 3, lifecycle_status: 'DRAFT' },
  ] }))
  await page.route('**/api/v1/change-requests?*', route => route.fulfill({ json: [] }))
}

test('Data Admin can adopt a released specification and inspect per-product results', async ({ page }) => {
  await setup(page, dataAdmin)
  const posts: Array<{ path: string; subject: string }> = []
  await page.route('**/api/catalog/specifications/*/formula-adoptions', async route => {
    posts.push({ path: new URL(route.request().url()).pathname, subject: route.request().headers()['x-external-subject'] ?? '' })
    await route.fulfill({ json: summary })
  })
  await page.goto('/impact')
  const panel = page.getByRole('region', { name: 'Batch specification adoption' })
  await expect(panel.getByRole('combobox', { name: 'Released specification' })).toContainText('spec_chocolate_v2')
  await expect(panel.getByRole('combobox', { name: 'Released specification' })).not.toContainText('spec-draft')
  await panel.getByRole('combobox', { name: 'Released specification' }).selectOption(target.specification_version_id)
  await panel.getByRole('button', { name: 'Adopt for current products' }).click()
  await expect(panel).toContainText('1 adopted, 1 skipped, 0 failed (2 candidates)')
  await expect(panel).toContainText('product-one: ADOPTED')
  await expect(panel).toContainText('product-two: SKIPPED')
  expect(posts).toEqual([{ path: `/api/catalog/specifications/${target.specification_version_id}/formula-adoptions`, subject: 'dev-external-admin' }])
})

test('Change Manager lacks batch adoption permission and cannot send the write', async ({ page }) => {
  await setup(page, changeManager)
  let postCount = 0
  await page.route('**/api/catalog/specifications/*/formula-adoptions', async route => {
    postCount++
    await route.fulfill({ status: 403, json: { code: 'FORBIDDEN', message: 'Permission denied' } })
  })
  await page.goto('/impact')
  const panel = page.getByRole('region', { name: 'Batch specification adoption' })
  await expect(panel).toContainText('requires both DATA.MAINTAIN and FORMULA.RELEASE')
  await panel.getByRole('combobox', { name: 'Released specification' }).selectOption(target.specification_version_id)
  await expect(panel.getByRole('button', { name: 'Adopt for current products' })).toBeDisabled()
  expect(postCount).toBe(0)
})

test('a definitive server permission denial is reported without inventing success or retry state', async ({ page }) => {
  await setup(page, dataAdmin)
  await page.route('**/api/catalog/specifications/*/formula-adoptions', route =>
    route.fulfill({ status: 403, json: { code: 'FORBIDDEN', message: 'Permission denied' } }))
  await page.goto('/impact')
  const panel = page.getByRole('region', { name: 'Batch specification adoption' })
  await panel.getByRole('combobox', { name: 'Released specification' }).selectOption(target.specification_version_id)
  await panel.getByRole('button', { name: 'Adopt for current products' }).click()
  await expect(panel.getByRole('alert')).toContainText('FORBIDDEN')
  await expect(panel.getByRole('button', { name: 'Retry the same batch adoption' })).toHaveCount(0)
  await expect(panel).not.toContainText('0 adopted')
})
