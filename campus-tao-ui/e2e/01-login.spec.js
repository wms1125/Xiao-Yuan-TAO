import { expect, test } from '@playwright/test'

import { ADMIN, ADMIN_NICK_NAME, fillLoginForm, login, toastText } from './support/auth.js'

test.describe('路径 1：登录', () => {
  test('正确账号能进系统，并停留在需要登录才能看的页面', async ({ page }) => {
    await login(page, ADMIN)

    // 登录后应落到首页/工作台，且不再是登录页
    await expect(page).not.toHaveURL(/\/login/)
    // 顶栏头像旁的昵称是"真的登进去了"的直接证据。
    // 注意：这里显示的是 sys_user.nick_name（演示库里是"若依"），不是登录名 'admin'；
    // 定位到 .user-nickname 而不是整个 body，否则命中首页文案里的"若依"算是蒙对。
    await expect(page.locator('.user-nickname')).toContainText(ADMIN_NICK_NAME)
  })

  test('负向：密码错误必须停在登录页并给出提示', async ({ page }) => {
    await page.goto('/login')
    // 必须和 login() 走同一套填表逻辑：少了 networkidle 等待，
    // Vite 预打包的整页 reload 会把刚填的值清空，点下去只剩前端校验
    // （"请输入您的密码"），看着像"密码错误没提示"，其实是没把值送出去。
    await fillLoginForm(page, { username: ADMIN.username, password: 'this-is-not-the-password' })
    await page.getByRole('button', { name: '登 录', exact: true }).click()

    const toast = await toastText(page)
    expect(toast).toMatch(/密码|错误|失败/)
    // 关键：不能因为失败提示弹出来就放行跳转
    await expect(page).toHaveURL(/\/login/)
  })

  test('负向：勾选记住后密码仍不得写入 cookie', async ({ page, context }) => {
    await login(page, ADMIN, { rememberMe: true })

    // 历史上"记住密码"是把密码加密后写进 cookie。前端既要加密又要解密，
    // 私钥只能放在前端——等于把钥匙和锁一起交给用户，换更强的密钥也白费。
    // 现在只记住用户名，密码一律不落盘。这条用例守住这个结论。
    const cookies = await context.cookies()
    const passwordCookie = cookies.find((c) => c.name.toLowerCase() === 'password')
    expect(passwordCookie, `密码不得写入 cookie，实际写入：${JSON.stringify(passwordCookie)}`).toBeUndefined()
    const usernameCookie = cookies.find((c) => c.name === 'username')
    expect(usernameCookie).toBeTruthy()

    // token 存在 Admin-Token cookie 里，不抹掉它访问 /login 会被路由重定向回首页，
    // 那样根本看不到登录页（曾在这里失败过：定位不到输入框）。
    await context.clearCookies({ name: 'Admin-Token' })

    // 重新打开登录页：用户名回填，密码框必须是空的
    await page.goto('/login')
    await expect(page.getByPlaceholder('校园账号')).toHaveValue(ADMIN.username)
    await expect(page.getByPlaceholder('登录密码')).toHaveValue('')
  })
})
