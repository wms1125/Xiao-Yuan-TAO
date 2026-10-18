import { describe, it, expect, vi, beforeEach } from 'vitest'

// 后端 #29：认证失败从 "HTTP 200 + body code=401" 改为真实 HTTP 401。
// 请求会从 axios 响应拦截器的成功分支落到 error 分支——
// 本文件直接驱动拦截器的 rejected handler，验证 error 分支的行为终态。

const confirmMock = vi.fn(() => new Promise(() => {})) // 挂起的 promise，模拟用户未操作
const logOutMock = vi.fn(() => Promise.resolve({}))

vi.mock('element-plus', () => ({
  ElNotification: { error: vi.fn() },
  ElMessage: Object.assign(vi.fn(), { error: vi.fn() }),
  ElMessageBox: { confirm: (...args) => confirmMock(...args) },
  ElLoading: { service: vi.fn(() => ({ close: vi.fn() })) }
}))

vi.mock('@/store/modules/user', () => ({
  default: () => ({ logOut: logOutMock })
}))

import request from '@/utils/request'
import { isRelogin } from '@/utils/request'

// axios 拦截器对象结构：handlers[i] = { fulfilled, rejected }
const errorBranch = request.interceptors.response.handlers.find((h) => typeof h.rejected === 'function')

function axios401Error(url = '/campus/notice/top') {
  return Object.assign(new Error('Request failed with status code 401'), {
    config: { url },
    response: { status: 401, data: { code: 401, msg: '认证失败' } }
  })
}

describe('request 错误分支：真实 HTTP 401 必须引导重新登录（#29）', () => {
  beforeEach(() => {
    confirmMock.mockClear()
    logOutMock.mockClear()
    isRelogin.show = false
  })

  it('HTTP 401 → 弹"系统提示"重登录确认框，并拒绝并给出会话过期文案', async () => {
    await expect(errorBranch.rejected(axios401Error())).rejects.toThrow('无效的会话')
    expect(confirmMock).toHaveBeenCalledTimes(1)
    const [msg, title] = confirmMock.mock.calls[0]
    expect(msg).toContain('登录状态已过期')
    expect(title).toBe('系统提示')
  })

  it('连续多个 401 只弹一次确认框（isRelogin 防重入），不再叠加', async () => {
    await expect(errorBranch.rejected(axios401Error('/a'))).rejects.toThrow()
    await expect(errorBranch.rejected(axios401Error('/b'))).rejects.toThrow()
    expect(confirmMock).toHaveBeenCalledTimes(1)
  })

  it('非 401 错误（如 500）不走重登录弹窗，保持原有"系统接口500异常"文案', async () => {
    const err = Object.assign(new Error('Request failed with status code 500'), {
      response: { status: 500, data: {} }
    })
    await expect(errorBranch.rejected(err)).rejects.toBe(err)
    expect(confirmMock).not.toHaveBeenCalled()
  })

  it('成功分支的 body code=401（历史行为，兼容过渡期）仍汇入同一处理', async () => {
    const fulfilled = request.interceptors.response.handlers.find((h) => typeof h.fulfilled === 'function')
    const res = { data: { code: 401, msg: '认证失败' }, request: { responseType: 'json' } }
    await expect(fulfilled.fulfilled(res)).rejects.toBe('无效的会话，或者会话已过期，请重新登录。')
    expect(confirmMock).toHaveBeenCalledTimes(1)
  })
})
