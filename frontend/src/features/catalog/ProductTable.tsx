import { ProductGlyph } from '@/components/catalog-shared'
import { Button } from '@/components/ui/button'
import { groupColors, groups, type Product } from '@/data/catalog'
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
            <th>产品名称</th>
            <th>品牌 / 所有者</th>
            <th>配方版本</th>
            {!compact && <th>基线分组</th>}
            <th>
              <span className="sr-only">操作</span>
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
                <span className="version-chip">V1</span>
                <span className="released-dot" /> 已发布
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
                  aria-label={`查看 ${p.product_description}`}
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
          <h3>没有匹配的产品</h3>
          <p>试试其他名称、品牌或 FDC 编号。</p>
        </div>
      )}
    </div>
  )
}
