import request from '@/utils/request'

// 购物车列表
export function listCart() {
  return request({ url: '/campus/cart/list', method: 'get' })
}
// 加入购物车
export function addCart(data) {
  return request({ url: '/campus/cart/add', method: 'post', data: data })
}
// 修改数量
export function updateCartQty(data) {
  return request({ url: '/campus/cart/quantity', method: 'put', data: data })
}
// 勾选切换
export function checkCart(data) {
  return request({ url: '/campus/cart/check', method: 'put', data: data })
}
// 删除
export function delCart(ids) {
  return request({ url: '/campus/cart/' + ids, method: 'delete' })
}
