import request from '@/utils/request'

// 当前生效支付渠道与资金语义（mode: sandbox / real / unavailable）
// 前端只负责展示后端给的 notice，不自己拼文案——否则两端文案迟早走样。
export function getPaymentChannel() {
  return request({ url: '/campus/payment/channel', method: 'get' })
}
