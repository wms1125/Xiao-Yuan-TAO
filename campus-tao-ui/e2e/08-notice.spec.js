import { expect, test } from '@playwright/test'

import { ADMIN, login } from './support/auth.js'
import { seedOrder } from './support/seed.js'

/**
 * 路径 8：通知中心（P2-8 新增页面，此前 7 条路径没有覆盖它）。
 *
 * 这条用例一次守住三件事：
 * 1. **菜单记录在不在**：路由由后端 sys_menu 驱动，V6 迁移没把「通知中心」写进去，
 *    页面能编译过但永远点不进来；
 * 2. **未读角标与筛选解耦**：未读数取服务端聚合，不能因为切了筛选就"消失"；
 * 3. **分页 total 必须跟筛选走**：开发时注入过"total 不跟随筛选"的故障并被单测抓红，
 *    这里在真实浏览器里再兜一层。
 */
test.describe('路径 8：通知中心', () => {
  test('未读数、列表与分页联动，且菜单里有入口', async ({ page, request }) => {
    // 先造一笔新订单：下单会向买家推一条"订单创建成功"未读通知。
    // 不依赖库里历史数据的剩余量，用例才能反复跑。
    await seedOrder(request, { pay: false, ship: false })

    await login(page, ADMIN)
    await page.goto('/campus/notice')

    await expect(page.locator('.campus-head__title')).toHaveText('通知中心')
    // 菜单项由 V6 迁移注册；缺了它 import.meta.glob 扫得到组件也进不来
    await expect(page.locator('.el-menu-item').filter({ hasText: '通知中心' })).toHaveCount(1)

    const unreadStat = page
      .locator('.campus-stat')
      .filter({ hasText: '未读通知' })
      .locator('.campus-stat__value--accent')
    const totalStat = page.locator('.campus-stat').filter({ hasText: '当前筛选' }).locator('.campus-stat__value')

    // 未读数必须 > 0 且不超过总数——角标不可能多于条数
    await expect(unreadStat).not.toHaveText('0')
    const unread = Number(await unreadStat.innerText())
    const total = Number(await totalStat.innerText())
    expect(unread).toBeGreaterThan(0)
    expect(total).toBeGreaterThanOrEqual(unread)

    // 列表里有"未读"状态的行，且带"标为已读"入口
    await expect(page.locator('.el-table tbody tr').filter({ hasText: '未读' }).first()).toBeVisible()

    // 单条标记已读：未读数精确掉 1（请求重拉未读总数这条链路是通的）
    await page
      .locator('.el-table tbody tr')
      .filter({ hasText: '未读' })
      .first()
      .getByRole('button', { name: '标为已读' })
      .click()
    await expect(unreadStat).toHaveText(String(unread - 1))

    // 分页：总数超过每页条数时翻页必须是真换一批，不能翻完还是首页那 10 条
    await expect(page.locator('.notice-pager')).toBeVisible()
    expect(total).toBeGreaterThan(10)
    const firstRowTime = await page.locator('.el-table tbody tr').first().innerText()
    await page.locator('.notice-pager .btn-next').click()
    await expect(page.locator('.notice-pager .el-pager li.is-active')).toHaveText('2')
    await expect(page.locator('.el-table tbody tr').first()).not.toHaveText(firstRowTime)
  })

  test('负向：全部已读后未读数归零，"只看未读"必须筛出空且总数归零', async ({ page, request }) => {
    await seedOrder(request, { pay: false, ship: false })

    await login(page, ADMIN)
    await page.goto('/campus/notice')

    const unreadStat = page
      .locator('.campus-stat')
      .filter({ hasText: '未读通知' })
      .locator('.campus-stat__value--accent')
    const totalStat = page.locator('.campus-stat').filter({ hasText: '当前筛选' }).locator('.campus-stat__value')

    await page.getByRole('button', { name: '全部已读' }).click()
    // 归零且"全部已读"按钮随之消失（v-if unreadCount > 0 才是它该有的状态）
    await expect(unreadStat).toHaveText('0')
    await expect(page.getByRole('button', { name: '全部已读' })).toHaveCount(0)

    // 切"只看未读"：总数必须跟着筛选走，而不是仍显示全量条数
    await page.locator('.el-form-item').filter({ hasText: '状态' }).locator('.el-select').click()
    await page.getByRole('option', { name: '未读' }).click()
    await page.getByRole('button', { name: '搜索' }).click()

    await expect(totalStat).toHaveText('0')
    await expect(page.getByText('暂无通知')).toBeVisible()

    // 重置回"全部"：通知又回来了，红点仍为 0（读状态不被筛选条件带偏）
    await page.getByRole('button', { name: '重置' }).click()
    await expect(totalStat).not.toHaveText('0')
    await expect(unreadStat).toHaveText('0')
    await expect(page.locator('.el-table tbody tr').first()).toBeVisible()
  })
})
