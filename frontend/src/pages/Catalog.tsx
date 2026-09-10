import {
  exportProducts,
  Panel,
  ReadOnlyNotice,
} from '@/components/catalog-shared'
import { FormulaCreator } from '@/components/FormulaCreator'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { groups, type Product } from '@/data/catalog'
import { data } from '@/data/catalog'
import { ProductDetails } from '@/features/catalog/ProductDetails'
import { ProductTable } from '@/features/catalog/ProductTable'
import {
  ArrowDownToLine,
  ChevronLeft,
  ChevronRight,
  Search,
  ShieldCheck,
} from 'lucide-react'
import { useMemo, useState } from 'react'
export function Catalog({ formulas = false }: { formulas?: boolean }) {
  const [query, setQuery] = useState('')
  const [group, setGroup] = useState('all')
  const [page, setPage] = useState(1)
  const [selected, setSelected] = useState<Product | null>(null)
  const filtered = useMemo(
    () =>
      data.product.filter(
        (p) =>
          (group === 'all' || p.fixture_group === group) &&
          `${p.product_description} ${p.brand_owner} ${p.fdc_id}`
            .toLowerCase()
            .includes(query.toLowerCase()),
      ),
    [group, query],
  )
  const total = Math.max(1, Math.ceil(filtered.length / 8))
  return (
    <>
      <div className="page-title">
        <div>
          <div className="eyebrow">
            {formulas ? 'VERSIONED & TRACEABLE' : 'YOUR PRODUCT LIBRARY'}
          </div>
          <h1>{formulas ? 'Formula versions' : 'Products'}</h1>
          <p>
            {formulas
              ? 'Explore material references and formula history for each product.'
              : 'Browse product records and their upstream supply links.'}
          </p>
        </div>
        <Button variant="outline" onClick={() => exportProducts(filtered)}>
          <ArrowDownToLine size={16} /> Export list
        </Button>
      </div>
      <ReadOnlyNotice />
      {formulas && (
        <div className="formula-entry">
          <FormulaCreator />
        </div>
      )}
      <Panel>
        <div className="catalog-toolbar">
          <div className="search-box">
            <Search size={17} />
            <Input
              aria-label="Search products"
              placeholder="Search products, brands or FDC IDs…"
              value={query}
              onChange={(e) => {
                setQuery(e.target.value)
                setPage(1)
              }}
            />
          </div>
          <select
            aria-label="Filter fixture group"
            value={group}
            onChange={(e) => {
              setGroup(e.target.value)
              setPage(1)
            }}
          >
            <option value="all">All groups</option>
            {Object.entries(groups).map(([key, label]) => (
              <option key={key} value={key}>
                {label}
              </option>
            ))}
          </select>
          <span className="results-count">{filtered.length} products</span>
        </div>
        <ProductTable
          products={filtered.slice((page - 1) * 8, page * 8)}
          onSelect={setSelected}
        />
        <div className="pagination">
          <span>
            Page {page} / {total} · Read-only API data
          </span>
          <div>
            <Button
              variant="outline"
              size="icon"
              aria-label="Previous page"
              disabled={page === 1}
              onClick={() => setPage(page - 1)}
            >
              <ChevronLeft size={16} />
            </Button>
            <Button
              variant="outline"
              size="icon"
              aria-label="Next page"
              disabled={page >= total}
              onClick={() => setPage(page + 1)}
            >
              <ChevronRight size={16} />
            </Button>
          </div>
        </div>
      </Panel>
      {formulas && (
        <div className="availability-note">
          <ShieldCheck size={18} />
          <div>
            <strong>Local demo saving and publishing</strong>
            <p>Create a formula preview, enable the local demo identity, and save a draft. Publication requires a separate confirmation.</p>
          </div>
        </div>
      )}
      <ProductDetails product={selected} close={() => setSelected(null)} />
    </>
  )
}
