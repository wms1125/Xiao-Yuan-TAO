import request from '@/utils/request'

// 商品评价列表
export function listReviewByProduct(productId) {
  return request({ url: '/campus/review/product/' + productId, method: 'get' })
}
// 提交评价
export function addReview(data) {
  return request({ url: '/campus/review/add', method: 'post', data: data })
}
// 卖家视角：本人名下商品收到的评价（分页）
export function listSellerReview(query) {
  return request({ url: '/campus/review/seller', method: 'get', params: query })
}
