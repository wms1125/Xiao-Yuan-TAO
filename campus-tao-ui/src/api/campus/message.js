import request from '@/utils/request'

// 会话列表（分页）。
// query = { pageNum, pageSize }：服务端每页硬上限 100 条，超出直接报错而不是静默截断。
// 曾经这里是"一次拉全量"，用户订单越多响应体越大，且拿不到总数无法翻页。
export function listConversation(query) {
  return request({ url: '/campus/message/list', method: 'get', params: query })
}
// 我的未读消息总数
export function unreadMessageCount() {
  return request({ url: '/campus/message/unread', method: 'get' })
}
// 某订单会话的消息（仅买卖双方可见）
export function listOrderMessage(orderNo) {
  return request({ url: '/campus/message/order/' + orderNo, method: 'get' })
}
// 发送消息；toUserId 仅在订单跨多卖家时需要指定，服务端会校验其必须是订单当事方
export function sendMessage(data) {
  return request({ url: '/campus/message/send', method: 'post', data: data })
}
// 把某订单下发给我的未读消息标记为已读
export function readOrderMessage(orderNo) {
  return request({ url: '/campus/message/read/' + orderNo, method: 'post' })
}
