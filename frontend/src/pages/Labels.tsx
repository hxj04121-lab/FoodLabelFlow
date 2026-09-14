import { listAllergens, type Allergen } from '@/api/labels'
import { Panel, SectionHead, SourceBadge } from '@/components/catalog-shared'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { AlertTriangle, FileText, RefreshCw, ShieldCheck } from 'lucide-react'
import { useEffect, useState } from 'react'

type LoadState =
  | { status: 'loading' }
  | { status: 'ready'; allergens: Allergen[] }
  | { status: 'error'; message: string }

const BASELINE_JURISDICTION = 'US'

export function Labels() {
  const [attempt, setAttempt] = useState(0)
  const [state, setState] = useState<LoadState>({ status: 'loading' })

  useEffect(() => {
    const controller = new AbortController()
    let active = true
    const timeout = setTimeout(() => controller.abort(), 15_000)
    setState({ status: 'loading' })

    listAllergens(BASELINE_JURISDICTION, controller.signal)
      .then((allergens) => {
        if (active) setState({ status: 'ready', allergens })
      })
      .catch((error: unknown) => {
        if (!active) return
        setState({
          status: 'error',
          message: controller.signal.aborted
            ? 'The label API request timed out.'
            : error instanceof Error
              ? error.message
              : 'The label API request failed.',
        })
      })
      .finally(() => clearTimeout(timeout))

    return () => {
      active = false
      controller.abort()
      clearTimeout(timeout)
    }
  }, [attempt])

  return (
    <>
      <div className="page-title">
        <div>
          <div className="eyebrow">LABEL CORRECTNESS</div>
          <h1>Labels</h1>
          <p>Prepare a traceable label against the canonical allergen rules.</p>
        </div>
        <Badge variant="outline">Sprint 2 foundation</Badge>
      </div>

      <div className="source-notice">
        <ShieldCheck size={15} />
        <span>
          Live label API only · {BASELINE_JURISDICTION} baseline · Offline seed data is never substituted
        </span>
      </div>

      {state.status === 'loading' && (
        <Panel className="label-state" >
          <RefreshCw className="animate-spin" size={28} />
          <h2>Loading canonical allergens</h2>
          <p role="status">Connecting to the label API and checking the frozen Sprint 2 contract.</p>
        </Panel>
      )}

      {state.status === 'error' && (
        <Panel className="label-state label-state-error">
          <AlertTriangle size={30} />
          <h2>Label API unavailable</h2>
          <p role="alert">{state.message}</p>
          <p>No preview or seed result has been shown in its place.</p>
          <Button onClick={() => setAttempt((value) => value + 1)}>
            <RefreshCw size={16} /> Retry connection
          </Button>
        </Panel>
      )}

      {state.status === 'ready' && state.allergens.length === 0 && (
        <Panel className="label-state">
          <FileText size={30} />
          <h2>No canonical allergens returned</h2>
          <p>The API is connected, but the US jurisdiction has no allergen entries.</p>
          <Button variant="outline" onClick={() => setAttempt((value) => value + 1)}>
            <RefreshCw size={16} /> Refresh
          </Button>
        </Panel>
      )}

      {state.status === 'ready' && state.allergens.length > 0 && (
        <div className="label-layout">
          <Panel className="label-catalog">
            <SectionHead
              title="Canonical allergens"
              caption={`${state.allergens.length} entries returned for ${BASELINE_JURISDICTION}`}
              action={<SourceBadge />}
            />
            <ul className="allergen-grid" aria-label="Canonical allergens">
              {state.allergens.map((allergen) => (
                <li key={allergen.allergenId}>
                  <span className="allergen-code">{allergen.allergenCode}</span>
                  <strong>{allergen.displayName}</strong>
                  <small>{allergen.jurisdictionCode}</small>
                </li>
              ))}
            </ul>
          </Panel>

          <Panel className="label-next">
            <FileText size={27} />
            <h2>Draft and validation controls</h2>
            <p>
              The page foundation is connected. Formula context, label drafts and validation results are delivered in the next scoped tasks.
            </p>
            <div className="label-dependencies">
              <Badge variant="outline">SCRUM-18 · Draft context</Badge>
              <Badge variant="outline">SCRUM-19 · Validation feedback</Badge>
            </div>
          </Panel>
        </div>
      )}
    </>
  )
}
