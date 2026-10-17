// @vitest-environment jsdom

import { describe, it, expect, vi, beforeEach } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

// 组件只从 element-plus 取消息提示，整体替换为替身
vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
  ElMessageBox: { confirm: vi.fn() }
}))

const { routeQueryMock } = vi.hoisted(() => ({ routeQueryMock: { value: {} } }))
vi.mock('vue-router', () => ({
  useRoute: () => ({ query: routeQueryMock.value }),
  useRouter: () => ({ push: vi.fn() })
}))

vi.mock('@/api/campus/message', () => ({
  listConversation: vi.fn(),
  listOrderMessage: vi.fn(),
  readOrderMessage: vi.fn(),
  sendMessage: vi.fn(),
  unreadMessageCount: vi.fn()
}))

vi.mock('@/api/login', () => ({
  getInfo: vi.fn()
}))

import { ElMessage } from 'element-plus'
import {
  listConversation,
  listOrderMessage,
  readOrderMessage,
  sendMessage,
  unreadMessageCount
} from '@/api/campus/message'
import { getInfo } from '@/api/login'
import MessagePage from '@/views/campus/message/index.vue'
import { elementStubs } from './support/element-stubs'

const MY_ID = 2
const SELLER_ID = 3

const convRow = (over = {}) => ({
  orderNo: 'CT202601010001',
  lastContent: '请问还有货吗',
  lastTime: '2026-10-16 10:00:00',
  unread: 2,
  orderStatus: 2,
  totalAmount: '60.00',
  buyerId: MY_ID,
  buyerName: 'ry',
  itemCount: 1,
  ...over
})

const msgRow = (over = {}) => ({
  messageId: 1,
  orderNo: 'CT202601010001',
  fromUserId: SELLER_ID,
  toUserId: MY_ID,
  content: '有的，拍下即可',
  isRead: 1,
  createTime: '2026-10-16 10:01:00',
  fromUserName: '李雨桐',
  toUserName: 'ry',
  ...over
})

/**
 * 服务端分页响应：{ total, rows }。
 * 曾经会话列表是"一次全量返回数组"，前端拿 res.data 当列表；
 * 现在是标准表格分页结构，rows 才是列表，total 是会话总数（不是当前页长度）。
 */
const paged = (rows, total = rows.length) => ({ total, rows })

function stubConversations(rows = [convRow()], total = rows.length) {
  listConversation.mockResolvedValue(paged(rows, total))
}

function stubMessages(rows = [msgRow()]) {
  listOrderMessage.mockResolvedValue({ code: 200, data: rows })
}

async function mountPage() {
  const wrapper = mount(MessagePage, { global: elementStubs() })
  await flushPromises()
  return wrapper
}

beforeEach(() => {
  vi.clearAllMocks()
  routeQueryMock.value = {}
  // 登录态：getInfo 返回当前用户 ID
  getInfo.mockResolvedValue({ code: 200, user: { userId: MY_ID } })
  readOrderMessage.mockResolvedValue({ code: 200 })
  // 未读总数走独立接口（/campus/message/unread）：分页后未读数可能落在其他页
  unreadMessageCount.mockResolvedValue({ code: 200, data: { count: 0 } })
})

