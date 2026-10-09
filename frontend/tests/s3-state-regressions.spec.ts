import { test, expect } from './catalog-fixture'
import type { Page } from '@playwright/test'

const change = (id: string) => ({ changeRequestId: id, changeType: 'INGREDIENT_SPEC',
  supplierMaterialId: `material-${id}`, previousSpecificationVersionId: `spec-${id}-v1`,
  targetSpecificationVersionId: `spec-${id}-v2`, description: `Change ${id}`,
  status: 'SUBMITTED', createdAt: '2026-10-08T00:00:00Z' })
const analysis = (id: string, changeId: string) => ({ impactAnalysisId: id, changeRequestId: changeId,
  ruleSetVersionId: 'ruleset_us_falcpa_demo_v1', status: 'COMPLETED', completedAt: '2026-10-08T00:01:00Z',
  relevantProductCount: 0, noActionCount: 0, reviewRequiredCount: 0, findings: [] })
const task = (id: string) => ({ reviewTaskId: id, productId: `product-${id}`, currentLabelVersionId: `old-${id}`,
  draftLabelVersionId: `draft-${id}`, targetLabelVersionId: `draft-${id}`, status: 'OPEN', decision: null, resolvedAt: null })

async function impactReads(page: Page) {
  await page.route('**/api/identity/current', route => route.fulfill({ json: {
    userId: 'user_change_manager', username: 'change.manager', displayName: 'Demo Change Manager',
    roles: ['CHANGE_MANAGER'], permissions: ['CHANGE_REQUEST.CREATE', 'IMPACT.RUN'],
  } }))
  await page.route('**/api/v1/change-requests?*', route => route.fulfill({ json: [change('a'), change('b')] }))
  await page.route('**/api/v1/change-requests/*', route => route.fulfill({ json: change(new URL(route.request().url()).pathname.split('/').at(-1)!) }))
  await page.route('**/api/v1/impact-analyses/*', route => {
    const id = new URL(route.request().url()).pathname.split('/').at(-1)!
    return route.fulfill({ json: analysis(id, id === 'run-a' ? 'a' : 'b') })
  })
}

test('caller cancellation remains active while a successful response body is still streaming', async ({ page }) => {
  await page.route('**/api/review-tasks?*', route => route.fulfill({ json: [] }))
  await page.goto('/reviews')
  await expect(page.getByRole('region', { name: 'Connected identity' })).toContainText('Demo Label Officer')
  const result = await page.evaluate(async () => {
    const { requestLabelJson } = await import('/src/api/labels.ts')
    const originalFetch = window.fetch
    let aborted = false
    window.fetch = async (input, init) => {
      if (input !== '/api/streamed-read-regression') return originalFetch(input, init)
      const stream = new ReadableStream({ start(controller) {
        controller.enqueue(new TextEncoder().encode('{"incomplete":'))
        init?.signal?.addEventListener('abort', () => {
          aborted = true
          controller.error(new DOMException('The read was aborted', 'AbortError'))
        }, { once: true })
      } })
      return new Response(stream, { status: 200 })
    }
    const controller = new AbortController()
    const read = requestLabelJson('/api/streamed-read-regression', { signal: controller.signal })
      .then(() => 'unexpected-success', () => 'cancelled')
    const timer = setTimeout(() => controller.abort(), 30)
    try {
      const outcome = await Promise.race([read, new Promise(resolve => setTimeout(() => resolve('hung'), 1000))])
      return { outcome, aborted }
    } finally { clearTimeout(timer); window.fetch = originalFetch }
  })
  expect(result).toEqual({ outcome: 'cancelled', aborted: true })
})

test('a browser-created analysis is restored by GET after reload without repeating the command', async ({ page }) => {
  await impactReads(page)
  let posts = 0
  let reads = 0
  await page.route('**/api/v1/change-requests/a/impact-analyses', route => {
    posts++
    return route.fulfill({ status: 201, json: analysis('run-a', 'a') })
  })
  await page.route('**/api/v1/impact-analyses/run-a', route => { reads++; return route.fulfill({ json: analysis('run-a', 'a') }) })
  await page.goto('/impact')
  await expect(page.getByLabel('Change request', { exact: true })).toHaveValue('a')
  await page.getByRole('checkbox', { name: 'Use the connected identity for impact writes' }).check()
  await page.getByRole('button', { name: 'Run impact analysis', exact: true }).click()
  const results = page.getByRole('region', { name: 'Analysis results' })
  await expect(results.getByRole('heading', { name: 'run-a', exact: true })).toBeVisible()
  await expect(page).toHaveURL(url => url.searchParams.get('impactAnalysisId') === 'run-a' && url.searchParams.get('changeRequestId') === 'a')
  await page.reload()
  await expect(results.getByRole('heading', { name: 'run-a', exact: true })).toBeVisible()
  await expect(page.getByLabel('Change request', { exact: true })).toHaveValue('a')
  expect(reads).toBeGreaterThan(0)
  expect(posts).toBe(1)
})

