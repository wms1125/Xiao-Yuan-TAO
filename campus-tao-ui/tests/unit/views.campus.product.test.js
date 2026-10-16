// @vitest-environment jsdom

import { describe, it, expect, vi, beforeEach } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
  ElMessageBox: { confirm: vi.fn(), prompt: vi.fn() }
}))

vi.mock('@/api/campus/product', () => ({
  listProduct: vi.fn(),
  getProduct: vi.fn(),
  addProduct: vi.fn(),
  updateProduct: vi.fn(),
  delProduct: vi.fn(),
  changeProductStatus: vi.fn()
}))

vi.mock('@/api/campus/category', () => ({
  listCategory: vi.fn()
}))

vi.mock('@/api/campus/cart', () => ({
  addCart: vi.fn()
}))

import { ElMessage, ElMessageBox } from 'element-plus'
import {
  listProduct,
  getProduct,
  addProduct,
  updateProduct,
  delProduct,
  changeProductStatus
} from '@/api/campus/product'
import { listCategory } from '@/api/campus/category'
import { addCart } from '@/api/campus/cart'
import ProductPage from '@/views/campus/product/index.vue'
import { elementStubs } from './support/element-stubs'

const productRow = (over = {}) => ({
  id: 100,
  categoryId: 1,
  categoryName: '数码',
  title: '九成新 iPad',
  price: '1200.00',
  stock: 3,
  status: 1,
  conditionLevel: 2,
  images: 'a.jpg,b.jpg',
  createTime: '2026-10-16 10:00:00',
  ...over
})

function stubList(rows = [productRow()], total = rows.length) {
  listProduct.mockResolvedValue({ code: 200, rows, total })
  listCategory.mockResolvedValue({ code: 200, rows: [{ id: 1, name: '数码' }] })
}

async function mountPage() {
  const wrapper = mount(ProductPage, { global: elementStubs() })
  await flushPromises()
  return wrapper
}

const btnByText = (wrapper, text) => wrapper.findAll('.el-button-stub').find((b) => b.text().includes(text))

/** 精确匹配：行内「删除」不能命中表头上方的「批量删除」（后者在文档序里更靠前） */
const btnExact = (wrapper, text) => wrapper.findAll('.el-button-stub').find((b) => b.text().trim() === text)

/** 弹窗内第一个输入框 = 商品标题（页面上方搜索框也是 el-input，必须限定作用域） */
const dialogTitleInput = (wrapper) => wrapper.find('.el-dialog-stub .el-input-stub')

beforeEach(() => {
  vi.clearAllMocks()
})

