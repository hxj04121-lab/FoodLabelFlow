import {
  createLabelDraft,
  getLabelDraft,
  getLabelDeclarations,
  LabelApiError,
  listAllergens,
  type Allergen,
  type LabelDraft,
  type ValidationRun,
} from '@/api/labels'
import { catalogGet } from '@/api/catalog'
import { getReviewTask } from '@/api/label-workflow'
import { useCurrentIdentity } from '@/components/CurrentIdentityPanel'
import { Panel, SectionHead, SourceBadge } from '@/components/catalog-shared'
import { LabelValidationPanel } from '@/components/LabelValidationPanel'
import { LabelAllergenPanel } from '@/components/LabelAllergenPanel'
import { LabelDeclarationsPanel } from '@/components/LabelDeclarationsPanel'
import { LabelWorkflowPanel } from '@/components/LabelWorkflowPanel'
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
import { useSearchParams } from 'react-router-dom'

type AllergenState =
  | { status: 'loading' }
  | { status: 'ready'; allergens: Allergen[] }
  | { status: 'error'; message: string }

const BASELINE_JURISDICTION = 'US'
const UNCONFIRMED_DRAFT_MESSAGE =
  'The earlier draft creation is still unconfirmed. Verify its outcome on the server before creating another draft.'

export function Labels() {
  const [searchParams] = useSearchParams()
  const [allergenAttempt, setAllergenAttempt] = useState(0)
  const [allergenState, setAllergenState] = useState<AllergenState>({
    status: 'loading',
  })
  const [productId, setProductId] = useState(searchParams.get('productId') ?? data.product[0]?.product_id ?? '')
  const [lookupId, setLookupId] = useState('')
  const [draft, setDraft] = useState<LabelDraft | null>(null)
  const [validationRun, setValidationRun] = useState<ValidationRun | null>(null)
  const [reviewTaskId, setReviewTaskId] = useState(searchParams.get('reviewTaskId') ?? '')
  const [declarationIds, setDeclarationIds] = useState<string[]>([])
  const [currentFormulaId, setCurrentFormulaId] = useState<string | null>(null)
  const [formulaLoading, setFormulaLoading] = useState(true)
  const [formulaError, setFormulaError] = useState('')
  const [demoEnabled, setDemoEnabled] = useState(false)
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState('')
  const [error, setError] = useState('')
  const [uncertain, setUncertain] = useState(false)
  const [pendingCreation, setPendingCreation] = useState<{
    reviewTaskId: string
    productId: string
    formulaVersionId: string
    declarations: Array<{ allergenId: string; declarationType: 'CONTAINS'; displayText: string }>
  } | null>(null)
  const writeLock = useRef(false)
  const local = ['127.0.0.1', 'localhost', '[::1]'].includes(location.hostname)
  const identity = useCurrentIdentity()
  const connected = identity.state.status === 'ready'
  const canCreate = connected && identity.hasPermission('LABEL.CREATE')

  const product = useMemo(
    () => data.product.find((candidate) => candidate.product_id === productId),
    [productId],
  )
  const formula = useMemo(
    () =>
      data.formula_version.find(
        (candidate) =>
          candidate.formula_version_id === currentFormulaId,
      ),
    [currentFormulaId],
  )

  useEffect(() => {
    const controller = new AbortController()
    setFormulaLoading(true)
    setFormulaError('')
    setCurrentFormulaId(null)
    catalogGet(`/products/${encodeURIComponent(productId)}`, controller.signal)
      .then(value => {
        if (controller.signal.aborted) return
        const product = value as { product_id?: unknown; current_formula_version_id?: unknown }
        if (product?.product_id !== productId ||
            !(product.current_formula_version_id === null || typeof product.current_formula_version_id === 'string')) {
          throw new Error('The catalog API returned an invalid current product formula.')
        }
        setCurrentFormulaId(product.current_formula_version_id)
      })
      .catch(cause => { if (!controller.signal.aborted) setFormulaError(cause instanceof Error ? cause.message : 'Current formula read failed.') })
      .finally(() => { if (!controller.signal.aborted) setFormulaLoading(false) })
    return () => controller.abort()
  }, [productId])

  useEffect(() => {
    const exactId = searchParams.get('labelVersionId')?.trim()
    if (!exactId) return
    const controller = new AbortController()
    setBusy(true)
    getLabelDraft(exactId, controller.signal).then(loaded => {
      if (controller.signal.aborted) return
      setDraft(loaded)
      setLookupId(loaded.labelVersionId)
      setProductId(loaded.productId)
      setMessage(`Loaded ${loaded.labelVersionId} from the server.`)
    }).catch(cause => {
      if (!controller.signal.aborted) setError(cause instanceof Error ? cause.message : 'Label read failed.')
    }).finally(() => { if (!controller.signal.aborted) setBusy(false) })
    return () => controller.abort()
  }, [searchParams])

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
    setValidationRun(null)
    setDeclarationIds([])
    setReviewTaskId('')
    setLookupId('')
    setMessage(uncertain ? UNCONFIRMED_DRAFT_MESSAGE : '')
    setError('')
  }

  async function createDraft() {
    if (
      writeLock.current ||
      !local ||
      !demoEnabled ||
      !canCreate ||
      allergenState.status !== 'ready' ||
      !productId ||
      !currentFormulaId || formulaLoading ||
      uncertain
    ) {
      return
    }
    writeLock.current = true
    setBusy(true)
    setError('')
    setMessage('')
    const selectedDeclarations = allergenState.status === 'ready'
      ? allergenState.allergens.filter(allergen => declarationIds.includes(allergen.allergenId))
        .map(allergen => ({ allergenId: allergen.allergenId, declarationType: 'CONTAINS' as const,
          displayText: `Contains ${allergen.displayName}` })) : []
    const requestedTaskId = reviewTaskId.trim()
    const requestedFormulaId = currentFormulaId
    try {
      const created = await createLabelDraft(productId, BASELINE_JURISDICTION, undefined, {
        declarations: selectedDeclarations,
        ...(requestedTaskId ? { reviewTaskId: requestedTaskId } : {}),
      }, true)
      if (created.productId !== productId || created.jurisdictionCode !== BASELINE_JURISDICTION ||
          created.formulaVersionId !== currentFormulaId || created.lifecycleStatus !== 'DRAFT' ||
          (identity.state.status === 'ready' && created.createdByUserId !== identity.state.actor.userId)) {
        throw new LabelApiError('DRAFT_CONTEXT_MISMATCH', 'The created draft does not match the selected current product formula and jurisdiction.', 200)
      }
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
      if (!apiError || apiError.status >= 500 || ['INVALID_RESPONSE', 'DRAFT_CONTEXT_MISMATCH'].includes(apiError.code)) {
        setUncertain(true)
        if (requestedTaskId && requestedFormulaId) setPendingCreation({
          reviewTaskId: requestedTaskId, productId, formulaVersionId: requestedFormulaId,
          declarations: selectedDeclarations,
        })
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
      // Reading an existing version does not identify the outcome of that creation command.
      setMessage(
        `Loaded ${loaded.labelVersionId} from the server.` +
        (uncertain ? ` ${UNCONFIRMED_DRAFT_MESSAGE}` : ''),
      )
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

  async function loadTaskDraft() {
    const exactTaskId = pendingCreation?.reviewTaskId ?? reviewTaskId.trim()
    if (!exactTaskId || busy || writeLock.current) return
    setBusy(true)
    setError('')
    setMessage('')
    try {
      const task = await getReviewTask(exactTaskId)
      if (!task.draftLabelVersionId) {
        setMessage(`Review task ${exactTaskId} has no bound draft.` + (uncertain ? ` ${UNCONFIRMED_DRAFT_MESSAGE}` : ''))
        return
      }
      const loaded = await getLabelDraft(task.draftLabelVersionId)
      if (task.productId !== loaded.productId || task.targetLabelVersionId !== loaded.labelVersionId) {
        throw new LabelApiError('REVIEW_TASK_TARGET_MISMATCH', 'The server task and label binding do not match.', 200)
      }
      let confirmed = false
      if (pendingCreation && loaded.productId === pendingCreation.productId &&
          loaded.formulaVersionId === pendingCreation.formulaVersionId && loaded.jurisdictionCode === BASELINE_JURISDICTION) {
        const persisted = await getLabelDeclarations(loaded)
        confirmed = persisted.declarations.length === pendingCreation.declarations.length &&
          pendingCreation.declarations.every(expected => persisted.declarations.some(actual =>
            actual.allergenId === expected.allergenId && actual.declarationType === expected.declarationType &&
            actual.displayText === expected.displayText && actual.declarationSource === 'USER_ENTERED'))
      }
      setDraft(loaded)
      setLookupId(loaded.labelVersionId)
      setProductId(loaded.productId)
      setReviewTaskId(exactTaskId)
      if (confirmed) { setUncertain(false); setPendingCreation(null) }
      setMessage(`Loaded the existing draft ${loaded.labelVersionId} bound to ${exactTaskId}.` +
        (uncertain && !confirmed ? ` ${UNCONFIRMED_DRAFT_MESSAGE}` : ''))
    } catch (cause: unknown) {
      setError(`${cause instanceof LabelApiError ? cause.code : 'NETWORK_ERROR'}: ${cause instanceof Error ? cause.message : 'Review task read failed.'}`)
    } finally { setBusy(false) }
  }

  return (
    <>
      <div className="page-title">
        <div>
          <div className="eyebrow">LABEL CORRECTNESS</div>
          <h1>Labels</h1>
          <p>Create and reopen a versioned label draft from the current formula.</p>
        </div>
        <Badge variant="outline">Version-bound label context</Badge>
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
              <strong>{formulaLoading ? 'Checking current formula…' : currentFormulaId ?? 'Not available'}</strong>
              <small>
                {formula
                  ? `V${formula.version_number} · ${formula.lifecycle_status}`
                  : 'A released formula is required before draft creation.'}
              </small>
            </div>
          </div>

          {formulaError && <p className="error-notice" role="alert">{formulaError}</p>}
          <fieldset className="declaration-entry" disabled={busy}>
            <legend>Declarations for the new draft</legend>
            <p>Select the declarations you intend to record. They are saved with this new version and cannot be edited after creation; validation checks them against the formula.</p>
            {allergenState.status === 'ready' && allergenState.allergens.map(allergen =>
              <label key={allergen.allergenId}><input type="checkbox"
                checked={declarationIds.includes(allergen.allergenId)}
                onChange={event => setDeclarationIds(ids => event.target.checked
                  ? [...ids, allergen.allergenId] : ids.filter(id => id !== allergen.allergenId))} />
                Declare {allergen.displayName} ({allergen.allergenCode})</label>)}
            {allergenState.status !== 'ready' && <p>The canonical allergen catalog must load before declarations can be selected.</p>}
            <label htmlFor="label-review-task-id">Review task ID</label>
            <input id="label-review-task-id" value={reviewTaskId} onChange={event => setReviewTaskId(event.target.value)}
              placeholder="Optional existing impact review task" />
            <Button variant="outline" disabled={busy || (!reviewTaskId.trim() && !pendingCreation)}
              onClick={loadTaskDraft}>Load review task draft</Button>
            <p>A review task binds only its first matching draft. Reopening a bound task loads its existing version.</p>
          </fieldset>

          <label className="demo-consent label-demo-consent">
            <input
              type="checkbox"
              checked={demoEnabled}
              disabled={!local || busy || !canCreate}
              onChange={(event) => setDemoEnabled(event.target.checked)}
            />
            Use the connected identity to create this draft
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
                !canCreate ||
                allergenState.status !== 'ready' ||
                busy ||
                uncertain ||
                formulaLoading || !currentFormulaId
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
            <h2>Version-bound label inputs</h2>
            <p>
              The sections below read the selected label's declarations and derived allergens from their owning APIs. They show distinct inputs to validation.
            </p>
          </div>
        </Panel>
      </div>
      <LabelAllergenPanel
        key={`derived:${productId}:${draft?.labelVersionId ?? ''}:${draft?.formulaVersionId ?? ''}:${draft?.ruleSetVersionId ?? ''}:${draft?.jurisdictionCode ?? ''}`}
        draft={draft}
      />
      <LabelDeclarationsPanel
        key={`declarations:${productId}:${draft?.labelVersionId ?? ''}:${draft?.formulaVersionId ?? ''}:${draft?.ruleSetVersionId ?? ''}:${draft?.jurisdictionCode ?? ''}`}
        draft={draft}
      />
      <LabelValidationPanel
        key={`validation:${productId}:${draft?.labelVersionId ?? ''}:${draft?.ruleSetVersionId ?? ''}`}
        draft={draft}
        onRunChange={setValidationRun}
        identityState={identity.state}
      />
      <LabelWorkflowPanel draft={draft} validationRun={validationRun} reviewTaskId={reviewTaskId}
        onDraftChange={setDraft} identity={identity} />
    </>
  )
}
