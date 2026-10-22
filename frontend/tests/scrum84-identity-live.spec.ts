import { expect, test, type Page } from '@playwright/test'
import { mkdirSync, writeFileSync } from 'node:fs'
import { resolve } from 'node:path'

const PRODUCT_ID = 'prod_usda_1118468'

async function switchTo(page: Page, key: string, subject: string) {
  const selector = page.getByRole('combobox', { name: 'Demo identity' })
  const identityResponsePromise = page.waitForResponse(response => {
    const url = new URL(response.url())
    return url.pathname === '/api/identity/current' && response.request().method() === 'GET'
      && response.status() === 200
  })
  await selector.selectOption(key)
  const identityResponse = await identityResponsePromise
  const headers = identityResponse.request().headers()
  expect(headers['x-auth-provider']).toBe('DEV_EXTERNAL')
  expect(headers['x-external-subject']).toBe(subject)
  const actor = await identityResponse.json() as { userId: string; displayName: string; permissions: string[] }
  await expect(page.locator('.identity-status')).toHaveText(actor.displayName)
  await expect(page.locator('.user-card strong')).toHaveText(actor.displayName)
  return actor
}

// Real HTTP/browser smoke against an explicitly isolated demo stack.
// The identity/privacy case changes only local UI state and performs reads;
// it never creates a business record or changes an account/permission row.
test('live demo identity switching resets unsaved label edits and reauthorizes audit reads', async ({
  page, request,
}, testInfo) => {
  test.skip(process.env.LIVE_SCRUM84_IDENTITY !== '1', 'Requires an isolated demo-enabled backend')
  const optionsResponse = await request.get('/api/identity/demo-options')
  expect(optionsResponse.status()).toBe(200)
  const options = await optionsResponse.json() as Array<{
    key: string
    subject: string
    actor: { userId: string; displayName: string; permissions: string[] }
  }>
  const expected = [
    ['MAKER', ['LABEL.CREATE', 'LABEL.VALIDATE', 'LABEL.SUBMIT_REVIEW']],
    ['CHECKER', ['LABEL.APPROVE', 'LABEL.REQUEST_CHANGES']],
    ['PUBLISHER', ['LABEL.PUBLISH']],
    ['DATA_ADMIN', ['DATA.MAINTAIN', 'FORMULA.RELEASE', 'AUDIT.READ']],
    ['CHANGE_MANAGER', ['CHANGE_REQUEST.CREATE', 'IMPACT.RUN', 'AUDIT.READ']],
  ] as const
  expect(options.map(option => option.key).sort()).toEqual(expected.map(([key]) => key).sort())

  const browserContext = page.context()
  const draftWriteRequests: string[] = []
  page.on('request', browserRequest => {
    const url = new URL(browserRequest.url())
    if (browserRequest.method() === 'POST' && url.pathname === '/api/labels/drafts') {
      draftWriteRequests.push(url.pathname)
    }
  })

  const initialIdentityResponsePromise = page.waitForResponse(response => {
    const url = new URL(response.url())
    return url.pathname === '/api/identity/current' && response.request().method() === 'GET'
      && response.status() === 200
  })
  await page.goto('/reviews')
  const selector = page.getByRole('combobox', { name: 'Demo identity' })
  await expect(selector).toBeVisible()
  const initialIdentityResponse = await initialIdentityResponsePromise
  const initialIdentityHeaders = initialIdentityResponse.request().headers()
  expect(initialIdentityHeaders['x-auth-provider']).toBe('DEV_EXTERNAL')
  expect(initialIdentityHeaders['x-external-subject']).toBe('dev-external-label-officer')
  const initialMaker = await initialIdentityResponse.json() as {
    userId: string; displayName: string; permissions: string[]
  }
  const makerOption = options.find(option => option.key === 'MAKER')!
  expect(initialMaker.userId).toBe(makerOption.actor.userId)
  await expect(selector).toHaveValue('MAKER')
  await expect(page.locator('.identity-status')).toHaveText(initialMaker.displayName)
  for (const [key, permissions] of expected) {
    const option = options.find(item => item.key === key)!
    for (const permission of permissions) expect(option.actor.permissions).toContain(permission)
    const actor = key === 'MAKER' ? initialMaker : await switchTo(page, key, option.subject)
    expect(actor.userId).toBe(option.actor.userId)
    for (const permission of permissions) expect(actor.permissions).toContain(permission)
    expect(page.context()).toBe(browserContext)
  }

  const maker = await switchTo(page, 'MAKER', makerOption.subject)
  expect(maker.permissions).toContain('LABEL.CREATE')
  await page.goto(`/labels?productId=${PRODUCT_ID}`)
  await expect(page.locator('.user-card strong')).toHaveText(maker.displayName)
  const soy = page.getByRole('checkbox', { name: 'Declare Soy (SOY)', exact: true })
  await expect(soy).toBeEnabled()
  await expect(soy).not.toBeChecked()
  const createConsent = page.getByRole('checkbox', { name: 'Use the connected identity to create this draft' })
  await expect(createConsent).toBeEnabled()
  await createConsent.check()
  const createDraft = page.getByRole('button', { name: 'Create label draft', exact: true })
  await expect(createDraft).toBeEnabled()
  await soy.check()
  await expect(soy).toBeChecked()

  const checkerOption = options.find(option => option.key === 'CHECKER')!
  const checker = await switchTo(page, 'CHECKER', checkerOption.subject)
  expect(checker.permissions).toContain('LABEL.APPROVE')
  expect(checker.permissions).not.toContain('LABEL.CREATE')
  expect(checker.permissions).not.toContain('AUDIT.READ')
  expect(page.context()).toBe(browserContext)
  expect(new URL(page.url()).pathname).toBe('/labels')
  expect(new URL(page.url()).searchParams.get('productId')).toBe(PRODUCT_ID)
  const soyAsChecker = page.getByRole('checkbox', { name: 'Declare Soy (SOY)', exact: true })
  await expect(soyAsChecker).not.toBeChecked()
  const consentAsChecker = page.getByRole('checkbox', { name: 'Use the connected identity to create this draft' })
  await expect(consentAsChecker).toBeDisabled()
  await expect(consentAsChecker).not.toBeChecked()
  await expect(page.getByRole('button', { name: 'Create label draft', exact: true })).toBeDisabled()

  const deniedHistory = await page.evaluate(async ({ productId, subject }) => {
    const response = await fetch(`/api/audit/products/${encodeURIComponent(productId)}/label-history`, {
      headers: { 'X-Auth-Provider': 'DEV_EXTERNAL', 'X-External-Subject': subject },
    })
    return { status: response.status, body: await response.json() as { code?: string } }
  }, { productId: PRODUCT_ID, subject: checkerOption.subject })
  expect(deniedHistory.status).toBe(403)
  expect(deniedHistory.body.code).toBe('AUTHORIZATION_DENIED')

  const makerAgain = await switchTo(page, 'MAKER', makerOption.subject)
  expect(makerAgain.userId).toBe(maker.userId)
  expect(makerAgain.permissions).toContain('LABEL.CREATE')
  expect(page.context()).toBe(browserContext)
  expect(new URL(page.url()).pathname).toBe('/labels')
  expect(new URL(page.url()).searchParams.get('productId')).toBe(PRODUCT_ID)
  const soyAfterReturn = page.getByRole('checkbox', { name: 'Declare Soy (SOY)', exact: true })
  await expect(soyAfterReturn).not.toBeChecked()
  const consentAfterReturn = page.getByRole('checkbox', { name: 'Use the connected identity to create this draft' })
  await expect(consentAfterReturn).not.toBeChecked()
  await expect(page.getByRole('button', { name: 'Create label draft', exact: true })).toBeDisabled()
  expect(draftWriteRequests).toEqual([])

  const evidence = {
    browserContextReused: true,
    samePageRoutePreserved: '/labels?productId=' + PRODUCT_ID,
    firstActor: { userId: maker.userId, unsavedSoySelected: true, draftWriteRequestsBeforeSwitch: 0 },
    switchedActor: { userId: checker.userId, labelCreate: checker.permissions.includes('LABEL.CREATE'),
      auditRead: checker.permissions.includes('AUDIT.READ'), historyHttpStatus: deniedHistory.status,
      historyErrorCode: deniedHistory.body.code },
    switchedBackActor: { userId: makerAgain.userId, unsavedSoyStillSelected: await soyAfterReturn.isChecked(),
      createConsentStillChecked: await consentAfterReturn.isChecked() },
    draftWriteRequestsAfterAllSwitches: draftWriteRequests.length,
  }
  const evidenceDir = process.env.S3_EVIDENCE_DIR
  if (evidenceDir) {
    mkdirSync(evidenceDir, { recursive: true })
    writeFileSync(resolve(evidenceDir, 'identity-switch-isolation.json'), JSON.stringify(evidence, null, 2) + '\n')
  }
  await testInfo.attach('live-identity-switch-isolation', {
    body: JSON.stringify(evidence), contentType: 'application/json',
  })
})
