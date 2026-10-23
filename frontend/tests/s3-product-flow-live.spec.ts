import { expect, test, type APIRequestContext, type BrowserContext, type Page } from '@playwright/test'
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { resolve } from 'node:path'

const ruleSetVersionId = 'ruleset_us_falcpa_demo_v1'
const testPrefix = process.env.S3_TEST_PREFIX ?? 'S3-SOY'
const subjectHeaders = (subject: string) => ({
  'X-Auth-Provider': 'DEV_EXTERNAL',
  'X-External-Subject': subject,
})
const golden = readFileSync(new URL('../../backend/src/test/resources/golden/s3-m2-soy-spec-v2-impact-v1.csv', import.meta.url), 'utf8')
  .trim().split(/\r?\n/).slice(1).map((line) => {
    const columns = line.split(',')
    return { productId: columns[10], outcome: columns[9] }
  })

async function json(request: APIRequestContext, path: string) {
  const subject = path.startsWith('/api/v1/impact-analyses/') ? 'dev-external-change-manager'
    : path.startsWith('/api/catalog/') ? 'dev-external-admin' : 'dev-external-label-officer'
  const response = await request.get(path, { headers: subjectHeaders(subject) })
  expect(response.status(), `${path}: ${await response.text()}`).toBe(200)
  return response.json()
}

// Context reads preserve the same trusted fixture actor as the command. They do
// not substitute the helper's ordinary officer/admin headers.
async function actorJson(request: APIRequestContext, path: string) {
  const response = await request.get(path)
  expect(response.status(), `${path}: ${await response.text()}`).toBe(200)
  return response.json()
}

async function post(request: APIRequestContext, path: string, data: object, subject: string, status: number) {
  const response = await request.post(path, { data, headers: subjectHeaders(subject) })
  expect(response.status(), `${path}: ${await response.text()}`).toBe(status)
  return response.json()
}

async function loadActorLabel(context: BrowserContext, path: string, labelId: string): Promise<Page> {
  const actorPage = await context.newPage()
  await actorPage.goto(path)
  await expect(actorPage.getByRole('region', { name: 'Label draft details' })).toContainText(labelId)
  await actorPage.getByRole('checkbox', { name: 'Use the connected identity for review and publication' }).check()
  return actorPage
}

