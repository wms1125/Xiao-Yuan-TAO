import request from '@/utils/request'

// 商品评价列表
export function listReviewByProduct(productId) {
  return request({ url: '/campus/review/product/' + productId, method: 'get' })
}
// 提交评价
export function addReview(data) {
  return request({ url: '/campus/review/add', method: 'post', data: data })
}
