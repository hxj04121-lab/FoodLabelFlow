import {test,expect} from './catalog-fixture'

test('all routes use English at desktop and mobile widths',async({page})=>{
  for(const width of [1440,390]) {
    await page.setViewportSize({width,height:900})
    for(const path of ['/','/products','/formulas','/materials','/suppliers','/labels','/impact','/reviews']) {
      await page.goto(path)
      await expect(page.locator('main h1')).toBeVisible()
      expect(await page.locator('html').getAttribute('lang')).toBe('en')
      expect(await page.locator('body').innerText()).not.toMatch(/\p{Script=Han}/u)
      expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBeTruthy()
    }
  }
})

test('English form, validation and confirmation fit on mobile',async({page})=>{
  await page.setViewportSize({width:390,height:844})
  await page.goto('/formulas')
  await page.getByRole('button',{name:'Create formula preview'}).click()
  await page.getByRole('button',{name:'Preview formula',exact:true}).click()
  const dialog=page.getByRole('dialog')
  await expect(page.getByRole('alert')).toContainText('Please fix')
  expect(await dialog.innerText()).not.toMatch(/\p{Script=Han}/u)
  expect(await dialog.evaluate(e=>e.scrollWidth<=e.clientWidth+1)).toBeTruthy()
  await page.getByLabel('Quantity (optional)').fill('8')
  await page.getByRole('button',{name:'Cancel',exact:true}).click()
  const alert=page.getByRole('alertdialog')
  await expect(alert).toBeVisible()
  expect(await alert.evaluate(e=>e.scrollWidth<=e.clientWidth+1)).toBeTruthy()
  expect(await alert.innerText()).not.toMatch(/\p{Script=Han}/u)
  await page.screenshot({path:'test-results/english-confirmation-mobile.png'})
})
