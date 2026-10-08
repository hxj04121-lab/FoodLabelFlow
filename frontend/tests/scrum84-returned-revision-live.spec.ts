import { expect, test } from '@playwright/test'

// Destructive, opt-in browser E2E. Requires an isolated disposable database with
// a maker-created V1, a bound ReviewTask in IN_REVIEW, and PASSED V1 validation.
// No route mocks and no direct SQL business-state mutations.
const taskId = process.env.SCRUM84_LIVE_TASK_ID
const makerHeaders = {
  'X-Auth-Provider': 'DEV_EXTERNAL',
  'X-External-Subject': 'dev-external-label-officer',
}
const enabled = process.env.SCRUM84_LIVE_WRITES === '1' && process.env.SCRUM84_DISPOSABLE_DB === 'YES'
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

  // Use the real controlled demo switch; browser commands go through Vite proxy.
  await page.goto(`/labels?productId=${encodeURIComponent(original.productId)}&reviewTaskId=${encodeURIComponent(taskId!)}`)
  await page.getByRole('combobox', { name: 'Demo identity' }).selectOption('CHECKER')
  await expect(page.getByRole('region', { name: 'Connected identity' })).toContainText('Demo Approver')
  const workflow = page.getByRole('region', { name: 'Label review and publication' })
  await expect(workflow).toContainText(v1Id)
  await workflow.getByRole('checkbox', { name: 'Use the connected identity for review and publication' }).check()
  await workflow.getByRole('button', { name: 'Request changes' }).click()
  await expect(workflow).toContainText(`Request changes confirmed for ${v1Id}`)

  await page.getByRole('combobox', { name: 'Demo identity' }).selectOption('MAKER')
  await expect(page.getByRole('region', { name: 'Connected identity' })).toContainText('Demo Label Officer')
  const editor = page.getByRole('region', { name: 'Structured label declarations' })
  await expect(editor.getByRole('button', { name: 'Save changes as new version' })).toBeEnabled()
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
  await workflow.getByRole('checkbox', { name: 'Use the connected identity for review and publication' }).check()
  await workflow.getByRole('button', { name: 'Submit for review' }).click()
  await expect(workflow).toContainText(`Review submission confirmed for ${v2Id}`)

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
  // Server-side audit, approval and validation persistence remain covered by
  // ReturnedDraftRevisionHttpMySqlTest; do not fabricate browser assertions.
})
