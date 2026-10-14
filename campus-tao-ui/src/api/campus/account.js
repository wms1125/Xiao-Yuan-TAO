import request from '@/utils/request'

// 我的沙箱账户
export function getAccount() {
  return request({ url: '/campus/account/balance', method: 'get' })
}
// 充值（演示）
export function recharge(data) {
  return request({ url: '/campus/account/recharge', method: 'post', data: data })
}
