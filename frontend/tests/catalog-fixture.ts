import { test as base, expect } from '@playwright/test'
import seed from '../src/data/seed-preview.json' with { type: 'json' }
// UI regression tests only: explicit HTTP fixtures, never live-integration evidence.
export const test=base.extend<{catalogFixture:void}>({catalogFixture:[async({page},use)=>{
  await page.route('**/api/catalog/**',async route=>{
    const url=new URL(route.request().url());const path=url.pathname.replace('/api/catalog','')
    let value:unknown
    const lists:Record<string,unknown[]>={'/suppliers':seed.supplier,'/materials':seed.supplier_material,'/specifications':seed.ingredient_specification_version,'/products':seed.product.map(p=>({...p,current_formula_version_id:seed.formula_version.find(f=>f.product_id===p.product_id)?.formula_version_id??null}))}
    if(lists[path]) value=lists[path].slice(Number(url.searchParams.get('offset')||0),Number(url.searchParams.get('offset')||0)+Number(url.searchParams.get('limit')||50))
    else if(path.startsWith('/specifications/')) { const id=path.split('/')[2];value={...seed.ingredient_specification_version.find(s=>s.specification_version_id===id),components:seed.spec_component.filter(c=>c.specification_version_id===id)} }
    else if(path.startsWith('/products/')&&path.endsWith('/formulas')) value=seed.formula_version.filter(f=>f.product_id===path.split('/')[2])
    else if(path.startsWith('/formulas/')) {const id=path.split('/')[2];value={...seed.formula_version.find(f=>f.formula_version_id===id),items:seed.formula_item.filter(i=>i.formula_version_id===id)}}
    if(value===undefined) await route.fulfill({status:404,json:{code:'RESOURCE_NOT_FOUND',message:'Test fixture missing'}})
    else await route.fulfill({json:value})
  }); await use()
},{auto:true}]})
export {expect}
