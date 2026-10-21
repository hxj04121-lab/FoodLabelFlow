import { adoptSpecificationForCurrentProducts, catalogList, type BatchAdoptionSummary } from '@/api/catalog'
import { LabelApiError } from '@/api/labels'
import { Panel, SectionHead } from '@/components/catalog-shared'
import { Button } from '@/components/ui/button'
import { useCurrentIdentity } from '@/components/CurrentIdentityPanel'
import { useEffect, useState } from 'react'

type TargetSpecification = {
  specification_version_id: string
  supplier_material_id: string
  version_number: number
  lifecycle_status: string
  effective_date: string
}

function isReleasedTarget(row: Record<string, unknown>, today: string): row is Record<string, unknown> & TargetSpecification {
  return typeof row.specification_version_id === 'string' &&
    typeof row.supplier_material_id === 'string' &&
    typeof row.version_number === 'number' && Number.isInteger(row.version_number) && row.lifecycle_status === 'RELEASED' &&
    typeof row.effective_date === 'string' && row.effective_date <= today
}

export function BatchAdoptionPanel() {
  const identity = useCurrentIdentity()
  const [targets, setTargets] = useState<TargetSpecification[]>([])
  const [targetId, setTargetId] = useState('')
  const [summary, setSummary] = useState<BatchAdoptionSummary | null>(null)
  const [uncertainTarget, setUncertainTarget] = useState('')
  const [busy, setBusy] = useState(false)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const local = ['127.0.0.1', 'localhost', '[::1]'].includes(location.hostname)
  const authorized = identity.hasPermission('DATA.MAINTAIN') && identity.hasPermission('FORMULA.RELEASE')

  useEffect(() => {
    const controller = new AbortController()
    setTargets([]); setTargetId(''); setSummary(null); setUncertainTarget(''); setError(''); setLoading(true)
    if (identity.state.status !== 'ready') {
      setLoading(false)
      return () => controller.abort()
    }
    catalogList('/specifications', controller.signal).then(rows => {
      if (controller.signal.aborted) return
      const today = new Date().toISOString().slice(0, 10)
      const eligible = rows.filter(row => isReleasedTarget(row, today)) as TargetSpecification[]
      eligible.sort((left, right) => left.supplier_material_id.localeCompare(right.supplier_material_id) ||
        left.version_number - right.version_number)
      setTargets(eligible)
    }).catch(cause => {
      if (!controller.signal.aborted) setError(cause instanceof Error ? cause.message : 'Released specifications could not be loaded.')
    }).finally(() => { if (!controller.signal.aborted) setLoading(false) })
    return () => controller.abort()
  }, [identity.session.generation, identity.state.status])

  async function run(target: string) {
    if (busy || !local || !authorized || !target) return
    setBusy(true); setError('')
    try {
      const result = await adoptSpecificationForCurrentProducts(target)
      setSummary(result); setUncertainTarget('')
    } catch (cause: unknown) {
      setError((cause instanceof LabelApiError ? cause.code + ': ' : '') +
        (cause instanceof Error ? cause.message : 'Batch adoption failed.'))
      const definitiveClientFailure = cause instanceof LabelApiError &&
        cause.status >= 400 && cause.status < 500 &&
        !['IDENTITY_CONTEXT_CHANGED', 'INVALID_RESPONSE', 'ADOPTION_TARGET_MISMATCH'].includes(cause.code)
      if (!definitiveClientFailure) setUncertainTarget(target)
    } finally { setBusy(false) }
  }

  const pendingTarget = uncertainTarget || targetId
  return <Panel>
    <SectionHead title="Batch specification adoption" />
    <section aria-label="Batch specification adoption" className="impact-content">
      <p>Adopts one released, effective specification into each current formula that uses its material. Each product has an independent result.</p>
      {loading && <p role="status">Loading released specifications…</p>}
      <label htmlFor="batch-adoption-target">Released specification</label>
      <select id="batch-adoption-target" value={uncertainTarget || targetId} disabled={busy || loading || !!uncertainTarget}
        onChange={event => { setTargetId(event.target.value); setSummary(null); setError('') }}>
        <option value="">Select a released specification</option>
        {targets.map(target => <option key={target.specification_version_id} value={target.specification_version_id}>
          {target.supplier_material_id} · V{target.version_number} · {target.specification_version_id}
        </option>)}
      </select>
      {!loading && targets.length === 0 && <p>No released, effective specification targets are available.</p>}
      {!authorized && <p role="status">Batch adoption requires both DATA.MAINTAIN and FORMULA.RELEASE on the connected identity.</p>}
      {!local && <p role="alert">Batch writes are disabled outside localhost until approved authentication is connected.</p>}
      <Button disabled={!local || !authorized || busy || loading || !pendingTarget}
        onClick={() => void run(pendingTarget)}>
        {uncertainTarget ? 'Retry the same batch adoption' : busy ? 'Adopting…' : 'Adopt for current products'}
      </Button>
      {error && <p role="alert">{error}{uncertainTarget ? ' The outcome is unconfirmed; retry only this same target or verify its saved product results.' : ''}</p>}
      {summary && <div aria-live="polite" className="batch-adoption-summary">
        <p>Target {summary.targetSpecificationVersionId}: {summary.adoptedCount} adopted, {summary.skippedCount} skipped, {summary.failedCount} failed ({summary.candidateCount} candidates).</p>
        {summary.results.length > 0 && <ul>{summary.results.map(result => <li key={result.productId}>
          {result.productId}: {result.status}{result.formulaVersionId ? ` · ${result.formulaVersionId}` : ''}
          {result.replayed ? ' · replayed' : ''}{result.errorCode ? ` · ${result.errorCode}` : ''}
        </li>)}</ul>}
      </div>}
    </section>
  </Panel>
}
