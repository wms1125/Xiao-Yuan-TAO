// @vitest-environment jsdom

import { describe, it, expect, vi, beforeEach } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), error: vi.fn(), warning: vi.fn() }
}))

vi.mock('@/api/campus/review', () => ({
  listSellerReview: vi.fn()
}))

import { listSellerReview } from '@/api/campus/review'
import ReviewPage from '@/views/campus/review/index.vue'
import { elementStubs, Pagination, statValues } from './support/element-stubs'

const reviewRow = (over = {}) => ({
  id: 1,
  productTitle: '二手自行车',
  rating: 5,
  content: '成色比描述还好',
  userName: 'ry',
  createTime: '2026-10-16 10:00:00',
  ...over
})

const listCall = (index = -1) => listSellerReview.mock.calls.at(index)[0]

function stubApi({ rows = [reviewRow()], total = 1 } = {}) {
  listSellerReview.mockResolvedValue({ code: 200, rows, total })
}

async function mountPage() {
  const wrapper = mount(ReviewPage, { global: elementStubs() })
  await flushPromises()
  return wrapper
}

beforeEach(() => {
  vi.clearAllMocks()
})

describe('评价管理页（卖家视角）', () => {
  it('挂载后请求本人名下商品的评价列表', async () => {
    stubApi()
    await mountPage()

    expect(listSellerReview).toHaveBeenCalledTimes(1)
    expect(listCall().pageNum).toBe(1)
  })

  it('渲染评价的商品标题、内容与评分', async () => {
    stubApi({ rows: [reviewRow({ productTitle: '考研英语真题', content: '笔记很整齐', rating: 4 })] })
    const wrapper = await mountPage()

    expect(wrapper.text()).toContain('考研英语真题')
    expect(wrapper.text()).toContain('笔记很整齐')
    expect(wrapper.find('.el-rate-stub').attributes('data-rate')).toBe('4')
  })

  it('概览按当前页评价计算平均分与好评数，总数取自分页信封', async () => {
    stubApi({
      rows: [reviewRow({ id: 1, rating: 5 }), reviewRow({ id: 2, rating: 3 })],
      total: 20
    })
    const wrapper = await mountPage()

    // [评价总数, 平均评分, 好评数]
    expect(statValues(wrapper)).toEqual(['20', '4.0', '1'])
  })

  it('翻页后按新页码重新请求', async () => {
    stubApi()
    const wrapper = await mountPage()

    const pagination = wrapper.findComponent(Pagination)
    pagination.vm.$emit('update:page', 2)
    pagination.vm.$emit('pagination')
    await flushPromises()

    expect(listCall().pageNum).toBe(2)
  })

  it('无评价时显示空态', async () => {
    stubApi({ rows: [], total: 0 })
    const wrapper = await mountPage()

    expect(wrapper.text()).toContain('暂无评价')
  })
})
