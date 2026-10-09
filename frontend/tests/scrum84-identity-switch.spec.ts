import { expect, test } from './catalog-fixture'

test.beforeEach(async ({ page }) => {
  // Reuse coverage collection while preserving these tests' anonymous startup.
  // Their identity routes are registered after the fixture and override its defaults.
  await page.setExtraHTTPHeaders({})
})

const identities = {
  'dev-external-label-officer': { userId: 'user_label_officer', username: 'label.officer', displayName: 'Demo Label Officer', roles: ['LABEL_OFFICER'], permissions: ['LABEL.CREATE', 'LABEL.VALIDATE', 'LABEL.SUBMIT_REVIEW'] },
  'dev-external-qa-approver': { userId: 'user_approver', username: 'qa.approver', displayName: 'Demo Approver', roles: ['APPROVER'], permissions: ['LABEL.APPROVE', 'LABEL.REQUEST_CHANGES'] },
  'dev-external-publisher': { userId: 'user_publisher', username: 'publication.manager', displayName: 'Demo Publisher', roles: ['PUBLISHER'], permissions: ['LABEL.PUBLISH'] },
} as const

test('demo identity switch reloads the verified actor and clears previous permissions', async ({ page }) => {
  const seenSubjects: string[] = []
  await page.route('**/api/identity/current', async route => {
    const subject = route.request().headers()['x-external-subject']
    if (!subject || !(subject in identities)) {
      await route.fulfill({ status: 401, json: { code: 'AUTHENTICATION_REQUIRED', message: 'Identity required' } })
      return
    }
    seenSubjects.push(subject)
    await route.fulfill({ json: identities[subject as keyof typeof identities] })
  })
  await page.route('**/api/identity/demo-options', route => route.fulfill({ json: Object.entries(identities).map(([subject, actor]) => ({
    key: subject.includes('label-officer') ? 'MAKER' : subject.includes('qa-approver') ? 'CHECKER' : 'PUBLISHER',
    subject, actor,
  })) }))
  await page.goto('/reviews')
  await expect(page.getByText('Demo Label Officer').first()).toBeVisible()
  await page.getByRole('combobox', { name: 'Demo identity' }).selectOption('CHECKER')
  await expect(page.getByText('Demo Approver').first()).toBeVisible()
  await expect(page.getByText('Demo Label Officer')).toHaveCount(0)
  await page.getByRole('combobox', { name: 'Demo identity' }).selectOption('PUBLISHER')
  await expect(page.getByText('Demo Publisher').first()).toBeVisible()
  await expect(page.getByText('Demo Approver')).toHaveCount(0)
  expect(seenSubjects).toContain('dev-external-label-officer')
  expect(seenSubjects).toContain('dev-external-qa-approver')
  expect(seenSubjects).toContain('dev-external-publisher')
})

test('late initial Maker identity response cannot overwrite a verified Checker', async ({ page }) => {
  let releaseMaker!: () => void
  let makerStarted!: () => void
  const makerGate = new Promise<void>(resolve => { releaseMaker = resolve })
  const makerRequested = new Promise<void>(resolve => { makerStarted = resolve })
  let makerCalls = 0
  await page.route('**/api/identity/current', async route => {
    const subject = route.request().headers()['x-external-subject']
    if (!subject) {
      await route.fulfill({ status: 401, json: { code: 'AUTHENTICATION_REQUIRED', message: 'Identity required' } })
    } else if (subject === 'dev-external-label-officer') {
      makerCalls++
      makerStarted()
      await makerGate
      try { await route.fulfill({ json: identities[subject] }) } catch { /* aborted by identity switch */ }
    } else if (subject && subject in identities) {
      await route.fulfill({ json: identities[subject as keyof typeof identities] })
    } else {
      await route.fulfill({ status: 401, json: { code: 'AUTHENTICATION_REQUIRED' } })
    }
  })
  await page.route('**/api/identity/demo-options', route => route.fulfill({ json: Object.entries(identities).map(([subject, actor]) => ({
    key: subject.includes('label-officer') ? 'MAKER' : subject.includes('qa-approver') ? 'CHECKER' : 'PUBLISHER',
    subject, actor,
  })) }))
  try {
    await page.goto('/reviews')
    await makerRequested
    await page.getByRole('combobox', { name: 'Demo identity' }).selectOption('CHECKER')
    await expect(page.getByText('Demo Approver').first()).toBeVisible()
    releaseMaker()
    await expect(page.getByText('Demo Label Officer')).toHaveCount(0)
    await expect(page.getByText('Demo Approver').first()).toBeVisible()
    expect(makerCalls).toBeGreaterThan(0)
  } finally {
    releaseMaker()
  }
})


