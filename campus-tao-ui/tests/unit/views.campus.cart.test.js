// @vitest-environment jsdom

import { describe, it, expect, vi, beforeEach } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
  ElMessageBox: { confirm: vi.fn(), prompt: vi.fn() }
}))

vi.mock('@/api/campus/cart', () => ({
  listCart: vi.fn(),
  updateCartQty: vi.fn(),
  delCart: vi.fn()
}))

vi.mock('@/api/campus/product', () => ({
  getProduct: vi.fn()
}))

vi.mock('@/api/campus/order', () => ({
  createOrder: vi.fn()
}))

import { ElMessage, ElMessageBox } from 'element-plus'
import { listCart, updateCartQty, delCart } from '@/api/campus/cart'
import { getProduct } from '@/api/campus/product'
import { createOrder } from '@/api/campus/order'
import CartPage from '@/views/campus/cart/index.vue'
import { elementStubs } from './support/element-stubs'

const { pushMock } = vi.hoisted(() => ({ pushMock: vi.fn() }))
vi.mock('vue-router', () => ({
  useRoute: () => ({ query: {} }),
  useRouter: () => ({ push: pushMock })
}))

/** 购物车表原始行：只有 id/productId/quantity/checked，商品信息靠 enrich 补齐 */
const cartRow = (over = {}) => ({ id: 1, productId: 100, quantity: 2, checked: 0, ...over })

const productInfo = (over = {}) => ({
  id: 100,
  title: '九成新 iPad',
  price: '1200.00',
  images: 'a.jpg,b.jpg',
  stock: 5,
  status: 1,
  ...over
})

function stubCart(rows = [cartRow()], info = productInfo()) {
  listCart.mockResolvedValue({ code: 200, data: rows })
  getProduct.mockResolvedValue({ code: 200, data: info })
}

async function mountPage() {
  const wrapper = mount(CartPage, { global: elementStubs() })
  await flushPromises()
  return wrapper
}

beforeEach(() => {
  vi.clearAllMocks()
})