test('switching change clears old findings and browser back rereads the saved run', async ({ page }) => {
  await impactReads(page)
  await page.goto('/impact?changeRequestId=a&impactAnalysisId=run-a&ruleSetVersionId=ruleset_us_falcpa_demo_v1')
  const results = page.getByRole('region', { name: 'Analysis results' })
  await expect(results).toContainText('run-a')
  await page.getByLabel('Change request', { exact: true }).selectOption('b')
  await expect(page).toHaveURL(url => url.searchParams.get('changeRequestId') === 'b' && !url.searchParams.has('impactAnalysisId'))
  await expect(results).not.toContainText('run-a')
  await page.reload()
  await expect(page.getByLabel('Change request', { exact: true })).toHaveValue('b')
  await expect(page.getByText('material-b', { exact: true })).toBeVisible()
  await page.goBack()
  await expect(results).toContainText('run-a')
})

test('saved analysis context mismatches are errors with no findings', async ({ page }) => {
  await impactReads(page)
  await page.goto('/impact?changeRequestId=b&impactAnalysisId=run-a&ruleSetVersionId=ruleset_us_falcpa_demo_v1')
  await expect(page.getByRole('alert')).toContainText('IMPACT_TARGET_MISMATCH')
  await expect(page.getByRole('region', { name: 'Analysis results' }).getByRole('heading', { name: 'run-a' })).toHaveCount(0)
})

test('missing exact change is not replaced with the first collection item and can be retried', async ({ page }) => {
  await impactReads(page)
  let present = false
  await page.route('**/api/v1/change-requests/saved-change', route => present
    ? route.fulfill({ json: change('saved-change') })
    : route.fulfill({ status: 404, json: { code: 'RESOURCE_NOT_FOUND', message: 'Saved change missing' } }))
  await page.goto('/impact?changeRequestId=saved-change')
  await expect(page.getByRole('alert')).toContainText('RESOURCE_NOT_FOUND')
  await expect(page.getByLabel('Change request', { exact: true })).toHaveValue('saved-change')
  present = true
  await page.getByRole('button', { name: 'Refresh change requests' }).click()
  await expect(page.getByText('material-saved-change', { exact: true })).toBeVisible()
  await expect(page.getByRole('alert')).toHaveCount(0)
})

test('complete pagination deduplicates overlapping pages and a failed refresh clears previous choices', async ({ page }) => {
  await impactReads(page)
  const first = Array.from({ length: 100 }, (_, index) => change(`c-${index}`))
  let fail = false
  await page.route('**/api/v1/change-requests?*', route => {
    if (fail) return route.fulfill({ status: 500, json: { code: 'INTERNAL_ERROR', message: 'Collection read failed' } })
    return route.fulfill({ json: new URL(route.request().url()).searchParams.get('offset') === '0' ? first : [first[99], change('last')] })
  })
  await page.goto('/impact')
  await expect(page.getByLabel('Change request', { exact: true }).locator('option')).toHaveCount(102)
  fail = true
  await page.getByRole('button', { name: 'Refresh change requests' }).click()
  await expect(page.getByRole('alert')).toContainText('INTERNAL_ERROR')
  await expect(page.getByLabel('Change request', { exact: true }).locator('option')).toHaveCount(1)
})

test('manual task choice persists in URL, reload and browser history', async ({ page }) => {
  await page.route('**/api/review-tasks?*', route => route.fulfill({ json: [task('a'), task('b')] }))
  await page.route('**/api/review-tasks/*', route => route.fulfill({ json: task(new URL(route.request().url()).pathname.split('/').at(-1)!) }))
  await page.goto('/reviews?reviewTaskId=a')
  const details = page.getByRole('region', { name: 'Review task details' })
  await expect(details).toContainText('draft-a')
  await page.getByRole('region', { name: 'Review task list' }).locator('article').filter({ hasText: 'product-b' })
    .getByRole('button', { name: 'View task details' }).click()
  await expect(details).toContainText('draft-b')
  await expect(page).toHaveURL(url => url.searchParams.get('reviewTaskId') === 'b')
  await page.reload()
  await expect(details).toContainText('draft-b')
  await page.goBack()
  await expect(details).toContainText('draft-a')
  await page.getByLabel('Existing review task ID').fill('b')
  await page.getByRole('button', { name: 'Load review task', exact: true }).click()
  await expect(details).toContainText('draft-b')
  await expect(page).toHaveURL(url => url.searchParams.get('reviewTaskId') === 'b')
})