test('switching identities clears ReviewTask details and rejects a late previous-actor response', async ({ page }) => {
  let releaseOld!: () => void
  let oldRequested!: () => void
  const gate = new Promise<void>(resolve => { releaseOld = resolve })
  const requested = new Promise<void>(resolve => { oldRequested = resolve })
  const oldTask = {
    reviewTaskId: 'old-maker-task', productId: 'prod_usda_1106285',
    currentLabelVersionId: 'old-label', draftLabelVersionId: 'maker-draft',
    targetLabelVersionId: 'maker-draft', status: 'OPEN', decision: null, resolvedAt: null,
  }
  await page.route('**/api/identity/current', route => {
    const subject = route.request().headers()['x-external-subject']
    if (subject && subject in identities) return route.fulfill({ json: identities[subject as keyof typeof identities] })
    return route.fulfill({ status: 401, json: { code: 'AUTHENTICATION_REQUIRED' } })
  })
  await page.route('**/api/identity/demo-options', route => route.fulfill({ json: Object.entries(identities).map(([subject, actor]) => ({
    key: subject.includes('label-officer') ? 'MAKER' : subject.includes('qa-approver') ? 'CHECKER' : 'PUBLISHER',
    subject, actor,
  })) }))
  await page.route('**/api/review-tasks?*', route => route.fulfill({ json: [] }))
  await page.route('**/api/review-tasks/old-maker-task', async route => {
    oldRequested()
    await gate
    try { await route.fulfill({ json: oldTask }) } catch { /* aborted on identity transition */ }
  })
  try {
    await page.goto('/reviews')
    await expect(page.getByText('Demo Label Officer').first()).toBeVisible()
    await page.getByLabel('Existing review task ID').fill('old-maker-task')
    await page.getByRole('button', { name: 'Load review task', exact: true }).click()
    await requested
    await page.getByRole('combobox', { name: 'Demo identity' }).selectOption('CHECKER')
    await expect(page.getByText('Demo Approver').first()).toBeVisible()
    releaseOld()
    await expect(page.getByRole('region', { name: 'Review task details' })).not.toContainText('maker-draft')
    await expect(page.getByRole('region', { name: 'Review task details' }).getByRole('link', { name: 'Open bound label version' })).toHaveCount(0)
  } finally {
    releaseOld()
  }
})


type RefreshRegressionWindow = Window & {
  __identityRefreshRegression?: {
    release: () => void
    readerReady: boolean
    completion: Promise<void> | null
  }
}

test('refreshing revoked Maker permissions removes the actor and all previously granted permissions', async ({ page }) => {
  let revoked = false
  let refreshReads = 0
  await page.route('**/api/identity/current', async route => {
    const subject = route.request().headers()['x-external-subject']
    if (subject !== 'dev-external-label-officer') {
      await route.fulfill({ status: 401, json: { code: 'AUTHENTICATION_REQUIRED', message: 'Identity required' } })
      return
    }
    if (revoked) refreshReads++
    await route.fulfill({ json: revoked
      ? { ...identities[subject], permissions: [] }
      : identities[subject] })
  })
  await page.goto('/reviews')
  const connected = page.getByRole('region', { name: 'Connected identity', exact: true })
  await expect(connected).toContainText('Demo Label Officer')
  await expect(connected).toContainText('LABEL.CREATE')
  revoked = true
  await connected.getByRole('button', { name: 'Refresh connected identity', exact: true }).click()
  await expect(connected).toContainText('Connected identity unavailable:')
  await expect(connected).toContainText('The connected account lacks the maker permissions: LABEL.CREATE, LABEL.VALIDATE, LABEL.SUBMIT_REVIEW.')
  await expect(connected).toContainText('Writes requiring this identity remain disabled.')
  await expect(connected.getByText('Demo Label Officer')).toHaveCount(0)
  await expect(connected.getByText('Granted permissions', { exact: true })).toHaveCount(0)
  const session = await page.evaluate(async () => {
    const { getIdentitySessionSnapshot } = await import('/src/api/identity-session.ts')
    return getIdentitySessionSnapshot()
  })
  expect(session.status).toBe('unavailable')
  expect(session.selection).toBe('MAKER')
  expect(session.actor).toBeNull()
  expect(refreshReads).toBe(1)
})

