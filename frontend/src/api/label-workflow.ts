import { isLabelDraft, LabelApiError, requestLabelJson, type LabelDraft } from './labels'

export type ReviewDecision = 'APPROVE' | 'REQUEST_CHANGES' | 'REJECT'

export type ReviewTask = {
  reviewTaskId: string
  productId: string
  currentLabelVersionId: string
  draftLabelVersionId: string | null
  targetLabelVersionId: string | null
  status: string
  decision: string | null
  resolvedAt: string | null
}

export async function getReviewTask(reviewTaskId: string, signal?: AbortSignal): Promise<ReviewTask> {
  const value = await requestLabelJson(`/api/review-tasks/${encodeURIComponent(reviewTaskId)}`, { signal })
  return requireReviewTask(value, reviewTaskId)
}

function requireReviewTask(value: unknown, reviewTaskId?: string): ReviewTask {
  const task = value as ReviewTask | null
  if (!task || typeof task !== 'object' || typeof task.reviewTaskId !== 'string' ||
      (reviewTaskId !== undefined && task.reviewTaskId !== reviewTaskId) ||
      typeof task.productId !== 'string' || typeof task.currentLabelVersionId !== 'string' ||
      typeof task.status !== 'string' ||
      ![task.draftLabelVersionId, task.targetLabelVersionId, task.decision, task.resolvedAt]
        .every(field => field === null || typeof field === 'string')) {
    throw new LabelApiError('INVALID_RESPONSE', 'The workflow API returned an invalid review task.', 200)
  }
  return task
}

export async function listReviewTasks(limit: number, offset: number, status?: string, signal?: AbortSignal): Promise<ReviewTask[]> {
  const parameters = new URLSearchParams({ limit: String(limit), offset: String(offset), ...(status ? { status } : {}) })
  const value = await requestLabelJson('/api/review-tasks?' + parameters, { signal })
  if (!Array.isArray(value) || value.length > limit) {
    throw new LabelApiError('INVALID_RESPONSE', 'The workflow API returned an invalid review task page.', 200)
  }
  const tasks = value.map(item => requireReviewTask(item))
  if (new Set(tasks.map(task => task.reviewTaskId)).size !== tasks.length ||
      tasks.some(task => !['OPEN', 'IN_REVIEW', 'CLOSED'].includes(task.status) || (status && task.status !== status))) {
    throw new LabelApiError('INVALID_RESPONSE', 'The workflow API returned duplicate tasks or a different status filter.', 200)
  }
  return tasks
}

export function requireWorkflowTarget(value: unknown, target: LabelDraft): LabelDraft {
  if (!isLabelDraft(value)) {
    throw new LabelApiError('INVALID_RESPONSE', 'The workflow API returned an invalid label version.', 200)
  }
  if (value.labelVersionId !== target.labelVersionId || value.productId !== target.productId ||
      value.formulaVersionId !== target.formulaVersionId || value.ruleSetVersionId !== target.ruleSetVersionId ||
      value.jurisdictionCode !== target.jurisdictionCode) {
    throw new LabelApiError('WORKFLOW_TARGET_MISMATCH', 'The workflow response does not match the exact selected label context.', 200)
  }
  return value
}

// Every workflow command uses the existing trusted request identity. This client never
// selects a subject or grants a role. The server enforces authorization and maker-checker.
export async function submitLabelForReview(target: LabelDraft): Promise<LabelDraft> {
  return requireWorkflowTarget(await requestLabelJson(
    `/api/labels/${encodeURIComponent(target.labelVersionId)}/review-submissions`, {
      method: 'POST', headers: { 'Content-Type': 'application/json' }, body: '{}',
    }), target)
}

export async function decideLabelReview(
  target: LabelDraft, decision: ReviewDecision, comments: string,
): Promise<LabelDraft> {
  return requireWorkflowTarget(await requestLabelJson(
    `/api/labels/${encodeURIComponent(target.labelVersionId)}/review-decisions`, {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ decision, ...(comments.trim() ? { comments: comments.trim() } : {}) }),
    }), target)
}

export async function publishReviewTaskLabel(target: LabelDraft, reviewTaskId: string): Promise<LabelDraft> {
  const task = await getReviewTask(reviewTaskId)
  if (task.productId !== target.productId || task.draftLabelVersionId !== target.labelVersionId ||
      task.targetLabelVersionId !== target.labelVersionId) {
    throw new LabelApiError('REVIEW_TASK_TARGET_MISMATCH', 'The review task is not bound to this exact label version.', 409)
  }
  return requireWorkflowTarget(await requestLabelJson(
    `/api/review-tasks/${encodeURIComponent(reviewTaskId)}/publications`, {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ labelVersionId: target.labelVersionId }),
    }), target)
}
