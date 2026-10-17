// @vitest-environment jsdom

import { describe, it, expect, vi, beforeEach } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

// 组件只从 element-plus 取消息提示，整体替换为替身，避免把整个组件库拖进 jsdom
vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
  ElMessageBox: { confirm: vi.fn(), prompt: vi.fn() }
}))

vi.mock('@/api/campus/order', () => ({
  listOrder: vi.fn(),
  orderStats: vi.fn(),
  getOrder: vi.fn(),
  createOrder: vi.fn(),
  payOrder: vi.fn(),
  cancelOrder: vi.fn(),
  shipOrder: vi.fn(),
  finishOrder: vi.fn(),
  getLogisticsStatus: vi.fn(),
  getLogisticsTrace: vi.fn(),
  syncLogisticsTrace: vi.fn()
}))

vi.mock('@/api/campus/review', () => ({
  addReview: vi.fn()
}))

vi.mock('@/api/campus/payment', () => ({
  getPaymentChannel: vi.fn()
}))

import { ElMessageBox } from 'element-plus'
import {
  listOrder,
  orderStats,
  getOrder,
  payOrder,
  cancelOrder,
  shipOrder,
  finishOrder,
  getLogisticsTrace
} from '@/api/campus/order'
import { addReview } from '@/api/campus/review'
import { getPaymentChannel } from '@/api/campus/payment'
import OrderPage from '@/views/campus/order/index.vue'
import { elementStubs, clickButton, statValues, Pagination } from './support/element-stubs'

const ORDER_NO = 'CT20261016001'

const orderRow = (over = {}) => ({
  orderNo: ORDER_NO,
  status: 0,
  totalAmount: 199.5,
  createTime: '2026-10-16 10:00:00',
  items: [{ productTitle: '二手自行车', quantity: 2, price: 99.75, subtotal: 199.5, image: 'bike.jpg' }],
  ...over
})

const listCall = (index = 0) => listOrder.mock.calls[index][0]
const statsCall = (index = 0) => orderStats.mock.calls[index][0]

function stubApi({
  rows = [orderRow()],
  total = 25,
  stats = [
    { status: 0, count: 0, amount: 0 },
    { status: 2, count: 4, amount: 0 },
    { status: 3, count: 11, amount: 0 }
  ]
} = {}) {
  listOrder.mockResolvedValue({ code: 200, rows, total })
  orderStats.mockResolvedValue({ code: 200, data: stats })
}

async function mountPage() {
  const wrapper = mount(OrderPage, { global: elementStubs() })
  await flushPromises()
  return wrapper
}

