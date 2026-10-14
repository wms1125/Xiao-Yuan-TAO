import request from '@/utils/request'

// 校园淘-二手商品 接口

// 查询商品列表
export function listProduct(query) {
  return request({
    url: '/campus/product/list',
    method: 'get',
    params: query
  })
}

// 查询商品详细
export function getProduct(id) {
  return request({
    url: '/campus/product/' + id,
    method: 'get'
  })
}

// 新增商品
export function addProduct(data) {
  return request({
    url: '/campus/product',
    method: 'post',
    data: data
  })
}

// 修改商品
export function updateProduct(data) {
  return request({
    url: '/campus/product',
    method: 'put',
    data: data
  })
}

// 删除商品
export function delProduct(ids) {
  return request({
    url: '/campus/product/' + ids,
    method: 'delete'
  })
}

// 上架/下架
export function changeProductStatus(id, status) {
  return request({
    url: '/campus/product/status',
    method: 'put',
    data: { id, status }
  })
}
