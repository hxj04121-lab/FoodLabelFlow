import seed from './seed-preview.json'
import { catalogGet, catalogList } from '@/api/catalog'
export type Product = Omit<(typeof seed.product)[number], 'current_formula_version_id'> & { current_formula_version_id: string | null }
type CatalogData = Omit<typeof seed, 'product'> & { product: Product[] }
export let data: CatalogData = { ...seed, product: [], supplier:[],supplier_material:[],ingredient_specification_version:[],formula_version:[],formula_item:[],spec_component:[],ingredient:[] }
export let loadedAt = ''
// No fallback to the bundled preview. Only validated HTTP results enter live views.
function records(value: unknown): Record<string, unknown>[] {
  if (!Array.isArray(value) || value.some(v => !v || typeof v !== 'object')) throw new Error('Invalid API record format')
  return value
}
function normalize(value: Record<string, unknown>) {
  return Object.fromEntries(Object.entries(value).filter(([,v]) => !Array.isArray(v) && (typeof v !== 'object' || v === null)).map(([k,v]) => [k, v == null ? null : String(v)]))
}
async function batch<T,R>(items:T[], task:(item:T)=>Promise<R>):Promise<R[]> {
  const out:R[]=[]
  for(let n=0;n<items.length;n+=6) out.push(...await Promise.all(items.slice(n,n+6).map(task)))
  return out
}
export async function loadCatalog(signal: AbortSignal) {
  const [suppliers, materials, specifications, products] = await Promise.all(['/suppliers','/materials','/specifications','/products'].map(p=>catalogList(p,signal)))
  for(const [rows,id] of [[suppliers,'supplier_id'],[materials,'supplier_material_id'],[specifications,'specification_version_id'],[products,'product_id']] as const) {
    if(rows.some(r=>typeof r[id]!=='string')) throw new Error(`API response is missing a valid ${id}`)
  }
  const specDetails = await batch(specifications, async s => await catalogGet(`/specifications/${encodeURIComponent(String(s.specification_version_id))}`, signal) as Record<string,unknown>)
  const histories = await batch(products, async p => records(await catalogGet(`/products/${encodeURIComponent(String(p.product_id))}/formulas`,signal)))
  const formulas = histories.flat()
  const details = await batch(formulas, async f=> await catalogGet(`/formulas/${encodeURIComponent(String(f.formula_version_id))}`,signal) as Record<string,unknown>)
  if(signal.aborted) return
  const rows = (r:Record<string,unknown>[])=>r.map(normalize)
  // Canonical snake_case is retained; numeric SQL values are normalized for existing presentation types.
  data = { ...seed, supplier:rows(suppliers), supplier_material:rows(materials), ingredient_specification_version:rows(specifications), product:rows(products), formula_version:rows(formulas), formula_item:rows(details.flatMap(d=>records(d.items))), spec_component:rows(specDetails.flatMap(s=>records(s.components))), ingredient:[] } as unknown as CatalogData
  loadedAt = new Date().toLocaleTimeString('en-GB')
}
export type Material = (typeof data.supplier_material)[number]
export const groups: Record<string, string> = {
  NO_ACTION_BASELINE_SOY: 'Soy declared',
  REVIEW_REQUIRED_BASELINE_NO_SOY: 'Soy not declared',
  NEGATIVE_CONTROL_NO_CHOCOLATE: 'No chocolate material',
}
export const groupColors: Record<string, string> = {
  NO_ACTION_BASELINE_SOY: 'teal',
  REVIEW_REQUIRED_BASELINE_NO_SOY: 'violet',
  NEGATIVE_CONTROL_NO_CHOCOLATE: 'blue',
}
