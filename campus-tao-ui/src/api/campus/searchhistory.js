import request from '@/utils/request'

// 最近搜索（默认 10 条，用于商城页搜索框下拉）
export function listSearchHistory(limit) {
  return request({ url: '/campus/searchhistory/list', method: 'get', params: { limit } })
}
// 记录一次搜索（空搜索词后端会忽略）
export function recordSearchHistory(keyword) {
  return request({ url: '/campus/searchhistory/record', method: 'post', data: { keyword } })
}
// 删除一条搜索历史
export function delSearchHistory(historyId) {
  return request({ url: '/campus/searchhistory/' + historyId, method: 'delete' })
}
// 清空全部搜索历史
export function clearSearchHistory() {
  return request({ url: '/campus/searchhistory/clear', method: 'delete' })
}