for (const lateStatus of [200, 404]) {
  test(`late task ${lateStatus} cannot replace the newly selected target`, async ({ page }) => {
    let release!: () => void
    let started!: () => void
    let settled!: () => void
    const gate = new Promise<void>(resolve => { release = resolve })
    const requested = new Promise<void>(resolve => { started = resolve })
    const finished = new Promise<void>(resolve => { settled = resolve })
    await page.route('**/api/review-tasks?*', route => route.fulfill({ json: [task('a'), task('b')] }))
    await page.route('**/api/review-tasks/*', async route => {
      if (route.request().url().endsWith('/a')) {
        started(); await gate
        await route.fulfill({ status: lateStatus, json: lateStatus === 200 ? task('a') : { code: 'REVIEW_TASK_NOT_FOUND', message: 'Old target missing' } }).catch(() => {})
        settled()
      } else await route.fulfill({ json: task('b') })
    })
    await page.goto('/reviews')
    const list = page.getByRole('region', { name: 'Review task list' })
    await list.locator('article').filter({ hasText: 'product-a' }).getByRole('button').click()
    await requested
    await list.locator('article').filter({ hasText: 'product-b' }).getByRole('button').click()
    const details = page.getByRole('region', { name: 'Review task details' })
    await expect(details).toContainText('draft-b')
    release(); await finished
    await expect(details).toContainText('draft-b')
    await expect(details).not.toContainText('draft-a')
    await expect(details.getByRole('alert')).toHaveCount(0)
  })
}

test('a creator holding approval permission still sees the independent-review guard', async ({ page }) => {
  // UI fixture only: no stored grants or production identities are changed.
  const label = { labelVersionId: 'self-label', productId: 'prod_usda_1106285', formulaVersionId: 'formula_1106285_v1',
    ruleSetVersionId: 'ruleset_us_falcpa_demo_v1', jurisdictionCode: 'US', versionNumber: 2,
    rawIngredientText: 'Wheat', lifecycleStatus: 'PENDING_REVIEW', isCurrentPublished: 'N',
    createdByUserId: 'user_label_officer', createdAt: '2026-10-08T00:00:00', dataProvenanceId: 'prov_project_seed' }
  await page.route('**/api/identity/current', route => route.fulfill({ json: {
    userId: label.createdByUserId, username: 'ui.fixture', displayName: 'Permitted creator UI fixture',
    roles: ['UI_FIXTURE'], permissions: ['LABEL.APPROVE'],
  } }))
  await page.route('**/api/labels/self-label', route => route.fulfill({ json: label }))
  await page.route('**/api/labels/*/declarations', route => route.fulfill({ json: { ...label, declarations: [] } }))
  await page.route('**/api/v1/label-versions/*/derived-allergens', route => route.fulfill({ json: { ...label, facts: [], unresolvedComponents: [] } }))
  await page.route('**/api/v1/allergens?*', route => route.fulfill({ json: [] }))
  let decisions = 0
  await page.route('**/api/labels/*/review-decisions', route => { decisions++; return route.fulfill({ status: 403 }) })
  await page.goto('/labels?productId=prod_usda_1106285&labelVersionId=self-label')
  await page.getByRole('checkbox', { name: 'Use the connected identity for review and publication' }).check()
  await expect(page.getByText('Your connected identity created this label.', { exact: false })).toBeVisible()
  await expect(page.getByRole('button', { name: 'Approve label', exact: true })).toBeDisabled()
  expect(decisions).toBe(0)
})

test('a timed-out exact task read permits retry without showing old context', async ({ page }) => {
  await page.clock.install()
  let release!: () => void
  const gate = new Promise<void>(resolve => { release = resolve })
  await page.route('**/api/review-tasks?*', route => route.fulfill({ json: [task('a')] }))
  await page.route('**/api/review-tasks/a', async route => { await gate; await route.fulfill({ json: task('a') }).catch(() => {}) })
  await page.goto('/reviews?reviewTaskId=a')
  const details = page.getByRole('region', { name: 'Review task details' })
  await expect(details.getByRole('status')).toContainText('Loading')
  await page.clock.fastForward(15001)
  await expect(details.getByRole('alert')).toContainText('timed out')
  await expect(details.getByRole('link')).toHaveCount(0)
  release()
  await page.getByRole('button', { name: 'Load review task', exact: true }).click()
  await expect(details).toContainText('draft-a')
})

