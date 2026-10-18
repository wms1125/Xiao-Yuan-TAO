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
//
// repeatSubmit: false —— 这两个接口必须显式关掉全局防重复提交，原因有两条：
// 1）语义上"收藏/取消收藏"本就可重复：服务端每次按当前状态取反并返回 favored，
//    前端完全依赖返回值渲染按钮。被拦下会留下"按钮没反应 + 弹出『请勿重复提交』"的错误体验。
// 2）全局防重复提交的缓存键是 { url, data }，而这里参数走的是 params（data 恒为 undefined），
//    键里没有 productId → 一秒内先收藏商品 A 再收藏商品 B 会被误判为重复提交。
//    参见 src/utils/request.js 第 42-58 行。
export function toggleFavorite(productId) {
  return request({
    url: '/campus/favorite/toggle',
    method: 'post',
    params: { productId },
    headers: { repeatSubmit: false }
  })
}
// 取消收藏（按商品 ID）；同上，取消收藏天然幂等，重复提交保护只会制造误报
export function removeFavorite(productId) {
  return request({
    url: '/campus/favorite/remove',
    method: 'post',
    params: { productId },
    headers: { repeatSubmit: false }
  })
}
