import { data } from '@/data/catalog'
import { createSpecification, releaseSpecification, type SpecificationComponentInput, type SpecificationRecord } from '@/api/catalog'
import { LabelApiError } from '@/api/labels'
import { useCurrentIdentity } from '@/components/CurrentIdentityPanel'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Plus, Trash2 } from 'lucide-react'
import { useState } from 'react'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from './ui/dialog'

type ComponentRow = SpecificationComponentInput & { rowId: number }

function today() {
  return new Date().toISOString().slice(0, 10)
}

function knownRejection(cause: unknown) {
  return cause instanceof LabelApiError && cause.status >= 400 && cause.status < 500
}

export function SpecificationCreator() {
  const identity = useCurrentIdentity()
  const [open, setOpen] = useState(false)
  const [materialId, setMaterialId] = useState('')
  const [effectiveDate, setEffectiveDate] = useState(today)
  const [provenanceId, setProvenanceId] = useState('prov_project_seed')
  const [components, setComponents] = useState<ComponentRow[]>([{ rowId: 1, ingredientId: '', rawPhrase: '', matchRule: '' }])
  const [nextRowId, setNextRowId] = useState(2)
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [feedback, setFeedback] = useState('')
  const [busy, setBusy] = useState(false)
  const [uncertain, setUncertain] = useState(false)
  const [created, setCreated] = useState<SpecificationRecord | null>(null)
  const [released, setReleased] = useState(false)

  const authorized = identity.hasPermission('DATA.MAINTAIN')
  const materialOptions = data.supplier_material.map(material => ({
    ...material,
    supplierName: data.supplier.find(supplier => supplier.supplier_id === material.supplier_id)?.supplier_name ?? material.supplier_id,
  }))

  function chooseMaterial(value: string) {
    setMaterialId(value)
    const latest = data.ingredient_specification_version
      .filter(specification => specification.supplier_material_id === value)
      .sort((a, b) => Number(b.version_number) - Number(a.version_number))[0]
    const existing = latest
      ? data.spec_component.filter(component => component.specification_version_id === latest.specification_version_id)
      : []
    setComponents(existing.length
      ? existing.map((component, index) => ({
          rowId: index + 1,
          ingredientId: component.ingredient_id,
          rawPhrase: component.raw_phrase,
          matchRule: component.match_rule,
        }))
      : [{ rowId: 1, ingredientId: '', rawPhrase: '', matchRule: '' }])
    setNextRowId(existing.length + 1)
    setErrors({})
    setFeedback(existing.length ? 'Copied the latest specification components. Edit or add ingredients before saving.' : '')
  }

  function validate() {
    const next: Record<string, string> = {}
    if (!materialOptions.some(material => material.supplier_material_id === materialId)) next.material = 'Select a supplier material.'
    if (!effectiveDate) next.effectiveDate = 'Choose an effective date.'
    if (!provenanceId.trim()) next.provenanceId = 'Enter a provenance ID.'
    if (components.length === 0) next.components = 'Add at least one specification component.'
    for (const component of components) {
      if (!data.ingredient.some(ingredient => ingredient.ingredient_id === component.ingredientId)) next[`ingredient-${component.rowId}`] = 'Select an ingredient from the catalog.'
      if (!component.rawPhrase.trim()) next[`rawPhrase-${component.rowId}`] = 'Enter the wording used by the supplier.'
      if (!component.matchRule.trim()) next[`matchRule-${component.rowId}`] = 'Enter the matching evidence or rule.'
    }
    setErrors(next)
    return Object.keys(next).length === 0
  }

  async function saveDraft(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (busy || uncertain || created || !authorized || !validate()) return
    setBusy(true)
    setFeedback('Saving specification draft…')
    try {
      const record = await createSpecification({
        materialId,
        effectiveDate,
        provenanceId: provenanceId.trim(),
        components: components.map(({ ingredientId, rawPhrase, matchRule }) => ({
          ingredientId, rawPhrase: rawPhrase.trim(), matchRule: matchRule.trim(),
        })),
      })
      if (record.lifecycle_status !== 'DRAFT' || record.supplier_material_id !== materialId) {
        setUncertain(true)
        setFeedback('The save response did not confirm the requested draft. Do not submit again; refresh the catalog and inspect the returned ID.')
        return
      }
      setCreated(record)
      setFeedback(`Draft V${record.version_number} saved. Review the component evidence, then release it explicitly.`)
    } catch (cause) {
      setUncertain(!knownRejection(cause))
      setFeedback(cause instanceof Error ? cause.message : 'The save result is unknown. Do not submit again until the catalog is checked.')
    } finally {
      setBusy(false)
    }
  }

  async function releaseDraft() {
    if (!created || busy || uncertain || released || !authorized) return
    setBusy(true)
    setFeedback('Releasing specification…')
    try {
      const record = await releaseSpecification(created.specification_version_id)
      if (record.lifecycle_status !== 'RELEASED') {
        setUncertain(true)
        setFeedback('The release response did not confirm RELEASED. Inspect the saved specification before retrying.')
        return
      }
      setCreated(record)
      setReleased(true)
      setFeedback(`Specification V${record.version_number} released: ${record.specification_version_id}`)
    } catch (cause) {
      setUncertain(!knownRejection(cause))
      setFeedback(cause instanceof Error ? cause.message : 'The release result is unknown. Inspect the saved specification before retrying.')
    } finally {
      setBusy(false)
    }
  }

  function addComponent() {
    setComponents(current => [...current, { rowId: nextRowId, ingredientId: '', rawPhrase: '', matchRule: '' }])
    setNextRowId(current => current + 1)
  }

  return <>
    <Button onClick={() => setOpen(true)} disabled={!authorized}>
      <Plus size={16} /> Create supplier specification
    </Button>
    {!authorized && <p role="status" className="muted">Specification create and release requires DATA.MAINTAIN. Switch to the connected Data Admin identity.</p>}
    <Dialog open={open} onOpenChange={value => { if (!busy) setOpen(value) }}>
      <DialogContent className="formula-creator">
        <DialogHeader>
          <DialogTitle>Create supplier specification</DialogTitle>
          <DialogDescription>Save a new immutable version, inspect it, then release it as a separate action.</DialogDescription>
        </DialogHeader>
        <p className="source-notice">Writes use the connected identity. This workspace exposes catalog maintenance through the Data Admin demo identity.</p>
        <p role="status" aria-live="polite" className="muted">{feedback}</p>
        {created ? <section aria-label="Specification draft result" className="formula-section">
          <h3>Specification V{created.version_number} · {released ? 'RELEASED' : created.lifecycle_status}</h3>
          <code>{created.specification_version_id}</code>
          <p>Supplier material: {created.supplier_material_id}</p>
          <p>Effective date: {effectiveDate}</p>
          <div className="trace-list">
            {components.map((component, index) => <div className="trace-row" key={component.rowId}>
              <span className="step-index">{index + 1}</span>
              <div><strong>{component.rawPhrase}</strong><p>{component.ingredientId} · {component.matchRule}</p></div>
            </div>)}
          </div>
          {uncertain && <Button type="button" variant="outline" onClick={() => window.location.reload()}>Reload catalog to inspect the saved version</Button>}
          {!released && <Button type="button" disabled={busy || uncertain} onClick={() => void releaseDraft()}>Release specification</Button>}
          {released && <Button type="button" variant="outline" onClick={() => { setOpen(false); window.dispatchEvent(new Event('catalog-updated')) }}>Done</Button>}
        </section> : <form noValidate onSubmit={event => void saveDraft(event)}>
          <section className="formula-section">
            <h3>01 · Supplier material</h3>
            <label htmlFor="specification-material">Supplier material *</label>
            <select id="specification-material" value={materialId} disabled={busy || uncertain} aria-invalid={!!errors.material} onChange={event => chooseMaterial(event.target.value)}>
              <option value="">Select a supplier material</option>
              {materialOptions.map(material => <option key={material.supplier_material_id} value={material.supplier_material_id}>{material.material_name} · {material.supplierName} · {material.supplier_material_id}</option>)}
            </select>
            {errors.material && <p className="field-error">{errors.material}</p>}
            <div className="formula-fields">
              <div><label htmlFor="specification-effective-date">Effective date *</label><Input id="specification-effective-date" type="date" value={effectiveDate} disabled={busy || uncertain} onChange={event => setEffectiveDate(event.target.value)} />{errors.effectiveDate && <p className="field-error">{errors.effectiveDate}</p>}</div>
              <div><label htmlFor="specification-provenance">Provenance ID *</label><Input id="specification-provenance" value={provenanceId} disabled={busy || uncertain} onChange={event => setProvenanceId(event.target.value)} />{errors.provenanceId && <p className="field-error">{errors.provenanceId}</p>}</div>
            </div>
          </section>
          <section className="formula-section">
            <div className="formula-section-heading"><h3>02 · Components</h3><Button type="button" variant="outline" size="sm" disabled={busy || uncertain} onClick={addComponent}><Plus size={15} /> Add ingredient</Button></div>
            {components.map((component, index) => <fieldset className="formula-item" key={component.rowId}>
              <div className="formula-item-heading"><span>Component {index + 1}</span><Button type="button" variant="ghost" size="icon" aria-label={`Remove ingredient ${index + 1}`} disabled={busy || uncertain || components.length === 1} onClick={() => setComponents(current => current.filter(row => row.rowId !== component.rowId))}><Trash2 size={16} /></Button></div>
              <label htmlFor={`specification-ingredient-${component.rowId}`}>Ingredient *</label>
              <select id={`specification-ingredient-${component.rowId}`} value={component.ingredientId} disabled={busy || uncertain} aria-invalid={!!errors[`ingredient-${component.rowId}`]} onChange={event => setComponents(current => current.map(row => row.rowId === component.rowId ? { ...row, ingredientId: event.target.value } : row))}>
                <option value="">Select a catalog ingredient</option>
                {data.ingredient.map(ingredient => <option key={ingredient.ingredient_id} value={ingredient.ingredient_id}>{ingredient.canonical_name} · {ingredient.ingredient_id}</option>)}
              </select>
              {errors[`ingredient-${component.rowId}`] && <p className="field-error">{errors[`ingredient-${component.rowId}`]}</p>}
              <div className="formula-fields">
                <div><label htmlFor={`specification-phrase-${component.rowId}`}>Supplier wording *</label><Input id={`specification-phrase-${component.rowId}`} value={component.rawPhrase} maxLength={300} disabled={busy || uncertain} aria-invalid={!!errors[`rawPhrase-${component.rowId}`]} onChange={event => setComponents(current => current.map(row => row.rowId === component.rowId ? { ...row, rawPhrase: event.target.value } : row))} />{errors[`rawPhrase-${component.rowId}`] && <p className="field-error">{errors[`rawPhrase-${component.rowId}`]}</p>}</div>
                <div><label htmlFor={`specification-match-${component.rowId}`}>Matching evidence *</label><Input id={`specification-match-${component.rowId}`} value={component.matchRule} maxLength={500} disabled={busy || uncertain} aria-invalid={!!errors[`matchRule-${component.rowId}`]} onChange={event => setComponents(current => current.map(row => row.rowId === component.rowId ? { ...row, matchRule: event.target.value } : row))} />{errors[`matchRule-${component.rowId}`] && <p className="field-error">{errors[`matchRule-${component.rowId}`]}</p>}</div>
              </div>
            </fieldset>)}
          </section>
          {errors.components && <p className="field-error">{errors.components}</p>}
          {uncertain && <p role="alert">Save result is unknown. Do not submit again; reload the catalog and inspect the latest specification.</p>}
          <Button type="submit" disabled={busy || uncertain || !authorized}>Save specification draft</Button>
        </form>}
      </DialogContent>
    </Dialog>
  </>
}
