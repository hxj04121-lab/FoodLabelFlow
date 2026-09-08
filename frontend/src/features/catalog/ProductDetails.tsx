import { ProductGlyph } from '@/components/catalog-shared'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { type Product } from '@/data/catalog'
import data from '@/data/seed-preview.json'
export function ProductDetails({
  product,
  close,
}: {
  product: Product | null
  close: () => void
}) {
  const formula = data.formula_version.find(
    (f) => f.product_id === product?.product_id,
  )
  const items = data.formula_item.filter(
    (f) => f.formula_version_id === formula?.formula_version_id,
  )
  return (
    <Dialog open={!!product} onOpenChange={(open) => !open && close()}>
      <DialogContent className="detail-dialog">
        <DialogHeader>
          <DialogTitle>产品与配方追溯</DialogTitle>
          <DialogDescription>
            种子数据快照 · 历史与版本只读展示
          </DialogDescription>
        </DialogHeader>
        {product && (
          <>
            <div className="detail-product">
              <ProductGlyph />
              <div>
                <h3>{product.product_description}</h3>
                <p>{product.brand_owner}</p>
                <code>{product.product_id}</code>
              </div>
            </div>
            <Tabs defaultValue="formula">
              <TabsList>
                <TabsTrigger value="formula">配方与物料</TabsTrigger>
                <TabsTrigger value="history">版本历史</TabsTrigger>
                <TabsTrigger value="source">数据来源</TabsTrigger>
              </TabsList>
              <TabsContent value="formula">
                <div className="detail-summary">
                  <span>配方 V{formula?.version_number ?? '—'}</span>
                  <Badge variant="outline">
                    {formula?.lifecycle_status ?? '未知'}
                  </Badge>
                  <span>基线当前版本</span>
                </div>
                <div className="trace-list">
                  {items.map((item, i) => {
                    const material = data.supplier_material.find(
                      (m) =>
                        m.supplier_material_id === item.supplier_material_id,
                    )
                    const supplier = data.supplier.find(
                      (s) => s.supplier_id === material?.supplier_id,
                    )
                    return (
                      <div className="trace-row" key={item.formula_item_id}>
                        <span className="step-index">{i + 1}</span>
                        <div>
                          <strong>{material?.material_name}</strong>
                          <p>{supplier?.supplier_name}</p>
                          <code>{item.specification_version_id}</code>
                        </div>
                        <Badge variant="outline">
                          规格 V
                          {
                            data.ingredient_specification_version.find(
                              (s) =>
                                s.specification_version_id ===
                                item.specification_version_id,
                            )?.version_number
                          }
                        </Badge>
                      </div>
                    )
                  })}
                </div>
              </TabsContent>
              <TabsContent value="history">
                <div className="history-entry">
                  <span className="history-dot" />
                  <div>
                    <h3>V{formula?.version_number} · 已发布</h3>
                    <p>
                      {formula?.released_at} · {formula?.released_by_user_id}
                    </p>
                    <code>{formula?.formula_version_id}</code>
                    <p>基线快照只有此版本。实时版本历史尚未接入。</p>
                  </div>
                </div>
              </TabsContent>
              <TabsContent value="source">
                <div className="source-detail">
                  <h3>公开产品记录</h3>
                  <p>USDA FoodData Central · FDC ID {product.fdc_id}</p>
                  <p>来源标识：{product.data_provenance_id}</p>
                  <h3>原始配料文本</h3>
                  <p>{product.source_ingredients_text}</p>
                  <p>
                    供应商、物料和配方关联为课程项目构造的演示关系，不代表该品牌的真实供应链。
                  </p>
                </div>
              </TabsContent>
            </Tabs>
            <div className="dialog-bottom">
              <span>只读基线 · V3__baseline_seed.sql</span>
              <Button variant="outline" onClick={close}>
                关闭
              </Button>
            </div>
          </>
        )}
      </DialogContent>
    </Dialog>
  )
}
