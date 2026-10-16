import request from '@/utils/request'

// 我的收藏列表（含商品概要，最新在前）
export function listFavorite() {
  return request({ url: '/campus/favorite/list', method: 'get' })
}
// 我的收藏总数
export function countFavorite() {
  return request({ url: '/campus/favorite/count', method: 'get' })
}
// 批量查询商品收藏状态（商品卡片列表用，避免 N+1）
export function favoriteStatus(productIds) {
  return request({ url: '/campus/favorite/status', method: 'post', data: productIds })
}
// 切换收藏状态；返回 favored=true/false 表示切换后的状态
export function toggleFavorite(productId) {
  return request({ url: '/campus/favorite/toggle', method: 'post', params: { productId } })
}
// 取消收藏（按商品 ID）
export function removeFavorite(productId) {
  return request({ url: '/campus/favorite/remove', method: 'post', params: { productId } })
}
