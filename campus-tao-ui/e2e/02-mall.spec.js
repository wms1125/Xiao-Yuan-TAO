import { expect, test } from '@playwright/test'

import { ADMIN, login } from './support/auth.js'

test.describe('路径 2：商城列表 → 商品详情', () => {
  test('列表有商品，点卡片能进详情且信息一致', async ({ page }) => {
    await login(page, ADMIN)
    await page.goto('/campus/mall')

    const cards = page.locator('.campus-product')
    await expect(cards.first()).toBeVisible()

    // 记下列表里第一件商品的标题与价格，详情页必须对得上——
    // 只断言"详情页打开了"是不够的，路由通了但数据串了也是缺陷
    const title = (await cards.first().locator('.campus-product__title').innerText()).trim()
    const price = (await cards.first().locator('.campus-price').innerText()).trim()
    expect(title.length).toBeGreaterThan(0)

    await cards.first().click()

    await expect(page).toHaveURL(/\/campus\/mall-detail/)
    await expect(page.locator('body')).toContainText(title)
    await expect(page.locator('body')).toContainText(price)
    await expect(page.getByRole('button', { name: '加入购物车', exact: true })).toBeVisible()
  })
})
