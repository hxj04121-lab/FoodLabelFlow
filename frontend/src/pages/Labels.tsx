import {
  createLabelDraft,
  getLabelDraft,
  LabelApiError,
  listAllergens,
  type Allergen,
  type LabelDraft,
} from '@/api/labels'
import { Panel, SectionHead, SourceBadge } from '@/components/catalog-shared'
import { LabelValidationPanel } from '@/components/LabelValidationPanel'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { data } from '@/data/catalog'
import {
  AlertTriangle,
  FileText,
  RefreshCw,
  ShieldCheck,
} from 'lucide-react'
import { useEffect, useMemo, useRef, useState } from 'react'

type AllergenState =
  | { status: 'loading' }
  | { status: 'ready'; allergens: Allergen[] }
  | { status: 'error'; message: string }

const BASELINE_JURISDICTION = 'US'

export function Labels() {
  const [allergenAttempt, setAllergenAttempt] = useState(0)
  const [allergenState, setAllergenState] = useState<AllergenState>({
    status: 'loading',
  })
  const [productId, setProductId] = useState(data.product[0]?.product_id ?? '')
  const [lookupId, setLookupId] = useState('')
  const [draft, setDraft] = useState<LabelDraft | null>(null)
  const [demoEnabled, setDemoEnabled] = useState(false)
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState('')
  const [error, setError] = useState('')
  const [uncertain, setUncertain] = useState(false)
  const writeLock = useRef(false)
  const local = ['127.0.0.1', 'localhost', '[::1]'].includes(location.hostname)

  const product = useMemo(
    () => data.product.find((candidate) => candidate.product_id === productId),
    [productId],
  )
  const formula = useMemo(
    () =>
      data.formula_version.find(
        (candidate) =>
          candidate.formula_version_id === product?.current_formula_version_id,
      ),
    [product],
  )

  useEffect(() => {
    const controller = new AbortController()
    let active = true
    const timeout = setTimeout(() => controller.abort(), 15_000)
    setAllergenState({ status: 'loading' })

    listAllergens(BASELINE_JURISDICTION, controller.signal)
      .then((allergens) => {
        if (active) setAllergenState({ status: 'ready', allergens })
      })
      .catch((requestError: unknown) => {
        if (!active) return
        setAllergenState({
          status: 'error',
          message: controller.signal.aborted
            ? 'The allergen API request timed out.'
            : requestError instanceof Error
              ? requestError.message
              : 'The allergen API request failed.',
        })
      })
      .finally(() => clearTimeout(timeout))

    return () => {
      active = false
      controller.abort()
      clearTimeout(timeout)
    }
  }, [allergenAttempt])

  function selectProduct(nextProductId: string) {
    setProductId(nextProductId)
    setDraft(null)
    setLookupId('')
    setMessage('')
    setError('')
    setUncertain(false)
  }

  async function createDraft() {
    if (
      writeLock.current ||
      !local ||
      !demoEnabled ||
      !productId ||
      !product?.current_formula_version_id ||
      uncertain
    ) {
      return
    }
    writeLock.current = true
    setBusy(true)
    setError('')
    setMessage('')
    try {
      const created = await createLabelDraft(productId, BASELINE_JURISDICTION)
      setDraft(created)
      setLookupId(created.labelVersionId)
      setDemoEnabled(false)
      setMessage(`Label draft V${created.versionNumber} created and read from the server.`)
    } catch (requestError: unknown) {
      const apiError = requestError instanceof LabelApiError ? requestError : null
      setError(
        `${apiError?.code ?? 'NETWORK_ERROR'}: ${
          requestError instanceof Error ? requestError.message : 'Draft creation failed.'
        }`,
      )
      if (!apiError || apiError.status >= 500) {
        setUncertain(true)
        setMessage(
          'The write outcome may be unknown. Do not create another draft; load the expected label ID or verify the server before retrying.',
        )
      }
    } finally {
      writeLock.current = false
      setBusy(false)
    }
  }

  async function loadDraft() {
    const exactId = lookupId.trim()
    if (!exactId || busy) return
    setBusy(true)
    setError('')
    setMessage('')
    try {
      const loaded = await getLabelDraft(exactId)
      setDraft(loaded)
      setProductId(loaded.productId)
      setUncertain(false)
      setMessage(`Loaded ${loaded.labelVersionId} from the server.`)
    } catch (requestError: unknown) {
      const apiError = requestError instanceof LabelApiError ? requestError : null
      setError(
        `${apiError?.code ?? 'NETWORK_ERROR'}: ${
          requestError instanceof Error ? requestError.message : 'Draft read failed.'
        }`,
      )
    } finally {
      setBusy(false)
    }
  }

  return (
    <>
      <div className="page-title">
        <div>
          <div className="eyebrow">LABEL CORRECTNESS</div>
          <h1>Labels</h1>
          <p>Create and reopen a versioned label draft from the current formula.</p>
        </div>
        <Badge variant="outline">SCRUM-18 · Partial integration</Badge>
      </div>

      <div className="source-notice">
        <ShieldCheck size={15} />
        <span>
          Live catalog and label APIs only · {BASELINE_JURISDICTION} baseline · Offline seed data is never substituted
        </span>
      </div>

      <div className="label-workspace">
        <Panel className="label-draft-panel">
          <SectionHead
            title="Label draft context"
            caption="Create from the selected product's current released formula"
            action={<SourceBadge />}
          />

          <div className="label-form-grid">
            <label>
              Product
              <select
                aria-label="Product"
                value={productId}
                disabled={busy}
                onChange={(event) => selectProduct(event.target.value)}
              >
                {data.product.map((candidate) => (
                  <option key={candidate.product_id} value={candidate.product_id}>
                    {candidate.product_description} · {candidate.product_id}
                  </option>
                ))}
              </select>
            </label>

            <div className="label-formula-summary">
              <span>Current formula</span>
              <strong>{product?.current_formula_version_id ?? 'Not available'}</strong>
              <small>
                {formula
                  ? `V${formula.version_number} · ${formula.lifecycle_status}`
                  : 'A released formula is required before draft creation.'}
              </small>
            </div>
          </div>

          <label className="demo-consent label-demo-consent">
            <input
              type="checkbox"
              checked={demoEnabled}
              disabled={!local || busy}
              onChange={(event) => setDemoEnabled(event.target.checked)}
            />
            Enable the local demo label-officer identity for this form
          </label>
          {!local && (
            <p role="alert">
              Draft writes are disabled outside localhost. Configure approved authentication before deployment.
            </p>
          )}

          {error && (
            <p className="error-notice" role="alert">
              {error}
            </p>
          )}
          <p className="label-operation-message" aria-live="polite">
            {message}
          </p>

          <div className="label-actions">
            <Button
              disabled={
                !local ||
                !demoEnabled ||
                busy ||
                uncertain ||
                !product?.current_formula_version_id
              }
              onClick={createDraft}
            >
              {busy ? 'Working…' : 'Create label draft'}
            </Button>
            <div className="label-lookup">
              <label htmlFor="label-version-id">Existing label version ID</label>
              <input
                id="label-version-id"
                value={lookupId}
                disabled={busy}
                placeholder="label_…"
                onChange={(event) => setLookupId(event.target.value)}
              />
              <Button
                variant="outline"
                disabled={busy || !lookupId.trim()}
                onClick={loadDraft}
              >
                Load label draft
              </Button>
            </div>
          </div>

          {draft && (
            <section className="label-draft-result" aria-label="Label draft details">
              <div className="label-result-heading">
                <div>
                  <span>Server label version</span>
                  <h2>{draft.labelVersionId}</h2>
                </div>
                <Badge variant="outline">{draft.lifecycleStatus}</Badge>
              </div>
              <dl>
                <div>
                  <dt>Version</dt>
                  <dd>V{draft.versionNumber}</dd>
                </div>
                <div>
                  <dt>Formula</dt>
                  <dd>{draft.formulaVersionId}</dd>
                </div>
                <div>
                  <dt>Rule set</dt>
                  <dd>{draft.ruleSetVersionId}</dd>
                </div>
                <div>
                  <dt>Jurisdiction</dt>
                  <dd>{draft.jurisdictionCode}</dd>
                </div>
                <div>
                  <dt>Created by</dt>
                  <dd>{draft.createdByUserId}</dd>
                </div>
                <div>
                  <dt>Created at</dt>
                  <dd>{new Date(draft.createdAt).toLocaleString('en-GB')}</dd>
                </div>
              </dl>
              <div className="label-ingredients">
                <span>Raw ingredient text</span>
                <p>{draft.rawIngredientText || 'No raw ingredient text returned.'}</p>
              </div>
            </section>
          )}
        </Panel>

        <Panel className="label-upstream-panel">
          <SectionHead
            title="Upstream data status"
            caption="Independent from draft creation and reads"
          />
          {allergenState.status === 'loading' && (
            <div className="label-upstream-state" role="status">
              <RefreshCw className="animate-spin" size={22} />
              Checking the canonical allergen endpoint…
            </div>
          )}
          {allergenState.status === 'error' && (
            <div className="label-upstream-state label-upstream-error">
              <AlertTriangle size={22} />
              <div>
                <strong>Canonical allergen HTTP API unavailable</strong>
                <p role="alert">{allergenState.message}</p>
                <Button
                  variant="outline"
                  onClick={() => setAllergenAttempt((value) => value + 1)}
                >
                  Retry check
                </Button>
              </div>
            </div>
          )}
          {allergenState.status === 'ready' && (
            <div className="label-upstream-state">
              <ShieldCheck size={22} />
              <div>
                <strong>Canonical allergen endpoint connected</strong>
                <p>{allergenState.allergens.length} entries returned for US.</p>
              </div>
            </div>
          )}
          <div className="label-dependency-note">
            <FileText size={23} />
            <h2>Derived facts and declarations pending</h2>
            <p>
              The merged draft response does not expose declarations or derived allergen facts. This UI does not invent them or read another module's database.
            </p>
            <div className="label-dependencies">
              <Badge variant="outline">SCRUM-42 · Core merged</Badge>
              <Badge variant="outline">SCRUM-45 · HTTP pending</Badge>
            </div>
          </div>
        </Panel>
      </div>
      <LabelValidationPanel draft={draft} />
    </>
  )
}
