// @vitest-environment jsdom

import { describe, it, expect, vi, beforeEach } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

// 组件只从 element-plus 取消息提示与确认框，整体替换为替身
vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() },
  ElMessageBox: { confirm: vi.fn() }
}))

vi.mock('@/api/campus/favorite', () => ({
  listFavorite: vi.fn(),
  countFavorite: vi.fn(),
  favoriteStatus: vi.fn(),
  toggleFavorite: vi.fn(),
  removeFavorite: vi.fn()
}))

import { ElMessage, ElMessageBox } from 'element-plus'
import { listFavorite, removeFavorite } from '@/api/campus/favorite'
import FavoritePage from '@/views/campus/favorite/index.vue'
import { elementStubs } from './support/element-stubs'

const { pushMock } = vi.hoisted(() => ({ pushMock: vi.fn() }))
vi.mock('vue-router', () => ({
  useRoute: () => ({ query: {} }),
  useRouter: () => ({ push: pushMock })
}))

const favRow = (over = {}) => ({
  favoriteId: 1,
  productId: 100,
  productTitle: '九成新 iPad',
  productPrice: '1200.00',
  productImages: 'a.jpg,b.jpg',
  productStatus: 1,
  sellerName: '李雨桐',
  createTime: '2026-10-16 10:00:00',
  ...over
})

function stubList(rows = [favRow()]) {
  listFavorite.mockResolvedValue({ code: 200, data: rows })
}

async function mountPage() {
  const wrapper = mount(FavoritePage, { global: elementStubs() })
  await flushPromises()
  return wrapper
}

beforeEach(() => {
  vi.clearAllMocks()
})

describe('我的收藏页', () => {
  it('挂载后请求当前用户的收藏列表', async () => {
    stubList()
    await mountPage()

    expect(listFavorite).toHaveBeenCalledTimes(1)
  })

  it('渲染收藏的商品标题、价格与卖家', async () => {
    stubList()
    const wrapper = await mountPage()

    expect(wrapper.text()).toContain('九成新 iPad')
    expect(wrapper.text()).toContain('1200.00')
    expect(wrapper.text()).toContain('李雨桐')
  })

  it('概览：总数 / 在售 / 已下架三个数值正确', async () => {
    stubList([favRow(), favRow({ favoriteId: 2, productId: 101, productStatus: 0, productTitle: '旧书' })])
    const wrapper = await mountPage()

    const stats = wrapper.findAll('.campus-stat__value').map((n) => n.text())
    expect(stats).toEqual(['2', '1', '1'])
  })

  it('在售商品显示「在售」标签，下架商品显示「已下架」', async () => {
    stubList([
      favRow({ favoriteId: 1, productStatus: 1 }),
      favRow({ favoriteId: 2, productId: 101, productStatus: 0, productTitle: '旧书' })
    ])
    const wrapper = await mountPage()

    const tags = wrapper.findAll('.el-tag-stub').map((t) => t.text())
    expect(tags).toContain('在售')
    expect(tags).toContain('已下架')
  })

  it('商品标题为空时显示「商品已下架」（联表取不到商品）', async () => {
    stubList([favRow({ productTitle: null, productStatus: 0 })])
    const wrapper = await mountPage()

    expect(wrapper.text()).toContain('商品已下架')
  })

  it('空收藏：展示引导文案而不是空白表格', async () => {
    stubList([])
    const wrapper = await mountPage()

    expect(wrapper.text()).toContain('还没有收藏任何商品')
    expect(wrapper.text()).toContain('校园商城')
  })

  it('取消收藏：确认后调用接口并刷新列表', async () => {
    stubList()
    ElMessageBox.confirm.mockResolvedValueOnce()
    removeFavorite.mockResolvedValueOnce({ code: 200 })
    const wrapper = await mountPage()

    const btn = wrapper.findAll('.el-button-stub').find((b) => b.text().includes('取消收藏'))
    await btn.trigger('click')
    await flushPromises()

    expect(ElMessageBox.confirm).toHaveBeenCalledTimes(1)
    expect(removeFavorite).toHaveBeenCalledWith(100)
    expect(listFavorite).toHaveBeenCalledTimes(2) // 挂载 1 次 + 取消后刷新 1 次
  })

  it('取消收藏：用户点取消时不调用接口（确认是前置闸门）', async () => {
    stubList()
    // 模拟用户点"取消"——confirm 的 Promise 被拒绝
    ElMessageBox.confirm.mockRejectedValueOnce(new Error('cancel'))
    const wrapper = await mountPage()

    const btn = wrapper.findAll('.el-button-stub').find((b) => b.text().includes('取消收藏'))
    await btn.trigger('click')
    await flushPromises()

    expect(ElMessageBox.confirm).toHaveBeenCalledTimes(1)
    expect(removeFavorite).not.toHaveBeenCalled()
  })

  it('清空收藏：空收藏夹时提示且不发请求', async () => {
    stubList([])
    const wrapper = await mountPage()

    const btn = wrapper.findAll('.el-button-stub').find((b) => b.text().includes('清空收藏'))
    await btn.trigger('click')
    await flushPromises()

    expect(ElMessage.warning).toHaveBeenCalledWith('收藏夹已经是空的')
    expect(ElMessageBox.confirm).not.toHaveBeenCalled()
  })

  it('清空收藏：确认后逐条取消并刷新', async () => {
    stubList([favRow({ productId: 100 }), favRow({ favoriteId: 2, productId: 101 })])
    ElMessageBox.confirm.mockResolvedValueOnce()
    removeFavorite.mockResolvedValue({ code: 200 })
    const wrapper = await mountPage()

    const btn = wrapper.findAll('.el-button-stub').find((b) => b.text().includes('清空收藏'))
    await btn.trigger('click')
    await flushPromises()

    expect(removeFavorite).toHaveBeenCalledTimes(2)
    expect(removeFavorite).toHaveBeenCalledWith(100)
    expect(removeFavorite).toHaveBeenCalledWith(101)
  })

  it('时间超过 19 位时截断展示（后端返回带 T 的 ISO 串）', async () => {
    stubList([favRow({ createTime: '2026-10-16T10:00:00.000+0800' })])
    const wrapper = await mountPage()

    expect(wrapper.text()).toContain('2026-10-16 10:00:00')
  })

  it('去看看：跳转到商品详情并带上商品 ID', async () => {
    stubList()
    const wrapper = await mountPage()

    const btn = wrapper.findAll('.el-button-stub').find((b) => b.text().includes('去看看'))
    await btn.trigger('click')

    expect(pushMock).toHaveBeenCalledWith({ path: '/campus/mall-detail', query: { productId: 100 } })
  })
})
