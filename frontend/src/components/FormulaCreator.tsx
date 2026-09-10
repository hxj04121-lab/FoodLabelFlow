import data from '@/data/seed-preview.json'
import {
  createFormula,
  getCatalogProduct,
  getFormulaHistory,
  releaseFormula,
  type FormulaVersion,
} from '@/api/client'
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
  const [created, setCreated] = useState<FormulaVersion | null>(null)
  const [expectedCurrentFormulaId, setExpectedCurrentFormulaId] = useState<string | null>(null)
  const [history, setHistory] = useState<FormulaVersion[]>([])
  const [submitting, setSubmitting] = useState(false)
  const [released, setReleased] = useState(false)
  const dirty =
    !released &&
    (!!product ||
      items.length !== 1 ||
      items.some((i) => i.material || i.specification || i.quantity || i.unit))
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
    setCreated(null)
    setExpectedCurrentFormulaId(null)
    setHistory([])
    setSubmitting(false)
    setReleased(false)
  }
  function close() {
    if (dirty) setDiscard(true)
    else reset()
  }
  function update(id: number, patch: Partial<Item>) {
    setItems((old) => old.map((i) => (i.id === id ? { ...i, ...patch } : i)))
    setPreview(false)
    setFeedback(
      'material' in patch ? '物料已变更，请重新选择对应规格版本。' : '',
    )
  }
  function collectErrors() {
    const next: Record<string, string> = {}
    if (!data.product.some((p) => p.product_id === product))
      next.product = '请选择所属产品。'
    items.forEach((i) => {
      if (!i.material) next[`material-${i.id}`] = '请选择供应商物料。'
      if (
        !data.ingredient_specification_version.some(
          (s) =>
            s.specification_version_id === i.specification &&
            s.supplier_material_id === i.material,
        )
      )
        next[`specification-${i.id}`] = '请选择该物料对应的规格版本。'
      if (
        i.quantity.trim() &&
        (!/^\d+(\.\d+)?$/.test(i.quantity.trim()) ||
          !Number.isFinite(Number(i.quantity)))
      )
        next[`quantity-${i.id}`] =
          '请输入非负数，例如 12.5；不接受负数或非数字。'
      else if (
        i.quantity.trim() &&
        (i.quantity.trim().split('.')[0].replace(/^0+/, '').length > 8 ||
          (i.quantity.trim().split('.')[1]?.length ?? 0) > 4)
      )
        next[`quantity-${i.id}`] =
          '数据库最多支持 8 位整数和 4 位小数，请调整数量。'
      if (Array.from(i.unit.trim()).length > 40)
        next[`unit-${i.id}`] = '单位最多 40 个字符，请缩短内容。'
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
    setFeedback('本地校验通过，配方预览已生成；尚未保存到服务器。')
  }
  async function saveDraft() {
    setSubmitting(true)
    setFeedback('正在将配方保存到服务器……')
    try {
      const currentProduct = await getCatalogProduct(product)
      const formula = await createFormula({
        productId: product,
        provenanceId: 'prov_project_seed',
        items: items.map((item) => ({
          materialId: item.material,
          specificationId: item.specification,
          quantity: item.quantity.trim() ? Number(item.quantity) : null,
          unit: item.unit.trim() || null,
        })),
      })
      setExpectedCurrentFormulaId(currentProduct.current_formula_version_id)
      setCreated(formula)
      setFeedback(`草稿 V${formula.version_number} 已保存到服务器。`)
    } catch (error) {
      setFeedback(`保存失败：${error instanceof Error ? error.message : '未知错误'}`)
    } finally {
      setSubmitting(false)
    }
  }
  async function publish() {
    if (!created) return
    setSubmitting(true)
    setFeedback('正在发布配方并读取历史……')
    try {
      const formula = await releaseFormula(
        created.formula_version_id,
        expectedCurrentFormulaId,
      )
      const versions = await getFormulaHistory(product)
      setCreated(formula)
      setHistory(versions)
      setReleased(true)
      setFeedback(`配方 V${formula.version_number} 已发布，历史版本已从数据库加载。`)
    } catch (error) {
      setFeedback(`发布失败：${error instanceof Error ? error.message : '未知错误'}`)
    } finally {
      setSubmitting(false)
    }
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
        创建配方
      </Button>
      <Dialog open={open} onOpenChange={(value) => !value && close()}>
        <DialogContent className="formula-creator">
          <DialogHeader>
            <DialogTitle>
              <span className="formula-title">
                <FlaskConical size={22} />
                {preview ? '配方内容预览' : '创建配方'}
              </span>
            </DialogTitle>
            <DialogDescription>
              配方创建 · 服务端校验 · 版本发布与历史
            </DialogDescription>
          </DialogHeader>
          <>
            <div className="source-notice">
              配方会写入 MySQL；版本号、创建人和发布状态由后端分配。
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
                  <h3>01 · 所属产品</h3>
                  <label htmlFor="product">
                    产品 <span aria-hidden="true">*</span>
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
                    <option value="">选择一个产品</option>
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
                    <h3>02 · 配方物料</h3>
                    <span>{items.length} 项 · 按列表顺序排列</span>
                  </div>
                  {items.map((item, index) => {
                    const material = data.supplier_material.find(
                      (m) => m.supplier_material_id === item.material,
                    )
                    const specs = data.ingredient_specification_version.filter(
                      (s) => s.supplier_material_id === item.material,
                    )
                    return (
                      <fieldset className="formula-item" key={item.id}>
                        <legend>物料 {index + 1}</legend>
                        <div className="formula-item-heading">
                          <span>配方项 {index + 1}</span>
                          <Button
                            type="button"
                            variant="ghost"
                            size="icon"
                            disabled={items.length === 1}
                            aria-label={`删除物料 ${index + 1}`}
                            onClick={() => {
                              setItems(items.filter((i) => i.id !== item.id))
                              setFeedback(`已删除物料 ${index + 1}。`)
                            }}
                          >
                            <Trash2 size={16} />
                          </Button>
                        </div>
                        <div className="formula-fields">
                          <div>
                            <label htmlFor={`material-${item.id}`}>
                              供应商物料 *
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
                              <option value="">选择物料</option>
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
                              规格版本 *
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
                                  ? '选择具体版本'
                                  : '请先选择物料'}
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
                              数量（选填）
                            </label>
                            <Input
                              id={`quantity-${item.id}`}
                              {...a11y(`quantity-${item.id}`)}
                              inputMode="decimal"
                              placeholder="例如 12.5"
                              value={item.quantity}
                              onChange={(e) =>
                                update(item.id, { quantity: e.target.value })
                              }
                            />
                            {error(`quantity-${item.id}`)}
                          </div>
                          <div>
                            <label htmlFor={`unit-${item.id}`}>
                              单位（选填）
                            </label>
                            <Input
                              id={`unit-${item.id}`}
                              {...a11y(`unit-${item.id}`)}
                              placeholder="例如 kg、g、%"
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
                            供应商：
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
                    添加物料
                  </Button>
                  <p className="muted">
                    数量和单位暂为选填；不限定单位列表、不计算 100%
                    合计。业务规则待接口约定后补充。
                  </p>
                </section>
                {Object.keys(errors).length > 0 && (
                  <div role="alert" className="error-notice">
                    <strong>
                      还有 {Object.keys(errors).length} 项需要修正
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
                              ? '产品'
                              : `物料 ${items.findIndex((i) => String(i.id) === key.split('-')[1]) + 1}`}
                            ：{message}
                          </button>
                        </li>
                      ))}
                    </ul>
                  </div>
                )}
                <div className="formula-actions">
                  <Button type="button" variant="outline" onClick={close}>
                    取消
                  </Button>
                  <Button type="submit">预览配方</Button>
                </div>
              </form>
            ) : (
              <section className="formula-section">
                <h3>{selectedProduct?.product_description}</h3>
                <p className="muted">
                  {selectedProduct?.product_id} ·{' '}
                  {created ? `服务器版本 V${created.version_number}` : '新版本号待服务器分配'}
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
                          数量：{i.quantity.trim() || '未填写'} · 单位：
                          {i.unit.trim() || '未填写'}
                        </p>
                      </div>
                    </div>
                  ))}
                </div>
                <p className="muted">
                  {released
                    ? '已通过服务端校验、写入数据库并发布为当前版本。'
                    : created
                      ? '草稿已写入数据库，可继续发布。'
                      : '输入已通过本地基础检查，点击保存后由服务端再次校验。'}
                </p>
                {history.length > 0 && (
                  <section aria-label="配方历史" className="formula-history">
                    <h3>配方历史</h3>
                    {history.map((version) => (
                      <div className="history-entry" key={version.formula_version_id}>
                        <span className="history-dot" />
                        <div>
                          <strong>V{version.version_number} · {version.lifecycle_status}</strong>
                          <p>{version.formula_version_id}</p>
                        </div>
                      </div>
                    ))}
                  </section>
                )}
                <div className="formula-actions">
                  {!created && <Button variant="outline" onClick={() => setPreview(false)}>
                    <ArrowLeft size={16} />
                    返回编辑
                  </Button>}
                  {!created && <Button disabled={submitting} onClick={saveDraft}>
                    {submitting ? '保存中……' : '保存草稿'}
                  </Button>}
                  {created && !released && <Button disabled={submitting} onClick={publish}>
                    {submitting ? '发布中……' : '发布配方'}
                  </Button>}
                  <Button variant="outline" onClick={close}>
                    {released ? '完成' : '关闭预览'}
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
