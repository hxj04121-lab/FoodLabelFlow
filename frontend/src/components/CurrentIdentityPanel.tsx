import { getCurrentIdentity, type CurrentIdentity } from '@/api/identity'
import { useEffect, useState } from 'react'
import { Button } from './ui/button'

export type CurrentIdentityState =
  | { status: 'loading' }
  | { status: 'ready'; actor: CurrentIdentity }
  | { status: 'unavailable'; message: string }

export function useCurrentIdentity() {
  const [attempt, setAttempt] = useState(0)
  const [state, setState] = useState<CurrentIdentityState>({ status: 'loading' })
  useEffect(() => {
    const controller = new AbortController()
    const timer = setTimeout(() => controller.abort(), 15000)
    let active = true
    setState({ status: 'loading' })
    getCurrentIdentity(controller.signal).then(actor => { if (active) setState({ status: 'ready', actor }) })
      .catch(cause => { if (active) setState({ status: 'unavailable', message: controller.signal.aborted
        ? 'The connected identity read timed out.' : cause instanceof Error ? cause.message : 'The connected identity is unavailable.' }) })
      .finally(() => clearTimeout(timer))
    return () => { active = false; controller.abort(); clearTimeout(timer) }
  }, [attempt])
  return { state, refresh: () => setAttempt(value => value + 1),
    hasPermission: (permission: string) => state.status === 'ready' && state.actor.permissions.includes(permission) }
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
