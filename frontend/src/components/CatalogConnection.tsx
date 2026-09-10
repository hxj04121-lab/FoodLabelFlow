import { useEffect, useState, type ReactNode } from 'react'
import { loadCatalog, loadedAt } from '@/data/catalog'
import { Button } from './ui/button'

export function CatalogConnection({ children }: { children:ReactNode }) {
  const [state,setState]=useState<'loading'|'ready'|'error'>('loading')
  const [error,setError]=useState('')
  const [attempt,setAttempt]=useState(0)
  useEffect(()=>{const refresh=()=>setAttempt(n=>n+1);window.addEventListener('catalog-updated',refresh);return()=>window.removeEventListener('catalog-updated',refresh)},[])
  useEffect(()=>{
    const controller=new AbortController()
    let active = true
    const timeout=setTimeout(()=>controller.abort(),30000)
    setState('loading')
    loadCatalog(controller.signal).then(()=>{if(controller.signal.aborted) throw new Error('Request timed out');if(active)setState('ready')}).catch(e=>{if(active){setError(controller.signal.aborted?'Request timed out. Please try again.':e instanceof Error?e.message:'Loading failed');setState('error')}}).finally(()=>clearTimeout(timeout))
    return ()=>{active=false;controller.abort();clearTimeout(timeout)}
  },[attempt])
  if(state==='loading') return <section className="panel upcoming" role="status"><h2>Loading catalog data</h2><p>Connecting to the catalog API and loading products, specifications and formula history.</p></section>
  if(state==='error') return <section className="panel upcoming"><h2>Catalog data is unavailable</h2><p role="alert">{error}</p><p>Start the M1 backend and try again. Offline preview data will not be substituted.</p><Button onClick={()=>setAttempt(attempt+1)}>Retry connection</Button></section>
  return <><div className="source-notice">Connected to the M1 read-only API · Loaded at {loadedAt} · Local demo writes available in the formula editor</div>{children}</>
}
