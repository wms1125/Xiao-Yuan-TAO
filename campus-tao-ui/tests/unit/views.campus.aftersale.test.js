// @vitest-environment jsdom

import { describe, it, expect, vi, beforeEach } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
  ElMessageBox: { confirm: vi.fn(), prompt: vi.fn() }
}))

vi.mock('@/api/campus/aftersale', () => ({
  listAftersale: vi.fn(),
  aftersaleStats: vi.fn(),
  applyAftersale: vi.fn(),
  auditAftersale: vi.fn()
}))

import { listAftersale, aftersaleStats, applyAftersale, auditAftersale } from '@/api/campus/aftersale'
import AftersalePage from '@/views/campus/aftersale/index.vue'
import {
  elementStubs,
  clickButton,
  statValues,
  Pagination,
  ElInput,
  ElForm,
  RightToolbar
} from './support/element-stubs'

const ORDER_NO = 'CT20261016001'
const AFTERSALE_NO = 'AS20261016001'

const aftersaleRow = (over = {}) => ({
  aftersaleNo: AFTERSALE_NO,
  orderNo: ORDER_NO,
  type: 1,
  refundAmount: 199.5,
  status: 0,
  reason: '商品与描述不符',
  userName: 'student1',
  createTime: '2026-10-16 10:00:00',
  handler: '',
  ...over
})

const listCall = (index = 0) => listAftersale.mock.calls[index][0]
const statsCall = (index = 0) => aftersaleStats.mock.calls[index][0]

function stubApi({
  rows = [aftersaleRow()],
  total = 5,
  stats = [
    { status: 0, count: 1, amount: 950 },
    { status: 2, count: 1, amount: 1650 },
    { status: 3, count: 3, amount: 569 }
  ]
} = {}) {
  listAftersale.mockResolvedValue({ code: 200, rows, total })
  aftersaleStats.mockResolvedValue({ code: 200, data: stats })
}

async function mountPage() {
  const wrapper = mount(AftersalePage, { global: elementStubs() })
  await flushPromises()
  return wrapper
}

describe('校园淘 - 售后页（分页与概览联动）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    stubApi()
  })

  it('挂载后同时拉取列表与概览，且概览不携带分页参数', async () => {
    await mountPage()

    expect(listCall()).toMatchObject({ pageNum: 1, pageSize: 10 })
    expect(statsCall()).toEqual({ orderNo: undefined, status: undefined })
  })

  it('概览数字与退款金额合计取自 /stats，与当前页数据无关', async () => {
    // 当前页只有 1 条「待审核」；若概览按当前页推导，退款金额合计只会是 199.50
    const wrapper = await mountPage()

    expect(statValues(wrapper)).toEqual(['5', '1', '¥3169.00'])
  })

  it('按订单号搜索：页码归 1 且列表与概览以同一条件刷新', async () => {
    const wrapper = await mountPage()

    const pagination = wrapper.findComponent(Pagination)
    pagination.vm.$emit('update:page', 2)
    pagination.vm.$emit('pagination')
    await flushPromises()
    expect(listCall(1)).toMatchObject({ pageNum: 2 })

    const queryInput = wrapper.findAllComponents(ElInput)[0]
    queryInput.vm.$emit('update:modelValue', ORDER_NO)
    await clickButton(wrapper, '搜索')
    await flushPromises()

    expect(listCall(2)).toMatchObject({ pageNum: 1, orderNo: ORDER_NO })
    expect(statsCall(1)).toEqual({ orderNo: ORDER_NO, status: undefined })
  })

  it('重置：清空表单条件、页码归 1 并刷新列表与概览', async () => {
    const wrapper = await mountPage()

    const queryForm = wrapper.findComponent(ElForm)
    await clickButton(wrapper, '重置')
    await flushPromises()

    expect(queryForm.vm.resetFields).toHaveBeenCalled()
    expect(listCall(1)).toMatchObject({ pageNum: 1 })
    expect(aftersaleStats).toHaveBeenCalledTimes(2)
  })

  it('分页与工具栏刷新只重拉列表，概览不重复请求', async () => {
    const wrapper = await mountPage()

    const pagination = wrapper.findComponent(Pagination)
    pagination.vm.$emit('update:page', 3)
    pagination.vm.$emit('pagination')
    await flushPromises()
    expect(listCall(1)).toMatchObject({ pageNum: 3 })
    expect(aftersaleStats).toHaveBeenCalledTimes(1)

    wrapper.findComponent(RightToolbar).vm.$emit('queryTable')
    await flushPromises()
    expect(listAftersale).toHaveBeenCalledTimes(3)
    expect(aftersaleStats).toHaveBeenCalledTimes(1)
  })

  it('审核：弹窗回显单号与金额，提交后按表单调用审核接口并刷新', async () => {
    auditAftersale.mockResolvedValue({ code: 200 })
    const wrapper = await mountPage()

    await clickButton(wrapper, '审核')
    await flushPromises()
    expect(wrapper.find('.audit-summary__no').text()).toBe(AFTERSALE_NO)
    expect(wrapper.find('.audit-summary').text()).toContain('¥199.50')

    await clickButton(wrapper, '提交审核')
    await flushPromises()

    expect(auditAftersale).toHaveBeenCalledWith({ aftersaleNo: AFTERSALE_NO, pass: true, remark: '' })
    expect(listAftersale).toHaveBeenCalledTimes(2)
    expect(aftersaleStats).toHaveBeenCalledTimes(2)
    expect(wrapper.find('.el-dialog-stub').exists()).toBe(false)
  })

  it('申请售后：提交后携带表单数据调用申请接口并刷新', async () => {
    applyAftersale.mockResolvedValue({ code: 200, data: 'AS20261016009' })
    const wrapper = await mountPage()

    await clickButton(wrapper, '申请售后')
    await flushPromises()

    const inputs = wrapper.findAllComponents(ElInput)
    inputs[1].vm.$emit('update:modelValue', ORDER_NO)
    inputs[2].vm.$emit('update:modelValue', '商品有质量问题')
    await clickButton(wrapper, '提交申请')
    await flushPromises()

    expect(applyAftersale).toHaveBeenCalledWith({ orderNo: ORDER_NO, type: 1, reason: '商品有质量问题' })
    expect(listAftersale).toHaveBeenCalledTimes(2)
  })

  it('单元格渲染：类型/状态文案、金额格式与已处理兜底', async () => {
    stubApi({
      rows: [
        aftersaleRow({ aftersaleNo: 'A', type: 1, status: 0, refundAmount: 199.5 }),
        aftersaleRow({ aftersaleNo: 'B', type: 2, status: 2, refundAmount: 0, handler: '' })
      ]
    })
    const wrapper = await mountPage()

    expect(wrapper.text()).toContain('仅退款')
    expect(wrapper.text()).toContain('退货退款')
    expect(wrapper.text()).toContain('待审核')
    expect(wrapper.text()).toContain('已驳回')
    expect(wrapper.text()).toContain('¥199.50')
    expect(wrapper.text()).toContain('¥0.00')
    // 非待审核行不出现「审核」按钮，改为展示处理人（缺省时兜底为「已处理」）
    expect(wrapper.findAll('.el-button-stub').filter((node) => node.text() === '审核')).toHaveLength(1)
    expect(wrapper.text()).toContain('已处理')
  })
})
