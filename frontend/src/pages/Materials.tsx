import {
  Panel,
  ProductGlyph,
  ReadOnlyNotice,
  SourceBadge,
} from '@/components/catalog-shared'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Input } from '@/components/ui/input'
import { type Material } from '@/data/catalog'
import data from '@/data/seed-preview.json'
import { ArrowUpRight, Check, Search, Truck } from 'lucide-react'
import { useState } from 'react'
export function Materials({ suppliers = false }: { suppliers?: boolean }) {
  const [selected, setSelected] = useState<Material | null>(null)
  const [query, setQuery] = useState('')
  const filtered = data.supplier_material.filter((m) =>
    `${m.material_name} ${m.material_code}`
      .toLowerCase()
      .includes(query.toLowerCase()),
  )
  return (
    <>
      <div className="page-title">
        <div>
          <div className="eyebrow">SUPPLY CHAIN DIRECTORY</div>
          <h1>{suppliers ? '供应商' : '物料与规格'}</h1>
          <p>从供应商到具体规格，保留每一层关联。</p>
        </div>
        <SourceBadge />
      </div>
      <ReadOnlyNotice />
      {suppliers ? (
        <div className="supplier-grid">
          {data.supplier.map((s, i) => (
            <Panel key={s.supplier_id} className="supplier-card">
              <span className={`supplier-icon tone-${i}`}>
                <Truck size={25} />
              </span>
              <Badge variant="outline">项目演示</Badge>
              <h2>{s.supplier_name}</h2>
              <p>{s.supplier_code}</p>
              <div className="supplier-stats">
                <strong>
                  {
                    data.supplier_material.filter(
                      (m) => m.supplier_id === s.supplier_id,
                    ).length
                  }
                </strong>
                <span>关联物料</span>
              </div>
              {data.supplier_material
                .filter((m) => m.supplier_id === s.supplier_id)
                .map((m) => (
                  <button
                    className="material-link"
                    key={m.supplier_material_id}
                    onClick={() => setSelected(m)}
                  >
                    <span>{m.material_name}</span>
                    <ArrowUpRight size={16} />
                  </button>
                ))}
            </Panel>
          ))}
        </div>
      ) : (
        <Panel>
          <div className="catalog-toolbar">
            <div className="search-box">
              <Search size={17} />
              <Input
                aria-label="搜索物料"
                placeholder="搜索物料名称或编号…"
                value={query}
                onChange={(e) => setQuery(e.target.value)}
              />
            </div>
            <span className="results-count">{filtered.length} 种物料</span>
          </div>
          <div className="table-scroll">
            <table>
              <thead>
                <tr>
                  <th>供应商物料</th>
                  <th>供应商</th>
                  <th>规格版本</th>
                  <th>状态</th>
                  <th>详情</th>
                </tr>
              </thead>
              <tbody>
                {filtered.map((m, i) => (
                  <tr key={m.supplier_material_id}>
                    <td>
                      <button
                        className="product-cell"
                        onClick={() => setSelected(m)}
                      >
                        <ProductGlyph index={i} />
                        <span>
                          <strong>{m.material_name}</strong>
                          <small>{m.material_code}</small>
                        </span>
                      </button>
                    </td>
                    <td>
                      {
                        data.supplier.find(
                          (s) => s.supplier_id === m.supplier_id,
                        )?.supplier_name
                      }
                    </td>
                    <td>V1</td>
                    <td>
                      <span className="group-tag teal">已发布</span>
                    </td>
                    <td>
                      <Button
                        variant="ghost"
                        size="icon"
                        aria-label={`查看 ${m.material_name}`}
                        onClick={() => setSelected(m)}
                      >
                        <ArrowUpRight size={17} />
                      </Button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
            {filtered.length === 0 && (
              <div className="empty-search">没有匹配的物料</div>
            )}
          </div>
        </Panel>
      )}
      <Dialog
        open={!!selected}
        onOpenChange={(open) => !open && setSelected(null)}
      >
        <DialogContent className="detail-dialog">
          <DialogHeader>
            <DialogTitle>{selected?.material_name}</DialogTitle>
            <DialogDescription>物料规格与上游成分 · 基线快照</DialogDescription>
          </DialogHeader>
          {selected && (
            <>
              <p>{selected.material_description}</p>
              <code>{selected.supplier_material_id}</code>
              {data.ingredient_specification_version
                .filter(
                  (s) =>
                    s.supplier_material_id === selected.supplier_material_id,
                )
                .map((s) => (
                  <div key={s.specification_version_id}>
                    <div className="detail-summary">
                      <strong>规格 V{s.version_number}</strong>
                      <Badge variant="outline">{s.lifecycle_status}</Badge>
                    </div>
                    <p className="muted">生效日期 {s.effective_date}</p>
                    <div className="trace-list">
                      {data.spec_component
                        .filter(
                          (c) =>
                            c.specification_version_id ===
                            s.specification_version_id,
                        )
                        .map((c, i) => (
                          <div className="trace-row" key={c.spec_component_id}>
                            <span className="step-index">{i + 1}</span>
                            <div>
                              <strong>{c.raw_phrase}</strong>
                              <p>{c.ingredient_id}</p>
                            </div>
                            <Check size={16} />
                          </div>
                        ))}
                    </div>
                  </div>
                ))}
              <div className="dialog-bottom">
                <span>来源：PROJECT_SEEDED</span>
                <Button variant="outline" onClick={() => setSelected(null)}>
                  关闭
                </Button>
              </div>
            </>
          )}
        </DialogContent>
      </Dialog>
    </>
  )
}
