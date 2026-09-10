import {
  exportProducts,
  Panel,
  ReadOnlyNotice,
  SectionHead,
} from '@/components/catalog-shared'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { groups, type Product } from '@/data/catalog'
import { data } from '@/data/catalog'
import { ProductDetails } from '@/features/catalog/ProductDetails'
import { ProductTable } from '@/features/catalog/ProductTable'
import {
  ArrowDownToLine,
  ArrowRight,
  ArrowUpRight,
  FlaskConical,
  GitBranch,
  Layers3,
  Package,
  Truck,
} from 'lucide-react'
import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
export function Overview() {
  const navigate = useNavigate()
  const [selected, setSelected] = useState<Product | null>(null)
  const [chart, setChart] = useState<'materials' | 'groups'>('materials')
  const bars =
    chart === 'materials'
      ? data.supplier_material.map((m) => ({
          name: m.material_name.replace(' Base', ''),
          count: new Set(
            data.formula_item
              .filter((f) => f.supplier_material_id === m.supplier_material_id)
              .map((f) => f.formula_version_id),
          ).size,
        }))
      : Object.entries(groups).map(([key, name]) => ({
          name,
          count: data.product.filter((p) => p.fixture_group === key).length,
        }))
  return (
    <>
      <div className="page-title">
        <div>
          <div className="eyebrow">YOUR WORKSPACE, AT A GLANCE</div>
          <h1>
            Overview <span className="hello-dot" />
          </h1>
          <p>Every version connected, from ingredient to label.</p>
        </div>
        <Button variant="outline" onClick={() => exportProducts(data.product)}>
          <ArrowDownToLine size={16} /> Export data
        </Button>
      </div>
      <ReadOnlyNotice />
      <div className="metrics-grid">
        {[
          {
            label: 'Product records',
            value: data.product.length,
            unit: 'products',
            icon: Package,
            note: 'Linked formula versions',
          },
          {
            label: 'Suppliers',
            value: data.supplier.length,
            unit: 'suppliers',
            icon: Truck,
            note: 'Demo supply chain',
          },
          {
            label: 'Supplier material',
            value: data.supplier_material.length,
            unit: 'materials',
            icon: Layers3,
            note: 'Versioned specifications',
          },
          {
            label: 'Formula versions',
            value: data.formula_version.length,
            unit: 'versions',
            icon: FlaskConical,
            note: 'Versions loaded',
          },
        ].map((metric, i) => (
          <Panel
            key={metric.label}
            className={`metric ${i === 0 ? 'metric-featured' : ''}`}
          >
            <div className="metric-top">
              <span>{metric.label}</span>
              <metric.icon size={20} />
            </div>
            <div className="metric-value">
              {metric.value}
              <span>{metric.unit}</span>
            </div>
            <div className="metric-note">
              <span className="tiny-dot" />
              {metric.note}
            </div>
          </Panel>
        ))}
      </div>
      <div className="overview-charts">
        <Panel>
          <SectionHead
            title="Supply chain overview"
            caption="Formula references · Database snapshot"
            action={
              <div className="segmented">
                <button
                  className={chart === 'materials' ? 'active' : ''}
                  onClick={() => setChart('materials')}
                >
                  By material
                </button>
                <button
                  className={chart === 'groups' ? 'active' : ''}
                  onClick={() => setChart('groups')}
                >
                  By group
                </button>
              </div>
            }
          />
          <div className="chart-summary">
            <strong>
              {chart === 'materials'
                ? data.formula_item.length
                : data.product.length}
            </strong>
            <span>
              {chart === 'materials' ? 'material references' : 'products'}
            </span>
            <Badge variant="outline">API data</Badge>
          </div>
          <div
            className="bar-chart"
            role="img"
            aria-label={bars.map((b) => `${b.name}: ${b.count}`).join('; ')}
          >
            <div className="axis-labels">
              {[1,.75,.5,.25,0].map(f=>Math.ceil(Math.max(4,...bars.map(b=>b.count))/4)*4*f).map((n) => (
                <span key={n}>{n}</span>
              ))}
            </div>
            <div className="bars">
              {bars.map((b, i) => (
                <div className="bar-column" key={b.name}>
                  <div
                    className={`bar bar-${i}`}
                    style={{ height: `${(b.count / (Math.ceil(Math.max(4,...bars.map(b=>b.count))/4)*4)) * 100}%` }}
                  >
                    <span>{b.count}</span>
                  </div>
                  <span className="bar-label">{b.name}</span>
                </div>
              ))}
            </div>
          </div>
        </Panel>
        <Panel>
          <SectionHead
            title="Demo dataset groups"
            caption="Fixture groups, not impact analysis results"
          />
          <div className="donut-layout">
            <div
              className="donut" style={{ background: (()=>{const total=data.product.length || 1; const a=data.product.filter(p=>p.fixture_group==="REVIEW_REQUIRED_BASELINE_NO_SOY").length/total*100; const b=a+data.product.filter(p=>p.fixture_group==="NO_ACTION_BASELINE_SOY").length/total*100; return `conic-gradient(#8862e4 0 ${a}%,#5dcfc4 ${a}% ${b}%,#6fb8e5 ${b}% 100%)`})() }}
              role="img"
              aria-label={Object.entries(groups).map(([key,label])=>`${label}: ${data.product.filter(p=>p.fixture_group===key).length}`).join("; ")}
            >
              <div>
                <strong>{data.product.length}</strong>
                <span>Product</span>
              </div>
            </div>
            <div className="donut-legend">
              {Object.entries(groups).map(([key, label], i) => (
                <div key={key}>
                  <span className={`legend-dot dot-${i}`} />
                  <span>{label}</span>
                  <strong>
                    {data.product.filter((p) => p.fixture_group === key).length}
                  </strong>
                </div>
              ))}
            </div>
          </div>
        </Panel>
      </div>
      <div className="overview-bottom">
        <Panel>
          <SectionHead
            title="Product records"
            caption="Explore products, formulas and specifications"
            action={
              <Button variant="ghost" onClick={() => navigate('/products')}>
                All products <ArrowRight size={16} />
              </Button>
            }
          />
          <ProductTable
            products={data.product.slice(0, 4)}
            compact
            onSelect={setSelected}
          />
        </Panel>
        <Panel className="journey-panel">
          <span className="journey-icon">
            <GitBranch size={24} />
          </span>
          <h2>Trace every change</h2>
          <p>Follow material and formula links to the exact specification used.</p>
          <div className="mini-journey">
            <span>Specifications</span>
            <span>Formula versions</span>
            <span>Product records</span>
          </div>
          <Button onClick={() => navigate('/formulas')}>
            Explore formulas <ArrowUpRight size={16} />
          </Button>
          <small>Labels and reviews will be connected in a later phase</small>
        </Panel>
      </div>
      <ProductDetails product={selected} close={() => setSelected(null)} />
    </>
  )
}