describe('消息沟通页', () => {
  it('挂载后请求会话列表并取当前用户身份', async () => {
    stubConversations()
    await mountPage()

    expect(listConversation).toHaveBeenCalledTimes(1)
    expect(getInfo).toHaveBeenCalledTimes(1)
    // 未读总数走独立接口：分页后未读数可能落在其他页，不能遍历当前页累加
    expect(unreadMessageCount).toHaveBeenCalledTimes(1)
  })

  it('渲染会话的订单号、最后一条消息与金额', async () => {
    stubConversations()
    const wrapper = await mountPage()

    expect(wrapper.text()).toContain('CT202601010001')
    expect(wrapper.text()).toContain('请问还有货吗')
    expect(wrapper.text()).toContain('60.00')
  })

  it('概览：会话总数与未读总数均取服务端值，不是当前页长度', async () => {
    // 当前页只回了 2 行，但总数是 37；未读数也不在这两行里
    stubConversations([convRow({ unread: 2 }), convRow({ orderNo: 'CT202601010002', unread: 1 })], 37)
    unreadMessageCount.mockResolvedValue({ code: 200, data: { count: 12 } })
    const wrapper = await mountPage()

    const stats = wrapper.findAll('.campus-stat__value').map((n) => n.text())
    expect(stats[0]).toBe('37') // 会话总数来自 total，不是 2
    expect(stats[1]).toBe('12') // 未读合计来自聚合接口，不是本页累加的 3
  })

  it('分页：total>0 时渲染分页器，并把 total/当前页码透给控件', async () => {
    stubConversations([convRow()], 37)
    const wrapper = await mountPage()

    const pager = wrapper.find('.el-pagination-stub')
    expect(pager.exists()).toBe(true)
    expect(pager.attributes('data-total')).toBe('37')
    expect(pager.attributes('data-page')).toBe('1')
  })

  it('分页：翻页时带新页码再请求一次列表', async () => {
    stubConversations([convRow()], 37)
    const wrapper = await mountPage()
    listConversation.mockClear()

    // 替身的"下一页"按钮会派发 current-change(currentPage + 1)
    await wrapper.find('.el-pagination__next-stub').trigger('click')
    await flushPromises()

    expect(listConversation).toHaveBeenCalledTimes(1)
    expect(listConversation).toHaveBeenCalledWith({ pageNum: 2, pageSize: 10 })
  })

  it('分页：空会话时不渲染分页器（total=0）', async () => {
    stubConversations([], 0)
    const wrapper = await mountPage()

    expect(wrapper.find('.el-pagination-stub').exists()).toBe(false)
  })

  it('未读会话显示角标，已读会话不显示', async () => {
    stubConversations([convRow({ unread: 2 }), convRow({ orderNo: 'CT202601010002', unread: 0 })])
    const wrapper = await mountPage()

    const badges = wrapper.findAll('.el-badge-stub').map((b) => b.attributes('data-value'))
    expect(badges).toEqual(['2'])
  })

  it('空会话：展示引导文案（含引导去订单页联系对方）', async () => {
    stubConversations([])
    const wrapper = await mountPage()

    expect(wrapper.text()).toContain('还没有会话')
    expect(wrapper.text()).toContain('我的订单')
  })

  it('未选择会话时消息区显示引导，不请求消息列表', async () => {
    stubConversations()
    const wrapper = await mountPage()

    expect(wrapper.text()).toContain('请选择一个会话')
    expect(listOrderMessage).not.toHaveBeenCalled()
  })

  it('点击会话：请求该订单消息并标记已读', async () => {
    stubConversations()
    stubMessages()
    const wrapper = await mountPage()

    const item = wrapper.find('.msg-conv__item')
    await item.trigger('click')
    await flushPromises()

    expect(listOrderMessage).toHaveBeenCalledWith('CT202601010001')
    // 打开会话即把发给我的未读标记为已读
    expect(readOrderMessage).toHaveBeenCalledWith('CT202601010001')
  })

  it('渲染对方发来的消息内容与昵称', async () => {
    stubConversations()
    stubMessages([msgRow()])
    const wrapper = await mountPage()

    await wrapper.find('.msg-conv__item').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('有的，拍下即可')
    expect(wrapper.text()).toContain('李雨桐')
  })

  it('自己发的消息靠右展示，对方的靠左（靠 fromUserId 与登录态比对）', async () => {
    stubConversations()
    stubMessages([msgRow(), msgRow({ messageId: 2, fromUserId: MY_ID, toUserId: SELLER_ID, content: '我拍下了' })])
    const wrapper = await mountPage()

    await wrapper.find('.msg-conv__item').trigger('click')
    await flushPromises()

    const items = wrapper.findAll('.msg-item')
    expect(items[0].classes()).toContain('msg-item--theirs')
    expect(items[1].classes()).toContain('msg-item--mine')
  })

  it('发送消息：调用接口后清空输入框并刷新会话', async () => {
    stubConversations()
    stubMessages()
    sendMessage.mockResolvedValue({ code: 200 })
    const wrapper = await mountPage()

    await wrapper.find('.msg-conv__item').trigger('click')
    await flushPromises()

    // 通过 el-input 替身的原生事件驱动输入（textarea 用 input 事件）
    const textarea = wrapper.find('textarea, input')
    await textarea.setValue('我明天来取')
    await flushPromises()

    const sendBtn = wrapper.findAll('.el-button-stub').find((b) => b.text().includes('发送'))
    await sendBtn.trigger('click')
    await flushPromises()

    expect(sendMessage).toHaveBeenCalledWith({ orderNo: 'CT202601010001', content: '我明天来取' })
    expect(ElMessage.success).toHaveBeenCalledWith('消息已发送')
    // 消息列表：点会话 1 次 + 发送后刷新 1 次
    expect(listOrderMessage).toHaveBeenCalledTimes(2)
    // 会话列表：挂载 1 + 点会话后刷新 1 + 发送后刷新 1
    expect(listConversation).toHaveBeenCalledTimes(3)
    // 未读总数三次都要重新取：标记已读与发送都会改变未读数
    expect(unreadMessageCount).toHaveBeenCalledTimes(3)
  })

  it('空内容时发送按钮被禁用（不发送空消息）', async () => {
    stubConversations()
    stubMessages()
    const wrapper = await mountPage()

    await wrapper.find('.msg-conv__item').trigger('click')
    await flushPromises()

    const sendBtn = wrapper.findAll('.el-button-stub').find((b) => b.text().includes('发送'))
    expect(sendBtn.attributes('disabled')).toBe('true')
  })

  it('未选择会话时不渲染输入区', async () => {
    stubConversations()
    const wrapper = await mountPage()

    const sendBtn = wrapper.findAll('.el-button-stub').find((b) => b.text().includes('发送'))
    expect(sendBtn).toBeUndefined()
  })

  it('从订单页带 orderNo 进入时自动选中该会话', async () => {
    routeQueryMock.value = { orderNo: 'CT202601010002' }
    stubConversations()
    stubMessages()
    await mountPage()

    expect(listOrderMessage).toHaveBeenCalledWith('CT202601010002')
    expect(readOrderMessage).toHaveBeenCalledWith('CT202601010002')
  })

  it('时间超过 19 位时截断展示', async () => {
    stubConversations([convRow({ lastTime: '2026-10-16T10:00:00.000+0800' })])
    const wrapper = await mountPage()

    expect(wrapper.text()).toContain('2026-10-16 10:00:00')
  })
})
