// @vitest-environment jsdom

import { describe, it, expect, vi, beforeEach } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

// 组件只从 element-plus 取消息提示，整体替换为替身
vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
  ElMessageBox: { confirm: vi.fn() }
}))

vi.mock('vue-router', () => ({
  useRoute: () => ({ query: {} }),
  useRouter: () => ({ push: vi.fn() })
}))

vi.mock('@/api/campus/notice', () => ({
  pageCampusNotice: vi.fn(),
  listCampusNotice: vi.fn(),
  getCampusNoticeTop: vi.fn(),
  getCampusNoticeUnreadCount: vi.fn(),
  readCampusNotice: vi.fn(),
  readAllCampusNotice: vi.fn()
}))

import { ElMessage } from 'element-plus'
import {
  pageCampusNotice,
  getCampusNoticeUnreadCount,
  readCampusNotice,
  readAllCampusNotice
} from '@/api/campus/notice'
import NoticePage from '@/views/campus/notice/index.vue'
import { elementStubs, clickButton } from './support/element-stubs'

const noticeRow = (over = {}) => ({
  noticeId: 1,
  userId: 2,
  type: 'order',
  title: '订单已支付',
  content: '订单 CT202601010001 支付成功',
  bizNo: 'CT202601010001',
  isRead: 0,
  createTime: '2026-10-16T10:00:00',
  readTime: null,
  ...over
})

/** 服务端分页响应 { total, rows } */
const paged = (rows, total = rows.length) => ({ total, rows })

async function mountPage() {
  const wrapper = mount(NoticePage, { global: elementStubs() })
  await flushPromises()
  return wrapper
}

beforeEach(() => {
  vi.clearAllMocks()
  // 未读总数独立接口：与筛选条件解耦
  getCampusNoticeUnreadCount.mockResolvedValue({ code: 200, data: 5 })
})