describe('商品管理页', () => {
  it('挂载后加载商品列表（默认分页）与分类选项', async () => {
    stubList()
    await mountPage()

    expect(listProduct).toHaveBeenCalledWith(expect.objectContaining({ pageNum: 1, pageSize: 10 }))
    expect(listCategory).toHaveBeenCalledWith({ pageSize: 999 })
  })

  it('渲染商品标题、编号库存、售价与成色文本', async () => {
    stubList()
    const wrapper = await mountPage()

    expect(wrapper.text()).toContain('九成新 iPad')
    expect(wrapper.text()).toContain('编号 100 · 库存 3')
    expect(wrapper.text()).toContain('1200.00')
    expect(wrapper.text()).toContain('几乎全新') // conditionLevel=2
  })

  it('成色为未知档位时显示「未知」而不是 undefined', async () => {
    stubList([productRow({ conditionLevel: 99 })])
    const wrapper = await mountPage()

    expect(wrapper.text()).toContain('未知')
  })

  it('搜索：点击搜索按钮重置到第 1 页并携带条件请求', async () => {
    stubList()
    const wrapper = await mountPage()

    const titleInput = wrapper.find('.el-input-stub')
    await titleInput.setValue('iPad')

    await btnByText(wrapper, '搜索').trigger('click')
    await flushPromises()

    expect(listProduct).toHaveBeenLastCalledWith(expect.objectContaining({ pageNum: 1, title: 'iPad' }))
  })

  it('发布商品：打开弹窗、标题为「发布商品」、表单为默认值', async () => {
    stubList()
    const wrapper = await mountPage()

    await btnByText(wrapper, '发布商品').trigger('click')

    expect(wrapper.find('.el-dialog-stub__title').text()).toBe('发布商品')
    expect(dialogTitleInput(wrapper).element.value).toBe('')
  })

  it('修改商品：拉取详情回填表单，弹窗标题为「修改商品」', async () => {
    stubList()
    getProduct.mockResolvedValue({ code: 200, data: productRow({ title: '九成新 iPad 2022' }) })
    const wrapper = await mountPage()

    await btnByText(wrapper, '修改').trigger('click')
    await flushPromises()

    expect(getProduct).toHaveBeenCalledWith(100)
    expect(wrapper.find('.el-dialog-stub__title').text()).toBe('修改商品')
    expect(dialogTitleInput(wrapper).element.value).toBe('九成新 iPad 2022')
  })

  it('提交新增：不携带 sellerId/sellerName（卖家身份由服务端写入）', async () => {
    stubList()
    addProduct.mockResolvedValue({ code: 200 })
    const wrapper = await mountPage()

    await btnByText(wrapper, '发布商品').trigger('click')
    await dialogTitleInput(wrapper).setValue('全新台灯')

    await btnByText(wrapper, '确 定').trigger('click')
    await flushPromises()

    expect(addProduct).toHaveBeenCalledTimes(1)
    const payload = addProduct.mock.calls[0][0]
    expect(payload.title).toBe('全新台灯')
    expect(payload.conditionLevel).toBe(3) // 默认成色
    expect(payload).not.toHaveProperty('sellerId')
    expect(payload).not.toHaveProperty('sellerName')
    expect(ElMessage.success).toHaveBeenCalledWith('发布成功')
    expect(wrapper.find('.el-dialog-stub').exists()).toBe(false)
    expect(listProduct).toHaveBeenCalledTimes(2) // 挂载 + 保存后刷新
  })

  it('提交修改：带 id 走更新接口，成功后关弹窗并刷新', async () => {
    stubList()
    getProduct.mockResolvedValue({ code: 200, data: productRow() })
    updateProduct.mockResolvedValue({ code: 200 })
    const wrapper = await mountPage()

    await btnByText(wrapper, '修改').trigger('click')
    await flushPromises()
    await btnByText(wrapper, '确 定').trigger('click')
    await flushPromises()

    expect(updateProduct).toHaveBeenCalledTimes(1)
    expect(updateProduct.mock.calls[0][0]).toMatchObject({ id: 100 })
    expect(addProduct).not.toHaveBeenCalled()
    expect(ElMessage.success).toHaveBeenCalledWith('修改成功')
  })

  it('取消编辑：关闭弹窗（不触任何保存接口）', async () => {
    stubList()
    const wrapper = await mountPage()

    await btnByText(wrapper, '发布商品').trigger('click')
    await btnByText(wrapper, '取 消').trigger('click')

    expect(wrapper.find('.el-dialog-stub').exists()).toBe(false)
    expect(addProduct).not.toHaveBeenCalled()
    expect(updateProduct).not.toHaveBeenCalled()
  })

  it('上下架开关：点按翻转状态并调接口，下架商品上架成功提示「已上架」', async () => {
    stubList([productRow({ status: 0 })])
    changeProductStatus.mockResolvedValue({ code: 200 })
    const wrapper = await mountPage()

    // 与真实开关一致：点按先翻转值（0→1），再以新值触发 change
    await wrapper.find('.el-switch-stub').trigger('click')
    await flushPromises()

    expect(changeProductStatus).toHaveBeenCalledWith(100, 1)
    expect(ElMessage.success).toHaveBeenCalledWith('已上架')
    expect(wrapper.find('.el-switch-stub').attributes('data-value')).toBe('1')
  })

  it('上下架失败：开关状态回翻（乐观更新被撤销）', async () => {
    stubList([productRow({ status: 1 })])
    changeProductStatus.mockRejectedValue(new Error('network'))
    const wrapper = await mountPage()

    // 点按后状态先乐观翻到 0，接口失败必须回翻为 1
    await wrapper.find('.el-switch-stub').trigger('click')
    await flushPromises()

    expect(changeProductStatus).toHaveBeenCalledWith(100, 0)
    expect(wrapper.find('.el-switch-stub').attributes('data-value')).toBe('1')
    expect(ElMessage.success).not.toHaveBeenCalled()
  })

  it('删除：确认后按行 id 调用删除接口并刷新', async () => {
    stubList()
    ElMessageBox.confirm.mockResolvedValueOnce()
    delProduct.mockResolvedValue({ code: 200 })
    const wrapper = await mountPage()

    await btnExact(wrapper, '删除').trigger('click')
    await flushPromises()

    expect(ElMessageBox.confirm).toHaveBeenCalledTimes(1)
    expect(delProduct).toHaveBeenCalledWith(100)
    expect(ElMessage.success).toHaveBeenCalledWith('删除成功')
    expect(listProduct).toHaveBeenCalledTimes(2)
  })

  it('删除：用户点取消时不发请求', async () => {
    stubList()
    ElMessageBox.confirm.mockRejectedValueOnce(new Error('cancel'))
    const wrapper = await mountPage()

    await btnExact(wrapper, '删除').trigger('click')
    await flushPromises()

    expect(delProduct).not.toHaveBeenCalled()
  })

  it('加购：数量在库存内则提交加购并提示', async () => {
    stubList()
    ElMessageBox.prompt.mockResolvedValueOnce({ value: '2' })
    addCart.mockResolvedValue({ code: 200 })
    const wrapper = await mountPage()

    await btnByText(wrapper, '加购').trigger('click')
    await flushPromises()

    expect(addCart).toHaveBeenCalledWith({ productId: 100, quantity: 2 })
    expect(ElMessage.success).toHaveBeenCalledWith('已加入购物车')
  })

  it('加购：数量超过库存时提示库存不足且不提交', async () => {
    stubList()
    ElMessageBox.prompt.mockResolvedValueOnce({ value: '5' }) // stock=3
    const wrapper = await mountPage()

    await btnByText(wrapper, '加购').trigger('click')
    await flushPromises()

    expect(ElMessage.warning).toHaveBeenCalledWith('库存不足')
    expect(addCart).not.toHaveBeenCalled()
  })

  it('加购：输入非数字时按 1 件提交', async () => {
    stubList()
    ElMessageBox.prompt.mockResolvedValueOnce({ value: 'abc' })
    addCart.mockResolvedValue({ code: 200 })
    const wrapper = await mountPage()

    await btnByText(wrapper, '加购').trigger('click')
    await flushPromises()

    expect(addCart).toHaveBeenCalledWith({ productId: 100, quantity: 1 })
  })

  it('加购：用户取消弹窗时不发请求', async () => {
    stubList()
    ElMessageBox.prompt.mockRejectedValueOnce(new Error('cancel'))
    const wrapper = await mountPage()

    await btnByText(wrapper, '加购').trigger('click')
    await flushPromises()

    expect(addCart).not.toHaveBeenCalled()
  })
})
