import { Button } from '@/components/ui/button'
import { Activity, Database, RefreshCw } from 'lucide-react'
import { useEffect, useState } from 'react'
import { getHealth, type HealthResponse } from '../api/client'

export function HealthPage() {
  const [health, setHealth] = useState<HealthResponse | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)
  const [checked, setChecked] = useState('')
  async function refresh() {
    setLoading(true)
    setError(null)
    setHealth(null)
    try {
      setHealth(await getHealth())
      setChecked(new Date().toLocaleTimeString('en-GB'))
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Connection failed')
    } finally {
      setLoading(false)
    }
  }
  useEffect(() => {
    void refresh()
  }, [])
  return (
    <>
      <div className="page-title">
        <div>
          <div className="eyebrow">LIVE SYSTEM STATUS</div>
          <h1>System status</h1>
          <p>Check the application and database connection in real time.</p>
        </div>
        <Button variant="outline" disabled={loading} onClick={refresh}>
          <RefreshCw size={16} className={loading ? 'animate-spin' : ''} />
          Check again
        </Button>
      </div>
      <div aria-live="polite">
        {loading && <div className="source-notice">Checking backend services…</div>}
        {error && (
          <div className="error-notice" role="alert">
            Connection failed: {error}. Check that the backend and database are running.
          </div>
        )}
      </div>
      <div className="health-grid">
        {[
          { label: 'Application', icon: Activity, value: health?.status },
          { label: 'Database', icon: Database, value: health?.database },
        ].map((item) => (
          <section className="panel health-card" key={item.label}>
            <item.icon size={27} />
            <h2>{item.label}</h2>
            <strong className={item.value === 'ok' ? 'health-ok' : ''}>
              {loading
                ? 'Checking'
                : item.value === 'ok'
                  ? 'Healthy'
                  : (item.value ?? 'Disconnected')}
            </strong>
            <p>{item.value === 'ok' ? 'ok' : '—'}</p>
          </section>
        ))}
      </div>
      {checked && !error && (
        <p className="muted">Last checked: {checked} · /api/health</p>
      )}
    </>
  )
}
