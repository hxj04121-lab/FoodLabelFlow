import { getLabelDeclarations, type LabelDraft } from '@/api/labels'
import { Panel, SectionHead } from '@/components/catalog-shared'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { useLabelRead } from '@/components/useLabelRead'

export function LabelDeclarationsPanel({ draft }: { draft: LabelDraft | null }) {
  const { state, retry } = useLabelRead(draft, getLabelDeclarations, 'label declaration')

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
            <Button variant="outline" onClick={retry}>Retry declarations</Button>
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
