import { expect, test } from '@playwright/test'

import { ADMIN, login } from './support/auth.js'
import { seedOrder, seedShippedOrder } from './support/seed.js'

/**
 * 选中一个 Element Plus 单选项。
 * 直接点 role=radio 会命中被隐藏的原生 input，被可见的 .el-radio__inner 挡住；
 * 点 label（用户真正点的那个东西）才稳。
 */
async function pickRadio(scope, label) {
  const item = scope.locator('.el-radio').filter({ hasText: label }).first()
  await item.click()
  await expect(item).toHaveClass(/is-checked/)
}

/** 打开"申请售后"对话框并填好表单，返回对话框定位器 */
async function openApplyDialog(page, orderNo, reason) {
  await page.getByRole('button', { name: '申请售后', exact: true }).click()
  const dialog = page.locator('.el-dialog').filter({ hasText: '申请售后' })
  await dialog.getByPlaceholder('请输入需要售后的订单号').fill(orderNo)
  await pickRadio(dialog, '仅退款')
  await dialog.getByPlaceholder('请说明商品问题与诉求').fill(reason)
  return dialog
}

test.describe('路径 7：售后申请与审核退款', () => {
  test('已发货订单能申请售后，审核通过后变成已退款', async ({ page, request }) => {
    const orderNo = await seedShippedOrder(request)
    await login(page, ADMIN)
    await page.goto('/campus/aftersale')

    const reason = `E2E 商品与描述不符，申请退款 ${Date.now()}`
    const dialog = await openApplyDialog(page, orderNo, reason)
    await dialog.getByRole('button', { name: '提交申请', exact: true }).click()

    const toast = page.locator('.el-message').first()
    await expect(toast).toContainText('申请已提交')
    const text = (await toast.textContent()) || ''
    const match = /售后单号\s*([A-Za-z0-9-]+)/.exec(text)
    expect(match, `没能从提示「${text}」里解析出售后单号`).not.toBeNull()
    const aftersaleNo = match[1]

    // 列表里出现这张售后单，状态为待审核
    const row = page.locator('.el-table__row').filter({ hasText: aftersaleNo }).first()
    await row.waitFor()
    await expect(row).toContainText('待审核')
    await expect(row).toContainText(orderNo)

    // 审核：通过并退款
    await row.getByRole('button', { name: '审核', exact: true }).click()
    const audit = page.locator('.el-dialog').filter({ hasText: '审核售后单' })
    await pickRadio(audit, '通过并退款')
    await audit.getByPlaceholder('填写审核意见').fill('E2E 审核通过')
    await audit.getByRole('button', { name: '提交审核', exact: true }).click()

    await expect(page.locator('.el-message').first()).toContainText('审核完成')
    await expect(row).toContainText('已退款')
    // 已经退款完成的单子不该还留着"审核"入口
    await expect(row.getByRole('button', { name: '审核', exact: true })).toHaveCount(0)
  })

  test('负向：未发货的订单申请售后会被后端挡下', async ({ page, request }) => {
    // 只付款不发货，专门用来撞"订单未发货，暂不能申请售后"这条业务规则
    const orderNo = await seedOrder(request, { pay: true, ship: false })
    await login(page, ADMIN)
    await page.goto('/campus/aftersale')

    const dialog = await openApplyDialog(page, orderNo, 'E2E 未发货订单不应受理')
    await dialog.getByRole('button', { name: '提交申请', exact: true }).click()

    // 拦截器会把后端的业务异常文案弹出来，且不能出现"申请已提交"
    const toast = page.locator('.el-message').first()
    await expect(toast).toContainText('未发货')
    await expect(page.locator('.el-message').filter({ hasText: '申请已提交' })).toHaveCount(0)

    // 对话框保持打开，用户还能改单号重试
    await expect(dialog).toBeVisible()
  })
})
