import request from '@/utils/request'

// 会话列表（我参与过的订单，带未读数）
export function listConversation() {
  return request({ url: '/campus/message/list', method: 'get' })
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