describe('通知中心页', () => {
  it('挂载后请求分页列表与未读总数', async () => {
    pageCampusNotice.mockResolvedValue(paged([noticeRow()]))
    await mountPage()

    expect(pageCampusNotice).toHaveBeenCalledTimes(1)
    expect(getCampusNoticeUnreadCount).toHaveBeenCalledTimes(1)
  })

  it('渲染类型标签、标题、内容与业务号', async () => {
    pageCampusNotice.mockResolvedValue(paged([noticeRow()]))
    const wrapper = await mountPage()

    expect(wrapper.text()).toContain('订单已支付')
    expect(wrapper.text()).toContain('订单 CT202601010001 支付成功')
    expect(wrapper.text()).toContain('CT202601010001')
  })

  it('未读通知显示「未读」标签，已读显示「已读」', async () => {
    pageCampusNotice.mockResolvedValue(
      paged([noticeRow({ noticeId: 1, isRead: 0 }), noticeRow({ noticeId: 2, isRead: 1, title: '已读的那条' })])
    )
    const wrapper = await mountPage()

    const tags = wrapper.findAll('.el-tag-stub').map((t) => t.text())
    expect(tags).toContain('未读')
    expect(tags).toContain('已读')
  })

  it('未读总数取服务端聚合值，不是当前页数出来的', async () => {
    // 当前页只有 1 行未读，但服务端未读总数是 5（其余未读在别的页）
    pageCampusNotice.mockResolvedValue(paged([noticeRow()], 37))
    getCampusNoticeUnreadCount.mockResolvedValue({ code: 200, data: 5 })
    const wrapper = await mountPage()

    const stats = wrapper.findAll('.campus-stat__value').map((n) => n.text())
    expect(stats[0]).toBe('5') // 未读通知
    expect(stats[1]).toBe('37') // 当前筛选总数来自 total，不是 rows.length
  })

  it('只给未读行显示「标为已读」按钮，已读行不显示', async () => {
    pageCampusNotice.mockResolvedValue(
      paged([noticeRow({ noticeId: 1, isRead: 0 }), noticeRow({ noticeId: 2, isRead: 1 })])
    )
    const wrapper = await mountPage()

    const readBtns = wrapper.findAll('.el-button-stub').filter((b) => b.text().includes('标为已读'))
    expect(readBtns).toHaveLength(1)
  })

  it('点击标为已读：调用接口后重新拉列表与未读总数', async () => {
    pageCampusNotice.mockResolvedValue(paged([noticeRow()]))
    readCampusNotice.mockResolvedValue({ code: 200 })
    const wrapper = await mountPage()
    pageCampusNotice.mockClear()
    getCampusNoticeUnreadCount.mockClear()

    const readBtn = wrapper.findAll('.el-button-stub').find((b) => b.text().includes('标为已读'))
    await readBtn.trigger('click')
    await flushPromises()

    expect(readCampusNotice).toHaveBeenCalledWith(1)
    expect(ElMessage.success).toHaveBeenCalledWith('已标记为已读')
    // 必须重新拉两端：列表要刷新状态，未读数要下降，否则红点不降
    expect(pageCampusNotice).toHaveBeenCalledTimes(1)
    expect(getCampusNoticeUnreadCount).toHaveBeenCalledTimes(1)
  })

  it('全部已读：调用接口并刷新列表', async () => {
    pageCampusNotice.mockResolvedValue(paged([noticeRow()]))
    readAllCampusNotice.mockResolvedValue({ code: 200 })
    const wrapper = await mountPage()

    await clickButton(wrapper, '全部已读')
    await flushPromises()

    expect(readAllCampusNotice).toHaveBeenCalledTimes(1)
    expect(ElMessage.success).toHaveBeenCalledWith('已全部标记已读')
  })

  it('未读总数为 0 时隐藏「全部已读」按钮', async () => {
    pageCampusNotice.mockResolvedValue(paged([noticeRow({ isRead: 1 })], 3))
    getCampusNoticeUnreadCount.mockResolvedValue({ code: 200, data: 0 })
    const wrapper = await mountPage()

    const allBtn = wrapper.findAll('.el-button-stub').find((b) => b.text().includes('全部已读'))
    expect(allBtn).toBeUndefined()
  })

  it('total > 0 时渲染分页器并透出 total', async () => {
    pageCampusNotice.mockResolvedValue(paged([noticeRow()], 37))
    const wrapper = await mountPage()

    const pager = wrapper.find('.el-pagination-stub')
    expect(pager.exists()).toBe(true)
    expect(pager.attributes('data-total')).toBe('37')
    expect(pager.attributes('data-page')).toBe('1')
  })

  it('翻页时带新页码再请求一次', async () => {
    pageCampusNotice.mockResolvedValue(paged([noticeRow()], 37))
    const wrapper = await mountPage()
    pageCampusNotice.mockClear()

    await wrapper.find('.el-pagination__next-stub').trigger('click')
    await flushPromises()

    expect(pageCampusNotice).toHaveBeenCalledTimes(1)
    // 请求体里不应带上 undefined 的筛选项（会被序列化成字符串 "undefined"）
    const query = pageCampusNotice.mock.calls[0][0]
    expect(query.pageNum).toBe(2)
    expect(query).not.toHaveProperty('type')
    expect(query).not.toHaveProperty('isRead')
  })

  it('空列表：展示引导文案，不渲染分页器', async () => {
    pageCampusNotice.mockResolvedValue(paged([], 0))
    const wrapper = await mountPage()

    expect(wrapper.text()).toContain('暂无通知')
    expect(wrapper.find('.el-pagination-stub').exists()).toBe(false)
  })

  it('按类型筛选后回到第 1 页再请求', async () => {
    pageCampusNotice.mockResolvedValue(paged([noticeRow()], 37))
    const wrapper = await mountPage()
    pageCampusNotice.mockClear()

    await clickButton(wrapper, '搜索')
    await flushPromises()

    expect(pageCampusNotice).toHaveBeenCalledTimes(1)
    expect(pageCampusNotice.mock.calls[0][0].pageNum).toBe(1)
  })
})
