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
import { listConversation, listOrderMessage, readOrderMessage, sendMessage } from '@/api/campus/message'
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

function stubConversations(rows = [convRow()]) {
  listConversation.mockResolvedValue({ code: 200, data: rows })
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
})

describe('消息沟通页', () => {
  it('挂载后请求会话列表并取当前用户身份', async () => {
    stubConversations()
    await mountPage()

    expect(listConversation).toHaveBeenCalledTimes(1)
    expect(getInfo).toHaveBeenCalledTimes(1)
  })

  it('渲染会话的订单号、最后一条消息与金额', async () => {
    stubConversations()
    const wrapper = await mountPage()

    expect(wrapper.text()).toContain('CT202601010001')
    expect(wrapper.text()).toContain('请问还有货吗')
    expect(wrapper.text()).toContain('60.00')
  })

  it('概览：会话总数与未读总数正确', async () => {
    stubConversations([convRow({ unread: 2 }), convRow({ orderNo: 'CT202601010002', unread: 1 })])
    const wrapper = await mountPage()

    const stats = wrapper.findAll('.campus-stat__value').map((n) => n.text())
    expect(stats[0]).toBe('2') // 会话总数
    expect(stats[1]).toBe('3') // 未读合计
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
    // 会话列表：挂载 1 + 点会话后刷新 1（标记已读后要刷新未读数）+ 发送后刷新 1
    expect(listConversation).toHaveBeenCalledTimes(3)
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
