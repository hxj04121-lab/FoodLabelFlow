/**
 * SCRUM-84: prepare a real IN_REVIEW V1 using only supported HTTP business APIs.
 * Intentionally not imported by ordinary Playwright runs.
 * Requires an isolated, disposable database with the released spec_chocolate_v2
 * fixture installed and baseline products from the S3 golden fixture.
 */
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { dirname } from 'node:path'
import { fileURLToPath } from 'node:url'

const required = {
  SCRUM84_LIVE_WRITES: '1',
  SCRUM84_DISPOSABLE_DB: 'YES',
  SCRUM84_BOOTSTRAP_CONFIRM: 'I_UNDERSTAND_THIS_WRITES_DATA',
}
for (const [key, value] of Object.entries(required)) {
  if (process.env[key] !== value) throw new Error(`${key} must be ${value}; no writes performed`)
}
const base = process.env.SCRUM84_API_BASE_URL
if (!base) throw new Error('SCRUM84_API_BASE_URL is required; use an isolated test backend, not a shared instance')
const origin = new URL(base)
if (!['127.0.0.1', 'localhost', '[::1]'].includes(origin.hostname)) {
  throw new Error('Refusing non-loopback API base URL')
}
const target = process.env.SCRUM84_BOOTSTRAP_PRODUCT || 'prod_usda_348560'
const goldenPath = fileURLToPath(new URL('../../backend/src/test/resources/golden/s3-m2-soy-spec-v2-impact-v1.csv', import.meta.url))
const rows = readFileSync(goldenPath, 'utf8').trim().split(/\r?\n/).slice(1).map(line => {
  const fields = line.split(',')
  return { productId: fields[10], outcome: fields[9] }
})
if (rows.length !== 60 || rows.filter(r => r.outcome === 'REVIEW_REQUIRED').length !== 20 ||
    !rows.some(r => r.productId === target && r.outcome === 'REVIEW_REQUIRED')) {
  throw new Error('Golden fixture does not match the expected 60/20 S3 baseline or selected target')
}
const headers = subject => ({ 'X-Auth-Provider': 'DEV_EXTERNAL', 'X-External-Subject': subject })
async function api(method, path, subject, body, expected) {
  const response = await fetch(new URL(path, origin), {
    method, headers: { ...headers(subject), ...(body === undefined ? {} : { 'Content-Type': 'application/json' }) },
    ...(body === undefined ? {} : { body: JSON.stringify(body) }),
  })
  const text = await response.text()
  if (response.status !== expected) throw new Error(`${method} ${path}: expected ${expected}, got ${response.status}: ${text.slice(0, 1200)}`)
  try { return JSON.parse(text) } catch { throw new Error(`${path}: expected JSON: ${text.slice(0, 200)}`) }
}
const get = (path, subject = 'dev-external-admin') => api('GET', path, subject, undefined, 200)
const post = (path, subject, body, status = 201) => api('POST', path, subject, body, status)
const ruleSetVersionId = 'ruleset_us_falcpa_demo_v1'

// Read-only preflight: no partial adoption if required released input is missing.
const options = await get('/api/identity/demo-options', 'dev-external-label-officer')
if (!Array.isArray(options) || !['MAKER', 'CHECKER', 'PUBLISHER'].every(k => options.some(o => o.key === k))) {
  throw new Error('Controlled demo identities are not enabled on this isolated backend')
}
const products = new Map()
for (const row of rows) {
  const product = await get(`/api/catalog/products/${row.productId}`)
  if (!product.current_formula_version_id || !product.current_published_label_version_id) {
    throw new Error(`Missing baseline formula/label for ${row.productId}`)
  }
  products.set(row.productId, product)
}
const spec = await get('/api/catalog/specifications/spec_chocolate_v2').catch(() => null)
if (!spec || spec.lifecycle_status !== 'RELEASED') {
  throw new Error('spec_chocolate_v2 is not released')
}
console.log('Read-only preflight passed. Beginning irreversible HTTP writes on disposable DB only.')
for (const row of rows.filter(r => r.outcome !== 'EXCLUDED_NO_FINDING')) {
  const product = products.get(row.productId)
  await post(`/api/catalog/products/${row.productId}/formula-adoptions`, 'dev-external-admin', {
    sourceFormulaVersionId: product.current_formula_version_id,
    targetSpecificationVersionId: 'spec_chocolate_v2',
  })
}
const change = await post('/api/v1/change-requests', 'dev-external-change-manager', {
  changeType: 'INGREDIENT_SPEC', supplierMaterialId: 'mat_chocolate_base',
  previousSpecificationVersionId: 'spec_chocolate_v1', targetSpecificationVersionId: 'spec_chocolate_v2',
  description: 'SCRUM-84 isolated V1 to V2 browser acceptance',
})
const analysis = await post(`/api/v1/change-requests/${change.changeRequestId}/impact-analyses`,
  'dev-external-change-manager', { ruleSetVersionId })
