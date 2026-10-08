import { getCurrentIdentity, getDemoIdentityOptions, type CurrentIdentity } from '@/api/identity'
import {
  demoIdentityChoices,
  getIdentitySessionSnapshot,
  initializeIdentitySession,
  refreshIdentity,
  subscribeIdentitySession,
  switchDemoIdentity,
} from '@/api/identity-session'
import { useEffect, useSyncExternalStore } from 'react'
import { Button } from './ui/button'

export type CurrentIdentityState =
  | { status: 'loading' }
  | { status: 'ready'; actor: CurrentIdentity }
  | { status: 'unavailable'; message: string }

export function useCurrentIdentity() {
  const session = useSyncExternalStore(
    subscribeIdentitySession,
    getIdentitySessionSnapshot,
    getIdentitySessionSnapshot,
  )

  useEffect(() => {
    void initializeIdentitySession(getCurrentIdentity)
  }, [])

  const state: CurrentIdentityState = session.status === 'ready' && session.actor
    ? { status: 'ready', actor: session.actor }
    : session.status === 'unavailable'
      ? { status: 'unavailable', message: session.error }
      : { status: 'loading' }

  return {
    state,
    session,
    refresh: () => refreshIdentity(getCurrentIdentity),
    hasPermission: (permission: string) => state.status === 'ready' && state.actor.permissions.includes(permission),
  }
}

export function CurrentIdentityPanel({ state, onRefresh }: { state: CurrentIdentityState; onRefresh: () => void }) {
  return <section className="connected-identity" aria-label="Connected identity">
    {state.status === 'loading' && <p role="status">Checking the connected identity and permissions…</p>}
    {state.status === 'ready' && <>
      <p><strong>{state.actor.displayName}</strong> · {state.actor.userId}</p>
      <p>Roles: {state.actor.roles.join(', ') || 'None'}</p>
      <details><summary>Granted permissions</summary><p>{state.actor.permissions.join(', ') || 'None'}</p></details>
    </>}
    {state.status === 'unavailable' && <p>Connected identity unavailable: {state.message} Writes requiring this identity remain disabled.</p>}
    <Button variant="outline" disabled={state.status === 'loading'} onClick={onRefresh}>Refresh connected identity</Button>
  </section>
}

export function ControlledDemoIdentitySwitcher({ showStatus = true }: { showStatus?: boolean }) {
  const identity = useCurrentIdentity()
  const localDemoHost = ['127.0.0.1', 'localhost', '[::1]'].includes(location.hostname)
  if (!localDemoHost) return null

  return <label className="identity-switcher">
    <span>Demo identity</span>
    <select
      aria-label="Demo identity"
      value={identity.session.selection}
      disabled={identity.session.status === 'switching'}
      onChange={event => {
        const choice = demoIdentityChoices.find(item => item.key === event.target.value)
        if (choice) void switchDemoIdentity(choice.key, async () => {
          const options = await getDemoIdentityOptions()
          if (!options.some(option => option.key === choice.key && option.subject === choice.subject)) {
            throw new Error(`The server does not expose the ${choice.label} demo identity.`)
          }
          return getCurrentIdentity()
        })
      }}
    >
      {demoIdentityChoices.map(choice => <option key={choice.key} value={choice.key}>{choice.label}</option>)}
    </select>
    {showStatus && <span aria-live="polite">
      {identity.session.status === 'switching'
        ? 'Refreshing identity and permissions…'
        : identity.session.status === 'ready'
          ? identity.session.actor?.displayName ?? ''
          : identity.session.error || 'Identity unavailable'}
    </span>}
  </label>
}
