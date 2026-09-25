export type Allergen = {
  allergenId: string
  allergenCode: string
  displayName: string
  jurisdictionCode: string
}

export type ValidationResult = {
  ruleDefinitionId?: string | null
  resultCode: string
  severity: string
  passed: boolean
  blocking: boolean
  message: string
}

export type ValidationRun = {
  validationRunId: string
  labelVersionId: string
  ruleSetVersionId: string
  status: 'PASSED' | 'FAILED'
  ranAt: string
  summary?: string
  results: ValidationResult[]
}

export type ApiError = {
  code: string
  message: string
  traceId: string | null
  evidenceId: string | null
}

export type LabelDraft = {
  labelVersionId: string
  productId: string
  formulaVersionId: string
  ruleSetVersionId: string
  jurisdictionCode: string
  versionNumber: number
  rawIngredientText: string
  lifecycleStatus: string
  isCurrentPublished: string
  createdByUserId: string
  createdAt: string
  dataProvenanceId: string
}

export type DerivationEvidence = {
  formulaItemId: string
  specificationVersionId: string
  specComponentId: string
  ingredientId: string
  ingredientAllergenId: string
  evidenceRule: string
  dataProvenanceId: string
}

export type LabelAllergenFacts = {
  labelVersionId: string
  formulaVersionId: string
  ruleSetVersionId: string
  jurisdictionCode: string
  facts: {
    allergenId: string
    allergenCode: string
    derivationEvidence: DerivationEvidence[]
  }[]
  unresolvedComponents: {
    formulaItemId: string
    specificationVersionId: string
    specComponentId: string
    ingredientId: string
    rawPhrase: string
    matchRule: string
    matchStatus: 'UNMAPPED' | 'AMBIGUOUS'
  }[]
}

export type LabelDeclarations = {
  labelVersionId: string
  formulaVersionId: string
  ruleSetVersionId: string
  jurisdictionCode: string
  declarations: {
    allergenId: string
    declarationType: 'CONTAINS'
    declarationSource: 'MIGRATED_PUBLIC_LABEL' | 'FORMULA_DERIVED' | 'SYSTEM_PROPOSED' | 'USER_ENTERED'
    displayText: string | null
  }[]
}

function hasRequiredText(value: Record<string, unknown>, fields: string[]): boolean {
  return fields.every((field) => typeof value[field] === 'string' && value[field].trim().length > 0)
}

function isLabelAllergenFacts(value: unknown): value is LabelAllergenFacts {
  return (
    isRecord(value) &&
    hasRequiredText(value, ['labelVersionId', 'formulaVersionId', 'ruleSetVersionId', 'jurisdictionCode']) &&
    Array.isArray(value.facts) &&
    value.facts.every((fact) =>
      isRecord(fact) && hasRequiredText(fact, ['allergenId', 'allergenCode']) &&
      Array.isArray(fact.derivationEvidence) && fact.derivationEvidence.length > 0 &&
      fact.derivationEvidence.every((evidence) => isRecord(evidence) && hasRequiredText(evidence, [
        'formulaItemId', 'specificationVersionId', 'specComponentId', 'ingredientId',
        'ingredientAllergenId', 'evidenceRule', 'dataProvenanceId',
      ])),
    ) &&
    Array.isArray(value.unresolvedComponents) &&
    value.unresolvedComponents.every((component) =>
      isRecord(component) && hasRequiredText(component, [
        'formulaItemId', 'specificationVersionId', 'specComponentId', 'ingredientId', 'rawPhrase', 'matchRule',
      ]) && (component.matchStatus === 'UNMAPPED' || component.matchStatus === 'AMBIGUOUS'),
    )
  )
}

function isLabelDeclarations(value: unknown): value is LabelDeclarations {
  const sources = ['MIGRATED_PUBLIC_LABEL', 'FORMULA_DERIVED', 'SYSTEM_PROPOSED', 'USER_ENTERED']
  return (
    isRecord(value) &&
    hasRequiredText(value, ['labelVersionId', 'formulaVersionId', 'ruleSetVersionId', 'jurisdictionCode']) &&
    Array.isArray(value.declarations) &&
    value.declarations.every((declaration) =>
      isRecord(declaration) && hasRequiredText(declaration, ['allergenId']) &&
      declaration.declarationType === 'CONTAINS' &&
      sources.includes(declaration.declarationSource as string) &&
      (declaration.displayText === null || typeof declaration.displayText === 'string'),
    )
  )
}

