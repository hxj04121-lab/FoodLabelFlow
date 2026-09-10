import { data } from '@/data/catalog'
import { ArrowLeft, FlaskConical, Plus, Trash2 } from 'lucide-react'
import { useEffect, useState } from 'react'
import { useBlocker } from 'react-router-dom'
import { DiscardChangesDialog } from './DiscardChangesDialog'
import { Button } from './ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from './ui/dialog'
import { Input } from './ui/input'

type Item = {
  id: number
  material: string
  specification: string
  quantity: string
  unit: string
}
const eligible = (s: typeof data.ingredient_specification_version[number]) => s.lifecycle_status === "RELEASED" && s.effective_date.slice(0,10) <= new Date().toLocaleDateString("en-CA")
const blank = (id: number): Item => ({
  id,
  material: '',
  specification: '',
  quantity: '',
  unit: '',
})

export function FormulaCreator() {
  const [open, setOpen] = useState(false)
  const [product, setProduct] = useState('')
  const [items, setItems] = useState<Item[]>([blank(1)])
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [preview, setPreview] = useState(false)
  const [discard, setDiscard] = useState(false)
  const [attempted, setAttempted] = useState(false)
  const [feedback, setFeedback] = useState('')
  const dirty =
    !!product ||
    items.length !== 1 ||
    items.some((i) => i.material || i.specification || i.quantity || i.unit)
  const blocker = useBlocker(
    ({ currentLocation, nextLocation }) =>
      open && !!dirty && currentLocation.pathname !== nextLocation.pathname,
  )
  function continueEditing() {
    setDiscard(false)
    if (blocker.state === 'blocked') blocker.reset()
  }
  function discardChanges() {
    reset()
    if (blocker.state === 'blocked') blocker.proceed()
  }
  useEffect(() => {
    if (!open || !dirty) return
    const handler = (e: BeforeUnloadEvent) => {
      e.preventDefault()
      e.returnValue = ''
    }
    window.addEventListener('beforeunload', handler)
    return () => window.removeEventListener('beforeunload', handler)
  }, [open, dirty])
  function reset() {
    setOpen(false)
    setProduct('')
    setItems([blank(1)])
    setErrors({})
    setPreview(false)
    setDiscard(false)
    setAttempted(false)
    setFeedback('')
  }
  function close() {
    if (dirty) setDiscard(true)
    else reset()
  }
  function update(id: number, patch: Partial<Item>) {
    setItems((old) => old.map((i) => (i.id === id ? { ...i, ...patch } : i)))
    setPreview(false)
    setFeedback(
      'material' in patch ? 'Material changed. Select a matching specification version.' : '',
    )
  }
  function collectErrors() {
    const next: Record<string, string> = {}
    if (!data.product.some((p) => p.product_id === product))
      next.product = 'Select a product.'
    items.forEach((i) => {
      if (!i.material) next[`material-${i.id}`] = 'Select a supplier material.'
      if (
        !data.ingredient_specification_version.some(
          (s) =>
            s.specification_version_id === i.specification &&
            s.supplier_material_id === i.material && eligible(s),
        )
      )
        next[`specification-${i.id}`] = 'Select a released, effective specification for this material.'
      if (
        i.quantity.trim() &&
        (!/^\d+(\.\d+)?$/.test(i.quantity.trim()) ||
            !Number.isFinite(Number(i.quantity)) || Number(i.quantity) <= 0)
      )
        next[`quantity-${i.id}`] =
            'Enter a positive number, such as 12.5. Zero, negatives and non-numeric values are not allowed.'
      else if (
        i.quantity.trim() &&
        (i.quantity.trim().split('.')[0].replace(/^0+/, '').length > 8 ||
          (i.quantity.trim().split('.')[1]?.length ?? 0) > 4)
      )
        next[`quantity-${i.id}`] =
          'Use at most 8 integer digits and 4 decimal places.'
      if (i.quantity.trim() && !i.unit.trim()) next[`unit-${i.id}`] = 'Enter a unit when a quantity is provided.'
      if (i.unit.trim() && !i.quantity.trim()) next[`quantity-${i.id}`] = 'Enter a quantity when a unit is provided.'
      if (Array.from(i.unit.trim()).length > 40)
        next[`unit-${i.id}`] = 'Unit must be at most 40 characters.'
    })
    return next
  }
  useEffect(() => {
    if (attempted) setErrors(collectErrors())
  }, [product, items, attempted])
  function validate() {
    if (preview) return
    setAttempted(true)
    const next = collectErrors()
    setErrors(next)
    if (Object.keys(next).length) {
      requestAnimationFrame(() =>
        document.getElementById(Object.keys(next)[0])?.focus(),
      )
      return
    }
    setPreview(true)
    setFeedback('Local checks passed. Preview ready; not saved to the server.')
  }
  const selectedProduct = data.product.find((p) => p.product_id === product)
  const error = (key: string) =>
    errors[key] ? (
      <p id={`${key}-error`} className="field-error">
        {errors[key]}
      </p>
    ) : null
  const a11y = (key: string) => ({
    'aria-invalid': !!errors[key],
    'aria-describedby': errors[key] ? `${key}-error` : undefined,
  })
  return (
    <>
      <Button onClick={() => setOpen(true)}>
        <Plus size={16} />
        Create formula preview
      </Button>
      <Dialog open={open} onOpenChange={(value) => !value && close()}>
        <DialogContent className="formula-creator">
          <DialogHeader>
            <DialogTitle>
              <span className="formula-title">
                <FlaskConical size={22} />
                {preview ? 'Formula preview' : 'Create formula'}
              </span>
            </DialogTitle>
            <DialogDescription>
              Local preview · Options loaded from the database; nothing submitted
            </DialogDescription>
          </DialogHeader>
          <>
            <div className="source-notice">
              This preview does not save or publish a formula. The server will assign its version, creator and status.
            </div>
            <p role="status" aria-live="polite" className="muted">
              {feedback}
            </p>
            {!preview ? (
              <form
                noValidate
                onSubmit={(e) => {
                  e.preventDefault()
                  validate()
                }}
              >
                <section className="formula-section">
                  <h3>01 · Product</h3>
                  <label htmlFor="product">
                    Product <span aria-hidden="true">*</span>
                  </label>
                  <select
                    id="product"
                    aria-required="true"
                    value={product}
                    {...a11y('product')}
                    onChange={(e) => {
                      setProduct(e.target.value)
                    }}
                  >
                    <option value="">Select a product</option>
                    {data.product.map((p) => (
                      <option key={p.product_id} value={p.product_id}>
                        {p.product_description} · FDC {p.fdc_id}
                      </option>
                    ))}
                  </select>
                  {error('product')}
                  {selectedProduct && (
                    <p className="muted">
                      {selectedProduct.brand_owner} ·{' '}
                      {selectedProduct.product_id}
                    </p>
                  )}
                </section>
                <section className="formula-section">
                  <div className="formula-section-heading">
                    <h3>02 · Formula items</h3>
                    <span>{items.length} items · in listed order</span>
                  </div>
                  {items.map((item, index) => {
                    const material = data.supplier_material.find(
                      (m) => m.supplier_material_id === item.material,
                    )
                    const specs = data.ingredient_specification_version.filter(
                      (s) => s.supplier_material_id === item.material && eligible(s),
                    )
                    return (
                      <fieldset className="formula-item" key={item.id}>
                        <legend>Item {index + 1}</legend>
                        <div className="formula-item-heading">
                          <span>Item {index + 1}</span>
                          <Button
                            type="button"
                            variant="ghost"
                            size="icon"
                            disabled={items.length === 1}
                            aria-label={`Remove material ${index + 1}`}
                            onClick={() => {
                              setItems(items.filter((i) => i.id !== item.id))
                              setFeedback(`Removed material ${index + 1}.`)
                            }}
                          >
                            <Trash2 size={16} />
                          </Button>
                        </div>
                        <div className="formula-fields">
                          <div>
                            <label htmlFor={`material-${item.id}`}>
                              Supplier material *
                            </label>
                            <select
                              id={`material-${item.id}`}
                              aria-required="true"
                              {...a11y(`material-${item.id}`)}
                              value={item.material}
                              onChange={(e) =>
                                update(item.id, {
                                  material: e.target.value,
                                  specification: '',
                                })
                              }
                            >
                              <option value="">Select a material</option>
                              {data.supplier_material.map((m) => (
                                <option
                                  key={m.supplier_material_id}
                                  value={m.supplier_material_id}
                                >
                                  {m.material_name} · {m.material_code}
                                </option>
                              ))}
                            </select>
                            {error(`material-${item.id}`)}
                          </div>
                          <div>
                            <label htmlFor={`specification-${item.id}`}>
                              Specification version *
                            </label>
                            <select
                              id={`specification-${item.id}`}
                              aria-required="true"
                              {...a11y(`specification-${item.id}`)}
                              disabled={!item.material}
                              value={item.specification}
                              onChange={(e) =>
                                update(item.id, {
                                  specification: e.target.value,
                                })
                              }
                            >
                              <option value="">
                                {item.material
                                  ? 'Select a version'
                                  : 'Select a material first'}
                              </option>
                              {specs.map((s) => (
                                <option
                                  key={s.specification_version_id}
                                  value={s.specification_version_id}
                                >
                                  V{s.version_number} · {s.lifecycle_status} ·{' '}
                                  {s.effective_date}
                                </option>
                              ))}
                            </select>
                            {error(`specification-${item.id}`)}
                          </div>
                          <div>
                            <label htmlFor={`quantity-${item.id}`}>
                              Quantity (optional)
                            </label>
                            <Input
                              id={`quantity-${item.id}`}
                              {...a11y(`quantity-${item.id}`)}
                              inputMode="decimal"
                              placeholder="e.g. 12.5"
                              value={item.quantity}
                              onChange={(e) =>
                                update(item.id, { quantity: e.target.value })
                              }
                            />
                            {error(`quantity-${item.id}`)}
                          </div>
                          <div>
                            <label htmlFor={`unit-${item.id}`}>
                              Unit (optional)
                            </label>
                            <Input
                              id={`unit-${item.id}`}
                              {...a11y(`unit-${item.id}`)}
                              placeholder="e.g. kg, g, %"
                              value={item.unit}
                              onChange={(e) =>
                                update(item.id, { unit: e.target.value })
                              }
                            />
                            {error(`unit-${item.id}`)}
                          </div>
                        </div>
                        {material && (
                          <p className="muted">
                            Supplier: {' '}
                            {
                              data.supplier.find(
                                (s) => s.supplier_id === material.supplier_id,
                              )?.supplier_name
                            }
                            {item.specification && <> · {item.specification}</>}
                          </p>
                        )}
                      </fieldset>
                    )
                  })}
                  <Button
                    type="button"
                    variant="outline"
                    onClick={() =>
                      setItems([
                        ...items,
                        blank(Math.max(...items.map((i) => i.id)) + 1),
                      ])
                    }
                  >
                    <Plus size={16} />
                    Add material
                  </Button>
                  <p className="muted">
                    Current M1 rules: provide both quantity and unit, or leave both blank. Quantity must be positive. No unit whitelist or 100% total is required. Pending M2 confirmation.
                  </p>
                </section>
                {Object.keys(errors).length > 0 && (
                  <div role="alert" className="error-notice">
                    <strong>
                      Please fix {Object.keys(errors).length} fields
                    </strong>
                    <ul>
                      {Object.entries(errors).map(([key, message]) => (
                        <li key={key}>
                          <button
                            type="button"
                            className="error-jump"
                            onClick={() => {
                              const target = document.getElementById(key)
                              if (
                                target instanceof HTMLSelectElement &&
                                target.disabled
                              )
                                document
                                  .getElementById(
                                    key.replace('specification-', 'material-'),
                                  )
                                  ?.focus()
                              else target?.focus()
                            }}
                          >
                            {key === 'product'
                              ? 'Product'
                              : `Item ${items.findIndex((i) => String(i.id) === key.split('-')[1]) + 1}`}
                            : {message}
                          </button>
                        </li>
                      ))}
                    </ul>
                  </div>
                )}
                <div className="formula-actions">
                  <Button type="button" variant="outline" onClick={close}>
                    Cancel
                  </Button>
                  <Button type="submit">Preview formula</Button>
                </div>
              </form>
            ) : (
              <section className="formula-section">
                <h3>{selectedProduct?.product_description}</h3>
                <p className="muted">
                  {selectedProduct?.product_id} · Version number assigned by the server
                </p>
                <div className="trace-list">
                  {items.map((i, index) => (
                    <div key={i.id} className="trace-row">
                      <span className="step-index">{index + 1}</span>
                      <div>
                        <strong>
                          {
                            data.supplier_material.find(
                              (m) => m.supplier_material_id === i.material,
                            )?.material_name
                          }
                        </strong>
                        <p>{i.specification}</p>
                        <p>
                          Quantity: {i.quantity.trim() || 'Not provided'} · Unit: {' '}
                          {i.unit.trim() || 'Not provided'}
                        </p>
                      </div>
                    </div>
                  ))}
                </div>
                <p className="muted">
                  Local checks passed. This formula has not been saved, published or validated by the server.
                </p>
                <div className="formula-actions">
                  <Button variant="outline" onClick={() => setPreview(false)}>
                    <ArrowLeft size={16} />
                    Back to editor
                  </Button>
                  <Button variant="outline" onClick={close}>
                    Close preview
                  </Button>
                </div>
              </section>
            )}
          </>
        </DialogContent>
      </Dialog>
      <DiscardChangesDialog
        open={discard || blocker.state === 'blocked'}
        onContinue={continueEditing}
        onDiscard={discardChanges}
      />
    </>
  )
}
