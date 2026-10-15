import { describe, it, expect, vi, beforeEach } from 'vitest'

// 拦截请求层，断言校园淘接口封装拼装出的 url / method / 参数正确
vi.mock('@/utils/request', () => ({ default: vi.fn(() => Promise.resolve({})) }))

import request from '@/utils/request'
import { listOrder, getOrder, createOrder, payOrder, shipOrder, finishOrder } from '@/api/campus/order'
import { listProduct, getProduct, addProduct, updateProduct, delProduct, changeProductStatus } from '@/api/campus/product'
import { listCart, addCart, updateCartQty, checkCart, delCart } from '@/api/campus/cart'

const lastCall = () => request.mock.calls[request.mock.calls.length - 1][0]

describe('校园淘 - 订单接口', () => {
  beforeEach(() => request.mockClear())

  it('列表接口为 GET /campus/order/list 并透传 params', () => {
    listOrder({ status: '1' })
    expect(lastCall()).toEqual({ url: '/campus/order/list', method: 'get', params: { status: '1' } })
  })

  it('详情/支付/发货/收货以订单号为路径参数', () => {
    getOrder('CT20241015001')
    expect(lastCall()).toEqual({ url: '/campus/order/CT20241015001', method: 'get' })

    payOrder('CT20241015001')
    expect(lastCall()).toEqual({ url: '/campus/order/pay/CT20241015001', method: 'post' })

    shipOrder('CT20241015001')
    expect(lastCall()).toEqual({ url: '/campus/order/ship/CT20241015001', method: 'post' })

    finishOrder('CT20241015001')
    expect(lastCall()).toEqual({ url: '/campus/order/finish/CT20241015001', method: 'post' })
  })

  it('下单为 POST /campus/order/create 且携带 data', () => {
    createOrder({ cartIds: [1], receiverName: '张三' })
    expect(lastCall()).toEqual({
      url: '/campus/order/create',
      method: 'post',
      data: { cartIds: [1], receiverName: '张三' }
    })
  })
})

describe('校园淘 - 商品接口', () => {
  beforeEach(() => request.mockClear())

  it('列表 GET、详情 GET、删除 DELETE 路径正确', () => {
    listProduct({ pageNum: 1 })
    expect(lastCall()).toEqual({ url: '/campus/product/list', method: 'get', params: { pageNum: 1 } })

    getProduct(7)
    expect(lastCall()).toEqual({ url: '/campus/product/7', method: 'get' })

    delProduct('7')
    expect(lastCall()).toEqual({ url: '/campus/product/7', method: 'delete' })
  })

  it('新增 POST、修改 PUT 均指向 /campus/product', () => {
    addProduct({ title: '二手自行车' })
    expect(lastCall()).toEqual({ url: '/campus/product', method: 'post', data: { title: '二手自行车' } })

    updateProduct({ id: 1, title: '二手自行车' })
    expect(lastCall()).toEqual({ url: '/campus/product', method: 'put', data: { id: 1, title: '二手自行车' } })
  })

  it('上下架为 PUT /campus/product/status', () => {
    changeProductStatus(1, '1')
    expect(lastCall()).toEqual({ url: '/campus/product/status', method: 'put', data: { id: 1, status: '1' } })
  })
})

describe('校园淘 - 购物车接口', () => {
  beforeEach(() => request.mockClear())

  it('列表 GET、加购 POST、改数量/勾选 PUT、删除 DELETE 路径正确', () => {
    listCart()
    expect(lastCall()).toEqual({ url: '/campus/cart/list', method: 'get' })

    addCart({ productId: 1, quantity: 2 })
    expect(lastCall()).toEqual({ url: '/campus/cart/add', method: 'post', data: { productId: 1, quantity: 2 } })

    updateCartQty({ id: 1, quantity: 3 })
    expect(lastCall()).toEqual({ url: '/campus/cart/quantity', method: 'put', data: { id: 1, quantity: 3 } })

    checkCart({ id: 1, checked: true })
    expect(lastCall()).toEqual({ url: '/campus/cart/check', method: 'put', data: { id: 1, checked: true } })

    delCart('1')
    expect(lastCall()).toEqual({ url: '/campus/cart/1', method: 'delete' })
  })
})
