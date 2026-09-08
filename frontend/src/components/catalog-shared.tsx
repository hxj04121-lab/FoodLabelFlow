import { Badge } from '@/components/ui/badge'
import { type Product } from '@/data/catalog'
import { Database, Package } from 'lucide-react'
import { type ReactNode } from 'react'
export function Panel({
  children,
  className = '',
}: {
  children: ReactNode
  className?: string
}) {
  return <section className={`panel ${className}`}>{children}</section>
}
export function SectionHead({
  title,
  caption,
  action,
}: {
  title: string
  caption?: string
  action?: ReactNode
}) {
  return (
    <div className="section-head">
      <div>
        <h2>{title}</h2>
        {caption && <p>{caption}</p>}
      </div>
      {action}
    </div>
  )
}
export function SourceBadge() {
  return (
    <Badge variant="outline" className="source-badge">
      <Database size={12} /> 基线预览
    </Badge>
  )
}
export function ReadOnlyNotice() {
  return (
    <div className="source-notice">
      <Database size={15} />
      <span>
        当前展示数据库种子文件的只读快照，非实时业务数据。创建、发布与审核将在对应接口接入后开放。
      </span>
    </div>
  )
}
export function ProductGlyph({ index = 0 }: { index?: number }) {
  return (
    <span className={`product-glyph tone-${index % 4}`}>
      <Package size={21} strokeWidth={1.5} />
    </span>
  )
}
export function exportProducts(products: Product[]) {
  const fields = [
    'product_id',
    'product_description',
    'brand_owner',
    'fixture_group',
  ] as const
  const csv =
    '\ufeff' +
    [
      fields.join(','),
      ...products.map((p) =>
        fields.map((f) => `"${String(p[f]).replaceAll('"', '""')}"`).join(','),
      ),
    ].join('\r\n')
  const url = URL.createObjectURL(
    new Blob([csv], { type: 'text/csv;charset=utf-8' }),
  )
  const a = document.createElement('a')
  a.href = url
  a.download = 'spectrace-seed-preview.csv'
  a.click()
  URL.revokeObjectURL(url)
}
