import { LabelApiError, requestLabelJson } from './labels'
import { activateDefaultDemoIdentity, isIdentitySelectionControlled } from './identity-session'

export type CurrentIdentity = {
  userId: string
  username: string
  displayName: string
  roles: string[]
  permissions: string[]
}

export type DemoIdentityOption = {
  key: 'MAKER' | 'CHECKER' | 'PUBLISHER'
  subject: string
  actor: CurrentIdentity
}

export async function getCurrentIdentity(signal?: AbortSignal): Promise<CurrentIdentity> {
  let value: CurrentIdentity | null
  try {
    value = await requestLabelJson('/api/identity/current', { signal }) as CurrentIdentity | null
  } catch (cause: unknown) {
    if (!(cause instanceof LabelApiError) || cause.code !== 'AUTHENTICATION_REQUIRED' || isIdentitySelectionControlled()) {
      throw cause
    }
    activateDefaultDemoIdentity()
    value = await requestLabelJson('/api/identity/current', { signal }) as CurrentIdentity | null
  }
  if (!value || typeof value !== 'object' ||
      ![value.userId, value.username, value.displayName].every(field => typeof field === 'string' && field.trim().length > 0) ||
      ![value.roles, value.permissions].every(fields => Array.isArray(fields) && fields.every(field => typeof field === 'string' && field.trim().length > 0))) {
    throw new LabelApiError('INVALID_RESPONSE', 'The identity API returned an invalid current identity.', 200)
  }
  return value
}

export async function getDemoIdentityOptions(signal?: AbortSignal): Promise<DemoIdentityOption[]> {
  const value = await requestLabelJson('/api/identity/demo-options', { signal })
  if (!Array.isArray(value) || value.some(option => {
    if (!option || typeof option !== 'object') return true
    const candidate = option as Partial<DemoIdentityOption>
    return !['MAKER', 'CHECKER', 'PUBLISHER'].includes(candidate.key ?? '')
      || typeof candidate.subject !== 'string'
      || !candidate.actor || typeof candidate.actor !== 'object'
      || typeof candidate.actor.userId !== 'string'
      || !Array.isArray(candidate.actor.permissions)
  })) {
    throw new LabelApiError('INVALID_RESPONSE', 'The demo identity API returned invalid options.', 200)
  }
  return value as DemoIdentityOption[]
}
