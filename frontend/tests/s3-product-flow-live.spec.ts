import { expect, test, type APIRequestContext, type BrowserContext, type Page } from '@playwright/test'
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { resolve } from 'node:path'

const ruleSetVersionId = 'ruleset_us_falcpa_demo_v1'
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
  test.skip(process.env.LIVE_S3_FLOW !== '1', 'Requires an isolated real backend seeded with released specification input only')
  test.setTimeout(300_000)
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
  const makerContext = await browser.newContext({ baseURL: testInfo.project.use.baseURL,
    extraHTTPHeaders: subjectHeaders('dev-external-label-officer') })
  const officerPage = await makerContext.newPage()
  const adminContext = await browser.newContext({ baseURL: testInfo.project.use.baseURL,
    extraHTTPHeaders: subjectHeaders('dev-external-admin') })
  const changeContext = await browser.newContext({ baseURL: testInfo.project.use.baseURL,
    extraHTTPHeaders: subjectHeaders('dev-external-change-manager') })
  const qaContext = await browser.newContext({ baseURL: testInfo.project.use.baseURL,
    extraHTTPHeaders: subjectHeaders('dev-external-qa-approver') })
  const publisherContext = await browser.newContext({ baseURL: testInfo.project.use.baseURL,
    extraHTTPHeaders: subjectHeaders('dev-external-publisher') })
  try {
    const catalogAllergens = await json(request, '/api/v1/allergens?jurisdictionCode=US')
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
      if (row.outcome === 'EXCLUDED_NO_FINDING') continue
      const adoption = await post(request, `/api/catalog/products/${row.productId}/formula-adoptions`, {
        sourceFormulaVersionId: product.current_formula_version_id,
        targetSpecificationVersionId: 'spec_chocolate_v2',
      }, 'dev-external-admin', 201)
      expect(adoption.formula_version_id).not.toBe(product.current_formula_version_id)
      adopted.set(row.productId, adoption.formula_version_id)
    }
    expect(adopted.size).toBe(40)
    reachedStage = 'actual impact UI creation and analysis'
    const impactPage = await changeContext.newPage()
    await impactPage.goto('/impact')
    await impactPage.getByText('Create a specification change request', { exact: true }).click()
    await impactPage.getByLabel('Supplier material', { exact: true }).selectOption('mat_chocolate_base')
    await impactPage.getByLabel('Previous specification', { exact: true }).selectOption('spec_chocolate_v1')
    await impactPage.getByLabel('Target specification', { exact: true }).selectOption('spec_chocolate_v2')
    await impactPage.getByLabel('Change description').fill(
      'Live browser S3 SOY: actual adoption, declarations, evaluator, independent approval and publication')
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
      const page = officerPage
      const expectedCreator = 'user_label_officer'
      const expectedAllergenIds = [...new Set<string>([
        ...before.get(finding.productId)!.declarations.declarations.map((declaration: any) => declaration.allergenId),
        'all_soy',
      ])].sort()
      const reviewTaskId = finding.reviewTask.reviewTaskId
      lastAttempt = { productId: finding.productId, reviewTaskId }
      reachedStage = 'actual first-creation declaration UI'
      const findingCard = renderedResults.locator('article').filter({
        has: impactPage.getByRole('heading', { name: finding.productId, exact: true }),
      })
      const initialPath = await findingCard.getByRole('link', { name: 'Prepare replacement label', exact: true }).getAttribute('href')
      expect(initialPath).toContain(`reviewTaskId=${reviewTaskId}`)
      expect(initialPath).toContain(`productId=${finding.productId}`)
      if (!initialPath) throw new Error('The real finding did not expose its bound review link')
      await page.goto(initialPath)
      await expect(page.getByLabel('Product', { exact: true })).toHaveValue(finding.productId)
      await expect(page.getByLabel('Review task ID')).toHaveValue(reviewTaskId)
      for (const allergenId of expectedAllergenIds) {
        const allergen = catalogAllergens.find((entry: any) => entry.allergenId === allergenId)
        expect(allergen, `The explicit historical/SOY input ${allergenId} must exist in the catalog`).toBeTruthy()
        await page.getByRole('checkbox', {
          name: `Declare ${allergen.displayName} (${allergen.allergenCode})`, exact: true,
        }).check()
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
      if (published.size === 0) {
        await page.getByRole('checkbox', { name: 'Use the connected identity for review and publication' }).check()
        // This existing maker lacks LABEL.APPROVE. The browser proves UI and ACL
        // rejection; the independent maker-checker policy has separate unit/service tests.
        await expect(page.getByRole('button', { name: 'Approve label', exact: true })).toBeDisabled()
        const denied = await post(makerContext.request, `/api/labels/${draft.labelVersionId}/review-decisions`, {
          decision: 'APPROVE', comments: 'Existing maker without approval permission must be rejected',
        }, 'dev-external-label-officer', 403)
        expect(denied.code).toBe('AUTHORIZATION_DENIED')
        expect(denied.message).toContain('LABEL.APPROVE')
        expect((await json(request, `/api/review-tasks/${reviewTaskId}`)).decision).toBeNull()
        await expect(page.getByRole('region', { name: 'Label draft details' })).toContainText('PENDING_REVIEW')
        lastAttempt = { ...lastAttempt, actualMakerAcl: { status: 403, response: denied,
          normalUiApproveDisabled: true, storedDecision: null, independentMakerCheckerPolicyReached: false } }
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
      published.set(finding.productId, draft.labelVersionId)
      observations.push({ productId: finding.productId, reviewTaskId, draft, validation,
        checkerSubject: 'dev-external-qa-approver', publisherSubject: 'dev-external-publisher',
        persistedCurrentLabelVersionId: draft.labelVersionId, declarations: immutableDeclarations,
        repeatPublicationHttpStatus: 409, actualMakerAcl: lastAttempt.actualMakerAcl })
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
    const evidence = { browserExecuted: true, mockedResponses: false, fixtureSqlWrites:
      ['Released spec_chocolate_v2 and its specification components only; catalog and historic label inputs from Flyway'],
      declarationWrites: 'Only the actual first-creation draft UI/API', validationWrites: 'Only the actual evaluator HTTP API',
      identityCoverage: 'Existing explicit local maker opt-in and isolated preauthenticated QA/publisher fixture browser contexts; production login decision remains separate',
      publisherIdentity, adminIdentity, closedReviewTasks: closedTasks,
      makerGuardCoverage: 'Existing officer UI self-approval disabled and actual HTTP ACL403 with zero decision writes. Compound CREATE+APPROVE browser identity was not introduced; independent maker-checker policy is covered separately in unit/service tests. This browser does not prove a permitted-creator database self-approval negative.',
      counts: { goldenProducts: 60, actualAdoptions: adopted.size, findings: analysis.findings.length,
        noAction: analysis.noActionCount, reviewRequired: analysis.reviewRequiredCount, published: published.size,
        immutableHistoricLabelsChecked: before.size }, observations }
    writeFileSync(resolve(evidenceDir, 's3-product-flow-observations.json'), JSON.stringify(evidence, null, 2) + '\n')
    await testInfo.attach('real-s3-business-path', { body: JSON.stringify(evidence), contentType: 'application/json' })
    reachedStage = 'complete: twenty actual publications and sixty immutable histories verified'
  } finally {
    writeFileSync(resolve(evidenceDir, 's3-product-flow-progress.json'), JSON.stringify({ reachedStage, lastAttempt,
      actualAdoptionsCompleted: adopted.size, actualPublicationsCompleted: published.size, observations }, null, 2) + '\n')
    await makerContext.close()
    await adminContext.close()
    await changeContext.close()
    await qaContext.close()
    await publisherContext.close()
  }
})
