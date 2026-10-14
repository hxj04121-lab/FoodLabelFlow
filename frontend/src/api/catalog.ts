import { identityHeaders } from './identity-session'

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

export async function adoptSpecificationForCurrentProducts(specificationVersionId: string): Promise<BatchAdoptionSummary> {
  const response = await fetch(`/api/catalog/specifications/${encodeURIComponent(specificationVersionId)}/formula-adoptions`, {
    method: 'POST', headers: identityHeaders(),
  })
  let value: unknown
  try { value = await response.json() } catch { throw new Error(`The API returned invalid JSON (${response.status})`) }
  if (!response.ok) {
    const error = value as { code?: string; message?: string }
    throw new Error(`${error.code ?? response.status}: ${error.message ?? 'Batch adoption failed'}`)
  }
  if (!value || typeof value !== 'object') throw new Error('The batch adoption API returned an invalid summary.')
  const summary = value as BatchAdoptionSummary
  const invalidResult = Array.isArray(summary.results) && summary.results.some(row => !row ||
    typeof row.productId !== 'string' || typeof row.replayed !== 'boolean' ||
    !['ADOPTED', 'SKIPPED', 'FAILED'].includes(row.status))
  if (summary.targetSpecificationVersionId !== specificationVersionId || !Array.isArray(summary.results) ||
      invalidResult ||
      summary.candidateCount !== summary.results.length || summary.adoptedCount !== summary.results.filter(row => row.status === 'ADOPTED').length ||
      summary.skippedCount !== summary.results.filter(row => row.status === 'SKIPPED').length ||
      summary.failedCount !== summary.results.filter(row => row.status === 'FAILED').length) {
    throw new Error('The batch adoption API returned inconsistent product results.')
  }
  return summary
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
