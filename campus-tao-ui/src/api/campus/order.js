import request from '@/utils/request'

// 我的订单列表（分页）
export function listOrder(params) {
  return request({ url: '/campus/order/list', method: 'get', params: params })
}
// 订单概览（按状态分组统计，供列表页统计卡片使用）
export function orderStats(params) {
  return request({ url: '/campus/order/stats', method: 'get', params: params })
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
// 发货（管理端，可录入物流单号）
export function shipOrder(orderNo, logisticsNo) {
  return request({ url: '/campus/order/ship/' + orderNo, method: 'post', params: { logisticsNo } })
}
// 确认收货
export function finishOrder(orderNo) {
  return request({ url: '/campus/order/finish/' + orderNo, method: 'post' })
}
// 物流轨迹查询通道是否已启用
export function getLogisticsStatus() {
  return request({ url: '/campus/logistics/status', method: 'get' })
}
// 订单物流轨迹
export function getLogisticsTrace(orderNo) {
  return request({ url: '/campus/logistics/trace/' + orderNo, method: 'get' })
}
// 手工同步订单物流轨迹
export function syncLogisticsTrace(orderNo) {
  return request({ url: '/campus/logistics/sync/' + orderNo, method: 'post' })
}
