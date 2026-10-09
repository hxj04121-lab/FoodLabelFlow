import { expect, test } from '@playwright/test'
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { execFileSync } from 'node:child_process'

// Destructive, opt-in browser E2E. Requires an isolated disposable database with
// a maker-created V1, a bound ReviewTask in IN_REVIEW, and PASSED V1 validation.
// No route mocks and no direct SQL business-state mutations.
const bootstrap = process.env.SCRUM84_BOOTSTRAP_OUTPUT
  ? JSON.parse(readFileSync(process.env.SCRUM84_BOOTSTRAP_OUTPUT, 'utf8')) : null
const taskId = process.env.SCRUM84_LIVE_TASK_ID ?? bootstrap?.reviewTaskId
const makerHeaders = {
  'X-Auth-Provider': 'DEV_EXTERNAL',
  'X-External-Subject': 'dev-external-label-officer',
}
const enabled = process.env.SCRUM84_LIVE_WRITES === '1' && process.env.SCRUM84_DISPOSABLE_DB === 'YES'
if (enabled && !taskId) throw new Error('An enabled revision run requires the exact bootstrapped task ID')

function storedEvidence(labelId: string) {
  const project = process.env.SCRUM84_COMPOSE_PROJECT
  if (!project || !/^[a-z0-9-]+$/.test(project) || !/^[a-zA-Z0-9_-]+$/.test(labelId)) {
    throw new Error('Revision qualification requires its explicit owned Compose project and a valid label ID')
  }
  const sql = `SELECT JSON_OBJECT(
    'approvals', COALESCE((SELECT JSON_ARRAYAGG(JSON_OBJECT('id', approval_record_id, 'task', review_task_id, 'decision', decision, 'actor', decided_by_user_id)) FROM approval_record WHERE label_version_id='${labelId}'), JSON_ARRAY()),
    'publications', COALESCE((SELECT JSON_ARRAYAGG(JSON_OBJECT('id', publication_record_id, 'actor', published_by_user_id)) FROM publication_record WHERE label_version_id='${labelId}'), JSON_ARRAY()),
    'auditCount', (SELECT COUNT(*) FROM audit_event WHERE entity_id='${labelId}'),
    'validationCount', (SELECT COUNT(*) FROM validation_run WHERE label_version_id='${labelId}'),
    'currentValidation', (SELECT JSON_OBJECT('id', validation_run_id, 'sequence', current_sequence, 'origin', origin, 'ruleSet', rule_set_version_id) FROM validation_current_run WHERE label_version_id='${labelId}' LIMIT 1));`
  return JSON.parse(execFileSync('docker', ['compose', '-p', project, 'exec', '-T', 'mysql', 'sh', '-c',
    'exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -D spectrace -N -B -e "$1"', '--', sql], { encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] }).trim())
}
// A write test must never run against the shared course database by accident.
// Both flags are required, even when a task ID is supplied.
test('live returned V1 to validated, approved and published V2 via browser', async ({ page, request }) => {
  test.skip(!enabled || !taskId, 'Requires SCRUM84_LIVE_WRITES=1, SCRUM84_DISPOSABLE_DB=YES and SCRUM84_LIVE_TASK_ID on a disposable database')
  test.setTimeout(180_000)
  // Read-only evidence requests use an explicitly authenticated Maker actor.
  // Browser writes continue to use the selected Checker/Maker/Publisher identity.
  const readApi = (path: string) => request.get(path, { headers: makerHeaders })
  const readTask = async () => {
    const response = await readApi(`/api/review-tasks/${encodeURIComponent(taskId!)}`)
    expect(response.status(), await response.text()).toBe(200)
    return response.json()
  }
  const originalTask = await readTask()
  expect(originalTask.status).toBe('IN_REVIEW')
  const v1Id = originalTask.targetLabelVersionId
  expect(v1Id).toBeTruthy()
  const originalResponse = await readApi(`/api/labels/${encodeURIComponent(v1Id)}`)
  expect(originalResponse.status()).toBe(200)
  const original = await originalResponse.json()
  expect(original.lifecycleStatus).toBe('PENDING_REVIEW')
  expect(original.createdByUserId).toBe('user_label_officer')
  const originalDeclarationsResponse = await readApi(`/api/labels/${encodeURIComponent(v1Id)}/declarations`)
  expect(originalDeclarationsResponse.status()).toBe(200)
  const originalDeclarations = await originalDeclarationsResponse.json()
  const initialPublishedResponse = await readApi(`/api/labels/${originalTask.currentLabelVersionId}`)
  expect(initialPublishedResponse.status()).toBe(200)
  const initialPublished = await initialPublishedResponse.json()
  expect(initialPublished.lifecycleStatus).toBe('PUBLISHED')

  // Use the real controlled demo switch; browser commands go through Vite proxy.
  await page.goto(`/labels?productId=${encodeURIComponent(original.productId)}&reviewTaskId=${encodeURIComponent(taskId!)}`)
  await page.getByRole('combobox', { name: 'Demo identity' }).selectOption('CHECKER')
  await expect(page.getByRole('region', { name: 'Connected identity' })).toContainText('Demo Approver')
  const workflow = page.getByRole('region', { name: 'Label review and publication' })
  await expect(workflow).toContainText(v1Id)
  await workflow.getByRole('checkbox', { name: 'Use the connected identity for review and publication' }).check()
  await workflow.getByRole('button', { name: 'Request changes' }).click()
  await expect(workflow).toContainText(`Request changes confirmed for ${v1Id}`)
  const returnedHistory = storedEvidence(v1Id)
  expect(returnedHistory.approvals).toEqual([expect.objectContaining({ task: taskId,
    decision: 'REQUEST_CHANGES', actor: 'user_approver' })])

  await page.getByRole('combobox', { name: 'Demo identity' }).selectOption('MAKER')
  await expect(page.getByRole('region', { name: 'Connected identity' })).toContainText('Demo Label Officer')
  const editor = page.getByRole('region', { name: 'Structured label declarations' })
  await expect(editor.getByRole('button', { name: 'Save changes as new version' })).toBeEnabled()
  const correctedText = 'Contains Soy (corrected declaration)'
  await editor.getByRole('textbox', { name: 'Soy declaration text' }).fill(correctedText)
  await editor.getByRole('button', { name: 'Save changes as new version' }).click()
  await expect(page.getByText(/Run validation for this exact version before resubmitting/)).toBeVisible()
  const revisedTask = await readTask()
  expect(revisedTask.reviewTaskId).toBe(taskId)
  const v2Id = revisedTask.targetLabelVersionId
  expect(v2Id).toBeTruthy()
  expect(v2Id).not.toBe(v1Id)
  const revisedLabelResponse = await readApi(`/api/labels/${encodeURIComponent(v2Id)}`)
  expect(revisedLabelResponse.status()).toBe(200)
  const revisedLabel = await revisedLabelResponse.json()
  expect(revisedLabel.versionNumber).toBe(original.versionNumber + 1)
  expect(revisedLabel.createdByUserId).toBe(original.createdByUserId)
  expect(revisedTask.draftLabelVersionId).toBe(v2Id)
  expect(revisedTask.status).toBe('OPEN')
  expect(revisedTask.decision).toBeNull()
  const revisedDeclarationsResponse = await readApi(`/api/labels/${v2Id}/declarations`)
  expect(revisedDeclarationsResponse.status()).toBe(200)
  const revisedDeclarations = await revisedDeclarationsResponse.json()
  expect(revisedDeclarations.declarations).toContainEqual(expect.objectContaining({
    allergenId: 'all_soy', displayText: correctedText, declarationSource: 'USER_ENTERED',
  }))
  await expect(page.getByRole('region', { name: 'Validation run results' })).toHaveCount(0)
  const beforeValidation = storedEvidence(v2Id)
  expect(beforeValidation.validationCount).toBe(0)
  await workflow.getByRole('checkbox', { name: 'Use the connected identity for review and publication' }).check()
  await expect(workflow.getByRole('button', { name: 'Submit for review' })).toBeDisabled()
  const blockedSubmit = await request.post(`/api/labels/${v2Id}/review-submissions`, { headers: makerHeaders, data: {} })
  expect(blockedSubmit.status()).toBe(409)
  expect(storedEvidence(v2Id)).toEqual(beforeValidation)
  const staleSubmit = await request.post(`/api/labels/${v1Id}/review-submissions`, { headers: makerHeaders, data: {} })
  expect(staleSubmit.status()).toBe(409)
  expect(storedEvidence(v1Id)).toEqual(returnedHistory)

  const oldVersion = await readApi(`/api/labels/${encodeURIComponent(v1Id)}`)
  expect(oldVersion.status()).toBe(200)
  expect((await oldVersion.json()).labelVersionId).toBe(v1Id)
  const oldDeclarations = await readApi(`/api/labels/${encodeURIComponent(v1Id)}/declarations`)
  expect(oldDeclarations.status()).toBe(200)
  expect(await oldDeclarations.json()).toEqual(originalDeclarations)

  const validation = page.getByRole('button', { name: 'Run validation' })
  await page.getByRole('checkbox', { name: 'Use the connected identity to validate this exact version' }).check()
  await validation.click()
  await expect(page.getByText(/completed with PASSED/)).toBeVisible()
  const v2RunId = await page.getByLabel('Existing validation run ID').inputValue()
  const v2RunResponse = await readApi(`/api/v1/validation-runs/${v2RunId}`)
  expect(v2RunResponse.status()).toBe(200)
  const v2Run = await v2RunResponse.json()
  expect(v2Run).toMatchObject({ labelVersionId: v2Id, ruleSetVersionId: revisedLabel.ruleSetVersionId, status: 'PASSED' })
  if (bootstrap) expect(v2RunId).not.toBe(bootstrap.validation.validationRunId)
  await workflow.getByRole('checkbox', { name: 'Use the connected identity for review and publication' }).check()
  await workflow.getByRole('button', { name: 'Submit for review' }).click()
  await expect(workflow).toContainText(`Review submission confirmed for ${v2Id}`)
  await expect(workflow.getByRole('button', { name: 'Approve label' })).toBeDisabled()
  const submittedEvidence = storedEvidence(v2Id)
  const deniedApproval = await request.post(`/api/labels/${v2Id}/review-decisions`, {
    headers: makerHeaders, data: { decision: 'APPROVE', comments: 'Maker has no approval permission' },
  })
  expect(deniedApproval.status()).toBe(403)
  expect(storedEvidence(v2Id)).toEqual(submittedEvidence)

  await page.getByRole('combobox', { name: 'Demo identity' }).selectOption('CHECKER')
  await expect(page.getByRole('region', { name: 'Connected identity' })).toContainText('Demo Approver')
  await workflow.getByRole('checkbox', { name: 'Use the connected identity for review and publication' }).check()
  await workflow.getByRole('button', { name: 'Approve label' }).click()
  await expect(workflow).toContainText(`Approve label confirmed for ${v2Id}`)

  await page.getByRole('combobox', { name: 'Demo identity' }).selectOption('PUBLISHER')
  await expect(page.getByRole('region', { name: 'Connected identity' })).toContainText('Demo Publisher')
  await workflow.getByRole('checkbox', { name: 'Use the connected identity for review and publication' }).check()
  await workflow.getByRole('button', { name: 'Publish label' }).click()
  await expect(workflow).toContainText(`Publication confirmed for ${v2Id}`)
  const finalTask = await readTask()
  expect(finalTask.targetLabelVersionId).toBe(v2Id)
  expect(finalTask.status).toBe('CLOSED')
  const publishedResponse = await readApi(`/api/labels/${encodeURIComponent(v2Id)}`)
  expect(publishedResponse.status()).toBe(200)
  const published = await publishedResponse.json()
  expect(published.lifecycleStatus).toBe('PUBLISHED')
  expect(published.isCurrentPublished).toBe('Y')
  const publishedHistory = storedEvidence(v2Id)
  expect(publishedHistory.approvals).toEqual([expect.objectContaining({ task: taskId,
    decision: 'APPROVE', actor: 'user_approver' })])
  expect(publishedHistory.publications).toEqual([expect.objectContaining({ actor: 'user_publisher' })])
  expect(publishedHistory.auditCount).toBeGreaterThan(submittedEvidence.auditCount)
  expect(publishedHistory.validationCount).toBe(1)
  expect(publishedHistory.currentValidation).toMatchObject({ id: v2RunId,
    ruleSet: revisedLabel.ruleSetVersionId, origin: 'RECORDED', sequence: 1 })
  expect(storedEvidence(v1Id)).toEqual(returnedHistory)
  const productResponse = await readApi(`/api/catalog/products/${original.productId}`)
  expect(productResponse.status()).toBe(200)
  const product = await productResponse.json()
  expect(product.current_published_label_version_id).toBe(v2Id)
  const supersededResponse = await readApi(`/api/labels/${originalTask.currentLabelVersionId}`)
  expect(supersededResponse.status()).toBe(200)
  const superseded = await supersededResponse.json()
  expect(superseded.lifecycleStatus).toBe('SUPERSEDED')
  expect({ ...superseded, lifecycleStatus: initialPublished.lifecycleStatus, isCurrentPublished: initialPublished.isCurrentPublished }).toEqual(initialPublished)
  await page.reload()
  await expect(workflow).toContainText(v2Id)
  await expect(page.getByRole('region', { name: 'Label draft details' })).toContainText('PUBLISHED')
  await expect(page.getByRole('region', { name: 'Connected identity' })).toContainText('Demo Publisher')
  const oldAfterPublish = await readApi(`/api/labels/${encodeURIComponent(v1Id)}`)
  expect(oldAfterPublish.status()).toBe(200)
  const returnedHistorical = await oldAfterPublish.json()
  // REQUEST_CHANGES legitimately transitions the returned snapshot to DRAFT.
  // Its immutable business content must remain byte-for-byte equivalent.
  expect(returnedHistorical.lifecycleStatus).toBe('DRAFT')
  expect({ ...returnedHistorical, lifecycleStatus: original.lifecycleStatus }).toEqual(original)
  const oldDeclarationsAfterPublish = await readApi(`/api/labels/${encodeURIComponent(v1Id)}/declarations`)
  expect(oldDeclarationsAfterPublish.status()).toBe(200)
  expect(await oldDeclarationsAfterPublish.json()).toEqual(originalDeclarations)
  if (bootstrap) {
    const oldRun = await readApi(`/api/v1/validation-runs/${bootstrap.validation.validationRunId}`)
    expect(oldRun.status()).toBe(200)
    expect(await oldRun.json()).toEqual(bootstrap.validation)
  }
  const evidenceDir = process.env.S3_EVIDENCE_DIR ?? 'test-results'
  mkdirSync(evidenceDir, { recursive: true })
  writeFileSync(resolve(evidenceDir, 'returned-revision-observations.json'), JSON.stringify({
    taskId, v1: original, v1Declarations: originalDeclarations, returnedHistory,
    v2: published, v2Declarations: revisedDeclarations, v2Run, publishedHistory, product, finalTask,
    denials: { missingValidation: blockedSubmit.status(), staleVersion: staleSubmit.status(),
      makerMissingApprovalPermission: deniedApproval.status() },
    identityScope: 'Maker ACL denial is distinct from the permitted-creator maker-checker policy negative in the existing S3 suite.',
    recordedAt: new Date().toISOString(),
  }, null, 2) + '\n')
  await page.screenshot({ path: resolve(evidenceDir, 'returned-revision-published.png'), fullPage: true })
})
