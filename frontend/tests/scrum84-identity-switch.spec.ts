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
