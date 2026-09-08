import {
  exportProducts,
  Panel,
  ReadOnlyNotice,
} from '@/components/catalog-shared'
import { FormulaCreator } from '@/components/FormulaCreator'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { groups, type Product } from '@/data/catalog'
import data from '@/data/seed-preview.json'
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
          <h1>{formulas ? '配方版本' : '产品目录'}</h1>
          <p>
            {formulas
              ? '查看每个产品的物料引用与配方历史。'
              : '集中查看产品档案与上游供应链关联。'}
          </p>
        </div>
        <Button variant="outline" onClick={() => exportProducts(filtered)}>
          <ArrowDownToLine size={16} /> 导出当前列表
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
              aria-label="搜索产品"
              placeholder="搜索产品、品牌或 FDC 编号…"
              value={query}
              onChange={(e) => {
                setQuery(e.target.value)
                setPage(1)
              }}
            />
          </div>
          <select
            aria-label="筛选基线分组"
            value={group}
            onChange={(e) => {
              setGroup(e.target.value)
              setPage(1)
            }}
          >
            <option value="all">全部分组</option>
            {Object.entries(groups).map(([key, label]) => (
              <option key={key} value={key}>
                {label}
              </option>
            ))}
          </select>
          <span className="results-count">{filtered.length} 个产品</span>
        </div>
        <ProductTable
          products={filtered.slice((page - 1) * 8, page * 8)}
          onSelect={setSelected}
        />
        <div className="pagination">
          <span>
            第 {page} / {total} 页 · 基线只读数据
          </span>
          <div>
            <Button
              variant="outline"
              size="icon"
              aria-label="上一页"
              disabled={page === 1}
              onClick={() => setPage(page - 1)}
            >
              <ChevronLeft size={16} />
            </Button>
            <Button
              variant="outline"
              size="icon"
              aria-label="下一页"
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
            <strong>服务器保存与发布暂未开放</strong>
            <p>配方接口接入后，可在这里创建新版本；已发布版本始终保留历史。</p>
          </div>
        </div>
      )}
      <ProductDetails product={selected} close={() => setSelected(null)} />
    </>
  )
}