if (analysis.relevantProductCount !== 40 || analysis.noActionCount !== 20 || analysis.reviewRequiredCount !== 20 ||
    analysis.findings?.length !== 40 || new Set(analysis.findings.map(f => f.productId)).size !== 40 ||
    !analysis.findings.every(f => rows.some(row => row.productId === f.productId && row.outcome === f.outcome))) {
  throw new Error('Actual impact results do not match the 40-finding golden scenario')
}
const finding = analysis.findings?.find(f => f.productId === target)
if (finding?.outcome !== 'REVIEW_REQUIRED' || !finding.reviewTask?.reviewTaskId) {
  throw new Error(`No REVIEW_REQUIRED task for ${target}; inspect impact analysis ${analysis.impactAnalysisId}`)
}
const taskId = finding.reviewTask.reviewTaskId
const oldId = products.get(target).current_published_label_version_id
const oldDeclarations = await get(`/api/labels/${encodeURIComponent(oldId)}/declarations`)
const declarationRows = oldDeclarations.declarations
if (!Array.isArray(declarationRows)) throw new Error('Missing old label declarations')
const allergens = new Set(declarationRows.map(d => d.allergenId))
allergens.add('all_soy')
const declarations = [...allergens].sort().map(id => ({
  allergenId: id, declarationType: 'CONTAINS',
  displayText: declarationRows.find(d => d.allergenId === id)?.displayText || `Contains ${id.slice(4)}`,
}))
const draft = await post('/api/labels/drafts', 'dev-external-label-officer', {
  productId: target, jurisdictionCode: 'US', reviewTaskId: taskId, declarations,
})
const labelId = draft.labelVersionId
const validation = await post(`/api/v1/label-versions/${encodeURIComponent(labelId)}/validation-runs`,
  'dev-external-label-officer', { ruleSetVersionId: draft.ruleSetVersionId })
if (validation.status !== 'PASSED') throw new Error(`V1 validation was ${validation.status}; task=${taskId}, label=${labelId}`)
await post(`/api/labels/${encodeURIComponent(labelId)}/review-submissions`, 'dev-external-label-officer', {}, 200)
const task = await get(`/api/review-tasks/${encodeURIComponent(taskId)}`)
const label = await get(`/api/labels/${encodeURIComponent(labelId)}`)
if (task.status !== 'IN_REVIEW' || task.targetLabelVersionId !== labelId || label.lifecycleStatus !== 'PENDING_REVIEW') {
  throw new Error(`V1 precondition mismatch: ${JSON.stringify({ task, label })}`)
}
console.log(`SCRUM84_LIVE_TASK_ID=${taskId}`)
if (process.env.SCRUM84_BOOTSTRAP_OUTPUT) {
  const output = process.env.SCRUM84_BOOTSTRAP_OUTPUT
  mkdirSync(dirname(output), { recursive: true })
  writeFileSync(output, JSON.stringify({ reviewTaskId: taskId, productId: target,
    originalPublishedLabelVersionId: oldId, changeRequestId: change.changeRequestId,
    impactAnalysisId: analysis.impactAnalysisId, analysis, draft, validation, task, declarations,
    adoptedProductCount: rows.filter(row => row.outcome !== 'EXCLUDED_NO_FINDING').length,
    recordedAt: new Date().toISOString() }, null, 2) + '\n')
}
console.log('V1 is IN_REVIEW with a real PASSED validation. Run the opt-in browser E2E against this SAME disposable backend.')
