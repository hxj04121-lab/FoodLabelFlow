import { getReviewTask, listReviewTasks, type ReviewTask } from '@/api/label-workflow'
import { CurrentIdentityPanel, useCurrentIdentity } from '@/components/CurrentIdentityPanel'
import { Panel, SectionHead } from '@/components/catalog-shared'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { useEffect, useRef, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'

const PAGE_SIZE = 20

export function Reviews() {
  const [searchParams] = useSearchParams()
  const identity = useCurrentIdentity()
  const [tasks, setTasks] = useState<ReviewTask[]>([])
  const [status, setStatus] = useState('OPEN')
  const [offset, setOffset] = useState(0)
  const [attempt, setAttempt] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [taskId, setTaskId] = useState(searchParams.get('reviewTaskId') ?? '')
  const [detail, setDetail] = useState<ReviewTask | null>(null)
  const [detailBusy, setDetailBusy] = useState(false)
  const [detailError, setDetailError] = useState('')
  const detailRequest = useRef(0)
  const identityGeneration = identity.session.generation
  // A stale request retains its render closure; compare against a ref updated on every render.
  const activeGeneration = useRef(identityGeneration)
  activeGeneration.current = identityGeneration

  useEffect(() => {
    detailRequest.current++
    setTasks([])
    setDetail(null)
    setDetailError('')
    setTaskId('')
    setOffset(0)
    setAttempt(value => value + 1)
  }, [identityGeneration])

  useEffect(() => {
    const controller = new AbortController()
    setLoading(true); setError(''); setTasks([])
    listReviewTasks(PAGE_SIZE, offset, status === 'ALL' ? undefined : status, controller.signal)
      .then(result => { if (!controller.signal.aborted) setTasks(result) })
      .catch(cause => { if (!controller.signal.aborted) setError(cause instanceof Error ? cause.message : 'Review tasks could not be read.') })
      .finally(() => { if (!controller.signal.aborted) setLoading(false) })
    return () => controller.abort()
  }, [status, offset, attempt, identityGeneration])

  async function loadDetail(exactId: string) {
    if (!exactId.trim()) return
    const request = ++detailRequest.current
    const generation = identityGeneration
    setTaskId(exactId); setDetail(null); setDetailBusy(true); setDetailError('')
    try {
      const read = await getReviewTask(exactId.trim())
      if (request === detailRequest.current && generation === activeGeneration.current) setDetail(read)
    } catch (cause: unknown) {
      if (request === detailRequest.current && generation === activeGeneration.current) setDetailError(cause instanceof Error ? cause.message : 'Review task could not be read.')
    } finally { if (request === detailRequest.current && generation === activeGeneration.current) setDetailBusy(false) }
  }

  useEffect(() => {
    const id = searchParams.get('reviewTaskId')?.trim()
    if (id && identity.state.status === 'ready') void loadDetail(id)
    return () => { detailRequest.current++ }
  }, [searchParams, identityGeneration, identity.state.status])

  return <div className="review-workspace">
    <div className="page-title"><div><h1>Review workspace</h1><p>Read actual tasks, inspect their version bindings and continue the guarded label workflow.</p></div>
      <Badge variant="outline">Live review tasks</Badge></div>
    <CurrentIdentityPanel state={identity.state} onRefresh={identity.refresh} />
    <div className="impact-layout"><Panel><SectionHead title="Review task list" caption="One server page at a time; displayed rows are not a global task count" />
      <section className="review-content" aria-label="Review task list">
        <label htmlFor="review-status-filter">Task status</label><select id="review-status-filter" value={status} disabled={loading}
          onChange={event => { setStatus(event.target.value); setOffset(0) }}>
          <option value="ALL">All statuses</option><option value="OPEN">Open</option><option value="IN_REVIEW">In review</option><option value="CLOSED">Closed</option>
        </select>
        {loading && <p role="status">Loading review tasks…</p>}
        {error && <p className="error-notice" role="alert">{error}</p>}
        {!loading && !error && tasks.length === 0 && <p>No tasks were returned for this page and status.</p>}
        <div className="review-task-list">{tasks.map(task => <article key={task.reviewTaskId}>
          <h2>{task.productId}</h2><Badge variant="outline">{task.status}</Badge><p>{task.reviewTaskId}</p>
          <Button variant="outline" onClick={() => loadDetail(task.reviewTaskId)}>View task details</Button>
        </article>)}</div>
        <p>{tasks.length} rows on this page · Offset {offset}</p>
        <div className="workflow-actions"><Button variant="outline" disabled={loading || offset === 0} onClick={() => setOffset(value => Math.max(0, value - PAGE_SIZE))}>Previous tasks</Button>
          <Button variant="outline" disabled={loading || !!error || tasks.length < PAGE_SIZE || offset + PAGE_SIZE > 100000} onClick={() => setOffset(value => value + PAGE_SIZE)}>Next tasks</Button>
          <Button variant="outline" disabled={loading} onClick={() => setAttempt(value => value + 1)}>Refresh review tasks</Button></div>
      </section></Panel>
      <Panel><SectionHead title="Review task details" caption="Only an exact authenticated task response supplies the handoff" />
        <section className="review-content" aria-label="Review task details">
          <label htmlFor="review-task-lookup">Existing review task ID</label><input id="review-task-lookup" value={taskId}
            onChange={event => setTaskId(event.target.value)} />
          <Button variant="outline" disabled={detailBusy || !taskId.trim()} onClick={() => loadDetail(taskId)}>Load review task</Button>
          {detailBusy && <p role="status">Loading the exact review task…</p>}
          {detailError && <p className="error-notice" role="alert">{detailError}</p>}
          {detail && <><h2>{detail.reviewTaskId}</h2><dl className="workflow-binding">
            <div><dt>Product</dt><dd>{detail.productId}</dd></div><div><dt>Status</dt><dd>{detail.status}</dd></div>
            <div><dt>Original published label</dt><dd>{detail.currentLabelVersionId}</dd></div>
            <div><dt>Bound draft label</dt><dd>{detail.draftLabelVersionId ?? 'Not created'}</dd></div>
            <div><dt>Target label</dt><dd>{detail.targetLabelVersionId ?? 'Not bound'}</dd></div>
            <div><dt>Decision</dt><dd>{detail.decision ?? 'Not decided'}</dd></div>
            <div><dt>Resolved at</dt><dd>{detail.resolvedAt ?? 'Not resolved'}</dd></div>
          </dl><Button asChild><Link to={'/labels?' + new URLSearchParams({ productId: detail.productId,
            reviewTaskId: detail.reviewTaskId, ...(detail.draftLabelVersionId ? { labelVersionId: detail.draftLabelVersionId } : {}) })}>
            {detail.draftLabelVersionId ? 'Open bound label version' : 'Prepare first replacement label'}</Link></Button></>}
          {!detail && !detailBusy && !detailError && <p>Select a task or load an exact task ID.</p>}
        </section></Panel></div>
  </div>
}
