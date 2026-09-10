export type HealthResponse = {
  status: string
  database: string
}

export type CatalogProduct = {
  product_id: string
  current_formula_version_id: string | null
}

export type FormulaVersion = {
  formula_version_id: string
  product_id: string
  version_number: number
  lifecycle_status: string
  is_current_released: string
}

export type CreateFormulaRequest = {
  productId: string
  provenanceId: string
  items: Array<{
    materialId: string
    specificationId: string
    quantity: number | null
    unit: string | null
  }>
}

const catalogHeaders = {
  'Content-Type': 'application/json',
  'X-Auth-Provider': 'DEV_EXTERNAL',
  'X-External-Subject': 'dev-external-admin',
}

export class CatalogRequestError extends Error {
  constructor(public code: string, message: string, public status: number) { super(message) }
}
async function catalogRequest<T>(path: string, init?: RequestInit): Promise<T> {
  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), 15000)
  try {
  const response = await fetch(path, { ...init, signal: controller.signal })
  if (!response.ok) {
    const error = (await response.json().catch(() => null)) as
      | { code?: string; message?: string }
      | null
    throw new CatalogRequestError(error?.code || 'HTTP_ERROR', error?.message || `Request failed with status ${response.status}`, response.status)
  }
  return response.json() as Promise<T>
  } finally { clearTimeout(timer) }
}

export async function getHealth(): Promise<HealthResponse> {
  const response = await fetch('/api/health')
  if (!response.ok) {
    throw new Error(`Health request failed with status ${response.status}`)
  }
  return response.json() as Promise<HealthResponse>
}

export function getCatalogProduct(productId: string): Promise<CatalogProduct> {
  return catalogRequest(`/api/catalog/products/${productId}`)
}

export function createFormula(request: CreateFormulaRequest): Promise<FormulaVersion> {
  return catalogRequest('/api/catalog/formulas', {
    method: 'POST',
    headers: catalogHeaders,
    body: JSON.stringify(request),
  })
}

export function releaseFormula(
  formulaId: string,
  expectedCurrentFormulaId: string | null,
): Promise<FormulaVersion> {
  return catalogRequest(`/api/catalog/formulas/${formulaId}/release`, {
    method: 'POST',
    headers: catalogHeaders,
    body: JSON.stringify({ expectedCurrentFormulaId }),
  })
}

export function getFormulaHistory(productId: string): Promise<FormulaVersion[]> {
  return catalogRequest(`/api/catalog/products/${productId}/formulas`)
}
