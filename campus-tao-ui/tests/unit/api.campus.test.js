import { describe, it, expect, vi, beforeEach } from 'vitest'

// 拦截请求层，断言校园淘接口封装拼装出的 url / method / 参数正确
vi.mock('@/utils/request', () => ({ default: vi.fn(() => Promise.resolve({})) }))

import request from '@/utils/request'
import {
  listOrder,
  orderStats,
  getOrder,
  createOrder,
  payOrder,
  shipOrder,
  finishOrder,
  getLogisticsStatus,
  getLogisticsTrace,
  syncLogisticsTrace
} from '@/api/campus/order'
import { listAftersale, aftersaleStats, applyAftersale, auditAftersale } from '@/api/campus/aftersale'
import {
  listProduct,
  getProduct,
  addProduct,
  updateProduct,
  delProduct,
  changeProductStatus
} from '@/api/campus/product'
import { listCart, addCart, updateCartQty, checkCart, delCart } from '@/api/campus/cart'
import {
  listCampusNotice,
  getCampusNoticeTop,
  getCampusNoticeUnreadCount,
  readCampusNotice,
  readAllCampusNotice
} from '@/api/campus/notice'
import { listNotifyLog } from '@/api/campus/notifylog'
import { listReviewByProduct, addReview, listSellerReview } from '@/api/campus/review'
import {
  listAddress,
  getDefaultAddress,
  addAddress,
  updateAddress,
  setDefaultAddress,
  delAddress
} from '@/api/campus/address'
import { listFavorite, countFavorite, favoriteStatus, toggleFavorite, removeFavorite } from '@/api/campus/favorite'
import {
  listConversation,
  unreadMessageCount,
  listOrderMessage,
  sendMessage,
  readOrderMessage
} from '@/api/campus/message'
import {
  listSearchHistory,
  recordSearchHistory,
  delSearchHistory,
  clearSearchHistory
} from '@/api/campus/searchhistory'

const lastCall = () => request.mock.calls[request.mock.calls.length - 1][0]

describe('校园淘 - 订单接口', () => {
  beforeEach(() => request.mockClear())

  it('列表接口为 GET /campus/order/list 并透传 params', () => {
    listOrder({ status: '1' })
    expect(lastCall()).toEqual({ url: '/campus/order/list', method: 'get', params: { status: '1' } })
  })

  it('详情/支付/收货以订单号为路径参数', () => {
    getOrder('CT20241015001')
    expect(lastCall()).toEqual({ url: '/campus/order/CT20241015001', method: 'get' })

    payOrder('CT20241015001')
    expect(lastCall()).toEqual({ url: '/campus/order/pay/CT20241015001', method: 'post' })

    finishOrder('CT20241015001')
    expect(lastCall()).toEqual({ url: '/campus/order/finish/CT20241015001', method: 'post' })
  })

  it('发货为 POST /campus/order/ship/{orderNo} 并携带物流单号', () => {
    shipOrder('CT20241015001', 'SF1234567890')
    expect(lastCall()).toEqual({
      url: '/campus/order/ship/CT20241015001',
      method: 'post',
      params: { logisticsNo: 'SF1234567890' }
    })
  })

  it('下单为 POST /campus/order/create 且携带 data', () => {
    createOrder({ cartIds: [1], receiverName: '张三' })
    expect(lastCall()).toEqual({
      url: '/campus/order/create',
      method: 'post',
      data: { cartIds: [1], receiverName: '张三' }
    })
  })

  it('概览统计为 GET /campus/order/stats 并透传筛选条件', () => {
    orderStats({ status: 0, orderNo: 'CT2026' })
    expect(lastCall()).toEqual({
      url: '/campus/order/stats',
      method: 'get',
      params: { status: 0, orderNo: 'CT2026' }
    })
  })
})

