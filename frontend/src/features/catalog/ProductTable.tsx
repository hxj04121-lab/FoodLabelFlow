import { ProductGlyph } from '@/components/catalog-shared'
import { Button } from '@/components/ui/button'
import { data, groupColors, groups, type Product } from '@/data/catalog'
import { ArrowUpRight, Search } from 'lucide-react'
const short = (text: string) =>
  text.length > 52 ? text.slice(0, 52) + '…' : text
export function ProductTable({
  products,
  onSelect,
  compact = false,
}: {
  products: Product[]
  onSelect: (p: Product) => void
  compact?: boolean
}) {
  return (
    <div className="table-scroll">
      <table>
        <thead>
          <tr>
            <th>Product name</th>
            <th>Brand / owner</th>
            <th>Formula versions</th>
            {!compact && <th>Fixture group</th>}
            <th>
              <span className="sr-only">Actions</span>
            </th>
          </tr>
        </thead>
        <tbody>
          {products.map((p, i) => (
            <tr key={p.product_id}>
              <td>
                <button className="product-cell" onClick={() => onSelect(p)}>
                  <ProductGlyph index={i} />
                  <span>
                    <strong title={p.product_description}>
                      {short(p.product_description)}
                    </strong>
                    <small>FDC {p.fdc_id}</small>
                  </span>
                </button>
              </td>
              <td>
                <span className="brand-cell">{p.brand_owner}</span>
              </td>
              <td>
                <span className="version-chip">{data.formula_version.find(f=>f.formula_version_id===p.current_formula_version_id)?.version_number ?? '—'}</span>
                {data.formula_version.find(f=>f.formula_version_id===p.current_formula_version_id)?.lifecycle_status ?? 'No current formula'}
              </td>
              {!compact && (
                <td>
                  <span className={`group-tag ${groupColors[p.fixture_group]}`}>
                    {groups[p.fixture_group]}
                  </span>
                </td>
              )}
              <td>
                <Button
                  variant="ghost"
                  size="icon"
                  aria-label={`View ${p.product_description}`}
                  onClick={() => onSelect(p)}
                >
                  <ArrowUpRight size={17} />
                </Button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
      {products.length === 0 && (
        <div className="empty-search">
          <Search />
          <h3>No matching products</h3>
          <p>Try another name, brand or FDC ID.</p>
        </div>
      )}
    </div>
  )
}
