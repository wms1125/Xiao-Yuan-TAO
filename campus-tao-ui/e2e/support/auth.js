/**
 * E2E 公共动作（校园淘）
 *
 * 登录走真实表单，不走「先调接口拿 token 再塞 localStorage」的捷径——
 * 后者会让"登录页本身坏了"这类缺陷在 E2E 里完全隐形。
 */
import { expect } from '@playwright/test'

export const ADMIN = { username: 'admin', password: 'admin123' }
export const BUYER = { username: 'ry', password: 'admin123' }
// 顶栏头像旁显示的是 sys_user.nick_name，不是登录名。
// 演示库里 admin / ry 的昵称都是"若依"——所以断言昵称必须写"若依"，
// 写 'admin' 会让用例永远红（这里曾经就是这样，白跑了一个 commit）。
export const ADMIN_NICK_NAME = '若依'

/**
 * 填写登录表单，带 Vite 预打包 reload 的保护。
 *
 * 单独抽出来是因为负向用例（密码错误、验证码等）也要走同一条路：
 * 它们历史上各自 `fill()` 一把就点登录，一旦撞上 reload，
 * 输入框被清空，点下去触发的只是前端表单校验——
 * 表现成"密码错误居然没有提示"，看着像产品缺陷，其实是测试自己没填上值。
 * @param {import('@playwright/test').Page} page
 * @param {{username: string, password: string}} account
 */
export async function fillLoginForm(page, account) {
  await page.waitForLoadState('networkidle')

  const userInput = page.getByPlaceholder('校园账号')
  const pwdInput = page.getByPlaceholder('登录密码')
  await userInput.waitFor()

  for (let attempt = 0; attempt < 3; attempt++) {
    await userInput.fill(account.username)
    await pwdInput.fill(account.password)
    const filled =
      (await userInput.inputValue()) === account.username && (await pwdInput.inputValue()) === account.password
    if (filled) break
    await page.waitForLoadState('networkidle')
  }

  // 值没落进输入框就直接报出来，别让它伪装成"提示没有出现"
  await expect(userInput).toHaveValue(account.username)
  await expect(pwdInput).toHaveValue(account.password)
}

/**
 * 通过登录页登录，并等到真正离开 /login。
 * @param {import('@playwright/test').Page} page
 * @param {{username: string, password: string}} account
 * @param {{rememberMe?: boolean}} [options]
 */
export async function login(page, account = ADMIN, { rememberMe = false } = {}) {
  await page.goto('/login')

  await fillLoginForm(page, account)

  if (rememberMe) {
    // Element Plus 的复选框要点 label（原生 input 是不可见的），否则
    // getByRole('checkbox') 会命中被隐藏的 input 而卡在"不可见"上。
    const rememberBox = page.locator('.el-checkbox').filter({ hasText: '记住用户名' }).first()
    await rememberBox.click()
    await expect(rememberBox).toHaveClass(/is-checked/)
  }

  await page.getByRole('button', { name: '登 录', exact: true }).click()
  // 登录成功的判据是"离开了登录页"，而不是某个提示文案——
  // 文案会改，路由不会
  await page.waitForURL((url) => !url.pathname.includes('/login'), { timeout: 20_000 })
}

/**
 * 读取页面上的 el-message 提示文本（成功/失败提示都走它）。
 * @param {import('@playwright/test').Page} page
 * @returns {Promise<string>}
 */
export async function toastText(page) {
  const toast = page.locator('.el-message').first()
  await toast.waitFor({ state: 'visible', timeout: 10_000 })
  return (await toast.innerText()).trim()
}
