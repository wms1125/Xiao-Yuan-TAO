import { expect, test } from '@playwright/test'

import { ADMIN, login } from './support/auth.js'

test.describe('路径 4：收藏 / 取消收藏', () => {
  test('点星标能收藏，再点一次能取消，图标状态跟着变', async ({ page }) => {
    await login(page, ADMIN)
    await page.goto('/campus/mall')

    const card = page.locator('.campus-product').first()
    await card.waitFor()

    const star = card.locator('.campus-product__fav')
    const wasFavored = await star.evaluate((el) => el.classList.contains('campus-product__fav--on'))

    // 两次点击之间故意不留等待：收藏按钮必须扛得住连点。
    // 曾经在这里挂过——全局防重复提交把第二次请求拦了，按钮纹丝不动还弹「请勿重复提交」。
    await star.click()
    await expect(star).toHaveClass(wasFavored ? /^(?!.*--on)/ : /--on/)

    // 第二次点击翻回原点，用例可重复执行
    await star.click()
    await expect(star).toHaveClass(wasFavored ? /--on/ : /^(?!.*--on)/)
  })

  test('负向：点收藏不能顺带跳进详情页（@click.stop 失效会暴露）', async ({ page }) => {
    await login(page, ADMIN)
    await page.goto('/campus/mall')

    const card = page.locator('.campus-product').first()
    await card.waitFor()

    const star = card.locator('.campus-product__fav')
    const wasFavored = await star.evaluate((el) => el.classList.contains('campus-product__fav--on'))

    await star.click()

    // 必须先确认"点击真的被处理了"，否则点击完全失效时本用例也会绿——那是假绿。
    await expect(star).toHaveClass(wasFavored ? /^(?!.*--on)/ : /--on/)

    // 星标上是 @click.stop，冒泡没被拦住就会触发卡片点击 → 跳详情
    await expect(page).toHaveURL(/\/campus\/mall$/)

    // 复位，避免影响后续用例
    await star.click()
    await expect(star).toHaveClass(wasFavored ? /--on/ : /^(?!.*--on)/)
  })
})
