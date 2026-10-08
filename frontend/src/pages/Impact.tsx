import { createChangeRequest, getImpactAnalysis, listChangeRequests, runImpactAnalysis,
  type ChangeRequest, type ImpactAnalysis } from '@/api/impact'
import { LabelApiError } from '@/api/labels'
import { Panel, SectionHead } from '@/components/catalog-shared'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { data } from '@/data/catalog'
import { catalogList } from '@/api/catalog'
import { CurrentIdentityPanel, useCurrentIdentity } from '@/components/CurrentIdentityPanel'
import { useEffect, useRef, useState } from 'react'
import { Link } from 'react-router-dom'

export function Impact() {
  const [changes, setChanges] = useState<ChangeRequest[]>([])
  const [changeId, setChangeId] = useState('')
  const [ruleSetId, setRuleSetId] = useState('ruleset_us_falcpa_demo_v1')
  const [runId, setRunId] = useState('')
  const [analysis, setAnalysis] = useState<ImpactAnalysis | null>(null)
  const [materialId, setMaterialId] = useState(data.supplier_material[0]?.supplier_material_id ?? '')
  const [previousId, setPreviousId] = useState('')
  const [targetId, setTargetId] = useState('')
  const [description, setDescription] = useState('')
  const [identityEnabled, setIdentityEnabled] = useState(false)
  const [busy, setBusy] = useState(false)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [message, setMessage] = useState('')
  const [uncertainCreate, setUncertainCreate] = useState(false)
  const [uncertainRun, setUncertainRun] = useState<{ changeId: string; ruleSetId: string } | null>(null)
  const [readAttempt, setReadAttempt] = useState(0)
  const [specificationAttempt, setSpecificationAttempt] = useState(0)
  const [specifications, setSpecifications] = useState<Array<{ specification_version_id: string }>>([])
  const [specificationsLoading, setSpecificationsLoading] = useState(true)
  const lock = useRef(false)
  const local = ['127.0.0.1', 'localhost', '[::1]'].includes(location.hostname)
  const identity = useCurrentIdentity()
  const selected = changes.find(change => change.changeRequestId === changeId)

  useEffect(() => {
    const controller = new AbortController()
    setSpecificationsLoading(true)
    setSpecifications([])
    catalogList('/specifications', controller.signal).then(rows => {
      if (controller.signal.aborted) return
      const matching = rows.filter(row => row.supplier_material_id === materialId)
      if (matching.some(row => typeof row.specification_version_id !== 'string')) {
        throw new Error('The specification catalog returned an invalid version ID.')
      }
      setSpecifications(matching as Array<{ specification_version_id: string }>)
    }).catch(cause => {
      if (!controller.signal.aborted) setError(cause instanceof Error ? cause.message : 'Specification versions could not be loaded.')
    }).finally(() => { if (!controller.signal.aborted) setSpecificationsLoading(false) })
    return () => controller.abort()
  }, [materialId, specificationAttempt])

  useEffect(() => {
    const controller = new AbortController()
    setLoading(true)
    listChangeRequests(controller.signal).then(result => {
      if (controller.signal.aborted) return
      setChanges(result)
      setChangeId(current => current || result[0]?.changeRequestId || '')
    }).catch(cause => {
      if (!controller.signal.aborted) setError(cause instanceof Error ? cause.message : 'Change requests could not be loaded.')
    }).finally(() => { if (!controller.signal.aborted) setLoading(false) })
    return () => controller.abort()
  }, [readAttempt])

  function displayError(cause: unknown, write: 'create' | 'run' | null = null,
    runTarget?: { changeId: string; ruleSetId: string }) {
    const apiError = cause instanceof LabelApiError ? cause : null
    setError((apiError?.code ?? 'NETWORK_ERROR') + ': ' + (cause instanceof Error ? cause.message : 'The impact request failed.'))
    if (write && (!apiError || apiError.status >= 500 || ['INVALID_RESPONSE', 'CHANGE_TARGET_MISMATCH', 'IMPACT_TARGET_MISMATCH'].includes(apiError.code))) {
      if (write === 'create') setUncertainCreate(true)
      else setUncertainRun(runTarget ?? uncertainRun ?? { changeId, ruleSetId: ruleSetId.trim() })
      setMessage(write === 'create'
        ? 'Change creation is unconfirmed. Verify the server before creating another change; reading an unrelated change does not clear this guard.'
        : 'Analysis outcome is unconfirmed. Load its exact run ID, or repeat the same idempotent change and rule-set request.')
    }
  }

  async function create() {
    if (lock.current || !local || !identityEnabled || !identity.hasPermission('CHANGE_REQUEST.CREATE') || uncertainCreate || uncertainRun || specificationsLoading || !previousId || !targetId || !description.trim()) return
    lock.current = true; setBusy(true); setError(''); setMessage('')
    try {
      const created = await createChangeRequest({ changeType: 'INGREDIENT_SPEC', supplierMaterialId: materialId,
        previousSpecificationVersionId: previousId, targetSpecificationVersionId: targetId, description: description.trim() })
      setChanges(current => [created, ...current]); setChangeId(created.changeRequestId); setAnalysis(null)
      setMessage('Created change request ' + created.changeRequestId + '.')
      setIdentityEnabled(false)
    } catch (cause: unknown) { displayError(cause, 'create') }
    finally { lock.current = false; setBusy(false) }
  }

  async function run() {
    if (lock.current || !local || !identityEnabled || !identity.hasPermission('IMPACT.RUN') || !changeId || !ruleSetId.trim()) return
    const target = uncertainRun ?? { changeId, ruleSetId: ruleSetId.trim() }
    lock.current = true; setBusy(true); setError(''); setMessage(''); setAnalysis(null)
    try {
      const loaded = await runImpactAnalysis(target.changeId, target.ruleSetId)
      setAnalysis(loaded); setRunId(loaded.impactAnalysisId); setChangeId(loaded.changeRequestId)
      setRuleSetId(loaded.ruleSetVersionId); setUncertainRun(null); setIdentityEnabled(false)
      setMessage('Analysis ' + loaded.impactAnalysisId + ' read from the server: ' + loaded.relevantProductCount + ' findings.')
    } catch (cause: unknown) { displayError(cause, 'run', target) }
    finally { lock.current = false; setBusy(false) }
  }

  async function load() {
    if (lock.current || !runId.trim()) return
    lock.current = true; setBusy(true); setError(''); setAnalysis(null)
    try {
      const loaded = await getImpactAnalysis(runId.trim())
      if (uncertainRun && (loaded.changeRequestId !== uncertainRun.changeId || loaded.ruleSetVersionId !== uncertainRun.ruleSetId)) {
        throw new LabelApiError('IMPACT_TARGET_MISMATCH', 'This run does not identify the unconfirmed analysis command.', 200)
      }
      setAnalysis(loaded); setChangeId(loaded.changeRequestId); setRuleSetId(loaded.ruleSetVersionId); setUncertainRun(null)
      setMessage('Loaded analysis ' + loaded.impactAnalysisId + '.')
    } catch (cause: unknown) { displayError(cause) }
    finally { lock.current = false; setBusy(false) }
  }

  return <div className="impact-page">
    <div className="page-title"><div><h1>Change impact</h1>
      <p>Analyze a specification change and continue each required review with its actual task.</p></div>
      <Badge variant="outline">Live impact APIs</Badge></div>
    <p className="source-notice">A connected, authorized identity is required. Analysis preserves published label history; replacement labels require separate validation, independent review and publication.</p>
    <div className="impact-layout"><Panel><SectionHead title="Specification change" />
      <div className="impact-content">
        <CurrentIdentityPanel state={identity.state} onRefresh={identity.refresh} />
        <label htmlFor="impact-change">Change request</label><select id="impact-change" value={changeId}
          disabled={busy || loading || !!uncertainRun} onChange={event => { setChangeId(event.target.value); setAnalysis(null); setRunId('') }}>
          <option value="">{loading ? 'Loading change requests…' : 'Select a change request'}</option>
          {changes.map(change => <option key={change.changeRequestId} value={change.changeRequestId}>{change.description} · {change.changeRequestId}</option>)}
        </select>
        <Button variant="outline" disabled={busy || loading} onClick={() => setReadAttempt(value => value + 1)}>Refresh change requests</Button>
        <Button variant="outline" asChild><Link to="/materials">Browse materials &amp; specs</Link></Button>
        <dl className="impact-context"><div><dt>Supplier material</dt><dd>{selected?.supplierMaterialId ?? 'Not selected'}</dd></div>
          <div><dt>Before specification</dt><dd>{selected?.previousSpecificationVersionId ?? 'Not selected'}</dd></div>
          <div><dt>After specification</dt><dd>{selected?.targetSpecificationVersionId ?? 'Not selected'}</dd></div></dl>
        <label htmlFor="impact-rule-set">Impact rule set ID</label><input id="impact-rule-set" value={ruleSetId}
          disabled={busy || !!uncertainRun} onChange={event => { setRuleSetId(event.target.value); setAnalysis(null) }} />
        <label className="demo-consent"><input type="checkbox" checked={identityEnabled} disabled={!local || busy}
          onChange={event => setIdentityEnabled(event.target.checked)} />Use the connected identity for impact writes</label>
        <Button disabled={!local || !identityEnabled || !identity.hasPermission('IMPACT.RUN') || busy || !changeId || !ruleSetId.trim()} onClick={run}>
          {uncertainRun ? 'Retry the same impact analysis' : 'Run impact analysis'}</Button>
        <label htmlFor="impact-run-id">Existing impact analysis ID</label><input id="impact-run-id" value={runId}
          disabled={busy} onChange={event => setRunId(event.target.value)} />
        <Button variant="outline" disabled={busy || !runId.trim()} onClick={load}>Load impact analysis</Button>
        <details><summary>Create a specification change request</summary><div className="impact-create">
          <label htmlFor="impact-material">Supplier material</label><select id="impact-material" value={materialId} disabled={busy || !!uncertainRun}
            onChange={event => { setMaterialId(event.target.value); setPreviousId(''); setTargetId('') }}>
            {data.supplier_material.map(material => <option key={material.supplier_material_id} value={material.supplier_material_id}>{material.supplier_material_id}</option>)}
          </select>
          <Button variant="outline" disabled={busy || specificationsLoading} onClick={() => { setPreviousId(''); setTargetId(''); setSpecificationAttempt(value => value + 1) }}>Refresh specification versions</Button>
          <label htmlFor="impact-before">Previous specification</label><select id="impact-before" value={previousId} disabled={busy || specificationsLoading || !!uncertainRun} onChange={event => setPreviousId(event.target.value)}>
            <option value="">Select the previous version</option>{specifications.map(spec => <option key={spec.specification_version_id} value={spec.specification_version_id}>{spec.specification_version_id}</option>)}
          </select>
          <label htmlFor="impact-after">Target specification</label><select id="impact-after" value={targetId} disabled={busy || specificationsLoading || !!uncertainRun} onChange={event => setTargetId(event.target.value)}>
            <option value="">Select the target version</option>{specifications.map(spec => <option key={spec.specification_version_id} value={spec.specification_version_id}>{spec.specification_version_id}</option>)}
          </select>
          <label htmlFor="impact-description">Change description</label><textarea id="impact-description" value={description} maxLength={1000} disabled={busy} onChange={event => setDescription(event.target.value)} />
          <Button disabled={!local || !identityEnabled || !identity.hasPermission('CHANGE_REQUEST.CREATE') || busy || uncertainCreate || !!uncertainRun || specificationsLoading || !previousId || !targetId || previousId === targetId || !description.trim()} onClick={create}>Create change request</Button>
        </div></details>
        {!local && <p role="alert">Impact writes are disabled outside localhost until approved authentication is connected.</p>}
        {error && <p className="error-notice" role="alert">{error}</p>}
        <p aria-live="polite">{message}</p>
      </div></Panel>
      <Panel><SectionHead title="Analysis results" /><section className="impact-results" aria-label="Analysis results">
        {!analysis && <><h3>No analysis loaded</h3><p>Run or load an exact analysis. This empty state does not mean no products are affected.</p></>}
        {analysis && <><h3>{analysis.impactAnalysisId}</h3><p>{analysis.relevantProductCount} findings · {analysis.noActionCount} no action · {analysis.reviewRequiredCount} review required</p>
          <p>Change {analysis.changeRequestId} · Rule set {analysis.ruleSetVersionId}</p>
          <div className="impact-findings">{analysis.findings.map(finding => <article key={finding.impactFindingId}>
            <h4>{finding.productId}</h4><Badge variant="outline">{finding.outcome}</Badge><p>{finding.explanation}</p>
            <p>Current formula: {finding.currentFormulaVersionId}<br />Proposed formula: {finding.proposedFormulaVersionId}</p>
            {finding.missingAllergenCodes.length > 0 && <p>Missing declarations: {finding.missingAllergenCodes.join(', ')}</p>}
            {finding.reviewTask && <><p>Review task: {finding.reviewTask.reviewTaskId}</p>
              <Button variant="outline" asChild><Link to={'/labels?' + new URLSearchParams({ productId: finding.productId,
                reviewTaskId: finding.reviewTask.reviewTaskId, ...(finding.reviewTask.draftLabelVersionId
                  ? { labelVersionId: finding.reviewTask.draftLabelVersionId } : {}) })}>
                {finding.reviewTask.draftLabelVersionId ? 'Open bound label draft' : 'Prepare replacement label'}</Link></Button></>}
          </article>)}</div></>}
      </section></Panel></div>
  </div>
}
