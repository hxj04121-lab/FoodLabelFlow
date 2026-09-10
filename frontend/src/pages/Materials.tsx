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
import { data } from '@/data/catalog'
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
          <h1>{suppliers ? 'Suppliers' : 'Materials & specs'}</h1>
          <p>Follow every link from supplier to specification.</p>
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
              <Badge variant="outline">Demo fixture</Badge>
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
                <span>Linked materials</span>
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
                aria-label="Search materials"
                placeholder="Search material names or codes…"
                value={query}
                onChange={(e) => setQuery(e.target.value)}
              />
            </div>
            <span className="results-count">{filtered.length} materials</span>
          </div>
          <div className="table-scroll">
            <table>
              <thead>
                <tr>
                  <th>Supplier material</th>
                  <th>Suppliers</th>
                  <th>Specification version</th>
                  <th>Status</th>
                  <th>Details</th>
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
                    <td>{data.ingredient_specification_version.filter(s=>s.supplier_material_id===m.supplier_material_id).map(s=>`V${s.version_number}`).join(' / ') || 'No specifications'}</td>
                    <td>
                      <span className="group-tag teal">{data.ingredient_specification_version.filter(s=>s.supplier_material_id===m.supplier_material_id).map(s=>s.lifecycle_status).join(' / ') || '—'}</span>
                    </td>
                    <td>
                      <Button
                        variant="ghost"
                        size="icon"
                        aria-label={`View ${m.material_name}`}
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
              <div className="empty-search">No matching materials</div>
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
            <DialogDescription>Specifications and components · API snapshot</DialogDescription>
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
                      <strong>Spec V{s.version_number}</strong>
                      <Badge variant="outline">{s.lifecycle_status}</Badge>
                    </div>
                    <p className="muted">Effective date {s.effective_date}</p>
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
                <span>Source: PROJECT_SEEDED</span>
                <Button variant="outline" onClick={() => setSelected(null)}>
                  Close
                </Button>
              </div>
            </>
          )}
        </DialogContent>
      </Dialog>
    </>
  )
}
