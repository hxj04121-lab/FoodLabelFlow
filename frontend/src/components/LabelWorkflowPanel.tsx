import { getLabelDraft, LabelApiError, type LabelDraft, type ValidationRun } from '@/api/labels'
import { decideLabelReview, publishReviewTaskLabel, requireWorkflowTarget, submitLabelForReview,
  type ReviewDecision } from '@/api/label-workflow'
import { Panel, SectionHead } from './catalog-shared'
import { Badge } from './ui/badge'
import { Button } from './ui/button'
import { useEffect, useRef, useState } from 'react'
import { CurrentIdentityPanel, useCurrentIdentity } from './CurrentIdentityPanel'

type PendingCommand = { label: LabelDraft; expectedStatus: string; action: string }

export function LabelWorkflowPanel({ draft, validationRun, reviewTaskId, onDraftChange, identity }: {
  draft: LabelDraft | null
  validationRun: ValidationRun | null
  reviewTaskId: string
  onDraftChange: (draft: LabelDraft) => void
  identity: ReturnType<typeof useCurrentIdentity>
}) {
  const [connectedIdentityEnabled, setConnectedIdentityEnabled] = useState(false)
  const [comments, setComments] = useState('')
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState('')
  const [error, setError] = useState('')
  const [pending, setPending] = useState<PendingCommand | null>(null)
  const lock = useRef(false)
  const currentId = useRef(draft?.labelVersionId)
  currentId.current = draft?.labelVersionId
  const local = ['127.0.0.1', 'localhost', '[::1]'].includes(location.hostname)
  const independent = identity.state.status === 'ready' && identity.state.actor.userId !== draft?.createdByUserId
  const validationPassed = validationRun?.labelVersionId === draft?.labelVersionId &&
    validationRun?.ruleSetVersionId === draft?.ruleSetVersionId && validationRun?.status === 'PASSED'

  useEffect(() => {
    setConnectedIdentityEnabled(false)
    setComments('')
    setError('')
    setMessage('')
  }, [draft?.labelVersionId])

  async function command(action: string, expectedStatus: string, execute: (target: LabelDraft) => Promise<LabelDraft>) {
    if (!draft || lock.current || pending || !local || !connectedIdentityEnabled) return
    const target = draft
    lock.current = true
    setBusy(true)
    setError('')
    setMessage('')
    try {
      const result = requireWorkflowTarget(await execute(target), target)
      if (result.lifecycleStatus !== expectedStatus ||
          (expectedStatus === 'PUBLISHED' && result.isCurrentPublished !== 'Y')) {
        throw new LabelApiError('WORKFLOW_STATE_MISMATCH', 'The server did not confirm the expected workflow state.', 200)
      }
      if (currentId.current === target.labelVersionId) onDraftChange(result)
      setConnectedIdentityEnabled(false)
      setMessage(`${action} confirmed for ${result.labelVersionId}: ${result.lifecycleStatus}.`)
    } catch (cause: unknown) {
      const apiError = cause instanceof LabelApiError ? cause : null
      setError(`${apiError?.code ?? 'NETWORK_ERROR'}: ${cause instanceof Error ? cause.message : 'The workflow request failed.'}`)
      if (!apiError || apiError.status >= 500 || ['INVALID_RESPONSE', 'WORKFLOW_TARGET_MISMATCH', 'WORKFLOW_STATE_MISMATCH'].includes(apiError.code)) {
        setPending({ label: target, action, expectedStatus })
        setMessage(`The outcome of ${action.toLowerCase()} for ${target.labelVersionId} is unconfirmed. Read that exact version before sending another workflow command.`)
      }
    } finally {
      lock.current = false
      setBusy(false)
    }
  }

  async function refresh() {
    const target = pending?.label ?? draft
    if (!target || lock.current) return
    lock.current = true
    setBusy(true)
    setError('')
    try {
      const read = requireWorkflowTarget(await getLabelDraft(target.labelVersionId), target)
      if (currentId.current === read.labelVersionId) onDraftChange(read)
      if (pending && read.lifecycleStatus === pending.expectedStatus &&
          (pending.expectedStatus !== 'PUBLISHED' || read.isCurrentPublished === 'Y')) {
        setPending(null)
        setMessage(`Server state confirms ${pending.action.toLowerCase()} for ${read.labelVersionId}: ${read.lifecycleStatus}.`)
      } else {
        setMessage(`Loaded ${read.labelVersionId}: ${read.lifecycleStatus}.` +
          (pending ? ' The earlier workflow command is still unconfirmed; verify the server before retrying.' : ''))
      }
    } catch (cause: unknown) {
      setError(`${cause instanceof LabelApiError ? cause.code : 'NETWORK_ERROR'}: ${cause instanceof Error ? cause.message : 'Workflow read failed.'}`)
    } finally { lock.current = false; setBusy(false) }
  }

  const disabled = !draft || !local || busy || !!pending
  return <Panel className="workflow-panel">
    <SectionHead title="Review and publication" caption="Submit the exact validated version, then obtain an independent decision and publish its review task"
      action={<Badge variant="outline">{draft?.lifecycleStatus ?? 'Select a label draft'}</Badge>} />
    <section className="workflow-content" aria-label="Label review and publication">
      <CurrentIdentityPanel state={identity.state} onRefresh={identity.refresh} />
      {!draft && <p>Create or load an exact label version to continue.</p>}
      {draft && <>
        <dl className="workflow-binding"><div><dt>Label version</dt><dd>{draft.labelVersionId}</dd></div>
          <div><dt>Review task</dt><dd>{reviewTaskId || 'No review task selected'}</dd></div></dl>
        <p>Declarations are fixed at draft creation. Review submission requires the latest validation to pass. Approval requires an authorized person other than the maker; publication requires its own permission.</p>
        <label className="demo-consent"><input type="checkbox" checked={connectedIdentityEnabled} disabled={disabled}
          onChange={event => setConnectedIdentityEnabled(event.target.checked)} />Use the connected identity for review and publication</label>
        <p>A trusted maker, reviewer or publisher identity must be supplied by the connected environment. This form does not select an identity or assign permissions.</p>
        {!independent && draft.lifecycleStatus === 'PENDING_REVIEW' && <p>Your connected identity created this label. An independent reviewer must make the decision.</p>}
        {!validationPassed && draft.lifecycleStatus === 'DRAFT' && <p>Run or load a PASSED validation for this exact label and rule set before submitting.</p>}
        <Button disabled={disabled || !connectedIdentityEnabled || !validationPassed || !identity.hasPermission('LABEL.SUBMIT_REVIEW') || draft.lifecycleStatus !== 'DRAFT'}
          onClick={() => command('Review submission', 'PENDING_REVIEW', submitLabelForReview)}>Submit for review</Button>
        <label htmlFor="review-comments">Review comments</label><textarea id="review-comments" value={comments}
          disabled={busy} maxLength={1000} onChange={event => setComments(event.target.value)} />
        <div className="workflow-actions">{([
          ['APPROVE', 'Approve label', 'APPROVED'], ['REQUEST_CHANGES', 'Request changes', 'DRAFT'],
          ['REJECT', 'Reject label', 'REJECTED'],
        ] as Array<[ReviewDecision, string, string]>).map(([decision, label, status]) =>
          <Button key={decision} variant="outline" disabled={disabled || !connectedIdentityEnabled || !independent || !identity.hasPermission('LABEL.' + decision) || draft.lifecycleStatus !== 'PENDING_REVIEW'}
            onClick={() => command(label, status, target => decideLabelReview(target, decision, comments))}>{label}</Button>)}
          <Button disabled={disabled || !connectedIdentityEnabled || !identity.hasPermission('LABEL.PUBLISH') || !reviewTaskId.trim() || draft.lifecycleStatus !== 'APPROVED'}
            onClick={() => command('Publication', 'PUBLISHED', target => publishReviewTaskLabel(target, reviewTaskId.trim()))}>Publish label</Button>
        </div>
      </>}
      {!local && <p role="alert">Workflow writes are disabled outside localhost until approved authentication is connected.</p>}
      {error && <p className="error-notice" role="alert">{error}</p>}
      <p className="label-operation-message" aria-live="polite">{message}</p>
      {pending && <p role="status">Unconfirmed command: {pending.action} for {pending.label.labelVersionId}. Switching labels does not clear this guard.</p>}
      <Button variant="outline" disabled={busy || (!draft && !pending)} onClick={refresh}>Refresh workflow state</Button>
    </section>
  </Panel>
}