export class LabelApiError extends Error {
  constructor(
    public readonly code: string,
    message: string,
    public readonly status: number,
    public readonly traceId: string | null = null,
    public readonly evidenceId: string | null = null,
  ) {
    super(message)
    this.name = 'LabelApiError'
  }
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
}

function isAllergen(value: unknown): value is Allergen {
  return (
    isRecord(value) &&
    typeof value.allergenId === 'string' &&
    typeof value.allergenCode === 'string' &&
    typeof value.displayName === 'string' &&
    typeof value.jurisdictionCode === 'string'
  )
}

function isValidationResult(value: unknown): value is ValidationResult {
  return (
    isRecord(value) &&
    (value.ruleDefinitionId === undefined ||
      value.ruleDefinitionId === null ||
      typeof value.ruleDefinitionId === 'string') &&
    typeof value.resultCode === 'string' &&
    typeof value.severity === 'string' &&
    typeof value.passed === 'boolean' &&
    typeof value.blocking === 'boolean' &&
    typeof value.message === 'string'
  )
}

function isValidationRun(value: unknown): value is ValidationRun {
  return (
    isRecord(value) &&
    typeof value.validationRunId === 'string' &&
    typeof value.labelVersionId === 'string' &&
    typeof value.ruleSetVersionId === 'string' &&
    (value.status === 'PASSED' || value.status === 'FAILED') &&
    typeof value.ranAt === 'string' &&
    (value.summary === undefined || typeof value.summary === 'string') &&
    Array.isArray(value.results) &&
    value.results.every(isValidationResult)
  )
}

function isLabelDraft(value: unknown): value is LabelDraft {
  return (
    isRecord(value) &&
    typeof value.labelVersionId === 'string' &&
    typeof value.productId === 'string' &&
    typeof value.formulaVersionId === 'string' &&
    typeof value.ruleSetVersionId === 'string' &&
    typeof value.jurisdictionCode === 'string' &&
    typeof value.versionNumber === 'number' &&
    typeof value.rawIngredientText === 'string' &&
    typeof value.lifecycleStatus === 'string' &&
    typeof value.isCurrentPublished === 'string' &&
    typeof value.createdByUserId === 'string' &&
    typeof value.createdAt === 'string' &&
    typeof value.dataProvenanceId === 'string'
  )
}

async function requestJson(path: string, init?: RequestInit): Promise<unknown> {
  const response = await fetch(path, init)
  let body: unknown
  try {
    body = await response.json()
  } catch {
    throw new LabelApiError(
      'INVALID_RESPONSE',
      `The label API returned invalid JSON (${response.status}).`,
      response.status,
    )
  }

  if (!response.ok) {
    const error = isRecord(body) ? body : {}
    throw new LabelApiError(
      typeof error.code === 'string' ? error.code : 'HTTP_ERROR',
      typeof error.message === 'string'
        ? error.message
        : `Label API request failed with status ${response.status}.`,
      response.status,
      typeof error.traceId === 'string' ? error.traceId : null,
      typeof error.evidenceId === 'string' ? error.evidenceId : null,
    )
  }

  return body
}

const labelOfficerHeaders = {
  'Content-Type': 'application/json',
  'X-Auth-Provider': 'DEV_EXTERNAL',
  'X-External-Subject': 'dev-external-label-officer',
}

export async function createLabelDraft(
  productId: string,
  jurisdictionCode: string,
  signal?: AbortSignal,
): Promise<LabelDraft> {
  const result = await requestJson('/api/labels/drafts', {
    method: 'POST',
    headers: labelOfficerHeaders,
    body: JSON.stringify({ productId, jurisdictionCode }),
    signal,
  })
  if (!isLabelDraft(result)) {
    throw new LabelApiError(
      'INVALID_RESPONSE',
      'The label API returned an invalid draft.',
      200,
    )
  }
  return result
}

