import request from '@/utils/request'

// 我的收款码（未上传返回 data=null）
export function getMyPayQr() {
  return request({ url: '/campus/payqr', method: 'get' })
}
// 保存/覆盖本人收款码（图片先经 /common/upload 上传，此处只存 URL）
export function saveMyPayQr(qrUrl) {
  return request({ url: '/campus/payqr', method: 'post', data: { qrUrl } })
}
