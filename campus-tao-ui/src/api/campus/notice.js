import request from '@/utils/request'

// 通知中心列表（分页）。params = { isRead, type, pageNum, pageSize }
// 与 listCampusNotice 的区别：那个是铃铛用的"取若干条"，这个能翻完历史并返回 total。
// type 只接受 order/ship/aftersale/system 四个值（表里就这么存）。
export function pageCampusNotice(params) {
  return request({ url: '/campus/notice/page', method: 'get', params: params })
}

// 我的校园淘消息列表（isRead=0 只看未读）
export function listCampusNotice(params) {
  return request({ url: '/campus/notice/list', method: 'get', params: params })
}

// 铃铛下拉：未读 Top5 + 未读总数
export function getCampusNoticeTop() {
  return request({ url: '/campus/notice/top', method: 'get' })
}

// 未读数量
export function getCampusNoticeUnreadCount() {
  return request({ url: '/campus/notice/unreadCount', method: 'get' })
}

// 标记单条已读
export function readCampusNotice(noticeId) {
  return request({ url: '/campus/notice/read/' + noticeId, method: 'post' })
}

// 全部标记已读
export function readAllCampusNotice() {
  return request({ url: '/campus/notice/readAll', method: 'post' })
}
