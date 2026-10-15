import request from '@/utils/request'

// 外呼留痕列表
export function listNotifyLog(params) {
  return request({ url: '/campus/notifylog/list', method: 'get', params: params })
}
