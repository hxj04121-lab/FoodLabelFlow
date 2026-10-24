import { expect, test } from '@playwright/test'
import { isLocalDemoHost } from '../src/lib/local-demo'

const approved = 'https://spectrace.52zzx.top'

test('hosted demo remains disabled without an explicit origin setting', () => {
  expect(isLocalDemoHost(new URL(approved), '')).toBe(false)
})

test('loopback origins retain the existing local development behavior', () => {
  for (const origin of ['http://localhost:5173', 'http://127.0.0.1:15180', 'http://[::1]:5173']) {
    expect(isLocalDemoHost(new URL(origin), '')).toBe(true)
  }
})

test('only the exact configured HTTPS origin is enabled', () => {
  expect(isLocalDemoHost(new URL(approved), approved)).toBe(true)
  expect(isLocalDemoHost(new URL('https://demo.example.invalid:8443'), `${approved}, https://demo.example.invalid:8443`)).toBe(true)
})

for (const origin of [
  'http://spectrace.52zzx.top',
  'https://spectrace.52zzx.top:8443',
  'https://spectrace.52zzx.top.example.invalid',
  'https://other.52zzx.top',
  'https://52zzx.top',
]) {
  test(`rejects unconfigured origin ${origin}`, () => {
    expect(isLocalDemoHost(new URL(origin), approved)).toBe(false)
  })
}

for (const setting of ['', '*', '*.52zzx.top', 'spectrace.52zzx.top',
  'http://spectrace.52zzx.top', `${approved}/path`, `${approved}?allow=true`,
  `${approved}#demo`, 'https://user:password@spectrace.52zzx.top', 'not a URL']) {
  test(`does not broaden the allowlist for invalid setting ${JSON.stringify(setting)}`, () => {
    expect(isLocalDemoHost(new URL(approved), setting)).toBe(false)
  })
}

for (const setting of ['https://*.52zzx.top', 'https://foo*.52zzx.top']) {
  test(`rejects a fully qualified wildcard setting even for a literal wildcard URL: ${setting}`, () => {
    expect(isLocalDemoHost(new URL(setting), setting)).toBe(false)
  })
}
