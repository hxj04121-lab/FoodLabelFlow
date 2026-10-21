import { LabelApiError, requestLabelJson } from './labels'

export async function catalogGet(path: string, signal?: AbortSignal): Promise<unknown> {
  const response = await fetch(`/api/catalog${path}`, { signal })
  let body: unknown
  try { body = await response.json() } catch { throw new Error(`The API returned invalid JSON (${response.status})`) }
  if (!response.ok) {
    const error = body as { code?: string; message?: string }
    throw new Error(`${error.code ?? response.status}: ${error.message ?? 'Read failed'}`)
  }
  return body
}
export async function catalogList(path: string, signal: AbortSignal): Promise<Record<string, unknown>[]> {
  const result: Record<string, unknown>[] = []
  for (let offset = 0; offset < 10000; offset += 100) {
    const page = await catalogGet(`${path}?limit=100&offset=${offset}`, signal)
    if (!Array.isArray(page) || page.some(r => !r || typeof r !== 'object')) throw new Error('Invalid API list format')
    result.push(...page)
    if (page.length < 100) return result
  }
  throw new Error('The catalog exceeds the client loading limit. Partial totals cannot be displayed.')
}

export type BatchAdoptionResult = {
  productId: string
  status: 'ADOPTED' | 'SKIPPED' | 'FAILED'
  formulaVersionId: string | null
  replayed: boolean
  errorCode: string | null
  message: string | null
}

export type BatchAdoptionSummary = {
  targetSpecificationVersionId: string
  candidateCount: number
  adoptedCount: number
  skippedCount: number
  failedCount: number
  results: BatchAdoptionResult[]
}

function object(value: unknown): value is Record<string, unknown> {
  return !!value && typeof value === 'object' && !Array.isArray(value)
}

function nullableText(value: unknown): value is string | null {
  return value === null || typeof value === 'string'
}

function parseBatchAdoption(value: unknown): BatchAdoptionSummary {
  if (!object(value) || typeof value.targetSpecificationVersionId !== 'string' ||
      !['candidateCount', 'adoptedCount', 'skippedCount', 'failedCount'].every(key =>
        Number.isSafeInteger(value[key]) && (value[key] as number) >= 0) ||
      !Array.isArray(value.results)) {
    throw new LabelApiError('INVALID_RESPONSE', 'The batch adoption API returned an incomplete summary.', 200)
  }
  const results = value.results
  const validResults = results.every(row => object(row) &&
    typeof row.productId === 'string' && row.productId.trim().length > 0 &&
    ['ADOPTED', 'SKIPPED', 'FAILED'].includes(row.status as string) &&
    nullableText(row.formulaVersionId) && typeof row.replayed === 'boolean' &&
    nullableText(row.errorCode) && nullableText(row.message))
  if (!validResults || value.candidateCount !== results.length ||
      value.adoptedCount !== results.filter(row => row.status === 'ADOPTED').length ||
      value.skippedCount !== results.filter(row => row.status === 'SKIPPED').length ||
      value.failedCount !== results.filter(row => row.status === 'FAILED').length) {
    throw new LabelApiError('INVALID_RESPONSE', 'The batch adoption API returned inconsistent per-product results.', 200)
  }
  return value as BatchAdoptionSummary
}

export async function adoptSpecificationForCurrentProducts(targetSpecificationVersionId: string): Promise<BatchAdoptionSummary> {
  const value = parseBatchAdoption(await requestLabelJson(
    `/api/catalog/specifications/${encodeURIComponent(targetSpecificationVersionId)}/formula-adoptions`,
    { method: 'POST' },
  ))
  if (value.targetSpecificationVersionId !== targetSpecificationVersionId) {
    throw new LabelApiError('ADOPTION_TARGET_MISMATCH', 'The batch adoption response names a different target specification.', 200)
  }
  return value
}