test('a late refresh reader cannot overwrite a newer verified Checker identity', async ({ page }) => {
  const seenSubjects: string[] = []
  await page.route('**/api/identity/current', async route => {
    const subject = route.request().headers()['x-external-subject']
    if (!subject || !(subject in identities)) {
      await route.fulfill({ status: 401, json: { code: 'AUTHENTICATION_REQUIRED', message: 'Identity required' } })
      return
    }
    seenSubjects.push(subject)
    await route.fulfill({ json: identities[subject as keyof typeof identities] })
  })
  await page.goto('/reviews')
  const connected = page.getByRole('region', { name: 'Connected identity', exact: true })
  await expect(connected).toContainText('Demo Label Officer')
  try {
    await page.evaluate(async () => {
      const { refreshIdentity } = await import('/src/api/identity-session.ts')
      const { getCurrentIdentity } = await import('/src/api/identity.ts')
      let release!: () => void
      const gate = new Promise<void>(resolve => { release = resolve })
      const state = { release, readerReady: false, completion: null as Promise<void> | null }
      ;(window as RefreshRegressionWindow).__identityRefreshRegression = state
      // Pause after the real HTTP reader succeeds: aborting tracked fetch alone must
      // not mask the session settlement's independent stale-generation guard.
      state.completion = refreshIdentity(async () => {
        const actor = await getCurrentIdentity()
        state.readerReady = true
        await gate
        return actor
      })
    })
    await expect.poll(() => page.evaluate(() =>
      (window as RefreshRegressionWindow).__identityRefreshRegression?.readerReady ?? false,
    )).toBe(true)
    await expect(connected.getByRole('button', { name: 'Refresh connected identity', exact: true })).toBeDisabled()
    await expect(page.getByRole('combobox', { name: 'Demo identity' })).toBeDisabled()
    // A public session transition models a newer caller while the normal UI remains
    // disabled. Both actors still come from controlled real browser HTTP responses.
    const latest = await page.evaluate(async () => {
      const { switchDemoIdentity, getIdentitySessionSnapshot } = await import('/src/api/identity-session.ts')
      const { getCurrentIdentity } = await import('/src/api/identity.ts')
      await switchDemoIdentity('CHECKER', getCurrentIdentity)
      return getIdentitySessionSnapshot()
    })
    await expect(connected).toContainText('Demo Approver')
    expect(latest.status).toBe('ready')
    expect(latest.selection).toBe('CHECKER')
    expect(latest.actor?.userId).toBe('user_approver')
    expect(latest.actor?.permissions).toEqual(['LABEL.APPROVE', 'LABEL.REQUEST_CHANGES'])
    await page.evaluate(async () => {
      const state = (window as RefreshRegressionWindow).__identityRefreshRegression!
      state.release()
      await state.completion
    })
    const settled = await page.evaluate(async () => {
      const { getIdentitySessionSnapshot } = await import('/src/api/identity-session.ts')
      return getIdentitySessionSnapshot()
    })
    expect(settled).toEqual(latest)
    await expect(connected).toContainText('Demo Approver')
    await expect(connected).not.toContainText('Demo Label Officer')
    await expect(connected).not.toContainText('LABEL.CREATE')
    expect(seenSubjects.filter(subject => subject === 'dev-external-label-officer')).toHaveLength(2)
    expect(seenSubjects).toContain('dev-external-qa-approver')
  } finally {
    await page.evaluate(() => (window as RefreshRegressionWindow).__identityRefreshRegression?.release())
  }
})
