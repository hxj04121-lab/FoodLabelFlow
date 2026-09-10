import { useRef, useState } from 'react'
import { Button } from './ui/button'
import { CatalogRequestError, createFormula, getCatalogProduct, getFormulaHistory, releaseFormula, type CreateFormulaRequest, type FormulaVersion } from '@/api/client'

export function FormulaPersistence({request,onSaved,onBusy}: {
  request: CreateFormulaRequest; onSaved:()=>void; onBusy:(busy:boolean)=>void
}) {
  const [enabled,setEnabled]=useState(false)
  const [created,setCreated]=useState<FormulaVersion|null>(null)
  const [history,setHistory]=useState<FormulaVersion[]>([])
  const [expected,setExpected]=useState<string|null>(null)
  const [busy,setBusy]=useState(false)
  const lock=useRef(false)
  const [message,setMessage]=useState('')
  const [error,setError]=useState('')
  const [uncertain,setUncertain]=useState(false)
  const [conflict,setConflict]=useState(false)
  const [confirm,setConfirm]=useState(false)
  const local=['127.0.0.1','localhost','[::1]'].includes(location.hostname)
  async function run(operation:()=>Promise<void>, mutation=false) {
    if(lock.current)return
    lock.current=true;setBusy(true);onBusy(true);setError('')
    try {await operation()} catch(e) {
      const code=e instanceof CatalogRequestError?e.code:'NETWORK_ERROR'
      setError(`${code}: ${e instanceof Error?e.message:'Request failed'}`)
      if(code==='CURRENT_FORMULA_CHANGED')setConflict(true)
      if(mutation && (!(e instanceof CatalogRequestError)||e.status>=500)) {setUncertain(true);setMessage('The outcome may be unknown. Do not repeat the write. Check server history before taking further action.')}
    } finally {lock.current=false;setBusy(false);onBusy(false)}
  }
  async function save() {
    if(created||uncertain||!enabled||!local)return
    await run(async()=>{
      const p=await getCatalogProduct(request.productId)
      setExpected(p.current_formula_version_id)
      const f=await createFormula(request)
      setCreated(f);onSaved();setMessage(`Draft V${f.version_number} saved to the server.`)
    },true)
  }
  async function refresh() {
    await run(async()=>{
      const [p,versions]=await Promise.all([getCatalogProduct(request.productId),getFormulaHistory(request.productId)])
      setHistory(versions);setExpected(p.current_formula_version_id)
      if(created){const current=versions.find(v=>v.formula_version_id===created.formula_version_id);if(current)setCreated(current)}
      setConflict(false);setConfirm(false)
      setMessage('History refreshed from the server. Review the current version before publishing.')
    })
  }
  async function publish() {
    if(!created||created.lifecycle_status!=='DRAFT'||uncertain||conflict||!enabled||!local)return
    setConfirm(false)
    await run(async()=>{
      const released=await releaseFormula(created.formula_version_id,expected)
      setCreated(released);setMessage(`Formula V${released.version_number} published successfully.`)
    },true)
    // History retrieval is separate: a read failure must never turn a successful publish into a failed write.
  }
  return <section className="formula-section persistence-panel" aria-label="Server actions">
    <h3>Save and publish</h3>
    <p className="muted">Local course environment only. Uses the main-branch DEV_EXTERNAL admin mapping and project-seeded provenance; this is not production authentication.</p>
    <label className="demo-consent"><input type="checkbox" checked={enabled} disabled={!local||busy} onChange={e=>setEnabled(e.target.checked)}/> Enable local demo identity for this form</label>
    {!local&&<p role="alert">Writes are disabled outside localhost. Configure approved authentication before deployment.</p>}
    {error&&<p className="error-notice" role="alert">{error}</p>}
    <p aria-live="polite">{message}</p>
    {created&&<p className="muted">Server version: V{created.version_number} · {created.lifecycle_status} · {created.formula_version_id}</p>}
    {conflict&&<p className="muted">Another operation changed the current formula. Refresh history and review it before confirming publication again.</p>}
    <div className="formula-actions">
      {!created&&<Button disabled={!local||!enabled||busy||uncertain} onClick={save}>{busy?'Working…':'Save draft'}</Button>}
      {created?.lifecycle_status==='DRAFT'&&<Button disabled={!local||!enabled||busy||uncertain||conflict} onClick={()=>setConfirm(true)}>Publish formula</Button>}
      <Button variant="outline" disabled={busy} onClick={refresh}>Refresh server history</Button>
    </div>
    {confirm&&<div className="source-notice" role="group" aria-label="Confirm publication"><div><p>Publish V{created?.version_number}? Expected current formula: {expected??'None'}. Existing version content will be retained.</p><div className="formula-actions"><Button variant="outline" onClick={()=>setConfirm(false)}>Cancel publication</Button><Button disabled={busy} onClick={publish}>Confirm publication</Button></div></div></div>}
    {history.length>0&&<section aria-label="Formula history"><h3>Server history</h3>{history.map(f=><p key={f.formula_version_id}>V{f.version_number} · {f.lifecycle_status} {f.is_current_released==='Y'?'· Current':''} <code>{f.formula_version_id}</code></p>)}</section>}
  </section>
}
