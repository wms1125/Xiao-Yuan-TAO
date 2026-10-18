import { expect, test } from '@playwright/test'

import { ADMIN, login } from './support/auth.js'
import { ensureTradeProduct } from './support/seed.js'

/**
 * 让购物车里"只有目标商品被勾选"。
 *
 * 购物车加载时会按 campus_cart.checked 自动勾选若干行（cart/index.vue 第 254-260 行），
 * 所以不能无脑点一下——那一下可能是"取消勾选"。这里只在状态不符时才点，天然幂等。
 */
async function selectOnlyCartRow(page, title) {
  const rows = page.locator('.el-table__row')
  const total = await rows.count()
  expect(total, '购物车是空的，前面的加购步骤没成功').toBeGreaterThan(0)

  for (let i = 0; i < total; i++) {
    const row = rows.nth(i)
    const mine = ((await row.textContent()) || '').includes(title)
    const checked = await row
      .locator('.el-checkbox')
      .first()
      .evaluate((el) => el.classList.contains('is-checked'))
    if (mine !== checked) {
      await row.locator('.el-checkbox__inner').first().click()
    }
  }

  const mineRow = rows.filter({ hasText: title }).first()
  await expect(mineRow.locator('.el-checkbox').first()).toHaveClass(/is-checked/)
  await expect(page.getByRole('button', { name: '去结算', exact: true })).toBeEnabled()
  return mineRow
}

/** 把目标行的数量压回 1，避免历次运行把数量越加越大、把总价推高 */
async function normalizeQuantity(cartRow) {
  const input = cartRow.locator('.el-input-number input')
  if ((await input.inputValue()) !== '1') {
    await input.fill('1')
    await input.blur()
  }
  await expect(input).toHaveValue('1')
}

/**
 * 下单：详情页加购 → 购物车勾选 → 填收货信息 → 提交。
 * 订单号从"下单成功，订单号 xxx"的提示里取，用它定位订单行，
 * 免得同一商品的历史订单混在一起时点错行。
 */
async function placeOrder(page, product) {
  await page.goto(`/campus/mall-detail?productId=${product.id}`)
  await page.getByRole('button', { name: '加入购物车', exact: true }).click()
  await expect(page.locator('.el-message').first()).toContainText('已加入购物车')

  await page.goto('/campus/cart')
  const cartRow = page.locator('.el-table__row').filter({ hasText: product.title }).first()
  await cartRow.waitFor()
  await selectOnlyCartRow(page, product.title)
  await normalizeQuantity(cartRow)

  await page.getByRole('button', { name: '去结算', exact: true }).click()

  const dialog = page.locator('.el-dialog').filter({ hasText: '确认订单信息' })
  await dialog.getByPlaceholder('请输入收货人姓名').fill('E2E 收货人')
  await dialog.getByPlaceholder('请输入联系电话').fill('13800138000')
  await dialog.getByPlaceholder('例如：3 号宿舍楼 502 室').fill('E2E 测试地址 1 号')
  await dialog.getByRole('button', { name: '提交订单', exact: true }).click()

  const toast = page.locator('.el-message').first()
  await expect(toast).toContainText('下单成功')
  const text = (await toast.textContent()) || ''
  const match = /订单号\s*([A-Za-z0-9-]+)/.exec(text)
  expect(match, `没能从提示「${text}」里解析出订单号`).not.toBeNull()

  // 提交成功后前端会自己跳到订单列表，这里等它落地
  await page.waitForURL(/\/campus\/order$/)
  return match[1]
}

test.describe('路径 5：下单 → 支付 → 发货 → 确认收货', () => {
  test('整条交易链路的订单状态逐级推进', async ({ page, request }) => {
    const product = await ensureTradeProduct(request)
    await login(page, ADMIN)

    const orderNo = await placeOrder(page, product)
    const orderRow = page.locator('.el-table__row').filter({ hasText: orderNo }).first()
    await orderRow.waitFor()
    await expect(orderRow).toContainText('待付款')

    // ---- 支付 ----
    await orderRow.getByRole('button', { name: '支付', exact: true }).click()
    await page.locator('.el-message-box').getByRole('button', { name: '确定', exact: true }).click()
    await expect(page.locator('.el-message').first()).toContainText('支付成功')
    await expect(orderRow).toContainText('已付款')

    // ---- 发货（需要填物流单号）----
    await orderRow.getByRole('button', { name: '发货', exact: true }).click()
    const shipBox = page.locator('.el-message-box')
    await shipBox.locator('input').fill('SF' + Date.now())
    await shipBox.getByRole('button', { name: '确认发货', exact: true }).click()
    await expect(page.locator('.el-message').first()).toContainText('已发货')
    await expect(orderRow).toContainText('已发货')

    // ---- 确认收货 ----
    await orderRow.getByRole('button', { name: '确认收货', exact: true }).click()
    await page.locator('.el-message-box').getByRole('button', { name: '确定', exact: true }).click()
    await expect(page.locator('.el-message').first()).toContainText('已确认收货')
    await expect(orderRow).toContainText('已完成')

    // 走完全流程后"确认收货"必须消失，否则说明按钮的状态判断漏了分支
    await expect(orderRow.getByRole('button', { name: '确认收货', exact: true })).toHaveCount(0)
  })

  test('负向：未付款的订单不给发货和收货入口', async ({ page, request }) => {
    const product = await ensureTradeProduct(request)
    await login(page, ADMIN)

    const orderNo = await placeOrder(page, product)
    const orderRow = page.locator('.el-table__row').filter({ hasText: orderNo }).first()
    await orderRow.waitFor()

    // 待付款状态下不该出现"发货""确认收货"入口。
    // 后端也会拒，但界面先拦住才不会让用户白点一次再看报错。
    await expect(orderRow).toContainText('待付款')
    await expect(orderRow.getByRole('button', { name: '发货', exact: true })).toHaveCount(0)
    await expect(orderRow.getByRole('button', { name: '确认收货', exact: true })).toHaveCount(0)

    // 收尾：取消这笔订单，别留个"待付款"污染后续用例
    await orderRow.getByRole('button', { name: '取消', exact: true }).click()
    await page.locator('.el-message-box').getByRole('button', { name: '确定', exact: true }).click()
    await expect(orderRow).toContainText('已取消')
  })
})
