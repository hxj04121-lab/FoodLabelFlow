import type { CurrentIdentity } from './identity'

export type DemoIdentityKey = 'MAKER' | 'CHECKER' | 'PUBLISHER'
export type DemoIdentityStatus = 'loading' | 'ready' | 'switching' | 'unavailable'

export type IdentitySessionSnapshot = {
  status: DemoIdentityStatus
  generation: number
  selection: DemoIdentityKey
  actor: CurrentIdentity | null
  error: string
}

export const demoIdentityChoices: Array<{ key: DemoIdentityKey; subject: string; label: string }> = [
  { key: 'MAKER', subject: 'dev-external-label-officer', label: 'Maker' },
  { key: 'CHECKER', subject: 'dev-external-qa-approver', label: 'Checker' },
  { key: 'PUBLISHER', subject: 'dev-external-publisher', label: 'Publisher' },
]

const requiredPermissions: Record<DemoIdentityKey, string[]> = {
  MAKER: ['LABEL.CREATE', 'LABEL.VALIDATE', 'LABEL.SUBMIT_REVIEW'],
  CHECKER: ['LABEL.APPROVE', 'LABEL.REQUEST_CHANGES'],
  PUBLISHER: ['LABEL.PUBLISH'],
}

function readSelection(): DemoIdentityKey {
  try {
    const key = window.localStorage.getItem('foodlabelflow.demoIdentity')
    return demoIdentityChoices.some(choice => choice.key === key)
      ? key as DemoIdentityKey
      : 'MAKER'
  } catch {
    return 'MAKER'
  }
}

let snapshot: IdentitySessionSnapshot = {
  status: 'loading',
  generation: 0,
  selection: readSelection(),
  actor: null,
  error: '',
}
const listeners = new Set<() => void>()
const pending = new Set<AbortController>()
let initialized = false
let subjectOverrideEnabled = false

export function getIdentitySessionSnapshot(): IdentitySessionSnapshot {
  return snapshot
}

export function subscribeIdentitySession(listener: () => void): () => void {
  listeners.add(listener)
  return () => listeners.delete(listener)
}

function publish(next: IdentitySessionSnapshot) {
  snapshot = next
  listeners.forEach(listener => listener())
}

export function identityHeaders(existing?: HeadersInit): Headers {
  const headers = new Headers(existing)
  if (subjectOverrideEnabled) {
    headers.set('X-Auth-Provider', 'DEV_EXTERNAL')
    headers.set('X-External-Subject', demoIdentityChoices.find(choice => choice.key === snapshot.selection)!.subject)
  }
  return headers
}

export function isIdentitySelectionControlled(): boolean {
  return subjectOverrideEnabled
}

export function activateDefaultDemoIdentity(): number {
  subjectOverrideEnabled = true
  const generation = snapshot.generation + 1
  pending.forEach(controller => controller.abort())
  pending.clear()
  publish({ ...snapshot, generation, selection: 'MAKER', actor: null })
  return generation
}

export function trackIdentityRequest(controller: AbortController): number {
  pending.add(controller)
  return snapshot.generation
}

export function finishIdentityRequest(controller: AbortController) {
  pending.delete(controller)
}

export function isIdentityGenerationCurrent(generation: number): boolean {
  return generation === snapshot.generation
}

function startTransition(selection: DemoIdentityKey, status: DemoIdentityStatus): number {
  const generation = snapshot.generation + 1
  pending.forEach(controller => controller.abort())
  pending.clear()
  try {
    window.localStorage.setItem('foodlabelflow.demoIdentity', selection)
  } catch {
    // The active in-memory identity remains usable when browser storage is unavailable.
  }
  publish({ status, generation, selection, actor: null, error: '' })
  return generation
}

function validateActor(selection: DemoIdentityKey, actor: CurrentIdentity): string | null {
  const missing = requiredPermissions[selection].filter(permission => !actor.permissions.includes(permission))
  return missing.length === 0
    ? null
    : `The connected account lacks the ${selection.toLowerCase()} permissions: ${missing.join(', ')}.`
}

