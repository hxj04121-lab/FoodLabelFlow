import { LabelApiError, requestLabelJson } from './labels'

export type ChangeRequest = {
  changeRequestId: string
  changeType: 'INGREDIENT_SPEC'
  supplierMaterialId: string
  previousSpecificationVersionId: string
  targetSpecificationVersionId: string
  description: string
  status: string
  createdAt: string
}
export type ImpactFinding = {
  impactFindingId: string
  productId: string
  outcome: 'NO_ACTION' | 'REVIEW_REQUIRED'
  currentFormulaVersionId: string
  proposedFormulaVersionId: string
  currentLabelVersionId: string
  missingAllergenCodes: string[]
  explanation: string
  reviewTask?: {
    reviewTaskId: string
    impactFindingId: string
    productId: string
    currentFormulaVersionId: string
    currentLabelVersionId: string
    draftLabelVersionId: string | null
  }
}
export type ImpactAnalysis = {
  impactAnalysisId: string
  changeRequestId: string
  ruleSetVersionId: string
  status: string
  completedAt: string
  relevantProductCount: number
  noActionCount: number
  reviewRequiredCount: number
  findings: ImpactFinding[]
}
function record(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
}
function texts(value: Record<string, unknown>, fields: string[]): boolean {
  return fields.every(field => typeof value[field] === 'string' && value[field].trim().length > 0)
}
function isChange(value: unknown): value is ChangeRequest {
  return record(value) && value.changeType === 'INGREDIENT_SPEC' && texts(value, [
    'changeRequestId', 'supplierMaterialId', 'previousSpecificationVersionId',
    'targetSpecificationVersionId', 'description', 'status', 'createdAt',
  ])
}
function isFinding(value: unknown): value is ImpactFinding {
  if (!record(value) || !texts(value, ['impactFindingId', 'productId', 'currentFormulaVersionId',
    'proposedFormulaVersionId', 'currentLabelVersionId', 'explanation']) ||
    !Array.isArray(value.missingAllergenCodes) || !value.missingAllergenCodes.every(code => typeof code === 'string')) return false
  if (value.outcome === 'NO_ACTION') return value.reviewTask === undefined || value.reviewTask === null
  if (value.outcome !== 'REVIEW_REQUIRED' || !record(value.reviewTask)) return false
  const task = value.reviewTask
  return texts(task, ['reviewTaskId', 'impactFindingId', 'productId', 'currentFormulaVersionId', 'currentLabelVersionId']) &&
    task.impactFindingId === value.impactFindingId && task.productId === value.productId &&
    task.currentFormulaVersionId === value.currentFormulaVersionId && task.currentLabelVersionId === value.currentLabelVersionId &&
    (task.draftLabelVersionId === null || typeof task.draftLabelVersionId === 'string')
}
function requireAnalysis(value: unknown): ImpactAnalysis {
  if (!record(value) || !texts(value, ['impactAnalysisId', 'changeRequestId', 'ruleSetVersionId', 'status', 'completedAt']) ||
      value.status !== 'COMPLETED' || !Array.isArray(value.findings) || !value.findings.every(isFinding) ||
      value.relevantProductCount !== value.findings.length ||
      value.noActionCount !== value.findings.filter(finding => finding.outcome === 'NO_ACTION').length ||
      value.reviewRequiredCount !== value.findings.filter(finding => finding.outcome === 'REVIEW_REQUIRED').length ||
      new Set(value.findings.map(finding => finding.impactFindingId)).size !== value.findings.length ||
      new Set(value.findings.map(finding => finding.productId)).size !== value.findings.length ||
      new Set(value.findings.flatMap(finding => finding.reviewTask ? [finding.reviewTask.reviewTaskId] : [])).size !== value.reviewRequiredCount) {
    throw new LabelApiError('INVALID_RESPONSE', 'The impact API returned incomplete or inconsistent findings.', 200)
  }
  return value as ImpactAnalysis
}
export async function listChangeRequests(signal?: AbortSignal): Promise<ChangeRequest[]> {
  const requests: ChangeRequest[] = []
  for (let offset = 0; offset < 10000; offset += 100) {
    const value = await requestLabelJson(`/api/v1/change-requests?limit=100&offset=${offset}`, { signal })
    if (!Array.isArray(value) || !value.every(isChange)) throw new LabelApiError('INVALID_RESPONSE', 'The impact API returned an invalid change request list.', 200)
    requests.push(...value)
    if (value.length < 100) return requests
  }
  throw new LabelApiError('LIST_LIMIT', 'The change request list exceeds the client limit. Partial results cannot be displayed.', 200)
}
export async function createChangeRequest(input: Omit<ChangeRequest, 'changeRequestId' | 'status' | 'createdAt'>): Promise<ChangeRequest> {
  const value = await requestLabelJson('/api/v1/change-requests', {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(input),
  })
  if (!isChange(value)) throw new LabelApiError('INVALID_RESPONSE', 'The impact API returned an invalid change request.', 200)
  if (value.supplierMaterialId !== input.supplierMaterialId ||
      value.previousSpecificationVersionId !== input.previousSpecificationVersionId ||
      value.targetSpecificationVersionId !== input.targetSpecificationVersionId || value.description !== input.description) {
    throw new LabelApiError('CHANGE_TARGET_MISMATCH', 'The created change does not match the submitted specification versions.', 200)
  }
  return value
}
export async function runImpactAnalysis(changeRequestId: string, ruleSetVersionId: string): Promise<ImpactAnalysis> {
  const value = requireAnalysis(await requestLabelJson(`/api/v1/change-requests/${encodeURIComponent(changeRequestId)}/impact-analyses`, {
    method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ ruleSetVersionId }),
  }))
  if (value.changeRequestId !== changeRequestId || value.ruleSetVersionId !== ruleSetVersionId) {
    throw new LabelApiError('IMPACT_TARGET_MISMATCH', 'The analysis does not match the selected change and rule set.', 200)
  }
  return value
}
export async function getImpactAnalysis(impactAnalysisId: string): Promise<ImpactAnalysis> {
  const value = requireAnalysis(await requestLabelJson(`/api/v1/impact-analyses/${encodeURIComponent(impactAnalysisId)}`))
  if (value.impactAnalysisId !== impactAnalysisId) throw new LabelApiError('IMPACT_TARGET_MISMATCH', 'The API returned a different impact analysis.', 200)
  return value
}