test('captures the live S3 SOY flow with twenty independently approved publications and preserved history', async ({
  request, browser,
}, testInfo) => {
  test.skip(process.env.LIVE_S3_FLOW !== '1', 'Requires a fresh isolated baseline catalog and real local services')
  test.setTimeout(300_000)
  const compoundMakerSubject = process.env.S3_COMPOUND_MAKER_SUBJECT ?? 'dev-external-label-officer'
  const compoundMakerAliasSubject = process.env.S3_COMPOUND_MAKER_ALIAS_SUBJECT ?? 'dev-external-label-officer'
  const compoundMakerUserId = process.env.S3_COMPOUND_MAKER_USER_ID ?? 'user_label_officer'
  const hasCompoundMakerFixture = Boolean(process.env.S3_COMPOUND_MAKER_USER_ID)
  const evidenceDir = resolve(process.env.S3_EVIDENCE_DIR || 'test-results')
  mkdirSync(evidenceDir, { recursive: true })
  expect(golden).toHaveLength(60)
  expect(new Set(golden.map((row) => row.productId)).size).toBe(60)
  const before = new Map<string, { product: any; formula: any; label: any; declarations: any }>()
  const adopted = new Map<string, string>()
  const published = new Map<string, string>()
  const observations: object[] = []
  let reachedStage = 'initial catalog and actual formula adoptions'
  let lastAttempt: Record<string, unknown> = {}
  let releaseHeldCurrentFormula: (() => void) | null = null
  const makerContext = await browser.newContext({ baseURL: testInfo.project.use.baseURL,
    extraHTTPHeaders: subjectHeaders('dev-external-label-officer') })
  const officerPage = await makerContext.newPage()
  const compoundMakerContext = await browser.newContext({ baseURL: testInfo.project.use.baseURL,
    extraHTTPHeaders: subjectHeaders(compoundMakerSubject) })
  const compoundMakerPage = await compoundMakerContext.newPage()
  const compoundAliasContext = await browser.newContext({ baseURL: testInfo.project.use.baseURL,
    extraHTTPHeaders: subjectHeaders(compoundMakerAliasSubject) })
  const adminContext = await browser.newContext({ baseURL: testInfo.project.use.baseURL,
    extraHTTPHeaders: subjectHeaders('dev-external-admin') })
  const changeContext = await browser.newContext({ baseURL: testInfo.project.use.baseURL,
    extraHTTPHeaders: subjectHeaders('dev-external-change-manager') })
  const qaContext = await browser.newContext({ baseURL: testInfo.project.use.baseURL,
    extraHTTPHeaders: subjectHeaders('dev-external-qa-approver') })
  const publisherContext = await browser.newContext({ baseURL: testInfo.project.use.baseURL,
    extraHTTPHeaders: subjectHeaders('dev-external-publisher') })
  const auditorContext = await browser.newContext({ baseURL: testInfo.project.use.baseURL,
    extraHTTPHeaders: subjectHeaders('dev-external-auditor') })
  try {
    const catalogAllergens = await json(request, '/api/v1/allergens?jurisdictionCode=US')
    const compoundMakerIdentity = await actorJson(compoundMakerContext.request, '/api/identity/current')
    expect(compoundMakerIdentity.userId).toBe(compoundMakerUserId)
    for (const permission of ['LABEL.CREATE', 'LABEL.VALIDATE', 'LABEL.SUBMIT_REVIEW']) {
      expect(compoundMakerIdentity.permissions).toContain(permission)
    }
    if (hasCompoundMakerFixture) expect(compoundMakerIdentity.permissions).toContain('LABEL.APPROVE')
    else expect(compoundMakerIdentity.permissions).not.toContain('LABEL.APPROVE')
    const compoundAliasIdentity = await actorJson(compoundAliasContext.request, '/api/identity/current')
    expect(compoundAliasIdentity).toEqual(compoundMakerIdentity)
    const adminIdentityResponse = await adminContext.request.get('/api/identity/current')
    expect(adminIdentityResponse.status()).toBe(200)
    const adminIdentity = await adminIdentityResponse.json()
    expect(adminIdentity.userId).toBe('user_admin')
    expect(adminIdentity.permissions).not.toContain('LABEL.CREATE')
    expect(adminIdentity.permissions).not.toContain('LABEL.APPROVE')
    for (const row of golden) {
      const product = await json(request, `/api/catalog/products/${row.productId}`)
      before.set(row.productId, {
        product,
        formula: await json(request, `/api/catalog/formulas/${product.current_formula_version_id}`),
        label: await json(request, `/api/labels/${product.current_published_label_version_id}`),
        declarations: await json(request, `/api/labels/${product.current_published_label_version_id}/declarations`),
      })
    }
    reachedStage = 'actual supplier specification create and release UI'
    const specificationPage = await adminContext.newPage()
    await specificationPage.goto('/materials')
    await specificationPage.getByRole('button', { name: 'Create supplier specification' }).click()
    await specificationPage.getByLabel('Supplier material *').selectOption('mat_chocolate_base')
    await expect(specificationPage.getByLabel('Ingredient *').first()).toHaveValue('ing_cocoa')
    await expect(specificationPage.getByLabel('Ingredient *').nth(1)).toHaveValue('ing_sugar')
    await specificationPage.getByRole('button', { name: 'Add ingredient' }).click()
    await specificationPage.getByLabel('Ingredient *').nth(2).selectOption('ing_soy_lecithin')
    await specificationPage.getByLabel('Supplier wording *').nth(2).fill('Soy lecithin')
    await specificationPage.getByLabel('Matching evidence *').nth(2).fill(`${testPrefix} supplier specification evidence`)
    const createSpecificationResponse = specificationPage.waitForResponse(response => response.request().method() === 'POST'
      && new URL(response.url()).pathname === '/api/catalog/specifications')
    await specificationPage.getByRole('button', { name: 'Save specification draft' }).click()
    const createdSpecification = await createSpecificationResponse
    expect(createdSpecification.status()).toBe(201)
    const createdSpec = await createdSpecification.json()
    const targetSpecificationVersionId = createdSpec.specification_version_id as string
    expect(createdSpec).toMatchObject({ supplier_material_id: 'mat_chocolate_base', version_number: 2, lifecycle_status: 'DRAFT' })
    expect(createdSpec.components.map((component: any) => component.ingredient_id)).toContain('ing_soy_lecithin')
    const releaseSpecificationResponse = specificationPage.waitForResponse(response => response.request().method() === 'POST'
      && new URL(response.url()).pathname === `/api/catalog/specifications/${targetSpecificationVersionId}/release`)
    await specificationPage.getByRole('button', { name: 'Release specification' }).click()
    expect((await releaseSpecificationResponse).status()).toBe(200)
    await expect(specificationPage.getByRole('heading', { name: 'Specification V2 · RELEASED' })).toBeVisible()
    const releasedSpecification = await json(request, `/api/catalog/specifications/${targetSpecificationVersionId}`)
    expect(releasedSpecification).toMatchObject({
      specification_version_id: targetSpecificationVersionId, lifecycle_status: 'RELEASED', supplier_material_id: 'mat_chocolate_base',
    })
    expect(releasedSpecification.components.map((component: any) => component.ingredient_id)).toContain('ing_soy_lecithin')
    await specificationPage.screenshot({ path: resolve(evidenceDir, 's3-live-specification-released.png'), fullPage: true })
    // Adoption is a business action in the actual Data Admin browser flow.
    // The harness only observes the response and rereads the resulting catalog;
    // it does not POST adoption requests outside the UI.
    reachedStage = 'actual batch adoption UI'
    const adoptionPage = await adminContext.newPage()
    await adoptionPage.goto('/impact')
    const adoptionPanel = adoptionPage.getByRole('region', { name: 'Batch specification adoption' })
    await expect(adoptionPanel).toBeVisible()
    await adoptionPanel.getByLabel('Released specification').selectOption(targetSpecificationVersionId)
    const adoptionPromise = adoptionPage.waitForResponse((response) => response.request().method() === 'POST'
      && new URL(response.url()).pathname === `/api/catalog/specifications/${targetSpecificationVersionId}/formula-adoptions`)
    await adoptionPanel.getByRole('button', { name: 'Adopt for current products', exact: true }).click()
    const adoptionResponse = await adoptionPromise
    expect(adoptionResponse.status()).toBe(200)
    const adoptionSummary = await adoptionResponse.json()
    expect(adoptionSummary).toMatchObject({
      targetSpecificationVersionId, candidateCount: 40, adoptedCount: 40, skippedCount: 0, failedCount: 0,
    })
    await expect(adoptionPanel).toContainText('40 adopted, 0 skipped, 0 failed (40 candidates).')
    await expect(adoptionPanel.locator('li')).toHaveCount(40)
    for (const row of golden) {
      const product = await json(request, `/api/catalog/products/${row.productId}`)
      const original = before.get(row.productId)!
      if (product.current_formula_version_id !== original.product.current_formula_version_id) {
        expect(product.current_formula_version_id).not.toBe(original.product.current_formula_version_id)
        adopted.set(row.productId, product.current_formula_version_id)
      } else {
        expect(row.outcome).toBe('EXCLUDED_NO_FINDING')
      }
    }
    expect(adopted.size).toBe(40)
    reachedStage = 'actual impact UI creation and analysis'
    const impactPage = await changeContext.newPage()
    await impactPage.goto('/impact')
    await impactPage.getByText('Create a specification change request', { exact: true }).click()
    await impactPage.getByLabel('Supplier material', { exact: true }).selectOption('mat_chocolate_base')
    await impactPage.getByLabel('Previous specification', { exact: true }).selectOption('spec_chocolate_v1')
    await impactPage.getByLabel('Target specification', { exact: true }).selectOption(targetSpecificationVersionId)
    await impactPage.getByLabel('Change description').fill(
      `${testPrefix}: actual adoption, declarations, evaluator, independent approval and publication`)
    await impactPage.getByRole('checkbox', { name: 'Use the connected identity for impact writes' }).check()
    const changePromise = impactPage.waitForResponse((response) => response.request().method() === 'POST'
      && new URL(response.url()).pathname === '/api/v1/change-requests')
    await impactPage.getByRole('button', { name: 'Create change request', exact: true }).click()
    const changeResponse = await changePromise
    expect(changeResponse.status()).toBe(201)
    const change = await changeResponse.json()
    const trigger = `/api/v1/change-requests/${change.changeRequestId}/impact-analyses`
    await expect(impactPage.getByLabel('Change request', { exact: true })).toHaveValue(change.changeRequestId)
    await impactPage.getByRole('checkbox', { name: 'Use the connected identity for impact writes' }).check()
    const impactPromise = impactPage.waitForResponse((response) => response.request().method() === 'POST'
      && new URL(response.url()).pathname === trigger)
    await impactPage.getByRole('button', { name: 'Run impact analysis', exact: true }).click()
    const impactResponse = await impactPromise
    expect(impactResponse.status()).toBe(201)
    const analysis = await impactResponse.json()
    expect(analysis.relevantProductCount).toBe(40)
    expect(analysis.noActionCount).toBe(20)
    expect(analysis.reviewRequiredCount).toBe(20)
    expect(analysis.findings).toHaveLength(40)
    const renderedResults = impactPage.getByRole('region', { name: 'Analysis results' })
    await expect(renderedResults.locator('article')).toHaveCount(40)
    await expect(renderedResults.getByRole('link', { name: 'Prepare replacement label', exact: true })).toHaveCount(20)
    await renderedResults.screenshot({ path: resolve(evidenceDir, 's3-live-impact-golden.png') })
    expect(Object.fromEntries(analysis.findings.map((finding: any) => [finding.productId, finding.outcome])))
      .toEqual(Object.fromEntries(golden.filter((row) => row.outcome !== 'EXCLUDED_NO_FINDING')
        .map((row) => [row.productId, row.outcome])))
    const replay = await post(request, trigger, { ruleSetVersionId }, 'dev-external-change-manager', 200)
    expect(replay).toEqual(analysis)
    expect(await json(request, `/api/v1/impact-analyses/${analysis.impactAnalysisId}`)).toEqual(analysis)

    for (const finding of analysis.findings) {
      expect(finding.currentLabelVersionId).toBe(before.get(finding.productId)!.label.labelVersionId)
      expect(finding.currentFormulaVersionId).toBe(before.get(finding.productId)!.formula.formula_version_id)
      expect(finding.proposedFormulaVersionId).toBe(adopted.get(finding.productId))
      if (finding.outcome === 'NO_ACTION') {
        expect(finding.reviewTask).toBeUndefined()
        expect(finding.missingAllergenCodes).toEqual([])
        continue
      }
      expect(finding.missingAllergenCodes).toEqual(['SOY'])
      const isCompoundMaker = hasCompoundMakerFixture && published.size === 0
      const page = isCompoundMaker ? compoundMakerPage : officerPage
      const expectedCreator = isCompoundMaker ? compoundMakerUserId : 'user_label_officer'
      const expectedMakerSubject = isCompoundMaker ? compoundMakerSubject : 'dev-external-label-officer'
      const expectedAllergenIds = [...new Set<string>([
        ...before.get(finding.productId)!.declarations.declarations.map((declaration: any) => declaration.allergenId),
        'all_soy',
      ])].sort()
      const reviewTaskId = finding.reviewTask.reviewTaskId
      lastAttempt = { productId: finding.productId, reviewTaskId }
      reachedStage = 'actual first-creation declaration UI'
      const findingCard = renderedResults.locator('article').filter({ hasText: finding.productId })
      const initialPath = await findingCard.getByRole('link', { name: 'Prepare replacement label', exact: true }).getAttribute('href')
      expect(initialPath).toContain(`reviewTaskId=${reviewTaskId}`)
      expect(initialPath).toContain(`productId=${finding.productId}`)
      if (!initialPath) throw new Error('The real finding did not expose its bound review link')
      let formulaRequestStarted: Promise<void> | null = null
      if (isCompoundMaker) {
        let release!: () => void
        let markStarted!: () => void
        const responseGate = new Promise<void>(resolve => { release = resolve })
        formulaRequestStarted = new Promise<void>(resolve => { markStarted = resolve })
        releaseHeldCurrentFormula = release
        await page.route(`**/api/catalog/products/${finding.productId}`, async route => {
          markStarted()
          await responseGate
          await route.continue()
        })
      }
      await page.goto(initialPath)
      await expect(page.getByLabel('Product', { exact: true })).toHaveValue(finding.productId)
      await expect(page.getByLabel('Review task ID')).toHaveValue(reviewTaskId)
      const previousLabelId = before.get(finding.productId)!.label.labelVersionId
      await expect(page.getByRole('status')).toContainText(
        `Declarations were prefilled from previous label ${previousLabelId}; review and edit them before draft creation.`)
      if (formulaRequestStarted) await formulaRequestStarted
      if (isCompoundMaker) {
        const identity = page.getByRole('region', { name: 'Connected identity' })
        await expect(identity).toContainText(compoundMakerUserId)
        await expect(identity).toContainText('LABEL.CREATE')
        await expect(identity).toContainText('LABEL.APPROVE')
      }
      const previousAllergenIds = new Set<string>(
        before.get(finding.productId)!.declarations.declarations.map((declaration: any) => declaration.allergenId))
      for (const allergen of catalogAllergens) {
        const checkbox = page.getByRole('checkbox', {
          name: `Declare ${allergen.displayName} (${allergen.allergenCode})`, exact: true,
        })
        if (previousAllergenIds.has(allergen.allergenId)) await expect(checkbox).toBeChecked()
        else await expect(checkbox).not.toBeChecked()
      }
      for (const allergenId of expectedAllergenIds) {
        const allergen = catalogAllergens.find((entry: any) => entry.allergenId === allergenId)
        expect(allergen, `The explicit historical/SOY input ${allergenId} must exist in the catalog`).toBeTruthy()
        const checkbox = page.getByRole('checkbox', {
          name: `Declare ${allergen.displayName} (${allergen.allergenCode})`, exact: true,
        })
        if (!previousAllergenIds.has(allergenId)) await checkbox.check()
      }
      if (releaseHeldCurrentFormula) {
        releaseHeldCurrentFormula()
        releaseHeldCurrentFormula = null
        await expect(page.getByRole('alert')).toContainText(
          `The previous label uses formula ${before.get(finding.productId)!.label.formulaVersionId}, while the product now uses ${adopted.get(finding.productId)}. Review the copied declarations against the current formula.`)
        for (const allergenId of expectedAllergenIds) {
          const allergen = catalogAllergens.find((entry: any) => entry.allergenId === allergenId)
          await expect(page.getByRole('checkbox', {
            name: `Declare ${allergen.displayName} (${allergen.allergenCode})`, exact: true,
          })).toBeChecked()
        }
        await page.unroute(`**/api/catalog/products/${finding.productId}`)
      }
      await page.getByRole('checkbox', { name: 'Use the connected identity to create this draft' }).check()
      const draftPromise = page.waitForResponse((response) => response.request().method() === 'POST'
        && new URL(response.url()).pathname === '/api/labels/drafts')
      await page.getByRole('button', { name: 'Create label draft', exact: true }).click()
      const draftResponse = await draftPromise
      expect(draftResponse.status()).toBe(201)
      const draft = await draftResponse.json()
      lastAttempt = { ...lastAttempt, draft }
      expect(draft.createdByUserId).toBe(expectedCreator)
      expect(draft.formulaVersionId).toBe(adopted.get(finding.productId))
      const immutableDeclarations = await json(request, `/api/labels/${draft.labelVersionId}/declarations`)
      expect(immutableDeclarations.declarations.map((declaration: any) => declaration.allergenId).sort())
        .toEqual(expectedAllergenIds)
      expect(immutableDeclarations.declarations.every((declaration: any) =>
        declaration.declarationType === 'CONTAINS' && declaration.declarationSource === 'USER_ENTERED')).toBe(true)
      await expect(page.getByRole('region', { name: 'Structured label declarations' })).toContainText('USER_ENTERED')
      await page.getByRole('checkbox', { name: 'Use the connected identity for review and publication' }).check()
      await expect(page.getByRole('button', { name: 'Submit for review', exact: true })).toBeDisabled()
      await page.getByRole('checkbox', {
        name: 'Use the connected identity to validate this exact version',
      }).check()
      reachedStage = 'actual evaluator UI and persisted run'
      const validatePromise = page.waitForResponse((response) => response.request().method() === 'POST'
        && new URL(response.url()).pathname === `/api/v1/label-versions/${draft.labelVersionId}/validation-runs`)
      await page.getByRole('button', { name: 'Run validation', exact: true }).click()
      const validateResponse = await validatePromise
      expect(validateResponse.status()).toBe(201)
      const validation = await validateResponse.json()
      lastAttempt = { ...lastAttempt, validation }
      expect(validation.status).toBe('PASSED')
      expect(await json(request, `/api/v1/validation-runs/${validation.validationRunId}`)).toEqual(validation)
      await expect(page.getByRole('region', { name: 'Validation run results' })).toContainText('PASSED')
      await page.getByRole('checkbox', { name: 'Use the connected identity for review and publication' }).check()
      reachedStage = 'actual HTTP review submission from maker UI'
      await page.getByRole('button', { name: 'Submit for review', exact: true }).click()
      await expect(page.getByRole('region', { name: 'Label draft details' })).toContainText('PENDING_REVIEW')
      if (isCompoundMaker) {
        reachedStage = 'actual permitted-maker UI guard and policy denial with unchanged persisted binding'
        await page.getByRole('checkbox', { name: 'Use the connected identity for review and publication' }).check()
        await expect(page.getByRole('checkbox', { name: 'Use the connected identity for review and publication' })).toBeChecked()
        await expect(page.getByText('Your connected identity created this label. An independent reviewer must make the decision.')).toBeVisible()
        await expect(page.getByRole('button', { name: 'Approve label', exact: true })).toBeDisabled()
        const beforeSelfApproval = {
          label: await actorJson(compoundMakerContext.request, `/api/labels/${draft.labelVersionId}`),
          task: await actorJson(compoundMakerContext.request, `/api/review-tasks/${reviewTaskId}`),
        }
        expect(beforeSelfApproval.label.createdByUserId).toBe(compoundMakerIdentity.userId)
        expect(beforeSelfApproval.label.lifecycleStatus).toBe('PENDING_REVIEW')
        expect(beforeSelfApproval.task.status).toBe('IN_REVIEW')
        expect(beforeSelfApproval.task.draftLabelVersionId).toBe(draft.labelVersionId)
        expect(beforeSelfApproval.task.targetLabelVersionId).toBe(draft.labelVersionId)
        expect(beforeSelfApproval.task.decision).toBeNull()
        const denied = await post(compoundMakerContext.request, `/api/labels/${draft.labelVersionId}/review-decisions`, {
          decision: 'APPROVE', comments: 'Permitted creator must fail the independent maker-checker policy',
        }, compoundMakerSubject, 403)
        expect(denied.code).toBe('AUTHORIZATION_DENIED')
        expect(denied.message).toBe('Maker-checker violation: creator cannot approve own label')
        const afterSelfApproval = {
          label: await actorJson(compoundMakerContext.request, `/api/labels/${draft.labelVersionId}`),
          task: await actorJson(compoundMakerContext.request, `/api/review-tasks/${reviewTaskId}`),
        }
        expect(afterSelfApproval).toEqual(beforeSelfApproval)
        // A second real subject spelling must resolve to the same persisted user;
        // the actor's stable user ID, rather than the subject text, enforces the rule.
        const aliasDenied = await post(compoundAliasContext.request, `/api/labels/${draft.labelVersionId}/review-decisions`, {
          decision: 'APPROVE', comments: 'The same permitted creator remains the maker through its fixture alias',
        }, compoundMakerAliasSubject, 403)
        expect(aliasDenied.code).toBe('AUTHORIZATION_DENIED')
        expect(aliasDenied.message).toBe('Maker-checker violation: creator cannot approve own label')
        const afterAliasSelfApproval = {
          label: await actorJson(compoundAliasContext.request, `/api/labels/${draft.labelVersionId}`),
          task: await actorJson(compoundAliasContext.request, `/api/review-tasks/${reviewTaskId}`),
        }
        expect(afterAliasSelfApproval).toEqual(beforeSelfApproval)
        // Separately preserve the existing officer permission negative. This caller
        // is not the compound maker and its ACL403 is not maker-checker evidence.
        const officerDenied = await post(makerContext.request, `/api/labels/${draft.labelVersionId}/review-decisions`, {
          decision: 'APPROVE', comments: 'Existing officer without approval permission must be rejected',
        }, 'dev-external-label-officer', 403)
        expect(officerDenied.code).toBe('AUTHORIZATION_DENIED')
        expect(officerDenied.message).toContain('LABEL.APPROVE')
        expect({
          label: await actorJson(compoundMakerContext.request, `/api/labels/${draft.labelVersionId}`),
          task: await actorJson(compoundMakerContext.request, `/api/review-tasks/${reviewTaskId}`),
        }).toEqual(beforeSelfApproval)
        await page.screenshot({ path: resolve(evidenceDir, 's3-live-compound-maker-self-approval-disabled.png'), fullPage: true })
        lastAttempt = { ...lastAttempt, actualMakerChecker: {
          actualIdentity: compoundMakerIdentity, actualAliasIdentity: compoundAliasIdentity,
          makerSubject: compoundMakerSubject, aliasSubject: compoundMakerAliasSubject,
          normalUiApproveDisabled: true, connectedConsentChecked: true,
          httpStatus: 403, response: denied, aliasHttpStatus: 403, aliasResponse: aliasDenied,
          before: beforeSelfApproval, after: afterSelfApproval, afterAlias: afterAliasSelfApproval,
          persistedStateUnchanged: true, independentMakerCheckerPolicyReached: true,
        }, actualOfficerAcl: { status: 403, response: officerDenied, storedDecision: null,
          independentMakerCheckerPolicyReached: false } }
      } else if (published.size === 0) {
        const beforeSelfApproval = {
          label: await actorJson(makerContext.request, `/api/labels/${draft.labelVersionId}`),
          task: await actorJson(makerContext.request, `/api/review-tasks/${reviewTaskId}`),
        }
        const denied = await post(makerContext.request, `/api/labels/${draft.labelVersionId}/review-decisions`, {
          decision: 'APPROVE', comments: 'The label maker has no checker permission and cannot approve this label',
        }, 'dev-external-label-officer', 403)
        expect(denied.code).toBe('AUTHORIZATION_DENIED')
        expect(denied.message).toContain('LABEL.APPROVE')
        expect({
          label: await actorJson(makerContext.request, `/api/labels/${draft.labelVersionId}`),
          task: await actorJson(makerContext.request, `/api/review-tasks/${reviewTaskId}`),
        }).toEqual(beforeSelfApproval)
        await page.screenshot({ path: resolve(evidenceDir, 's3-live-maker-approval-denied.png'), fullPage: true })
        lastAttempt = { ...lastAttempt, actualMakerChecker: {
          actualIdentity: compoundMakerIdentity, makerSubject: compoundMakerSubject,
          httpStatus: 403, response: denied, before: beforeSelfApproval,
          persistedStateUnchanged: true, independentMakerCheckerPolicyReached: false,
        } }
      }
      const actorPath = `${initialPath}&labelVersionId=${draft.labelVersionId}`
      const qaPage = await loadActorLabel(qaContext, actorPath, draft.labelVersionId)
      reachedStage = 'actual independent QA approval UI'
      await qaPage.getByLabel('Review comments').fill('Independent QA approval of the immutable, passing SOY/WHEAT label')
      await qaPage.getByRole('button', { name: 'Approve label', exact: true }).click()
      await expect(qaPage.getByRole('region', { name: 'Label draft details' })).toContainText('APPROVED')
      const publisherPage = await loadActorLabel(publisherContext, actorPath, draft.labelVersionId)
      reachedStage = 'actual publisher UI and committed publication'
      await publisherPage.getByRole('button', { name: 'Publish label', exact: true }).click()
      await expect(publisherPage.getByRole('region', { name: 'Label draft details' })).toContainText('PUBLISHED')
      expect((await json(request, `/api/catalog/products/${finding.productId}`)).current_published_label_version_id)
        .toBe(draft.labelVersionId)
      expect(await json(request, `/api/labels/${draft.labelVersionId}/declarations`)).toEqual(immutableDeclarations)
      const replayedPublication = await post(request, `/api/review-tasks/${reviewTaskId}/publications`, {
        labelVersionId: draft.labelVersionId,
      }, 'dev-external-publisher', 409)
      expect(typeof replayedPublication.code).toBe('string')
      expect((await json(request, `/api/catalog/products/${finding.productId}`)).current_published_label_version_id)
        .toBe(draft.labelVersionId)
      let historyApiEvidence: object | undefined
      if (published.size === 0) {
        reachedStage = 'actual label history API permission and published-record checks'
        const makerIdentity = await actorJson(compoundMakerContext.request, '/api/identity/current')
        expect(makerIdentity.permissions).not.toContain('AUDIT.READ')
        const deniedHistory = await compoundMakerContext.request.get(
          `/api/audit/products/${finding.productId}/label-history`)
        expect(deniedHistory.status()).toBe(403)
        const deniedHistoryBody = await deniedHistory.json()
        expect(deniedHistoryBody.code).toBe('AUTHORIZATION_DENIED')

        const auditorIdentity = await actorJson(auditorContext.request, '/api/identity/current')
        expect(auditorIdentity.permissions).toContain('AUDIT.READ')
        const historyResponse = await auditorContext.request.get(
          `/api/audit/products/${finding.productId}/label-history`)
        expect(historyResponse.status(), await historyResponse.text()).toBe(200)
        const history = await historyResponse.json()
        expect(history.productId).toBe(finding.productId)
        expect(history.versions.map((version: any) => version.labelVersionId))
          .toEqual([before.get(finding.productId)!.label.labelVersionId, draft.labelVersionId])
        const publishedVersion = history.versions.find((version: any) =>
          version.labelVersionId === draft.labelVersionId)
        expect(publishedVersion).toMatchObject({ lifecycleStatus: 'PUBLISHED',
          publishedByUserId: 'user_publisher' })
        expect(publishedVersion.publishedAt).toBeTruthy()
        const expectedHistoryAllergenCodes = expectedAllergenIds.map((allergenId) => {
          const allergen = catalogAllergens.find((entry: any) => entry.allergenId === allergenId)
          expect(allergen, `The history must resolve allergen ${allergenId} from the catalog`).toBeTruthy()
          return allergen.allergenCode
        }).sort()
        expect(publishedVersion.declarations.map((declaration: any) => declaration.allergenCode).sort())
          .toEqual(expectedHistoryAllergenCodes)
        expect(publishedVersion.declarations).toEqual(expect.arrayContaining([
          expect.objectContaining({ allergenCode: 'SOY', declarationType: 'CONTAINS', source: 'USER_ENTERED' }),
        ]))
        expect(history.approvals).toEqual(expect.arrayContaining([
          expect.objectContaining({ labelVersionId: draft.labelVersionId, reviewTaskId, decision: 'APPROVE' }),
        ]))
        expect(history.auditEvents).toEqual(expect.arrayContaining([
          expect.objectContaining({ eventType: 'LABEL_DECISION_RECORDED',
            entityType: 'LABEL_VERSION', entityId: draft.labelVersionId }),
          expect.objectContaining({ eventType: 'LABEL_PUBLISHED',
            entityType: 'LABEL_VERSION', entityId: draft.labelVersionId }),
        ]))
        historyApiEvidence = {
          productId: finding.productId,
          auditorUserId: auditorIdentity.userId,
          auditorHttpStatus: historyResponse.status(),
          deniedUserId: makerIdentity.userId,
          deniedPermissions: makerIdentity.permissions,
          deniedHttpStatus: deniedHistory.status(),
          deniedErrorCode: deniedHistoryBody.code,
          versionIds: history.versions.map((version: any) => version.labelVersionId),
          declarationCodes: publishedVersion.declarations.map((declaration: any) => declaration.allergenCode),
          approvalTaskId: reviewTaskId,
          publicationEventFound: true,
        }
      }
      published.set(finding.productId, draft.labelVersionId)
      observations.push({ productId: finding.productId, reviewTaskId, draft, validation, makerUserId: expectedCreator, makerSubject: expectedMakerSubject,
        checkerSubject: 'dev-external-qa-approver', publisherSubject: 'dev-external-publisher',
        persistedCurrentLabelVersionId: draft.labelVersionId, declarations: immutableDeclarations,
        historyApiEvidence,
        replacementPrefillEvidence: { previousLabelId,
          prefilledAllergenIds: [...previousAllergenIds].sort(), editedAddedAllergenIds: ['all_soy'],
          staleFormulaWarning: before.get(finding.productId)!.label.formulaVersionId !== adopted.get(finding.productId),
          selectionPreservedAfterLateCurrentFormulaResponse: isCompoundMaker },
        repeatPublicationHttpStatus: 409, actualMakerChecker: lastAttempt.actualMakerChecker, actualOfficerAcl: lastAttempt.actualOfficerAcl })
      if (published.size === 1 || published.size === 20) {
        await publisherPage.screenshot({ path: resolve(evidenceDir, `s3-live-publication-${published.size}.png`), fullPage: true })
      }
      await qaPage.close()
      await publisherPage.close()
    }
    expect(published.size).toBe(20)
    reachedStage = 'actual closed review task list, exact bindings and publisher identity'
    const reviewsPage = await publisherContext.newPage()
    await reviewsPage.goto('/reviews')
    const connectedPublisher = reviewsPage.getByRole('region', { name: 'Connected identity' })
    await expect(connectedPublisher).toContainText('user_publisher')
    await expect(connectedPublisher).toContainText('PUBLISHER')
    const publisherIdentityResponse = await publisherContext.request.get('/api/identity/current')
    expect(publisherIdentityResponse.status()).toBe(200)
    const publisherIdentity = await publisherIdentityResponse.json()
    expect(publisherIdentity.userId).toBe('user_publisher')
    expect(publisherIdentity.permissions).toContain('LABEL.PUBLISH')
    const closedListPromise = reviewsPage.waitForResponse((response) => response.request().method() === 'GET'
      && new URL(response.url()).pathname === '/api/review-tasks'
      && new URL(response.url()).searchParams.get('status') === 'CLOSED')
    await reviewsPage.getByLabel('Task status', { exact: true }).selectOption('CLOSED')
    const closedListResponse = await closedListPromise
    expect(closedListResponse.status()).toBe(200)
    const closedTasks = await closedListResponse.json()
    expect(closedTasks).toHaveLength(20)
    const renderedClosedTasks = reviewsPage.getByRole('region', { name: 'Review task list' })
    await expect(renderedClosedTasks.locator('article')).toHaveCount(20)
    for (const task of closedTasks) {
      expect(task.currentLabelVersionId).toBe(before.get(task.productId)!.label.labelVersionId)
      expect(task.draftLabelVersionId).toBe(published.get(task.productId))
      expect(task.targetLabelVersionId).toBe(published.get(task.productId))
      expect(task.status).toBe('CLOSED')
      expect(task.decision).toBe('APPROVE')
      expect(task.resolvedAt).toBeTruthy()
    }
    expect(new Set(closedTasks.map((task: any) => task.productId))).toEqual(new Set(published.keys()))
    for (const task of [closedTasks[0], closedTasks[19]]) {
      await reviewsPage.getByLabel('Existing review task ID').fill(task.reviewTaskId)
      await reviewsPage.getByRole('button', { name: 'Load review task', exact: true }).click()
      const details = reviewsPage.getByRole('region', { name: 'Review task details' })
      await expect(details).toContainText(task.reviewTaskId)
      await expect(details).toContainText(task.currentLabelVersionId)
      await expect(details).toContainText(task.targetLabelVersionId)
      await expect(details).toContainText('APPROVE')
      const exactHandoff = await details.getByRole('link', { name: 'Open bound label version' }).getAttribute('href')
      expect(exactHandoff).toContain(`reviewTaskId=${task.reviewTaskId}`)
      expect(exactHandoff).toContain(`labelVersionId=${task.targetLabelVersionId}`)
    }
    await reviewsPage.screenshot({ path: resolve(evidenceDir, 's3-live-closed-review-workspace.png'), fullPage: true })
    reachedStage = 'all sixty original product histories'
    for (const row of golden) {
      const original = before.get(row.productId)!
      const product = await json(request, `/api/catalog/products/${row.productId}`)
      expect(product.current_formula_version_id).toBe(adopted.get(row.productId) || original.product.current_formula_version_id)
      expect(product.current_published_label_version_id).toBe(published.get(row.productId) || original.product.current_published_label_version_id)
      const oldFormula = await json(request, `/api/catalog/formulas/${original.product.current_formula_version_id}`)
      expect(oldFormula.items).toEqual(original.formula.items)
      expect(oldFormula.released_at).toEqual(original.formula.released_at)
      const oldLabel = await json(request, `/api/labels/${original.label.labelVersionId}`)
      expect(oldLabel).toEqual({ ...original.label, ...(published.has(row.productId)
        ? { lifecycleStatus: 'SUPERSEDED', isCurrentPublished: 'N' } : {}) })
      expect(await json(request, `/api/labels/${original.label.labelVersionId}/declarations`)).toEqual(original.declarations)
    }
    const evidence = { browserExecuted: true, mockedResponses: false, testPrefix, targetSpecificationVersionId,
      changeRequestId: change.changeRequestId, impactAnalysisId: analysis.impactAnalysisId, fixtureSqlWrites:
      ['Isolated NEW compound test user with existing officer/approver role links only; catalog and historic labels from unchanged Flyway'],
      declarationWrites: 'Only the actual first-creation draft UI/API', validationWrites: 'Only the actual evaluator HTTP API',
      identityCoverage: 'One isolated test-only compound CREATE+APPROVE maker, nineteen unchanged existing officer makers, independent existing QA/publisher fixture contexts and a same-user subject alias; production login decision remains separate',
      publisherIdentity, adminIdentity, compoundMakerIdentity, compoundAliasIdentity, closedReviewTasks: closedTasks,
      makerGuardCoverage: 'The actual creator has CREATE+APPROVE, selected connected consent, and cannot approve in the normal UI. Actual HTTP requests from this creator and a verified same-user alias reach maker-checker policy403; exact persisted label/task snapshots remain unchanged before independent QA approval and publication. Officer ACL403 is recorded separately.',
      counts: { goldenProducts: 60, actualAdoptions: adopted.size, findings: analysis.findings.length,
        noAction: analysis.noActionCount, reviewRequired: analysis.reviewRequiredCount, published: published.size,
        immutableHistoricLabelsChecked: before.size }, observations }
    writeFileSync(resolve(evidenceDir, 's3-product-flow-observations.json'), JSON.stringify(evidence, null, 2) + '\n')
    await testInfo.attach('real-s3-business-path', { body: JSON.stringify(evidence), contentType: 'application/json' })
    reachedStage = 'complete: twenty actual publications and sixty immutable histories verified'
  } finally {
    releaseHeldCurrentFormula?.()
    writeFileSync(resolve(evidenceDir, 's3-product-flow-progress.json'), JSON.stringify({ reachedStage, lastAttempt,
      actualAdoptionsCompleted: adopted.size, actualPublicationsCompleted: published.size, observations }, null, 2) + '\n')
    await makerContext.close()
    await compoundMakerContext.close()
    await compoundAliasContext.close()
    await adminContext.close()
    await changeContext.close()
    await qaContext.close()
    await publisherContext.close()
    await auditorContext.close()
  }
})
