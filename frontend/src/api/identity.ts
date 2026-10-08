import { LabelApiError, requestLabelJson } from './labels'

export type CurrentIdentity = {
  userId: string
  username: string
  displayName: string
  roles: string[]
  permissions: string[]
}

export async function getCurrentIdentity(signal?: AbortSignal): Promise<CurrentIdentity> {
  const value = await requestLabelJson('/api/identity/current', { signal }) as CurrentIdentity | null
  if (!value || typeof value !== 'object' ||
      ![value.userId, value.username, value.displayName].every(field => typeof field === 'string' && field.trim().length > 0) ||
      ![value.roles, value.permissions].every(fields => Array.isArray(fields) && fields.every(field => typeof field === 'string' && field.trim().length > 0))) {
    throw new LabelApiError('INVALID_RESPONSE', 'The identity API returned an invalid current identity.', 200)
  }
  return value
}
