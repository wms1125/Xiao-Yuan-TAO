import { expect, test } from '@playwright/test'

import { ADMIN, login } from './support/auth.js'

test.describe('路径 3：搜索与搜索历史', () => {
  test('搜过的关键词会进入"最近搜索"，且点击能回填', async ({ page }) => {
    await login(page, ADMIN)
    await page.goto('/campus/mall')

    const firstTitle = (await page.locator('.campus-product__title').first().innerText()).trim()
    const keyword = firstTitle.slice(0, 2)

    const search = page.getByPlaceholder('商品标题')
    await search.fill(keyword)
    await page.getByRole('button', { name: '搜索', exact: true }).click()

    // 搜完列表还得有内容——"搜出 0 条"也能算通过的话，这个断言就没意义了
    await expect(page.locator('.campus-product').first()).toBeVisible()

    // 刷新回到初始态再聚焦：历史下拉是 @focus 上的"切换"（toggle），
    // 搜索后它仍是打开状态，再点一次反而会关掉。重载页面让状态归零最稳。
    await page.reload()
    await page.locator('.campus-product').first().waitFor()
    await page.getByPlaceholder('商品标题').click()
    const history = page.locator('.mall-history')
    await expect(history).toBeVisible()
    await expect(history.locator('.mall-history__keyword').first()).toHaveText(keyword)

    // 点历史条目能回填到输入框
    await history.locator('.mall-history__item').first().click()
    await expect(search).toHaveValue(keyword)
  })
})
