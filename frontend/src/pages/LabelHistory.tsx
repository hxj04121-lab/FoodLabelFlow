import { getProductLabelHistory, type ProductLabelHistory } from '@/api/audit-history'
import { catalogList } from '@/api/catalog'
import { CurrentIdentityPanel, useCurrentIdentity } from '@/components/CurrentIdentityPanel'
import { Panel, SectionHead } from '@/components/catalog-shared'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { useEffect, useState } from 'react'

export function LabelHistory() {
  const identity = useCurrentIdentity()
  const [products, setProducts] = useState<Array<{ productId: string; productDisplayName: string }>>([])
  const [productId, setProductId] = useState('')
  const [history, setHistory] = useState<ProductLabelHistory | null>(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [attempt, setAttempt] = useState(0)
  const generation = identity.session.generation
  const canRead = identity.hasPermission('AUDIT.READ')

  useEffect(() => {
    const controller = new AbortController()
    setHistory(null); setProducts([]); setProductId(''); setError('')
    if (identity.state.status !== 'ready' || !canRead) return () => controller.abort()
    catalogList('/products', controller.signal).then(rows => {
      if (controller.signal.aborted) return
      const next = rows.filter(row => typeof row.product_id === 'string' && typeof row.product_description === 'string')
        .map(row => ({ productId: row.product_id as string, productDisplayName: row.product_description as string }))
      setProducts(next); setProductId(next[0]?.productId ?? '')
    }).catch(cause => { if (!controller.signal.aborted) setError(cause instanceof Error ? cause.message : 'Products could not be loaded.') })
    return () => controller.abort()
  }, [generation, identity.state.status, canRead])

  useEffect(() => {
    const controller = new AbortController()
    setHistory(null); setError('')
    if (!productId || identity.state.status !== 'ready' || !canRead) { setLoading(false); return () => controller.abort() }
    setLoading(true)
    getProductLabelHistory(productId, controller.signal)
      .then(value => { if (!controller.signal.aborted) setHistory(value) })
      .catch(cause => { if (!controller.signal.aborted) setError(cause instanceof Error ? cause.message : 'Product label history could not be read.') })
      .finally(() => { if (!controller.signal.aborted) setLoading(false) })
    return () => controller.abort()
  }, [productId, generation, identity.state.status, canRead, attempt])

  return <div className="review-workspace">
    <div className="page-title"><div><h1>Label history</h1><p>Read version snapshots, approvals, publication and attributable audit events.</p></div>
      <Badge variant="outline">AUDIT.READ</Badge></div>
    <CurrentIdentityPanel state={identity.state} onRefresh={identity.refresh} />
    {!canRead && identity.state.status === 'ready' && <p role="alert">This identity cannot read audit history. Switch to Change Manager, Data Admin or an Auditor account.</p>}
    {canRead && <div className="impact-layout"><Panel><SectionHead title="Product" caption="History is read from the server for one exact product" />
      <section className="review-content">
        <label htmlFor="history-product">Product</label>
        <select id="history-product" value={productId} disabled={!products.length || loading} onChange={event => setProductId(event.target.value)}>
          {products.map(product => <option key={product.productId} value={product.productId}>{product.productDisplayName} · {product.productId}</option>)}
        </select>
        <Button variant="outline" disabled={!productId || loading} onClick={() => setAttempt(value => value + 1)}>Refresh history</Button>
        {error && <p role="alert" className="error-notice">{error}</p>}
        {loading && <p role="status">Reading product history…</p>}
        {history && <p>{history.versions.length} label versions · {history.approvals.length} decisions · {history.auditEvents.length} audit events</p>}
      </section>
    </Panel><Panel><SectionHead title="Version and audit trail" />
      <section className="review-content" aria-label="Product label history">
        {history && <>
          <h2>{history.productDisplayName}</h2><p>Product {history.productId}</p>
          {history.versions.length === 0 && <p>No label versions exist for this product.</p>}
          {history.versions.map(version => <article className="history-version" key={version.labelVersionId}>
            <h3>V{version.versionNumber} · {version.labelVersionId}</h3><Badge variant="outline">{version.lifecycleStatus}</Badge>
            <dl className="workflow-binding">
              <div><dt>Jurisdiction</dt><dd>{version.jurisdictionCode}</dd></div>
              <div><dt>Formula</dt><dd>{version.formulaVersionId}</dd></div>
              <div><dt>Rule set</dt><dd>{version.ruleSetVersionId}</dd></div>
              <div><dt>Created</dt><dd>{version.createdAt} · {version.createdByDisplayName} ({version.createdByUserId})</dd></div>
              <div><dt>Published</dt><dd>{version.publishedAt ? `${version.publishedAt} · ${version.publishedByDisplayName} (${version.publishedByUserId}) · ${version.publicationChannel}` : 'Not published'}</dd></div>
              <div><dt>Declarations</dt><dd>{version.declarations.length ? version.declarations.map(row => `${row.allergenCode} ${row.declarationType}: ${row.displayText ?? '(no display text)'} [${row.source}]`).join('; ') : 'None recorded'}</dd></div>
            </dl>
          </article>)}
          <h3>Approval decisions</h3>
          {history.approvals.length ? <ul>{history.approvals.map(decision => <li key={decision.approvalRecordId}>
            {decision.decision} on {decision.labelVersionId} by {decision.decidedByDisplayName} ({decision.decidedByUserId}) at {decision.decidedAt}
            {decision.comments ? ` · ${decision.comments}` : ''} · task {decision.reviewTaskId}
          </li>)}</ul> : <p>No approval decisions recorded.</p>}
          <h3>Audit events</h3>
          {history.auditEvents.length ? <ul>{history.auditEvents.map(event => <li key={event.auditEventId}>
            {event.eventAt} · {event.eventType} · {event.actorDisplayName} ({event.actorUserId}) · {event.entityType} {event.entityId}
            <details><summary>Event details</summary><pre>{event.eventPayload}</pre></details>
          </li>)}</ul> : <p>No attributable label audit events recorded.</p>}
        </>}
      </section>
    </Panel></div>}
  </div>
}
