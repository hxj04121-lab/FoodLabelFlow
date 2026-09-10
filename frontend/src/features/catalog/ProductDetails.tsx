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
import { data } from '@/data/catalog'
export function ProductDetails({
  product,
  close,
}: {
  product: Product | null
  close: () => void
}) {
  const formula = data.formula_version.find(
    (f) => f.formula_version_id === product?.current_formula_version_id,
  )
  const items = data.formula_item.filter(
    (f) => f.formula_version_id === formula?.formula_version_id,
  )
  return (
    <Dialog open={!!product} onOpenChange={(open) => !open && close()}>
      <DialogContent className="detail-dialog">
        <DialogHeader>
          <DialogTitle>Product and formula traceability</DialogTitle>
          <DialogDescription>
            API snapshot · Read-only versions and history
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
                <TabsTrigger value="formula">Formula & materials</TabsTrigger>
                <TabsTrigger value="history">Version history</TabsTrigger>
                <TabsTrigger value="source">Data sources</TabsTrigger>
              </TabsList>
              <TabsContent value="formula">
                <div className="detail-summary">
                  <span>Formula V{formula?.version_number ?? '—'}</span>
                  <Badge variant="outline">
                    {formula?.lifecycle_status ?? 'Unknown'}
                  </Badge>
                  <span>Current formula</span>
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
                          Spec V
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
                {data.formula_version.filter(f=>f.product_id===product?.product_id).map(f=><div className="history-entry" key={f.formula_version_id}><span className="history-dot"/><div><h3>V{f.version_number} · {f.lifecycle_status}{f.formula_version_id===product?.current_formula_version_id?' · Current':''}</h3><p>{f.released_at ?? 'Not released'} · {f.released_by_user_id ?? '—'}</p><code>{f.formula_version_id}</code></div></div>)}
              </TabsContent>
              <TabsContent value="source">
                <div className="source-detail">
                  <h3>Public product record</h3>
                  <p>USDA FoodData Central · FDC ID {product.fdc_id}</p>
                  <p>Source ID: {product.data_provenance_id}</p>
                  <h3>Original ingredient text</h3>
                  <p>{product.source_ingredients_text}</p>
                  <p>
                    Supplier, material and formula links are course fixtures, not the actual supply chain of the brand.
                  </p>
                </div>
              </TabsContent>
            </Tabs>
            <div className="dialog-bottom">
              <span>Read-only API data · /api/catalog</span>
              <Button variant="outline" onClick={close}>
                Close
              </Button>
            </div>
          </>
        )}
      </DialogContent>
    </Dialog>
  )
}
