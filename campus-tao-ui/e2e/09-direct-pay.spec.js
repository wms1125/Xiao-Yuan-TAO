import { expect, test } from '@playwright/test'

import { BUYER, ADMIN, login } from './support/auth.js'
import { ensureSellerPayQr, ensureTradeProduct } from './support/seed.js'

/**
 * 路径 9：C2C 收款码直付（买家声明已付款 → 卖家确认收款）
 *
 * 与 05 号用例的分工：05 走「沙箱支付」虚拟资金通道；本用例走直付通道——
 * 钱由买家直接扫给卖家本人，平台不经手、只撮合状态。二者是两条不同的资金链路，
 * 05 绿不代表直付绿（直付此前只有单测 + 真机冒烟，CI 无端到端保护）。
 *
 * 角色分配受后端约束：CampusOrderServiceImpl 禁止购买自己发布的商品，
 * 而交易专供商品的卖家是 ry，所以**卖家 = ry、买家 = admin**。
 *
 * 前置数据（商品、收款码）由 seed 播种：真实文件上传弹窗在 CI 里既慢又脆，
 * 播种只负责"把前置摆好"；"收款码在 account 页与直付弹窗里是否如实渲染"
 * 由本用例断言，属于被测行为而非前置。
 */

const SELLER = BUYER // ry：交易专供商品的卖家
const BUYER_ACC = ADMIN // admin：买家（不能是卖家本人）

/** 让购物车里"只有目标商品被勾选"（与 05 号用例同源，避免勾选状态漂移） */
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

/** 把目标行数量压回 1，避免历次运行把数量越加越大、把直付金额推高 */
async function normalizeQuantity(cartRow) {
  const input = cartRow.locator('.el-input-number input')
  if ((await input.inputValue()) !== '1') {
    await input.fill('1')
    await input.blur()
  }
  await expect(input).toHaveValue('1')
}

/** 下单：详情页加购 → 购物车勾选 → 填收货信息 → 提交，返回订单号 */
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
  await dialog.getByPlaceholder('请输入收货人姓名').fill('E2E 直付收货人')
  await dialog.getByPlaceholder('请输入联系电话').fill('13800138000')
  await dialog.getByPlaceholder('例如：3 号宿舍楼 502 室').fill('E2E 直付测试地址 1 号')
  await dialog.getByRole('button', { name: '提交订单', exact: true }).click()

  const toast = page.locator('.el-message').first()
  await expect(toast).toContainText('下单成功')
  const text = (await toast.textContent()) || ''
  const match = /订单号\s*([A-Za-z0-9-]+)/.exec(text)
  expect(match, `没能从提示「${text}」里解析出订单号`).not.toBeNull()

  await page.waitForURL(/\/campus\/order$/)
  return match[1]
}

/**
 * 换账号：清掉 token（cookie + localStorage）再回登录页。
 * 不走顶栏"退出登录"是为了避免依赖下拉菜单的 DOM 细节。
 */
async function switchAccount(page, account) {
  await page.context().clearCookies()
  await page.evaluate(() => window.localStorage.clear())
  await page.goto('/login')
  await login(page, account)
}

test.describe('路径 9：C2C 收款码直付（买家声明付款 → 卖家确认收款）', () => {
  test('收款码直付状态链路：待付款 → 待卖家确认收款 → 已付款', async ({ page, request }) => {
    // ---------- 前置：专供商品（卖家 ry）+ 卖家收款码 ----------
    const product = await ensureTradeProduct(request)
    const qrUrl = await ensureSellerPayQr(request)

    // ---------- 卖家视角：account 页如实渲染收款码 ----------
    await login(page, SELLER)
    await page.goto('/campus/account')
    const qrImg = page.locator('.pay-qr__preview img')
    await qrImg.waitFor()
    await expect(qrImg).toHaveAttribute('src', qrUrl)

    // ---------- 买家视角：下单 → 扫码直付 ----------
    await switchAccount(page, BUYER_ACC)
    const orderNo = await placeOrder(page, product)

    const orderRow = page.locator('.el-table__row').filter({ hasText: orderNo }).first()
    await orderRow.waitFor()
    await expect(orderRow).toContainText('待付款')

    await orderRow.getByRole('button', { name: '扫码直付', exact: true }).click()

    // 直付弹窗必须展示"卖家收款码 + 应付金额"，这是买家付款的依据
    const payDialog = page.locator('.el-dialog').filter({ hasText: '扫码直付（付给卖家）' })
    await expect(payDialog).toBeVisible()
    await expect(payDialog.locator('.direct-pay__qr img')).toHaveAttribute('src', qrUrl)
    await expect(payDialog.locator('.direct-pay__amount-value')).toContainText(`¥${Number(product.price).toFixed(2)}`)

    await payDialog.getByRole('button', { name: '我已付款', exact: true }).click()
    await page.locator('.el-message-box').getByRole('button', { name: '确定', exact: true }).click()
    await expect(page.locator('.el-message').first()).toContainText('已通知卖家确认收款')
    await expect(orderRow).toContainText('待卖家确认收款')

    // ---------- 卖家视角：订单列表出现该直付单 → 确认已收款 ----------
    await switchAccount(page, SELLER)
    await page.goto('/campus/order')

    // 卖家能查到自己卖出的单（列表筛选是 user_id 或 seller_id），否则停在待确认时无入口
    const sellerRow = page.locator('.el-table__row').filter({ hasText: orderNo }).first()
    await sellerRow.waitFor()
    await expect(sellerRow).toContainText('待卖家确认收款')

    await sellerRow.getByRole('button', { name: '确认已收款', exact: true }).click()
    await page.locator('.el-message-box').getByRole('button', { name: '确定', exact: true }).click()
    await expect(page.locator('.el-message').first()).toContainText('已确认收款')
    await expect(sellerRow).toContainText('已付款')

    // 确认收款后入口必须消失，否则说明按钮的状态判断漏了分支
    await expect(sellerRow.getByRole('button', { name: '确认已收款', exact: true })).toHaveCount(0)
  })
})