describe('购物车页', () => {
  it('挂载后加载购物车，并逐条调用商品接口补齐标题/价格/库存', async () => {
    stubCart()
    await mountPage()

    expect(listCart).toHaveBeenCalledTimes(1)
    expect(getProduct).toHaveBeenCalledWith(100)
  })

  it('渲染补齐后的商品标题、单价与小计（单价 × 数量）', async () => {
    stubCart()
    const wrapper = await mountPage()

    expect(wrapper.text()).toContain('九成新 iPad')
    expect(wrapper.text()).toContain('1200.00')
    // 小计 = 1200.00 × 2
    expect(wrapper.text()).toContain('2400.00')
  })

  it('标题缺失时回退显示「商品 #ID」而非空白（商品接口异常的降级路径）', async () => {
    listCart.mockResolvedValue({ code: 200, data: [cartRow()] })
    getProduct.mockRejectedValue(new Error('network'))
    const wrapper = await mountPage()

    expect(wrapper.text()).toContain('商品 #100')
    expect(getProduct).toHaveBeenCalledTimes(1)
  })

  it('checked=1 的记录加载后自动选中，概览显示已选件数与待结算金额', async () => {
    stubCart([
      cartRow({ id: 1, productId: 100, quantity: 2, checked: 1 }),
      cartRow({ id: 2, productId: 101, quantity: 1, checked: 0 })
    ])
    getProduct.mockImplementation((pid) =>
      Promise.resolve({
        code: 200,
        data: pid === 100 ? productInfo() : productInfo({ id: 101, title: '旧书', price: '30.50' })
      })
    )
    const wrapper = await mountPage()

    // 只有 id=1 被自动选中：件数 2，金额 1200×2
    const stats = wrapper.findAll('.campus-stat__value').map((n) => n.text())
    expect(stats[0]).toBe('2') // 商品种类
    expect(stats[1]).toBe('2') // 已选件数
    expect(stats[2]).toBe('¥2400.00') // 待结算金额（模板自带 ¥ 前缀）
  })

  it('数量为 0 的脏数据在数量变更时收敛为 1 再提交', async () => {
    stubCart([cartRow({ id: 1, quantity: 0 })])
    updateCartQty.mockResolvedValue({ code: 200 })
    const wrapper = await mountPage()

    const numberInput = wrapper.find('.el-input-number-stub')
    await numberInput.trigger('change')
    await flushPromises()

    expect(updateCartQty).toHaveBeenCalledWith({ id: 1, quantity: 1 })
  })

  it('正常数量变更按现值提交（0 < 数量 < 库存）', async () => {
    stubCart([cartRow({ id: 1, quantity: 3 })])
    updateCartQty.mockResolvedValue({ code: 200 })
    const wrapper = await mountPage()

    const numberInput = wrapper.find('.el-input-number-stub')
    await numberInput.trigger('change')
    await flushPromises()

    expect(updateCartQty).toHaveBeenCalledWith({ id: 1, quantity: 3 })
  })

  it('移除单条：确认后调用 delCart 并刷新列表', async () => {
    stubCart()
    ElMessageBox.confirm.mockResolvedValueOnce()
    delCart.mockResolvedValue({ code: 200 })
    const wrapper = await mountPage()

    const btn = wrapper.findAll('.el-button-stub').find((b) => b.text().includes('移除'))
    await btn.trigger('click')
    await flushPromises()

    expect(ElMessageBox.confirm).toHaveBeenCalledTimes(1)
    expect(delCart).toHaveBeenCalledWith(1)
    expect(ElMessage.success).toHaveBeenCalledWith('已移除')
    expect(listCart).toHaveBeenCalledTimes(2) // 挂载 1 次 + 移除后刷新 1 次
  })

  it('移除单条：用户点取消时不发请求', async () => {
    stubCart()
    ElMessageBox.confirm.mockRejectedValueOnce(new Error('cancel'))
    const wrapper = await mountPage()

    const btn = wrapper.findAll('.el-button-stub').find((b) => b.text().includes('移除'))
    await btn.trigger('click')
    await flushPromises()

    expect(ElMessageBox.confirm).toHaveBeenCalledTimes(1)
    expect(delCart).not.toHaveBeenCalled()
  })

  it('未选中任何行时点「删除选中」：无确认弹窗也无请求（防呆闸门）', async () => {
    stubCart()
    const wrapper = await mountPage()

    const btn = wrapper.findAll('.el-button-stub').find((b) => b.text().includes('删除选中'))
    await btn.trigger('click')
    await flushPromises()

    expect(ElMessageBox.confirm).not.toHaveBeenCalled()
    expect(delCart).not.toHaveBeenCalled()
  })

  it('选中两条后删除选中：ids 以逗号拼接提交并清空选中', async () => {
    stubCart([
      cartRow({ id: 1, productId: 100, quantity: 2, checked: 1 }),
      cartRow({ id: 2, productId: 101, quantity: 1, checked: 1 })
    ])
    getProduct.mockResolvedValue({ code: 200, data: productInfo() })
    ElMessageBox.confirm.mockResolvedValueOnce()
    delCart.mockResolvedValue({ code: 200 })
    const wrapper = await mountPage()

    const btn = wrapper.findAll('.el-button-stub').find((b) => b.text().includes('删除选中'))
    await btn.trigger('click')
    await flushPromises()

    expect(delCart).toHaveBeenCalledWith('1,2')
    expect(ElMessage.success).toHaveBeenCalledWith('删除成功')
  })

  it('去结算：打开确认弹窗并重置上一次的表单残留', async () => {
    stubCart([cartRow({ checked: 1 })])
    const wrapper = await mountPage()

    const checkoutBtn = wrapper.findAll('.el-button-stub').find((b) => b.text().includes('去结算'))
    await checkoutBtn.trigger('click')

    expect(wrapper.find('.el-dialog-stub__title').text()).toBe('确认订单信息')
    // 上一次填过的值不应残留：打开时四个输入框都是空的
    const inputs = wrapper.findAll('.el-input-stub')
    expect(inputs.every((i) => i.element.value === '')).toBe(true)
  })

  it('提交订单：携带选中行的 cartIds 与收货信息，成功后关弹窗并跳订单页', async () => {
    stubCart([cartRow({ checked: 1 })])
    createOrder.mockResolvedValue({ code: 200, data: 'ORD20261017001' })
    const wrapper = await mountPage()

    const checkoutBtn = wrapper.findAll('.el-button-stub').find((b) => b.text().includes('去结算'))
    await checkoutBtn.trigger('click')

    const inputs = wrapper.findAll('.el-input-stub')
    await inputs[0].setValue('张三')
    await inputs[1].setValue('13800138000')
    await inputs[2].setValue('3 号宿舍楼 502 室')

    const submitBtn = wrapper.findAll('.el-button-stub').find((b) => b.text().includes('提交订单'))
    await submitBtn.trigger('click')
    await flushPromises()

    expect(createOrder).toHaveBeenCalledTimes(1)
    const payload = createOrder.mock.calls[0][0]
    expect(payload.cartIds).toEqual([1])
    expect(payload.receiverName).toBe('张三')
    expect(payload.receiverPhone).toBe('13800138000')
    expect(payload.address).toBe('3 号宿舍楼 502 室')
    expect(ElMessage.success).toHaveBeenCalledWith('下单成功，订单号 ORD20261017001')
    expect(pushMock).toHaveBeenCalledWith('/campus/order')
    // 成功后弹窗关闭
    expect(wrapper.find('.el-dialog-stub').exists()).toBe(false)
  })

  it('加载失败：结束 loading 并展示空态引导，不白屏', async () => {
    listCart.mockRejectedValue(new Error('network'))
    const wrapper = await mountPage()

    expect(wrapper.text()).toContain('购物车还是空的')
  })
})