export async function getLabelDraft(
  labelVersionId: string,
  signal?: AbortSignal,
): Promise<LabelDraft> {
  const result = await requestJson(
    `/api/labels/${encodeURIComponent(labelVersionId)}`,
    { headers: labelOfficerHeaders, signal },
  )
  if (!isLabelDraft(result)) {
    throw new LabelApiError(
      'INVALID_RESPONSE',
      'The label API returned an invalid draft.',
      200,
    )
  }
  return result
}

export async function listAllergens(
  jurisdictionCode: string,
  signal?: AbortSignal,
): Promise<Allergen[]> {
  const result = await requestJson(
    `/api/v1/allergens?jurisdictionCode=${encodeURIComponent(jurisdictionCode)}`,
    { headers: labelOfficerHeaders, signal },
  )
  if (!Array.isArray(result) || !result.every(isAllergen)) {
    throw new LabelApiError(
      'INVALID_RESPONSE',
      'The label API returned an invalid allergen list.',
      200,
    )
  }
  return result
}

export async function getLabelDerivedAllergens(
  draft: LabelDraft,
  signal?: AbortSignal,
): Promise<LabelAllergenFacts> {
  const result = await requestJson(
    `/api/v1/label-versions/${encodeURIComponent(draft.labelVersionId)}/derived-allergens`,
    { headers: labelOfficerHeaders, signal },
  )
  if (!isLabelAllergenFacts(result)) {
    throw new LabelApiError('INVALID_RESPONSE', 'The allergen API returned invalid derived facts.', 200)
  }
  if (
    result.labelVersionId !== draft.labelVersionId ||
    result.formulaVersionId !== draft.formulaVersionId ||
    result.ruleSetVersionId !== draft.ruleSetVersionId ||
    result.jurisdictionCode !== draft.jurisdictionCode
  ) {
    throw new LabelApiError(
      'DERIVATION_TARGET_MISMATCH',
      'The derived facts do not match the selected label, formula, rule set and jurisdiction.',
      200,
    )
  }
  return result
}

export async function getLabelDeclarations(
  draft: LabelDraft,
  signal?: AbortSignal,
): Promise<LabelDeclarations> {
  const result = await requestJson(
    `/api/labels/${encodeURIComponent(draft.labelVersionId)}/declarations`,
    { headers: labelOfficerHeaders, signal },
  )
  if (!isLabelDeclarations(result)) {
    throw new LabelApiError('INVALID_RESPONSE', 'The label API returned invalid declarations.', 200)
  }
  if (
    result.labelVersionId !== draft.labelVersionId ||
    result.formulaVersionId !== draft.formulaVersionId ||
    result.ruleSetVersionId !== draft.ruleSetVersionId ||
    result.jurisdictionCode !== draft.jurisdictionCode
  ) {
    throw new LabelApiError(
      'DECLARATION_TARGET_MISMATCH',
      'The declarations do not match the selected label, formula, rule set and jurisdiction.',
      200,
    )
  }
  return result
}

export async function runValidation(
  labelVersionId: string,
  ruleSetVersionId: string,
  signal?: AbortSignal,
): Promise<ValidationRun> {
  const result = await requestJson(
    `/api/v1/label-versions/${encodeURIComponent(labelVersionId)}/validation-runs`,
    {
      method: 'POST',
      headers: labelOfficerHeaders,
      body: JSON.stringify({ ruleSetVersionId }),
      signal,
    },
  )
  if (!isValidationRun(result)) {
    throw new LabelApiError(
      'INVALID_RESPONSE',
      'The label API returned an invalid validation run.',
      200,
    )
  }
  return result
}

export async function getValidationRun(
  validationRunId: string,
  signal?: AbortSignal,
): Promise<ValidationRun> {
  const result = await requestJson(
    `/api/v1/validation-runs/${encodeURIComponent(validationRunId)}`,
    { headers: labelOfficerHeaders, signal },
  )
  if (!isValidationRun(result)) {
    throw new LabelApiError(
      'INVALID_RESPONSE',
      'The label API returned an invalid validation run.',
      200,
    )
  }
  return result
}