describe('校园淘 - 订单页（分页与概览联动）', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    ElMessageBox.confirm.mockResolvedValue('confirm')
    ElMessageBox.prompt.mockResolvedValue({ value: '' })
    getOrder.mockResolvedValue({ code: 200, data: orderRow() })
    getLogisticsTrace.mockResolvedValue({ code: 200, data: { providerEnabled: true, traces: [] } })
    getPaymentChannel.mockResolvedValue({
      code: 200,
      data: { mode: 'sandbox', realMoney: false, notice: '沙箱演示环境：支付与退款仅记账于站内余额，不产生真实扣款' }
    })
    stubApi()
  })

  it('挂载后同时拉取列表与概览，且概览不携带分页参数', async () => {
    await mountPage()

    expect(listOrder).toHaveBeenCalledTimes(1)
    expect(listCall()).toMatchObject({ pageNum: 1, pageSize: 10 })

    // 分页参数只对列表有意义，聚合口径必须与列表筛选一致但不含分页
    expect(orderStats).toHaveBeenCalledTimes(1)
    expect(statsCall()).toEqual({ status: undefined, orderNo: undefined })
  })

  it('概览数字取自 /stats 聚合结果，与当前页数据无关', async () => {
    // 当前页恰好两行都是「已完成」：若概览按当前页推导，待付款/待收货都会是 0
    stubApi({ rows: [orderRow({ status: 3 }), orderRow({ orderNo: 'CT-2', status: 3 })], total: 25 })
    const wrapper = await mountPage()

    // 订单总数来自列表信封 total，其余三张卡片来自聚合
    expect(statValues(wrapper)).toEqual(['25', '0', '4', '11'])
  })

  it('切换状态筛选：页码归 1 且列表与概览以同一条件刷新', async () => {
    const wrapper = await mountPage()

    const pagination = wrapper.findComponent(Pagination)
    pagination.vm.$emit('update:page', 3)
    pagination.vm.$emit('pagination')
    await flushPromises()
    expect(listCall(1)).toMatchObject({ pageNum: 3 })

    const chip = wrapper.findAll('.status-chip').find((node) => node.text() === '已发货')
    await chip.trigger('click')
    await flushPromises()

    expect(listCall(2)).toMatchObject({ pageNum: 1, status: 2 })
    expect(statsCall(1)).toEqual({ status: 2, orderNo: undefined })
    expect(listOrder).toHaveBeenCalledTimes(3)
  })

  it('分页只翻列表：列表条数取自分页信封 rows，总数取自 total', async () => {
    stubApi({ rows: [orderRow(), orderRow({ orderNo: 'CT-2', status: 2 })], total: 25 })
    const wrapper = await mountPage()

    expect(wrapper.findAll('.order-cell')).toHaveLength(2)
    expect(statValues(wrapper)[0]).toBe('25')
  })

  it('单元格渲染：件数合计、多商品标题、金额两位小数、未知状态兜底', async () => {
    stubApi({
      rows: [
        orderRow({
          orderNo: 'A',
          status: 0,
          totalAmount: 199.5,
          items: [
            { productTitle: '二手自行车', quantity: 2, image: 'bike.jpg' },
            { productTitle: '台灯', quantity: 1 }
          ]
        }),
        orderRow({ orderNo: 'B', status: 9, totalAmount: 0, items: [] })
      ]
    })
    const wrapper = await mountPage()

    expect(wrapper.text()).toContain('二手自行车 等 2 种商品')
    expect(wrapper.text()).toContain('3 件')
    expect(wrapper.text()).toContain('¥199.50')
    expect(wrapper.text()).toContain('（无商品明细）')
    expect(wrapper.text()).toContain('未知')
  })

  it('详情：按订单号拉取详情并打开弹窗', async () => {
    const wrapper = await mountPage()

    await clickButton(wrapper, '详情')
    await flushPromises()

    expect(getOrder).toHaveBeenCalledWith(ORDER_NO)
    expect(wrapper.find('.el-dialog-stub').exists()).toBe(true)
    expect(wrapper.find('.detail-order-no').text()).toBe(ORDER_NO)
  })

  it('支付：确认后调用支付接口并同时刷新列表与概览', async () => {
    payOrder.mockResolvedValue({ code: 200 })
    const wrapper = await mountPage()

    await clickButton(wrapper, '支付')
    await flushPromises()

    expect(ElMessageBox.confirm).toHaveBeenCalled()
    expect(payOrder).toHaveBeenCalledWith(ORDER_NO)
    expect(listOrder).toHaveBeenCalledTimes(2)
    expect(orderStats).toHaveBeenCalledTimes(2)
  })

  it('取消：确认后调用取消接口并刷新', async () => {
    cancelOrder.mockResolvedValue({ code: 200 })
    const wrapper = await mountPage()

    await clickButton(wrapper, '取消')
    await flushPromises()

    expect(cancelOrder).toHaveBeenCalledWith(ORDER_NO)
    expect(listOrder).toHaveBeenCalledTimes(2)
  })

  it('发货与确认收货：按订单状态出现对应操作并调用接口', async () => {
    shipOrder.mockResolvedValue({ code: 200 })
    finishOrder.mockResolvedValue({ code: 200 })
    stubApi({ rows: [orderRow({ orderNo: 'S1', status: 1 }), orderRow({ orderNo: 'S2', status: 2 })] })
    const wrapper = await mountPage()

    await clickButton(wrapper, '发货')
    await flushPromises()
    expect(shipOrder).toHaveBeenCalledWith('S1', '')

    await clickButton(wrapper, '确认收货')
    await flushPromises()
    expect(finishOrder).toHaveBeenCalledWith('S2')
  })

  it('列表请求失败时不抛出未处理异常，加载态归位', async () => {
    listOrder.mockRejectedValue(new Error('network down'))
    const wrapper = mount(OrderPage, { global: elementStubs() })
    await flushPromises()

    expect(wrapper.vm.loading).toBe(false)
    expect(listOrder).toHaveBeenCalledTimes(1)
  })

  it('概览请求失败时不产生未处理 rejection，且不残留上一次筛选的统计', async () => {
    // 回归：getStats 原先只有 .then() 没有 .catch()，概览失败会产生未处理的 Promise rejection
    stubApi({ rows: [orderRow()], total: 25 })
    const wrapper = await mountPage()
    expect(statValues(wrapper)).toEqual(['25', '0', '4', '11'])

    orderStats.mockRejectedValue(new Error('stats down'))
    const chip = wrapper.findAll('.status-chip').find((node) => node.text() === '已付款')
    await chip.trigger('click')
    await flushPromises()

    // 列表仍然可用，概览清空而不是继续展示旧筛选下的数字
    expect(wrapper.vm.loading).toBe(false)
    expect(statValues(wrapper)).toEqual(['25', '0', '0', '0'])
  })

  it('已完成订单可打开评价弹窗：按订单明细逐件带入并默认 5 分', async () => {
    getOrder.mockResolvedValue({
      code: 200,
      data: {
        id: 88,
        orderNo: ORDER_NO,
        items: [
          { productId: 11, productTitle: '二手自行车' },
          { productId: 12, productTitle: '考研英语真题' }
        ]
      }
    })
    stubApi({ rows: [orderRow({ status: 3 })] })
    const wrapper = await mountPage()

    await clickButton(wrapper, '评价')
    await flushPromises()

    expect(getOrder).toHaveBeenCalledWith(ORDER_NO)
    const rows = wrapper.findAll('.review-row')
    expect(rows).toHaveLength(2)
    expect(rows[0].text()).toContain('二手自行车')
    expect(wrapper.findAll('.el-rate-stub').map((n) => n.attributes('data-rate'))).toEqual(['5', '5'])
  })

  it('提交评价：按商品逐件调用评价接口，全部成功时提示成功并关闭弹窗', async () => {
    addReview.mockResolvedValue({ code: 200 })
    getOrder.mockResolvedValue({
      code: 200,
      data: { id: 88, orderNo: ORDER_NO, items: [{ productId: 11, productTitle: '二手自行车' }] }
    })
    stubApi({ rows: [orderRow({ status: 3 })] })
    const wrapper = await mountPage()

    await clickButton(wrapper, '评价')
    await flushPromises()

    wrapper.vm.reviewForm.items[0].content = '成色很好'
    await clickButton(wrapper, '提交评价')
    await flushPromises()

    expect(addReview).toHaveBeenCalledWith({
      orderId: 88,
      productId: 11,
      rating: 5,
      content: '成色很好'
    })
    expect(wrapper.vm.reviewOpen).toBe(false)
  })

  it('沙箱支付：页面显式提示「不产生真实扣款」，文案取自服务端', async () => {
    const wrapper = await mountPage()

    const alert = wrapper.find('.el-alert-stub')
    expect(alert.exists()).toBe(true)
    // 配色表达风险等级：沙箱是 info，不是 warning/error
    expect(alert.attributes('data-type')).toBe('info')
    expect(alert.text()).toContain('不产生真实扣款')
  })

  it('渠道不可用：提示条升级为 error 配色，避免用户以为能正常支付', async () => {
    getPaymentChannel.mockResolvedValue({
      code: 200,
      data: {
        mode: 'unavailable',
        realMoney: false,
        notice: '当前支付渠道不可用（未注册或商户参数未配置齐），支付与退款将无法完成'
      }
    })
    const wrapper = await mountPage()

    const alert = wrapper.find('.el-alert-stub')
    expect(alert.attributes('data-type')).toBe('error')
    expect(alert.text()).toContain('不可用')
  })

  it('负向：资金语义接口失败时不显示提示条，订单列表照常渲染（旁路不阻断主流程）', async () => {
    getPaymentChannel.mockRejectedValue(new Error('network'))
    const wrapper = await mountPage()

    expect(wrapper.find('.el-alert-stub').exists()).toBe(false)
    expect(listOrder).toHaveBeenCalledTimes(1)
    expect(wrapper.text()).toContain('二手自行车')
  })
})