describe('校园淘 - 售后接口', () => {
  beforeEach(() => request.mockClear())

  it('列表为 GET /campus/aftersale/list 并透传分页与筛选', () => {
    listAftersale({ pageNum: 1, pageSize: 10, status: 0 })
    expect(lastCall()).toEqual({
      url: '/campus/aftersale/list',
      method: 'get',
      params: { pageNum: 1, pageSize: 10, status: 0 }
    })
  })

  it('概览统计为 GET /campus/aftersale/stats', () => {
    aftersaleStats({ orderNo: 'CT2026' })
    expect(lastCall()).toEqual({ url: '/campus/aftersale/stats', method: 'get', params: { orderNo: 'CT2026' } })
  })

  it('申请与审核均为 POST 且携带 data', () => {
    applyAftersale({ orderNo: 'CT2026', type: 1, reason: '商品与描述不符' })
    expect(lastCall()).toEqual({
      url: '/campus/aftersale/apply',
      method: 'post',
      data: { orderNo: 'CT2026', type: 1, reason: '商品与描述不符' }
    })

    auditAftersale({ aftersaleNo: 'AS2026', pass: true, remark: '同意' })
    expect(lastCall()).toEqual({
      url: '/campus/aftersale/audit',
      method: 'post',
      data: { aftersaleNo: 'AS2026', pass: true, remark: '同意' }
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

describe('校园淘 - 站内通知接口', () => {
  beforeEach(() => request.mockClear())

  it('列表 GET /campus/notice/list 透传已读筛选', () => {
    listCampusNotice({ isRead: 0 })
    expect(lastCall()).toEqual({ url: '/campus/notice/list', method: 'get', params: { isRead: 0 } })
  })

  it('铃铛聚合与未读数接口为 GET', () => {
    getCampusNoticeTop()
    expect(lastCall()).toEqual({ url: '/campus/notice/top', method: 'get' })

    getCampusNoticeUnreadCount()
    expect(lastCall()).toEqual({ url: '/campus/notice/unreadCount', method: 'get' })
  })

  it('标记已读以通知ID为路径参数，全部已读为 POST /readAll', () => {
    readCampusNotice(12)
    expect(lastCall()).toEqual({ url: '/campus/notice/read/12', method: 'post' })

    readAllCampusNotice()
    expect(lastCall()).toEqual({ url: '/campus/notice/readAll', method: 'post' })
  })
})

describe('校园淘 - 物流轨迹接口', () => {
  beforeEach(() => request.mockClear())

  it('通道状态为 GET /campus/logistics/status', () => {
    getLogisticsStatus()
    expect(lastCall()).toEqual({ url: '/campus/logistics/status', method: 'get' })
  })

  it('轨迹查询/手工同步以订单号为路径参数', () => {
    getLogisticsTrace('CT20241015001')
    expect(lastCall()).toEqual({ url: '/campus/logistics/trace/CT20241015001', method: 'get' })

    syncLogisticsTrace('CT20241015001')
    expect(lastCall()).toEqual({ url: '/campus/logistics/sync/CT20241015001', method: 'post' })
  })
})

describe('校园淘 - 外呼留痕接口', () => {
  beforeEach(() => request.mockClear())

  it('列表为 GET /campus/notifylog/list 并透传渠道/结果筛选', () => {
    listNotifyLog({ channel: 'sms', status: 2 })
    expect(lastCall()).toEqual({ url: '/campus/notifylog/list', method: 'get', params: { channel: 'sms', status: 2 } })
  })
})

describe('校园淘 - 评价接口', () => {
  beforeEach(() => request.mockClear())

  it('按商品查询评价：商品ID 走路径参数', () => {
    listReviewByProduct(7)
    expect(lastCall()).toEqual({ url: '/campus/review/product/7', method: 'get' })
  })

  it('提交评价为 POST /campus/review/add 并透传 body', () => {
    addReview({ orderId: 1, productId: 7, rating: 5, content: '很好' })
    expect(lastCall()).toEqual({
      url: '/campus/review/add',
      method: 'post',
      data: { orderId: 1, productId: 7, rating: 5, content: '很好' }
    })
  })

  it('卖家评价列表为 GET /campus/review/seller 并透传分页参数', () => {
    listSellerReview({ pageNum: 2, pageSize: 10 })
    expect(lastCall()).toEqual({
      url: '/campus/review/seller',
      method: 'get',
      params: { pageNum: 2, pageSize: 10 }
    })
  })
})

describe('校园淘 - 收货地址接口', () => {
  beforeEach(() => request.mockClear())

  it('列表为 GET /campus/address/list，默认地址为 GET /campus/address/default', () => {
    listAddress()
    expect(lastCall()).toEqual({ url: '/campus/address/list', method: 'get' })

    getDefaultAddress()
    expect(lastCall()).toEqual({ url: '/campus/address/default', method: 'get' })
  })

  it('新增为 POST /campus/address 且携带表单体', () => {
    addAddress({ receiver: '李四', phone: '13900000000', detail: '西校区 2 号楼 202' })
    expect(lastCall()).toEqual({
      url: '/campus/address',
      method: 'post',
      data: { receiver: '李四', phone: '13900000000', detail: '西校区 2 号楼 202' }
    })
  })

  it('修改为 PUT /campus/address 且 data 含 addressId', () => {
    updateAddress({ addressId: 7, receiver: '李四', phone: '13900000000', detail: '西校区 2 号楼 202' })
    expect(lastCall()).toEqual({
      url: '/campus/address',
      method: 'put',
      data: { addressId: 7, receiver: '李四', phone: '13900000000', detail: '西校区 2 号楼 202' }
    })
  })

  it('设为默认地址为 PUT /campus/address/default/{addressId}', () => {
    setDefaultAddress(7)
    expect(lastCall()).toEqual({ url: '/campus/address/default/7', method: 'put' })
  })

  it('删除地址为 DELETE /campus/address/{addressId}', () => {
    delAddress(7)
    expect(lastCall()).toEqual({ url: '/campus/address/7', method: 'delete' })
  })
})

describe('校园淘 - 商品收藏接口', () => {
  it('列表为 GET /campus/favorite/list', () => {
    listFavorite()
    expect(lastCall()).toEqual({ url: '/campus/favorite/list', method: 'get' })
  })

  it('计数为 GET /campus/favorite/count', () => {
    countFavorite()
    expect(lastCall()).toEqual({ url: '/campus/favorite/count', method: 'get' })
  })

  it('批量状态为 POST /campus/favorite/status 且携带商品 ID 数组', () => {
    favoriteStatus([7, 8])
    expect(lastCall()).toEqual({
      url: '/campus/favorite/status',
      method: 'post',
      data: [7, 8]
    })
  })

  it('切换收藏为 POST /campus/favorite/toggle 且 productId 走 query', () => {
    toggleFavorite(7)
    expect(lastCall()).toEqual({
      url: '/campus/favorite/toggle',
      method: 'post',
      params: { productId: 7 }
    })
  })

  it('取消收藏为 POST /campus/favorite/remove 且 productId 走 query', () => {
    removeFavorite(7)
    expect(lastCall()).toEqual({
      url: '/campus/favorite/remove',
      method: 'post',
      params: { productId: 7 }
    })
  })
})

describe('校园淘 - 站内信接口', () => {
  it('会话列表为 GET /campus/message/list', () => {
    listConversation()
    expect(lastCall()).toEqual({ url: '/campus/message/list', method: 'get' })
  })

  it('未读数为 GET /campus/message/unread', () => {
    unreadMessageCount()
    expect(lastCall()).toEqual({ url: '/campus/message/unread', method: 'get' })
  })

  it('订单会话消息为 GET /campus/message/order/{orderNo}', () => {
    listOrderMessage('CT202601010001')
    expect(lastCall()).toEqual({ url: '/campus/message/order/CT202601010001', method: 'get' })
  })

  it('发送为 POST /campus/message/send 且携带订单号与内容', () => {
    sendMessage({ orderNo: 'CT202601010001', toUserId: 3, content: '你好' })
    expect(lastCall()).toEqual({
      url: '/campus/message/send',
      method: 'post',
      data: { orderNo: 'CT202601010001', toUserId: 3, content: '你好' }
    })
  })

  it('标记已读为 POST /campus/message/read/{orderNo}', () => {
    readOrderMessage('CT202601010001')
    expect(lastCall()).toEqual({ url: '/campus/message/read/CT202601010001', method: 'post' })
  })
})

describe('校园淘 - 搜索历史接口', () => {
  it('最近搜索为 GET /campus/searchhistory/list 且 limit 走 query', () => {
    listSearchHistory(10)
    expect(lastCall()).toEqual({
      url: '/campus/searchhistory/list',
      method: 'get',
      params: { limit: 10 }
    })
  })

  it('不传 limit 时 params 中为 undefined（后端回落默认 10）', () => {
    listSearchHistory()
    expect(lastCall()).toEqual({
      url: '/campus/searchhistory/list',
      method: 'get',
      params: { limit: undefined }
    })
  })

  it('记录为 POST /campus/searchhistory/record 且携带关键词', () => {
    recordSearchHistory('自行车')
    expect(lastCall()).toEqual({
      url: '/campus/searchhistory/record',
      method: 'post',
      data: { keyword: '自行车' }
    })
  })

  it('删除单条为 DELETE /campus/searchhistory/{historyId}', () => {
    delSearchHistory(7)
    expect(lastCall()).toEqual({ url: '/campus/searchhistory/7', method: 'delete' })
  })

  it('清空为 DELETE /campus/searchhistory/clear', () => {
    clearSearchHistory()
    expect(lastCall()).toEqual({ url: '/campus/searchhistory/clear', method: 'delete' })
  })
})
