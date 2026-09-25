import {
  getValidationRun,
  LabelApiError,
  runValidation,
  type LabelDraft,
  type ValidationRun,
} from '@/api/labels'
import { Panel, SectionHead } from '@/components/catalog-shared'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { AlertTriangle, CheckCircle2, CircleSlash2 } from 'lucide-react'
import { useRef, useState } from 'react'

function requireCurrentTarget(run: ValidationRun, draft: LabelDraft): ValidationRun {
  if (
    run.labelVersionId !== draft.labelVersionId ||
    run.ruleSetVersionId !== draft.ruleSetVersionId
  ) {
    throw new LabelApiError(
      'VALIDATION_TARGET_MISMATCH',
      'The validation run does not match the selected label version and rule set.',
      200,
    )
  }
  return run
}

export function LabelValidationPanel({ draft }: { draft: LabelDraft | null }) {
  const [enabled, setEnabled] = useState(false)
  const [busy, setBusy] = useState(false)
  const [run, setRun] = useState<ValidationRun | null>(null)
  const [lookupId, setLookupId] = useState('')
  const [message, setMessage] = useState('')
  const [error, setError] = useState('')
  const [uncertain, setUncertain] = useState(false)
  const writeLock = useRef(false)
  const local = ['127.0.0.1', 'localhost', '[::1]'].includes(location.hostname)

  async function validate() {
    if (!draft || !enabled || !local || busy || uncertain || writeLock.current) return
    writeLock.current = true
    setBusy(true)
    setRun(null)
    setError('')
    setMessage('')
    try {
      const created = requireCurrentTarget(
        await runValidation(draft.labelVersionId, draft.ruleSetVersionId),
        draft,
      )
      setRun(created)
      setLookupId(created.validationRunId)
      setEnabled(false)
      setMessage(
        `Validation ${created.validationRunId} completed with ${created.status}.`,
      )
    } catch (requestError: unknown) {
      const apiError = requestError instanceof LabelApiError ? requestError : null
      setError(
        `${apiError?.code ?? 'NETWORK_ERROR'}: ${
          requestError instanceof Error ? requestError.message : 'Validation failed.'
        }`,
      )
      if (!apiError || apiError.status >= 500 || apiError.code === 'VALIDATION_TARGET_MISMATCH') {
        setUncertain(true)
        setMessage(
          'The validation write outcome may be unknown. Do not submit it again; load the run ID or verify the server first.',
        )
      }
    } finally {
      writeLock.current = false
      setBusy(false)
    }
  }

  async function loadRun() {
    const exactId = lookupId.trim()
    if (!draft || !exactId || busy) return
    setBusy(true)
    setRun(null)
    setError('')
    setMessage('')
    try {
      const loaded = requireCurrentTarget(await getValidationRun(exactId), draft)
      setRun(loaded)
      setUncertain(false)
      setMessage(`Loaded validation run ${loaded.validationRunId}.`)
    } catch (requestError: unknown) {
      const apiError = requestError instanceof LabelApiError ? requestError : null
      setError(
        `${apiError?.code ?? 'NETWORK_ERROR'}: ${
          requestError instanceof Error ? requestError.message : 'Validation read failed.'
        }`,
      )
    } finally {
      setBusy(false)
    }
  }

  return (
    <Panel className="validation-panel">
      <SectionHead
        title="Validation feedback"
        caption="Run and read validation for this exact label version"
        action={
          <Badge variant="outline">
            {draft ? `Rule set ${draft.ruleSetVersionId}` : 'Select a label draft'}
          </Badge>
        }
      />

      {!draft && (
        <div className="validation-empty">
          <CircleSlash2 size={27} />
          <h3>No label draft selected</h3>
          <p>Create or load an exact label version before requesting validation.</p>
        </div>
      )}

      {draft && (
        <div className="validation-controls">
          <div className="validation-target">
            <span>Exact validation target</span>
            <strong>{draft.labelVersionId}</strong>
            <small>{draft.ruleSetVersionId}</small>
          </div>
          <label className="demo-consent validation-consent">
            <input
              type="checkbox"
              checked={enabled}
              disabled={!local || busy}
              onChange={(event) => setEnabled(event.target.checked)}
            />
            Enable the local demo label-officer identity to validate this exact version
          </label>
          {!local && (
            <p role="alert">
              Validation writes are disabled outside localhost. Configure approved authentication before deployment.
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
          <div className="validation-actions">
            <Button
              disabled={!enabled || !local || busy || uncertain}
              onClick={validate}
            >
              {busy ? 'Working…' : 'Run validation'}
            </Button>
            <div className="validation-lookup">
              <label htmlFor="validation-run-id">Existing validation run ID</label>
              <input
                id="validation-run-id"
                value={lookupId}
                disabled={busy}
                placeholder="validation-run-id"
                onChange={(event) => setLookupId(event.target.value)}
              />
              <Button
                variant="outline"
                disabled={busy || !lookupId.trim()}
                onClick={loadRun}
              >
                Load validation run
              </Button>
            </div>
          </div>
        </div>
      )}

      {run && (
        <section className="validation-result" aria-label="Validation run results">
          <div className={`validation-summary validation-${run.status.toLowerCase()}`}>
            {run.status === 'PASSED' ? (
              <CheckCircle2 size={27} />
            ) : (
              <AlertTriangle size={27} />
            )}
            <div>
              <span>Validation run</span>
              <h2>{run.status}</h2>
              <p>
                {run.validationRunId} · {run.results.length} attributable result
                {run.results.length === 1 ? '' : 's'}
              </p>
            </div>
          </div>

          {run.summary && <p className="validation-run-summary">{run.summary}</p>}
          <div className="validation-results-list">
            {run.results.map((result, index) => (
              <article
                className={`validation-result-row ${result.passed ? 'result-pass' : 'result-fail'}`}
                key={`${result.ruleDefinitionId ?? 'input'}-${result.resultCode}-${index}`}
              >
                <div className="validation-result-status">
                  <Badge variant="outline">{result.passed ? 'PASS' : 'FAIL'}</Badge>
                  <strong>{result.resultCode}</strong>
                </div>
                <div className="validation-result-meta">
                  <span>{result.severity}</span>
                  <span>{result.blocking ? 'Blocking' : 'Non-blocking'}</span>
                  <span>{result.ruleDefinitionId ?? 'Input-level finding'}</span>
                </div>
                <p>{result.message}</p>
              </article>
            ))}
          </div>
        </section>
      )}
    </Panel>
  )
}
