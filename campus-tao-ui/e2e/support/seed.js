/**
 * E2E 数据播种：只负责"把前置数据准备好"，不参与任何断言。
 *
 * 为什么不直接用商城里现成的商品：
 * 下单会扣库存，买两次商品就售罄，用例第三轮必然红——那是数据耗尽，不是功能回归。
 * 所以交易链路用一件专供商品：库存 99，价格 1 元，卖家用另一个账号（ry），
 * 因为后端禁止购买自己发布的商品（CampusOrderServiceImpl 第 139 行）。
 */
const API_BASE = process.env.E2E_API_URL || 'http://127.0.0.1:8080'
const PASSWORD = 'admin123'

/** 专供交易链路使用的商品标题，固定不变，便于跨轮复用 */
export const TRADE_PRODUCT_TITLE = 'E2E 交易链路专用商品（自动创建，勿手工改动）'

async function apiLogin(request, username) {
  const res = await request.post(`${API_BASE}/login`, {
    data: { username, password: PASSWORD }
  })
  if (!res.ok()) throw new Error(`播种失败：${username} 登录返回 ${res.status()}`)
  const body = await res.json()
  if (!body.token) throw new Error(`播种失败：${username} 登录未返回 token`)
  return body.token
}

/**
 * RuoYi 的业务异常是 HTTP 200 + body.code=500，
 * 只看 res.ok() 会把"失败"当成功，后续用例报一个完全不相干的错。
 * 所以这里必须连 body.code 一起判。
 */
async function assertOk(res, label) {
  const text = await res.text()
  if (!res.ok()) throw new Error(`播种失败：${label} HTTP ${res.status()} ${text}`)
  let body
  try {
    body = JSON.parse(text)
  } catch {
    throw new Error(`播种失败：${label} 返回的不是 JSON：${text.slice(0, 200)}`)
  }
  if (body.code !== 200) {
    throw new Error(`播种失败：${label} 业务失败 code=${body.code} msg=${body.msg}`)
  }
  return body
}

/** 在商品列表里按标题找那件专供商品 */
async function findTradeProduct(request) {
  const res = await request.get(`${API_BASE}/campus/product/list`, {
    params: { title: 'E2E 交易链路专用商品', pageNum: 1, pageSize: 10 }
  })
  if (!res.ok()) return null
  const body = await res.json()
  const rows = body.rows || []
  return rows.find((r) => r.title === TRADE_PRODUCT_TITLE) || null
}

/**
 * 保证有一件可购买、库存充足的专供商品。
 * 已存在且库存 >= 2 就复用；否则用 ry 的身份补建一件。
 * @returns {Promise<{id: number, title: string, price: number, stock: number}>}
 */
export async function ensureTradeProduct(request) {
  const existing = await findTradeProduct(request)
  if (existing && existing.stock >= 2) {
    return existing
  }

  const token = await apiLogin(request, 'ry')
  const createRes = await request.post(`${API_BASE}/campus/product`, {
    headers: { Authorization: `Bearer ${token}` },
    data: {
      title: TRADE_PRODUCT_TITLE,
      categoryId: 1,
      price: 1,
      originalPrice: 1,
      conditionLevel: 1,
      stock: 99,
      status: 1,
      description: 'Playwright 交易链路用例的专用商品，由用例自动创建。'
    }
  })
  await assertOk(createRes, '创建专供商品')
  const created = await findTradeProduct(request)
  if (!created) throw new Error('播种失败：商品创建成功但列表里查不到')
  return created
}

/**
 * 造一笔订单，可选择推进到哪一步。
 *
 * 为什么用接口而不是界面点：售后申请要求订单已发货（CampusAftersaleServiceImpl 第 57 行），
 * 而"下单→支付→发货"这段已经由 05 号用例在界面上验证过了。
 * 这里再用界面点一遍只是重复劳动，还会让 07 号用例的失败原因变得难定位。
 *
 * @param {{pay?: boolean, ship?: boolean}} steps
 * @returns {Promise<string>} 订单号
 */
export async function seedOrder(request, { pay = true, ship = true } = {}) {
  const product = await ensureTradeProduct(request)
  const token = await apiLogin(request, 'admin')
  const auth = { Authorization: `Bearer ${token}` }

  const addRes = await request.post(`${API_BASE}/campus/cart/add`, {
    headers: auth,
    data: { productId: product.id, quantity: 1 }
  })
  await assertOk(addRes, '加入购物车')

  const cartRes = await request.get(`${API_BASE}/campus/cart/list`, { headers: auth })
  const cartBody = await cartRes.json()
  const cartRow = (cartBody.data || []).find((r) => r.productId === product.id)
  if (!cartRow) throw new Error('播种失败：加购后在购物车里找不到该商品')

  const createRes = await request.post(`${API_BASE}/campus/order/create`, {
    headers: auth,
    data: {
      cartIds: [cartRow.id],
      receiverName: 'E2E 收货人',
      receiverPhone: '13800138000',
      address: 'E2E 测试地址 1 号',
      remark: 'E2E 用例自动下单'
    }
  })
  const orderBody = await assertOk(createRes, '创建订单')
  const orderNo = orderBody.data
  if (!orderNo) throw new Error('播种失败：下单未返回订单号')

  const steps = []
  if (pay) steps.push('pay')
  if (ship) steps.push('ship')
  for (const step of steps) {
    const res = await request.post(`${API_BASE}/campus/order/${step}/${orderNo}`, {
      headers: auth,
      params: step === 'ship' ? { logisticsNo: 'SF' + Date.now() } : undefined
    })
    await assertOk(res, step === 'pay' ? '支付' : '发货')
  }
  return orderNo
}

/** 造一笔已发货的订单（售后用例的常用前置） */
export function seedShippedOrder(request) {
  return seedOrder(request, { pay: true, ship: true })
}

/** 直付用例用的卖家收款码地址（演示环境不校验图片可访问性，只存 URL） */
export const E2E_PAY_QR_URL = '/profile/upload/e2e/pay-qr.png'

/**
 * 保证卖家（ry）已上传收款码——C2C 直付用例的前置。
 *
 * 为什么不走「account 页 + ImageUpload」：那是真实文件上传弹窗（依赖后端 /common/upload
 * 与本地文件选择），在 CI 里既慢又脆；播种阶段只负责"把前置数据摆好"，
 * 页面上"收款码是否如实渲染"由用例本身断言（见 09-direct-pay.spec.js）。
 * @returns {Promise<string>} 收款码 URL
 */
export async function ensureSellerPayQr(request, qrUrl = E2E_PAY_QR_URL) {
  const token = await apiLogin(request, 'ry')
  const res = await request.post(`${API_BASE}/campus/payqr`, {
    headers: { Authorization: `Bearer ${token}` },
    data: { qrUrl }
  })
  await assertOk(res, '上传收款码')
  return qrUrl
}

/**
 * 以指定账号在某个订单下发一条消息。
 * 站内信的会话列表是从 campus_message 里反查出来的（CampusMessageMapper 第 41-63 行），
 * 所以"有订单"不等于"有会话"——必须真有一条消息，会话才会出现。
 */
export async function seedMessage(request, orderNo, content, from = 'ry') {
  const token = await apiLogin(request, from)
  const res = await request.post(`${API_BASE}/campus/message/send`, {
    headers: { Authorization: `Bearer ${token}` },
    data: { orderNo, content }
  })
  await assertOk(res, `${from} 发送消息`)
}
