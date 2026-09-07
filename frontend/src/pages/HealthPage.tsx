import { useEffect, useState } from 'react'
import { Activity, Database, RefreshCw } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { getHealth, type HealthResponse } from '../api/client'

export function HealthPage() {
  const [health,setHealth]=useState<HealthResponse|null>(null)
  const [error,setError]=useState<string|null>(null)
  const [loading,setLoading]=useState(false)
  const [checked,setChecked]=useState('')
  async function refresh() { setLoading(true); setError(null); setHealth(null); try { setHealth(await getHealth()); setChecked(new Date().toLocaleTimeString('zh-CN')) } catch(e) { setError(e instanceof Error?e.message:'连接失败') } finally { setLoading(false) } }
  useEffect(()=>{void refresh()},[])
  return <><div className="page-title"><div><div className="eyebrow">LIVE SYSTEM STATUS</div><h1>系统状态</h1><p>实时检查应用服务与数据库连接。</p></div><Button variant="outline" disabled={loading} onClick={refresh}><RefreshCw size={16} className={loading?'animate-spin':''}/>重新检查</Button></div><div aria-live="polite">{loading&&<div className="source-notice">正在检查后端服务…</div>}{error&&<div className="error-notice" role="alert">连接失败：{error}。请确认后端和数据库已经启动。</div>}</div><div className="health-grid">{[{label:'应用服务',icon:Activity,value:health?.status},{label:'数据库连接',icon:Database,value:health?.database}].map(item=><section className="panel health-card" key={item.label}><item.icon size={27}/><h2>{item.label}</h2><strong className={item.value==='ok'?'health-ok':''}>{loading?'检查中':item.value==='ok'?'运行正常':item.value??'未连接'}</strong><p>{item.value==='ok'?'ok': '—'}</p></section>)}</div>{checked&&!error&&<p className="muted">最近检查：{checked} · /api/health</p>}</>
}
