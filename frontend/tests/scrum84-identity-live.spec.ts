import { expect, test } from '@playwright/test'

// Real HTTP/browser smoke against an explicitly isolated demo stack.
// This test does not seed, update, approve, or publish business records.
test('live Maker Checker Publisher switching reauthorizes against backend', async ({ page, request }) => {
  test.skip(process.env.LIVE_SCRUM84_IDENTITY !== '1', 'Requires an isolated demo-enabled backend')
  const optionsResponse = await request.get('/api/identity/demo-options')
  expect(optionsResponse.status()).toBe(200)
  const options = await optionsResponse.json() as Array<{ key: string; subject: string; actor: { userId: string; permissions: string[] } }>
  const expected = [
    ['MAKER', 'LABEL.CREATE'],
    ['CHECKER', 'LABEL.APPROVE'],
    ['PUBLISHER', 'LABEL.PUBLISH'],
  ] as const
  expect(options.map(option => option.key).sort()).toEqual(expected.map(([key]) => key).sort())

  await page.goto('/reviews')
  const selector = page.getByRole('combobox', { name: 'Demo identity' })
  await expect(selector).toBeVisible()
  for (const [key, permission] of expected) {
    const option = options.find(item => item.key === key)!
    expect(option.actor.permissions).toContain(permission)
    await selector.selectOption(key)
    await expect(page.getByText('Refreshing permissions…')).toHaveCount(0)
    const identityResponse = await request.get('/api/identity/current', {
      headers: { 'X-Auth-Provider': 'DEV_EXTERNAL', 'X-External-Subject': option.subject },
    })
    expect(identityResponse.status()).toBe(200)
    const actor = await identityResponse.json() as { userId: string; displayName: string; permissions: string[] }
    expect(actor.userId).toBe(option.actor.userId)
    expect(actor.permissions).toContain(permission)
    await expect(page.getByText(actor.displayName).first()).toBeVisible()
  }
})
