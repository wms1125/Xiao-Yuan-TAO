import request from '@/utils/request'

// 售后列表（分页）
export function listAftersale(params) {
  return request({ url: '/campus/aftersale/list', method: 'get', params: params })
}
// 售后概览（按状态分组统计，供列表页统计卡片使用）
export function aftersaleStats(params) {
  return request({ url: '/campus/aftersale/stats', method: 'get', params: params })
}
// 申请售后
export function applyAftersale(data) {
  return request({ url: '/campus/aftersale/apply', method: 'post', data: data })
}
// 审核（管理端）
export function auditAftersale(data) {
  return request({ url: '/campus/aftersale/audit', method: 'post', data: data })
}
