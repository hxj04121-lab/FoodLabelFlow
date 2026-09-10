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
