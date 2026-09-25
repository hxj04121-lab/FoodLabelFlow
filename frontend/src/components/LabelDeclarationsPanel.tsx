import { useEffect, useState } from 'react'
import { getLabelDeclarations, LabelApiError, type LabelDeclarations, type LabelDraft } from '@/api/labels'
import { Panel, SectionHead } from '@/components/catalog-shared'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'

type ReadState =
  | { status: 'loading' }
  | { status: 'ready'; data: LabelDeclarations }
  | { status: 'error'; message: string }

export function LabelDeclarationsPanel({ draft }: { draft: LabelDraft | null }) {
  const [attempt, setAttempt] = useState(0)
  const [state, setState] = useState<ReadState>({ status: 'loading' })

  useEffect(() => {
    if (!draft) return
    const controller = new AbortController()
    let active = true
    const timeout = setTimeout(() => controller.abort(), 15_000)
    setState({ status: 'loading' })
    getLabelDeclarations(draft, controller.signal)
      .then((data) => { if (active) setState({ status: 'ready', data }) })
      .catch((error: unknown) => {
        if (!active) return
        setState({
          status: 'error',
          message: controller.signal.aborted
            ? 'The label declaration request timed out. Try again.'
            : error instanceof LabelApiError
              ? `${error.code}: ${error.message}`
              : 'Unable to connect to the label declaration service. Try again.',
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
    <Panel className="declaration-panel">
      <SectionHead title="Label declarations" caption="Stored declarations for this exact label version" />
      <section className="declaration-content" aria-label="Structured label declarations" aria-busy={!!draft && state.status === 'loading'}>
        {!draft && <p>Create or load a label version to view its declarations.</p>}
        {draft && <>
          <p className="derived-binding">Label: {draft.labelVersionId} · Formula: {draft.formulaVersionId} · Rule set: {draft.ruleSetVersionId} · {draft.jurisdictionCode}</p>
          <p>These are stored label declarations. Compare them with the derived facts and validation findings before review.</p>
          {state.status === 'loading' && <p role="status">Loading label declarations…</p>}
          {state.status === 'error' && <>
            <p role="alert" className="error-notice">{state.message}</p>
            <Button variant="outline" onClick={() => setAttempt((value) => value + 1)}>Retry declarations</Button>
          </>}
          {state.status === 'ready' && <>
            <p>{state.data.declarations.length} declaration(s) for this label version</p>
            {state.data.declarations.length === 0 && <p>No structured declarations are stored for this version. This does not mean the product is allergen-free; run validation to check for missing declarations.</p>}
            <div className="declaration-list">
              {state.data.declarations.map((declaration) => <article key={`${declaration.allergenId}-${declaration.declarationType}`}>
                <div className="declaration-heading">
                  <h3>{declaration.allergenId}</h3>
                  <Badge variant="outline">{declaration.declarationType}</Badge>
                </div>
                <p>{declaration.displayText || 'No display text provided.'}</p>
                <small>Source: {declaration.declarationSource}</small>
              </article>)}
            </div>
          </>}
        </>}
      </section>
    </Panel>
  )
}
