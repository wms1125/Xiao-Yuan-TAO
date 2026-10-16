// @vitest-environment jsdom

import { describe, it, expect, vi, beforeEach } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() }
}))

vi.mock('@/api/campus/product', () => ({
  listProduct: vi.fn(),
  getProduct: vi.fn()
}))

vi.mock('@/api/campus/category', () => ({
  listCategory: vi.fn()
}))

vi.mock('@/api/campus/cart', () => ({
  addCart: vi.fn()
}))

vi.mock('@/api/campus/review', () => ({
  listReviewByProduct: vi.fn()
}))

vi.mock('@/store/modules/user', () => ({
  default: () => ({ id: '2' })
}))

const { pushMock } = vi.hoisted(() => ({ pushMock: vi.fn() }))
vi.mock('vue-router', () => ({
  useRoute: () => ({ query: { productId: '7' } }),
  useRouter: () => ({ push: pushMock })
}))

import { ElMessage } from 'element-plus'
import { listProduct, getProduct } from '@/api/campus/product'
import { listCategory } from '@/api/campus/category'
import { addCart } from '@/api/campus/cart'
import { listReviewByProduct } from '@/api/campus/review'
import MallPage from '@/views/campus/mall/index.vue'
import MallDetail from '@/views/campus/mall/detail.vue'
import { elementStubs, clickButton, ElInput } from './support/element-stubs'

const CATEGORIES = [
  { id: 1, name: '教材书籍' },
  { id: 2, name: '数码电子' }
]

const productRow = (over = {}) => ({
  id: 7,
  title: '二手自行车',
  price: 199.5,
  originalPrice: 599,
  conditionLevel: 3,
  description: '骑了一年，刹车刚换过',
  images: 'bike-1.jpg,bike-2.jpg',
  stock: 2,
  sellerId: 9,
  sellerName: 'zhaomin',
  status: 1,
  categoryId: 2,
  createTime: '2026-10-10 09:00:00',
  ...over
})

const listCall = (index = -1) => listProduct.mock.calls.at(index)[0]

function stubMall({ rows = [productRow()], total = 1 } = {}) {
  listProduct.mockResolvedValue({ code: 200, rows, total })
  listCategory.mockResolvedValue({ code: 200, rows: CATEGORIES })
}

function stubDetail({ product = productRow(), reviews = [] } = {}) {
  getProduct.mockResolvedValue({ code: 200, data: product })
  listReviewByProduct.mockResolvedValue({ code: 200, data: reviews })
  listCategory.mockResolvedValue({ code: 200, rows: CATEGORIES })
  addCart.mockResolvedValue({ code: 200, msg: '已加入购物车' })
}

async function mountMall() {
  const wrapper = mount(MallPage, { global: elementStubs() })
  await flushPromises()
  return wrapper
}

async function mountDetail() {
  const wrapper = mount(MallDetail, { global: elementStubs() })
  await flushPromises()
  return wrapper
}

beforeEach(() => {
  vi.clearAllMocks()
})

describe('校园商城 · 商品浏览页', () => {
  it('挂载后只请求在售商品（status=1），并按返回行数渲染卡片', async () => {
    stubMall({ rows: [productRow({ id: 7 }), productRow({ id: 8, title: '考研英语真题' })], total: 2 })
    const wrapper = await mountMall()

    expect(listCall().status).toBe(1)
    expect(wrapper.findAll('.campus-product')).toHaveLength(2)
    expect(wrapper.text()).toContain('考研英语真题')
  })

  it('搜索时把页码归 1 并带上关键词', async () => {
    stubMall()
    const wrapper = await mountMall()

    wrapper.findComponent(ElInput).vm.$emit('update:modelValue', '自行车')
    await clickButton(wrapper, '搜索')
    await flushPromises()

    expect(listCall().pageNum).toBe(1)
    expect(listCall().title).toBe('自行车')
  })

  it('重置会清空关键词、分类与成色，并回到第一页', async () => {
    stubMall()
    const wrapper = await mountMall()

    wrapper.vm.queryParams.title = '自行车'
    wrapper.vm.queryParams.categoryId = 2
    wrapper.vm.queryParams.conditionLevel = 3
    await clickButton(wrapper, '重置')
    await flushPromises()

    const params = listCall()
    expect(params.title).toBeUndefined()
    expect(params.categoryId).toBeUndefined()
    expect(params.conditionLevel).toBeUndefined()
    expect(params.pageNum).toBe(1)
  })

  it('点击商品卡片跳转到详情页并携带商品 ID', async () => {
    stubMall()
    const wrapper = await mountMall()

    await wrapper.find('.campus-product').trigger('click')

    expect(pushMock).toHaveBeenCalledWith({ path: '/campus/mall-detail', query: { productId: 7 } })
  })

  it('无在售商品时显示空态而不是空白页', async () => {
    stubMall({ rows: [], total: 0 })
    const wrapper = await mountMall()

    expect(wrapper.findAll('.campus-product')).toHaveLength(0)
    expect(wrapper.text()).toContain('暂无在售商品')
  })
})

describe('校园商城 · 商品详情页', () => {
  it('加载商品与评价，渲染标题、价格与首图', async () => {
    stubDetail({ product: productRow(), reviews: [{ id: 1, rating: 5, content: '成色很好', userName: 'ry' }] })
    const wrapper = await mountDetail()

    expect(getProduct).toHaveBeenCalledWith('7')
    expect(wrapper.text()).toContain('二手自行车')
    expect(wrapper.text()).toContain('199.50')
    expect(wrapper.find('.detail-gallery__main img').attributes('src')).toBe('bike-1.jpg')
    expect(wrapper.text()).toContain('成色很好')
  })

  it('无评价时显示评价空态', async () => {
    stubDetail({ reviews: [] })
    const wrapper = await mountDetail()

    expect(wrapper.text()).toContain('暂无评价')
  })

  it('多图时点击缩略图会切换主图', async () => {
    stubDetail()
    const wrapper = await mountDetail()

    const thumbs = wrapper.findAll('.detail-gallery__thumb')
    expect(thumbs).toHaveLength(2)

    await thumbs[1].trigger('click')
    expect(wrapper.find('.detail-gallery__main img').attributes('src')).toBe('bike-2.jpg')
  })

  it('不能购买自己发布的商品，并给出原因', async () => {
    stubDetail({ product: productRow({ sellerId: 2 }) })
    const wrapper = await mountDetail()

    expect(wrapper.find('.detail-actions__hint').text()).toContain('不能购买自己发布的商品')
  })

  it('商品已下架时给出原因', async () => {
    stubDetail({ product: productRow({ status: 0 }) })
    const wrapper = await mountDetail()

    expect(wrapper.find('.detail-actions__hint').text()).toContain('商品已下架')
  })

  it('库存为 0 时给出原因', async () => {
    stubDetail({ product: productRow({ stock: 0 }) })
    const wrapper = await mountDetail()

    expect(wrapper.find('.detail-actions__hint').text()).toContain('库存不足')
  })

  it('加入购物车按当前数量提交并提示成功', async () => {
    stubDetail()
    const wrapper = await mountDetail()

    await clickButton(wrapper, '加入购物车')
    await flushPromises()

    expect(addCart).toHaveBeenCalledWith({ productId: 7, quantity: 1 })
    expect(ElMessage.success).toHaveBeenCalledWith('已加入购物车')
  })

  it('返回商城跳转到商城路由', async () => {
    stubDetail()
    const wrapper = await mountDetail()

    await clickButton(wrapper, '返回商城')

    expect(pushMock).toHaveBeenCalledWith({ path: '/campus/mall' })
  })
})
