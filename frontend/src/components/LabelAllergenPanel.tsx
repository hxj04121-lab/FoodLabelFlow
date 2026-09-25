import { useEffect, useState } from 'react'
import { getLabelDerivedAllergens, LabelApiError, type LabelAllergenFacts, type LabelDraft } from '@/api/labels'
import { Panel, SectionHead } from '@/components/catalog-shared'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'

type ReadState =
  | { status: 'loading' }
  | { status: 'ready'; data: LabelAllergenFacts }
  | { status: 'error'; message: string }

export function LabelAllergenPanel({ draft }: { draft: LabelDraft | null }) {
  const [attempt, setAttempt] = useState(0)
  const [state, setState] = useState<ReadState>({ status: 'loading' })

  useEffect(() => {
    if (!draft) return
    const controller = new AbortController()
    let active = true
    const timeout = setTimeout(() => controller.abort(), 15_000)
    setState({ status: 'loading' })
    getLabelDerivedAllergens(draft, controller.signal)
      .then((data) => { if (active) setState({ status: 'ready', data }) })
      .catch((error: unknown) => {
        if (!active) return
        setState({
          status: 'error',
          message: controller.signal.aborted
            ? 'The derived allergen request timed out. Try again.'
            : error instanceof LabelApiError
              ? `${error.code}: ${error.message}`
              : 'Unable to connect to the derived allergen service. Try again.',
        })
      })
      .finally(() => clearTimeout(timeout))
    return () => {
      active = false
      clearTimeout(timeout)
      controller.abort()
    }
  }, [draft, attempt])

  return (
    <Panel className="derived-panel">
      <SectionHead title="Derived allergens" caption="Read from this label's formula and rule-set versions" />
      <section className="derived-content" aria-label="Derived allergen facts" aria-busy={!!draft && state.status === 'loading'}>
        {!draft && <p>Create or load a label version to view its derived allergens.</p>}
        {draft && <>
          <p className="derived-binding">Label: {draft.labelVersionId} · Formula: {draft.formulaVersionId} · Rule set: {draft.ruleSetVersionId} · {draft.jurisdictionCode}</p>
          <p>These facts are recomputed from stored version-bound inputs. They are not a saved validation result or approval to submit.</p>
          {state.status === 'loading' && <p role="status">Loading derived allergens…</p>}
          {state.status === 'error' && <>
            <p role="alert" className="error-notice">{state.message}</p>
            <Button variant="outline" onClick={() => setAttempt((value) => value + 1)}>Retry derived allergens</Button>
          </>}
          {state.status === 'ready' && <>
            <p>{state.data.facts.length} derived allergen(s) · {state.data.unresolvedComponents.length} unresolved component(s)</p>
            {state.data.facts.length === 0 && <p>No derived allergen facts were returned. This does not establish that the label is allergen-free.</p>}
            <div className="derived-facts">
              {state.data.facts.map((fact) => <article key={fact.allergenId}>
                <h3>{fact.allergenCode}</h3>
                <p>{fact.allergenId}</p>
                <details>
                  <summary>Derivation evidence for {fact.allergenCode} ({fact.derivationEvidence.length})</summary>
                  {fact.derivationEvidence.map((evidence, index) => <dl key={index} className="derived-evidence">
                    <div><dt>Formula item</dt><dd>{evidence.formulaItemId}</dd></div>
                    <div><dt>Specification version</dt><dd>{evidence.specificationVersionId}</dd></div>
                    <div><dt>Component</dt><dd>{evidence.specComponentId}</dd></div>
                    <div><dt>Ingredient</dt><dd>{evidence.ingredientId}</dd></div>
                    <div><dt>Allergen mapping</dt><dd>{evidence.ingredientAllergenId}</dd></div>
                    <div><dt>Evidence rule</dt><dd>{evidence.evidenceRule}</dd></div>
                    <div><dt>Data provenance</dt><dd>{evidence.dataProvenanceId}</dd></div>
                  </dl>)}
                </details>
              </article>)}
            </div>
            {state.data.unresolvedComponents.length > 0 && <section aria-label="Unresolved components" className="derived-unresolved">
              <h3>Unresolved components</h3>
              <p role="status">Some ingredients could not be resolved. Review these components before drawing conclusions about allergens.</p>
              {state.data.unresolvedComponents.map((component, index) => <article key={index}>
                <Badge variant="outline">{component.matchStatus}</Badge>
                <h4>{component.rawPhrase}</h4>
                <dl className="derived-evidence">
                  <div><dt>Formula item</dt><dd>{component.formulaItemId}</dd></div>
                  <div><dt>Specification version</dt><dd>{component.specificationVersionId}</dd></div>
                  <div><dt>Component</dt><dd>{component.specComponentId}</dd></div>
                  <div><dt>Ingredient</dt><dd>{component.ingredientId}</dd></div>
                  <div><dt>Match rule</dt><dd>{component.matchRule}</dd></div>
                </dl>
              </article>)}
            </section>}
          </>}
        </>}
      </section>
    </Panel>
  )
}
