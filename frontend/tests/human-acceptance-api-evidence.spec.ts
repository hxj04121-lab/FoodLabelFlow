import { expect, test } from '@playwright/test'
import { execFileSync } from 'node:child_process'
import { testArtifactPath } from './artifact-path'

test('captures live RBAC API outcomes for human acceptance', async ({ page, request }) => {
  const body = {
    productId: 'prod_usda_1106285',
    provenanceId: 'prov_project_seed',
    items: [{ materialId: 'mat_chocolate_base', specificationId: 'spec_chocolate_v1', quantity: 1, unit: 'kg' }],
  }
  const unauthenticated = await request.post('/api/catalog/formulas', { data: body })
  const auditor = await request.post('/api/catalog/formulas', {
    data: body,
    headers: { 'X-Auth-Provider': 'DEV_EXTERNAL', 'X-External-Subject': 'dev-external-auditor' },
  })
  const admin = await request.post('/api/catalog/formulas', {
    data: body,
    headers: { 'X-Auth-Provider': 'DEV_EXTERNAL', 'X-External-Subject': 'dev-external-admin' },
  })
  expect(unauthenticated.status()).toBe(401)
  expect(auditor.status()).toBe(403)
  expect(admin.status()).toBe(201)
  await page.goto('about:blank')
  await page.setContent(`<main><h1>Live RBAC API acceptance</h1><pre>${JSON.stringify({
    unauthenticated: { status: unauthenticated.status(), body: await unauthenticated.json() },
    auditor: { status: auditor.status(), body: await auditor.json() },
    admin: { status: admin.status(), body: await admin.json() },
  }, null, 2)}</pre></main>`)
  await page.screenshot({ path: testArtifactPath('screenshots', 'wo-m4-rbac-http-results.png'), fullPage: true })
})

test('captures the live MySQL audit rows for human acceptance', async ({ page }) => {
  const audit = execFileSync('docker', [
    'compose', 'exec', '-T', 'mysql', 'mysql', '-uroot', '-pstage0-local-only', 'spectrace',
    '-e', "SELECT a.event_type, a.entity_id, a.actor_user_id, a.event_at FROM audit_event a JOIN formula_version f ON f.formula_version_id=a.entity_id WHERE f.product_id='prod_usda_1106285' AND f.version_number=(SELECT MAX(version_number) FROM formula_version WHERE product_id='prod_usda_1106285' AND lifecycle_status='RELEASED') ORDER BY a.event_at;",
  ], { encoding: 'utf8', env: { ...process.env, DOCKER_HOST: `unix://${process.env.HOME}/.docker/run/docker.sock` } })
  expect(audit).toContain('FORMULA_CREATED')
  expect(audit).toContain('FORMULA_RELEASED')
  await page.goto('about:blank')
  await page.setContent(`<main><h1>Live MySQL audit log acceptance</h1><pre>${audit.replaceAll('&', '&amp;').replaceAll('<', '&lt;')}</pre></main>`)
  await page.screenshot({ path: testArtifactPath('screenshots', 'wo-m5-audit-log-mysql.png'), fullPage: true })
})