for (const lateStatus of [200, 500]) {
  test(`browser back invalidates a late manual impact ${lateStatus} read`, async ({ page }) => {
    await impactReads(page)
    let release!: () => void
    let started!: () => void
    let settled!: () => void
    const gate = new Promise<void>(resolve => { release = resolve })
    const requested = new Promise<void>(resolve => { started = resolve })
    const finished = new Promise<void>(resolve => { settled = resolve })
    await page.route('**/api/v1/impact-analyses/manual-old-run', async route => {
      started(); await gate
      await route.fulfill({ status: lateStatus, json: lateStatus === 200 ? analysis('manual-old-run', 'a') : { code: 'OLD_ERROR', message: 'Obsolete read failure' } }).catch(() => {})
      settled()
    })
    await page.goto('/impact?changeRequestId=b&impactAnalysisId=run-b&ruleSetVersionId=ruleset_us_falcpa_demo_v1')
    const results = page.getByRole('region', { name: 'Analysis results' })
    await expect(results).toContainText('run-b')
    await page.getByLabel('Change request', { exact: true }).selectOption('a')
    await expect(page.getByText('material-a', { exact: true })).toBeVisible()
    await page.getByLabel('Existing impact analysis ID').fill('manual-old-run')
    await page.getByRole('button', { name: 'Load impact analysis', exact: true }).click()
    await requested
    await page.goBack()
    await expect(results).toContainText('run-b')
    release(); await finished
    await expect(page).toHaveURL(url => url.searchParams.get('impactAnalysisId') === 'run-b')
    await expect(results).not.toContainText('manual-old-run')
    await expect(page.getByRole('alert')).toHaveCount(0)
  })
}

test('a completed analysis command for an earlier selection does not redirect the current view', async ({ page }) => {
  await impactReads(page)
  let release!: () => void
  let started!: () => void
  const gate = new Promise<void>(resolve => { release = resolve })
  const requested = new Promise<void>(resolve => { started = resolve })
  let posts = 0
  await page.route('**/api/v1/change-requests/a/impact-analyses', async route => {
    posts++; started(); await gate
    await route.fulfill({ status: 201, json: analysis('new-run-a', 'a') })
  })
  await page.goto('/impact?changeRequestId=b&impactAnalysisId=run-b&ruleSetVersionId=ruleset_us_falcpa_demo_v1')
  const results = page.getByRole('region', { name: 'Analysis results' })
  await expect(results).toContainText('run-b')
  await page.getByLabel('Change request', { exact: true }).selectOption('a')
  await expect(page.getByText('material-a', { exact: true })).toBeVisible()
  await page.getByRole('checkbox', { name: 'Use the connected identity for impact writes' }).check()
  await page.getByRole('button', { name: 'Run impact analysis', exact: true }).click()
  await requested
  await page.goBack()
  await expect(results).toContainText('run-b')
  release()
  await expect(page.getByText('Analysis new-run-a completed for the earlier selection.', { exact: false })).toBeVisible()
  await expect(page).toHaveURL(url => url.searchParams.get('impactAnalysisId') === 'run-b')
  expect(posts).toBe(1)
})

test('a completed creation command preserves a newer saved analysis context', async ({ page }) => {
  await impactReads(page)
  await page.route('**/api/catalog/specifications?*', route => route.fulfill({ json: [
    { specification_version_id: 'spec_chocolate_v1', supplier_material_id: 'mat_chocolate_base' },
    { specification_version_id: 'spec_chocolate_v2', supplier_material_id: 'mat_chocolate_base' },
  ] }))
  let release!: () => void
  let started!: () => void
  const gate = new Promise<void>(resolve => { release = resolve })
  const requested = new Promise<void>(resolve => { started = resolve })
  await page.route('**/api/v1/change-requests', async route => {
    const body = route.request().postDataJSON()
    started(); await gate
    await route.fulfill({ status: 201, json: { ...body, changeRequestId: 'created-earlier', status: 'SUBMITTED', createdAt: '2026-10-08T00:00:00Z' } })
  })
  await page.goto('/impact?changeRequestId=b&impactAnalysisId=run-b&ruleSetVersionId=ruleset_us_falcpa_demo_v1')
  const results = page.getByRole('region', { name: 'Analysis results' })
  await expect(results).toContainText('run-b')
  await page.getByLabel('Change request', { exact: true }).selectOption('a')
  await expect(page.getByText('material-a', { exact: true })).toBeVisible()
  await page.getByText('Create a specification change request', { exact: true }).click()
  await page.getByLabel('Supplier material', { exact: true }).selectOption('mat_chocolate_base')
  await page.getByLabel('Previous specification', { exact: true }).selectOption('spec_chocolate_v1')
  await page.getByLabel('Target specification', { exact: true }).selectOption('spec_chocolate_v2')
  await page.getByLabel('Change description').fill('Recorded earlier command')
  await page.getByRole('checkbox', { name: 'Use the connected identity for impact writes' }).check()
  await page.getByRole('button', { name: 'Create change request', exact: true }).click()
  await requested
  await page.goBack()
  await expect(results).toContainText('run-b')
  release()
  await expect(page.getByText('Created change request created-earlier for the earlier selection.', { exact: false })).toBeVisible()
  await expect(page).toHaveURL(url => url.searchParams.get('impactAnalysisId') === 'run-b')
})
