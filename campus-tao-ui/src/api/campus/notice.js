import request from '@/utils/request'

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
