import { expect, test } from '@playwright/test'

import { ADMIN, login } from './support/auth.js'
import { seedMessage, seedShippedOrder } from './support/seed.js'

test.describe('路径 6：站内信', () => {
  test('打开会话能看到对方消息，发出去的消息立即出现在气泡里', async ({ page, request }) => {
    // 会话是从消息表反查出来的，所以先造一笔订单 + 一条对方发来的消息
    const orderNo = await seedShippedOrder(request)
    const incoming = `E2E 你好，这笔订单什么时候发货？${Date.now()}`
    await seedMessage(request, orderNo, incoming, 'ry')

    await login(page, ADMIN)
    await page.goto('/campus/message')

    // 会话列表里能找到这笔订单，且带着未读角标
    const conv = page.locator('.msg-conv__item').filter({ hasText: orderNo }).first()
    await conv.waitFor()
    await expect(conv.locator('.msg-conv__badge')).toBeVisible()

    await conv.click()

    // 点开之后未读角标消失，对方那条消息出现在气泡区
    await expect(conv.locator('.msg-conv__badge')).toHaveCount(0)
    await expect(page.locator('.msg-item__bubble').filter({ hasText: incoming })).toBeVisible()

    // 我发的消息要立刻出现，且发送者标为"我"
    const reply = `E2E 今天下午就发，单号稍后给你 ${Date.now()}`
    await page.getByPlaceholder('输入消息内容（最多 500 字）').fill(reply)
    await page.getByRole('button', { name: '发送', exact: true }).click()

    const myBubble = page.locator('.msg-item').filter({ hasText: reply }).first()
    await expect(myBubble).toBeVisible()
    await expect(myBubble).toContainText('我')
    // 发送成功后输入框要清空，否则用户会重复发出去
    await expect(page.getByPlaceholder('输入消息内容（最多 500 字）')).toHaveValue('')
  })

  test('负向：内容为空或只有空白时不能发送', async ({ page, request }) => {
    const orderNo = await seedShippedOrder(request)
    await seedMessage(request, orderNo, 'E2E 空白校验用例的前置消息', 'ry')

    await login(page, ADMIN)
    await page.goto('/campus/message')

    const conv = page.locator('.msg-conv__item').filter({ hasText: orderNo }).first()
    await conv.waitFor()
    await conv.click()

    const sendBtn = page.getByRole('button', { name: '发送', exact: true })
    const box = page.getByPlaceholder('输入消息内容（最多 500 字）')

    await expect(sendBtn).toBeDisabled()

    // 只敲空格也不能发——否则会往对方那里推一条空白消息
    await box.fill('   ')
    await expect(sendBtn).toBeDisabled()

    await box.fill('有内容了')
    await expect(sendBtn).toBeEnabled()
  })
})
