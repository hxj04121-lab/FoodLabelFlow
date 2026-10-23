import { LabelApiError, requestLabelJson } from './labels'

export type ProductLabelHistory = {
  productId: string
  productDisplayName: string
  versions: Array<{
    labelVersionId: string
    versionNumber: number
    jurisdictionCode: string
    lifecycleStatus: string
    formulaVersionId: string
    ruleSetVersionId: string
    createdAt: string
    createdByUserId: string
    createdByDisplayName: string
    publishedAt: string | null
    publishedByUserId: string | null
    publishedByDisplayName: string | null
    publicationChannel: string | null
    declarations: Array<{ allergenCode: string; declarationType: string; source: string; displayText: string | null }>
  }>
  approvals: Array<{
    approvalRecordId: string
    labelVersionId: string
    reviewTaskId: string
    decision: string
    decidedByUserId: string
    decidedByDisplayName: string
    decidedAt: string
    comments: string | null
  }>
  auditEvents: Array<{
    auditEventId: string
    eventType: string
    entityType: string
    entityId: string
    eventAt: string
    actorUserId: string
    actorDisplayName: string
    eventPayload: string
  }>
}

export async function getProductLabelHistory(productId: string, signal?: AbortSignal): Promise<ProductLabelHistory> {
  const value = await requestLabelJson(`/api/audit/products/${encodeURIComponent(productId)}/label-history`, { signal })
  if (!value || typeof value !== 'object') throw new LabelApiError('INVALID_RESPONSE', 'The audit API returned an invalid product history.', 200)
  const history = value as ProductLabelHistory
  if (history.productId !== productId || typeof history.productDisplayName !== 'string' ||
      !Array.isArray(history.versions) || !Array.isArray(history.approvals) || !Array.isArray(history.auditEvents)) {
    throw new LabelApiError('INVALID_RESPONSE', 'The audit API returned incomplete product history.', 200)
  }
  return history
}
