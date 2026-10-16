import request from '@/utils/request'

// 我的收货地址列表（默认地址排最前）
export function listAddress() {
  return request({ url: '/campus/address/list', method: 'get' })
}
// 默认地址（无地址时 data 为 null）
export function getDefaultAddress() {
  return request({ url: '/campus/address/default', method: 'get' })
}
// 新增地址
export function addAddress(data) {
  return request({ url: '/campus/address', method: 'post', data: data })
}
// 修改地址（data 需含 addressId）
export function updateAddress(data) {
  return request({ url: '/campus/address', method: 'put', data: data })
}
// 设为默认地址
export function setDefaultAddress(addressId) {
  return request({ url: '/campus/address/default/' + addressId, method: 'put' })
}
// 删除地址
export function delAddress(addressId) {
  return request({ url: '/campus/address/' + addressId, method: 'delete' })
}
