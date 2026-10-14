import request from '@/utils/request'

// 我的订单列表
export function listOrder(params) {
  return request({ url: '/campus/order/list', method: 'get', params: params })
}
// 订单详情
export function getOrder(orderNo) {
  return request({ url: '/campus/order/' + orderNo, method: 'get' })
}
// 下单
export function createOrder(data) {
  return request({ url: '/campus/order/create', method: 'post', data: data })
}
// 支付
export function payOrder(orderNo) {
  return request({ url: '/campus/order/pay/' + orderNo, method: 'post' })
}
// 取消
export function cancelOrder(orderNo) {
  return request({ url: '/campus/order/cancel/' + orderNo, method: 'post' })
}
// 发货（管理端）
export function shipOrder(orderNo) {
  return request({ url: '/campus/order/ship/' + orderNo, method: 'post' })
}
// 确认收货
export function finishOrder(orderNo) {
  return request({ url: '/campus/order/finish/' + orderNo, method: 'post' })
}
