import {test,expect} from './catalog-fixture'
async function preview(page:import('@playwright/test').Page) {
  await page.goto('/formulas')
  await page.getByRole('button',{name:'Create formula preview'}).click()
  await page.getByLabel('Product *',{exact:true}).selectOption('prod_usda_1106285')
  await page.getByLabel('Supplier material *').selectOption('mat_chocolate_base')
  await page.getByLabel('Specification version *').selectOption('spec_chocolate_v1')
  await page.getByRole('button',{name:'Preview formula',exact:true}).click()
  await page.getByRole('checkbox',{name:'Enable local demo identity for this form'}).check()
}
test('503 preserves entries and prevents automatic duplicate creation',async({page})=>{
  let writes=0
  await page.route('**/api/catalog/products/prod_usda_1106285',r=>r.fulfill({json:{product_id:'prod_usda_1106285',current_formula_version_id:'formula_1106285_v1'}}))
  await page.route('**/api/catalog/formulas',r=>{writes++;return r.fulfill({status:503,json:{code:'CATALOG_INTEGRATION_UNAVAILABLE',message:'Adapter unavailable'}})})
  await preview(page)
  await page.getByRole('button',{name:'Save draft',exact:true}).click()
  await expect(page.getByRole('alert')).toContainText('CATALOG_INTEGRATION_UNAVAILABLE')
  await expect(page.getByRole('button',{name:'Save draft',exact:true})).toBeDisabled()
  expect(writes).toBe(1)
  await expect(page.getByText('spec_chocolate_v1',{exact:true})).toBeVisible()
})
test('409 requires refreshed pointer and explicit confirmation',async({page})=>{
  let current='formula_1106285_v1';const bodies:unknown[]=[]
  await page.route('**/api/catalog/products/prod_usda_1106285',r=>r.fulfill({json:{product_id:'prod_usda_1106285',current_formula_version_id:current}}))
  await page.route('**/api/catalog/formulas',r=>r.fulfill({status:201,json:{formula_version_id:'draft-test',product_id:'prod_usda_1106285',version_number:2,lifecycle_status:'DRAFT',is_current_released:'N'}}))
  await page.route('**/api/catalog/formulas/draft-test/release',r=>{bodies.push(r.request().postDataJSON());current='other-formula';return r.fulfill({status:409,json:{code:'CURRENT_FORMULA_CHANGED',message:'Current formula changed'}})})
  await preview(page)
  await page.getByRole('button',{name:'Save draft',exact:true}).click()
  await page.getByRole('button',{name:'Publish formula',exact:true}).click()
  await page.getByRole('button',{name:'Confirm publication',exact:true}).click()
  await expect(page.getByRole('alert')).toContainText('CURRENT_FORMULA_CHANGED')
  await expect(page.getByRole('button',{name:'Publish formula',exact:true})).toBeDisabled()
  await page.getByRole('button',{name:'Refresh server history'}).click()
  await page.getByRole('button',{name:'Publish formula',exact:true}).click()
  await expect(page.getByRole('group',{name:'Confirm publication'})).toContainText('other-formula')
  expect(bodies).toEqual([{expectedCurrentFormulaId:'formula_1106285_v1'}])
})