function inferDemoIdentity(actor: CurrentIdentity): DemoIdentityKey | null {
  return demoIdentityChoices.find(choice =>
    requiredPermissions[choice.key].every(permission => actor.permissions.includes(permission)),
  )?.key ?? null
}

export async function initializeIdentitySession(
  readCurrentIdentity: () => Promise<CurrentIdentity>,
) {
  if (initialized) return
  initialized = true
  const initialGeneration = snapshot.generation
  try {
    const actor = await readCurrentIdentity()
    if (snapshot.status === 'switching' || (snapshot.generation !== initialGeneration &&
        !(subjectOverrideEnabled && snapshot.generation === initialGeneration + 1 &&
          snapshot.selection === 'MAKER' && snapshot.status === 'loading'))) return
    const selection = inferDemoIdentity(actor)
    if (subjectOverrideEnabled) {
      const validationError = validateActor(snapshot.selection, actor)
      publish(validationError
        ? { ...snapshot, status: 'unavailable', actor: null, error: validationError }
        : { ...snapshot, status: 'ready', actor, error: '' })
    } else {
      publish({ ...snapshot, selection: selection ?? snapshot.selection, status: 'ready', actor, error: '' })
    }
  } catch (cause: unknown) {
    if (snapshot.status === 'switching' || (snapshot.generation !== initialGeneration &&
        !(subjectOverrideEnabled && snapshot.generation === initialGeneration + 1 &&
          snapshot.selection === 'MAKER' && snapshot.status === 'loading'))) return
    publish({ ...snapshot, status: 'unavailable', actor: null,
      error: cause instanceof Error ? cause.message : 'The current identity could not be loaded.' })
  }
}

export async function switchDemoIdentity(
  selection: DemoIdentityKey,
  readCurrentIdentity: () => Promise<CurrentIdentity>,
) {
  if (!demoIdentityChoices.some(choice => choice.key === selection)) {
    throw new Error('Unsupported demo identity.')
  }
  const generation = startTransition(selection, 'switching')
  try {
    const actor = await readCurrentIdentity()
    if (!isIdentityGenerationCurrent(generation)) return
    const validationError = validateActor(selection, actor)
    if (validationError) {
      publish({ ...snapshot, status: 'unavailable', actor: null, error: validationError })
      return
    }
    publish({ ...snapshot, status: 'ready', actor, error: '' })
  } catch (cause: unknown) {
    if (!isIdentityGenerationCurrent(generation)) return
    publish({ ...snapshot, status: 'unavailable', actor: null,
      error: cause instanceof Error ? cause.message : 'The selected identity could not be verified.' })
  }
}

export async function refreshIdentity(readCurrentIdentity: () => Promise<CurrentIdentity>) {
  const generation = startTransition(snapshot.selection, 'switching')
  try {
    const actor = await readCurrentIdentity()
    if (!isIdentityGenerationCurrent(generation)) return
    const validationError = subjectOverrideEnabled ? validateActor(snapshot.selection, actor) : null
    if (validationError) {
      publish({ ...snapshot, status: 'unavailable', actor: null, error: validationError })
    } else {
      publish({ ...snapshot, status: 'ready', actor, error: '' })
    }
  } catch (cause: unknown) {
    if (!isIdentityGenerationCurrent(generation)) return
    publish({ ...snapshot, status: 'unavailable', actor: null,
      error: cause instanceof Error ? cause.message : 'The current identity could not be refreshed.' })
  }
}

export function beginIdentityRequest(path: string): { controller: AbortController; generation: number } {
  if (snapshot.status === 'switching'
      && !path.startsWith('/api/identity/current')
      && !path.startsWith('/api/identity/demo-options')) {
    throw new Error('Identity switch is in progress; wait for the new permissions to load.')
  }
  const controller = new AbortController()
  const generation = trackIdentityRequest(controller)
  return { controller, generation }
}

export function beginDemoIdentityRefresh(): number {
  initialized = true
  return startTransition(snapshot.selection, 'switching')
}
