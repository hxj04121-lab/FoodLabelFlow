import { getLabelDeclarations, type Allergen, type LabelDraft } from '@/api/labels'
import { Panel, SectionHead } from '@/components/catalog-shared'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { useLabelRead } from '@/components/useLabelRead'
import { useEffect, useState } from 'react'

export type EditableDeclaration = {
  allergenId: string
  declarationType: 'CONTAINS'
  displayText: string
}

export function LabelDeclarationsPanel({
  draft,
  allergens = [],
  revisionAllowed = false,
  saving = false,
  onSaveRevision,
}: {
  draft: LabelDraft | null
  allergens?: Allergen[]
  revisionAllowed?: boolean
  saving?: boolean
  onSaveRevision?: (declarations: EditableDeclaration[]) => void
}) {
  const { state, retry } = useLabelRead(draft, getLabelDeclarations, 'label declaration')
  const [edits, setEdits] = useState<EditableDeclaration[]>([])

  useEffect(() => {
    if (state.status === 'ready') {
      setEdits(state.data.declarations.map(declaration => ({
        allergenId: declaration.allergenId,
        declarationType: 'CONTAINS',
        displayText: declaration.displayText ?? '',
      })))
    } else {
      setEdits([])
    }
  }, [draft?.labelVersionId, state.status, state.status === 'ready' ? state.data : null])

  function toggle(allergen: Allergen, checked: boolean) {
    setEdits(current => checked
      ? [...current, { allergenId: allergen.allergenId, declarationType: 'CONTAINS', displayText: `Contains ${allergen.displayName}` }]
      : current.filter(declaration => declaration.allergenId !== allergen.allergenId))
  }

  return (
    <Panel className="declaration-panel">
      <SectionHead title="Label declarations" caption="Stored declarations for this exact label version" />
      <section className="declaration-content" aria-label="Structured label declarations" aria-busy={!!draft && state.status === 'loading'}>
        {!draft && <p>Create or load a label version to view its declarations.</p>}
        {draft && <>
          <p className="derived-binding">Label: {draft.labelVersionId} · Formula: {draft.formulaVersionId} · Rule set: {draft.ruleSetVersionId} · {draft.jurisdictionCode}</p>
          {revisionAllowed
            ? <p>This version was returned for changes. Edit a copy of its declaration snapshot; saving creates a new immutable LabelVersion.</p>
            : <p>These are stored declarations. Compare them with the derived facts and validation findings before review.</p>}
          {state.status === 'loading' && <p role="status">Loading label declarations…</p>}
          {state.status === 'error' && <>
            <p role="alert" className="error-notice">{state.message}</p>
            <Button variant="outline" onClick={retry}>Retry declarations</Button>
          </>}
          {state.status === 'ready' && <>
            <p>{state.data.declarations.length} declaration(s) for this label version</p>
            {state.data.declarations.length === 0 && !revisionAllowed && <p>No structured declarations are stored for this version. This does not mean the product is allergen-free; run validation to check for missing declarations.</p>}
            {revisionAllowed ? <fieldset className="declaration-editor" disabled={saving}>
              <legend>New version declaration snapshot</legend>
              {allergens.map(allergen => {
                const declaration = edits.find(row => row.allergenId === allergen.allergenId)
                return <div className="declaration-editor-row" key={allergen.allergenId}>
                  <label>
                    <input type="checkbox" checked={!!declaration} onChange={event => toggle(allergen, event.target.checked)} />
                    Declare {allergen.displayName} ({allergen.allergenCode})
                  </label>
                  {declaration && <input aria-label={`${allergen.displayName} declaration text`}
                    value={declaration.displayText} maxLength={300}
                    onChange={event => setEdits(current => current.map(row => row.allergenId === allergen.allergenId
                      ? { ...row, displayText: event.target.value } : row))} />}
                </div>
              })}
              {allergens.length === 0 && <p>The canonical allergen catalog must load before editing a revision.</p>}
            </fieldset> : <div className="declaration-list">
              {state.data.declarations.map(declaration => <article key={`${declaration.allergenId}-${declaration.declarationType}`}>
                <div className="declaration-heading">
                  <h3>{declaration.allergenId}</h3>
                  <Badge variant="outline">{declaration.declarationType}</Badge>
                </div>
                <p>{declaration.displayText || 'No display text provided.'}</p>
                <small>Source: {declaration.declarationSource}</small>
              </article>)}
            </div>}
            {revisionAllowed && <Button disabled={saving || allergens.length === 0}
              onClick={() => onSaveRevision?.(edits)}>{saving ? 'Creating new version…' : 'Save changes as new version'}</Button>}
          </>}
        </>}
      </section>
    </Panel>
  )
}
