import {
  exportProducts,
  Panel,
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
  FlaskConical,
  Layers3,
  Package,
  ShieldCheck,
  Truck,
} from 'lucide-react'
import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
const groupTones: Record<string, string> = {
  NO_ACTION_BASELINE_SOY: 'tone-soy',
  REVIEW_REQUIRED_BASELINE_NO_SOY: 'tone-nosoy',
  NEGATIVE_CONTROL_NO_CHOCOLATE: 'tone-none',
}
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
          tone: '',
        }))
      : Object.entries(groups).map(([key, name]) => ({
          name,
          count: data.product.filter((p) => p.fixture_group === key).length,
          tone: groupTones[key] ?? '',
        }))
  const maxCount = Math.max(1, ...bars.map((b) => b.count))
  return (
    <>
      <div className="page-title">
        <div>
          <div className="eyebrow">Workspace overview</div>
          <h1>Overview</h1>
          <p>Every version connected, from ingredient to label.</p>
        </div>
        <div className="page-actions">
          <Button variant="outline" size="lg" onClick={() => exportProducts(data.product)}>
            <ArrowDownToLine size={16} /> Export data
          </Button>
          <Button size="lg" onClick={() => navigate('/impact')}>
            Analyze a change <ArrowRight size={16} />
          </Button>
        </div>
      </div>
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
            label: 'Supplier materials',
            value: data.supplier_material.length,
            unit: 'materials',
            icon: Layers3,
            note: `${data.ingredient_specification_version.length} specification versions`,
          },
          {
            label: 'Formula versions',
            value: data.formula_version.length,
            unit: 'versions',
            icon: FlaskConical,
            note: `${data.formula_item.length} material references`,
          },
        ].map((metric, i) => (
          <Panel
            key={metric.label}
            className={`metric ${i === 0 ? 'metric-featured' : ''}`}
          >
            <div className="metric-top">
              <span>{metric.label}</span>
              <span className="metric-icon">
                <metric.icon size={17} />
              </span>
            </div>
            <div className="metric-value">
              {metric.value}
              <span>{metric.unit}</span>
            </div>
            <div className="metric-note">{metric.note}</div>
          </Panel>
        ))}
      </div>
      <div className="overview-charts">
        <Panel className="coverage-panel">
          <SectionHead
            title="Supply chain coverage"
            caption={
              chart === 'materials'
                ? 'Formula versions referencing each supplier material'
                : 'Products by demo fixture group, not impact results'
            }
            action={
              <div className="segmented">
                <button
                  className={chart === 'materials' ? 'active' : ''}
                  aria-pressed={chart === 'materials'}
                  onClick={() => setChart('materials')}
                >
                  By material
                </button>
                <button
                  className={chart === 'groups' ? 'active' : ''}
                  aria-pressed={chart === 'groups'}
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
            {bars.map((b) => (
              <div className="bar-row" key={b.name} aria-hidden="true">
                <span className="bar-name" title={b.name}>{b.name}</span>
                <span className="bar-track">
                  <span
                    className={`bar-fill ${b.tone}`}
                    style={{ display: 'block', width: `${(b.count / maxCount) * 100}%` }}
                  />
                </span>
                <span className="bar-count">{b.count}</span>
              </div>
            ))}
          </div>
        </Panel>
        <Panel className="trace-panel">
          <SectionHead
            title="Traceability chain"
            caption="How one change travels to the label"
          />
          <ol className="trace-chain">
            <li>
              <span className="trace-step">1</span>
              <span>
                <strong>Supplier material</strong>
                <small>What a supplier ships</small>
              </span>
              <span className="trace-count">{data.supplier_material.length}</span>
            </li>
            <li>
              <span className="trace-step">2</span>
              <span>
                <strong>Specification version</strong>
                <small>Its declared components</small>
              </span>
              <span className="trace-count">{data.ingredient_specification_version.length}</span>
            </li>
            <li>
              <span className="trace-step">3</span>
              <span>
                <strong>Formula version</strong>
                <small>Which specifications a product uses</small>
              </span>
              <span className="trace-count">{data.formula_version.length}</span>
            </li>
            <li>
              <span className="trace-step">4</span>
              <span>
                <strong>Label version</strong>
                <small>Validated, reviewed, published</small>
              </span>
              <Button variant="link" size="sm" onClick={() => navigate('/labels')}>
                Open labels
              </Button>
            </li>
          </ol>
          <p className="trace-note">
            <ShieldCheck size={18} />
            <span>
              Published labels keep their history. A change produces a replacement draft that needs validation and an independent review.
            </span>
          </p>
        </Panel>
      </div>
      <Panel className="overview-products">
        <SectionHead
          title="Product records"
          caption="Allergen baseline group and current formula for each product"
          action={
            <Button variant="ghost" onClick={() => navigate('/products')}>
              All products <ArrowRight size={16} />
            </Button>
          }
        />
        <ProductTable
          products={data.product.slice(0, 5)}
          onSelect={setSelected}
        />
      </Panel>
      <ProductDetails product={selected} close={() => setSelected(null)} />
    </>
  )
}
