import {
  exportProducts,
  Panel,
  ReadOnlyNotice,
  SectionHead,
} from '@/components/catalog-shared'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { groups, type Product } from '@/data/catalog'
import data from '@/data/seed-preview.json'
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
            工作概览 <span className="hello-dot" />
          </h1>
          <p>从原料到标签，每一个版本都有迹可循。</p>
        </div>
        <Button variant="outline" onClick={() => exportProducts(data.product)}>
          <ArrowDownToLine size={16} /> 导出基线
        </Button>
      </div>
      <ReadOnlyNotice />
      <div className="metrics-grid">
        {[
          {
            label: '产品档案',
            value: data.product.length,
            unit: '个产品',
            icon: Package,
            note: '已关联配方版本',
          },
          {
            label: '供应商',
            value: data.supplier.length,
            unit: '家供应商',
            icon: Truck,
            note: '项目演示供应链',
          },
          {
            label: '供应商物料',
            value: data.supplier_material.length,
            unit: '种物料',
            icon: Layers3,
            note: '规格版本可追溯',
          },
          {
            label: '配方版本',
            value: data.formula_version.length,
            unit: '个版本',
            icon: FlaskConical,
            note: '基线已发布版本',
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
            title="供应链数据概览"
            caption="配方引用分布 · 数据库基线"
            action={
              <div className="segmented">
                <button
                  className={chart === 'materials' ? 'active' : ''}
                  onClick={() => setChart('materials')}
                >
                  按物料
                </button>
                <button
                  className={chart === 'groups' ? 'active' : ''}
                  onClick={() => setChart('groups')}
                >
                  按分组
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
              {chart === 'materials' ? '条配方物料引用' : '个基线产品'}
            </span>
            <Badge variant="outline">V3 基线</Badge>
          </div>
          <div
            className="bar-chart"
            role="img"
            aria-label={bars.map((b) => `${b.name}: ${b.count}`).join('；')}
          >
            <div className="axis-labels">
              {[60, 45, 30, 15, 0].map((n) => (
                <span key={n}>{n}</span>
              ))}
            </div>
            <div className="bars">
              {bars.map((b, i) => (
                <div className="bar-column" key={b.name}>
                  <div
                    className={`bar bar-${i}`}
                    style={{ height: `${(b.count / 60) * 100}%` }}
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
            title="演示数据分组"
            caption="分组不代表已执行的影响分析"
          />
          <div className="donut-layout">
            <div
              className="donut"
              role="img"
              aria-label="三组基线数据各20个产品"
            >
              <div>
                <strong>60</strong>
                <span>基线产品</span>
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
            title="产品档案"
            caption="查看产品、配方和上游规格"
            action={
              <Button variant="ghost" onClick={() => navigate('/products')}>
                全部产品 <ArrowRight size={16} />
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
          <h2>让变更有据可查</h2>
          <p>沿着物料与配方的关联，查看产品使用的具体规格版本。</p>
          <div className="mini-journey">
            <span>原料规格</span>
            <span>配方版本</span>
            <span>产品档案</span>
          </div>
          <Button onClick={() => navigate('/formulas')}>
            探索配方追溯 <ArrowUpRight size={16} />
          </Button>
          <small>标签与审核功能将在后续阶段接入</small>
        </Panel>
      </div>
      <ProductDetails product={selected} close={() => setSelected(null)} />
    </>
  )
}
