import request from '@/utils/request'

// 售后列表
export function listAftersale(params) {
  return request({ url: '/campus/aftersale/list', method: 'get', params: params })
}
// 申请售后
export function applyAftersale(data) {
  return request({ url: '/campus/aftersale/apply', method: 'post', data: data })
}
// 审核（管理端）
export function auditAftersale(data) {
  return request({ url: '/campus/aftersale/audit', method: 'post', data: data })
}
