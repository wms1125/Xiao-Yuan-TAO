<template>
  <div class="login">
    <!-- 左：品牌区 -->
    <section class="login-brand">
      <div class="brand-inner">
        <div class="brand-mark">
          <img :src="brandLogo" alt="校园淘" class="brand-mark__img" />
          <span class="brand-mark__name">{{ brandName }}</span>
        </div>
        <p class="brand-desc">{{ brandDesc }}</p>
        <h2 class="brand-slogan">让每一件旧物<br />在校园里重新被需要</h2>
        <ul class="brand-points">
          <li><span class="brand-points__idx">01</span>同校交易，当面交付更安心</li>
          <li><span class="brand-points__idx">02</span>发布、下单、售后全流程闭环</li>
          <li><span class="brand-points__idx">03</span>沙箱账户，模拟支付不涉真金</li>
        </ul>
      </div>
      <span class="brand-watermark" aria-hidden="true">淘</span>
    </section>

    <!-- 右：表单区 -->
    <section class="login-panel">
      <div class="panel-inner">
        <header class="panel-head">
          <h1 class="panel-title">欢迎回来</h1>
          <p class="panel-sub">登录后即可发布商品、下单与查看订单</p>
        </header>

        <el-form ref="loginRef" :model="loginForm" :rules="loginRules" class="login-form">
          <el-form-item prop="username">
            <el-input
              v-model="loginForm.username"
              type="text"
              size="large"
              auto-complete="off"
              placeholder="校园账号"
            >
              <template #prefix><svg-icon icon-class="user" class="input-icon" /></template>
            </el-input>
          </el-form-item>
          <el-form-item prop="password">
            <el-input
              v-model="loginForm.password"
              type="password"
              size="large"
              auto-complete="off"
              placeholder="登录密码"
              show-password
              @keyup.enter="handleLogin"
            >
              <template #prefix><svg-icon icon-class="password" class="input-icon" /></template>
            </el-input>
          </el-form-item>
          <el-form-item prop="code" v-if="captchaEnabled">
            <div class="code-row">
              <el-input
                v-model="loginForm.code"
                size="large"
                auto-complete="off"
                placeholder="验证码"
                @keyup.enter="handleLogin"
              >
                <template #prefix><svg-icon icon-class="validCode" class="input-icon" /></template>
              </el-input>
              <div class="login-code" @click="getCode" title="点击刷新验证码">
                <img :src="codeUrl" class="login-code-img" alt="验证码" />
              </div>
            </div>
          </el-form-item>

          <div class="form-extra">
            <el-checkbox v-model="loginForm.rememberMe">记住密码</el-checkbox>
            <router-link v-if="register" class="link-type" :to="'/register'">立即注册</router-link>
          </div>

          <el-button
            :loading="loading"
            size="large"
            type="primary"
            class="submit-btn"
            @click.prevent="handleLogin"
          >
            <span v-if="!loading">登 录</span>
            <span v-else>登 录 中...</span>
          </el-button>
        </el-form>

        <p class="panel-foot">{{ footerContent }}</p>
      </div>
    </section>
  </div>
</template>

<script setup>
import { getCodeImg } from "@/api/login"
import Cookies from "js-cookie"
import { encrypt, decrypt } from "@/utils/jsencrypt"
import useUserStore from '@/store/modules/user'
import defaultSettings from '@/settings'
import brandLogo from '@/assets/logo/campus-logo.svg'

// 页面标题形如「校园淘 · 校园二手交易平台」，拆分为品牌名与说明
const [brandName, brandDesc] = (import.meta.env.VITE_APP_TITLE || '校园淘 · 校园二手交易平台')
  .split('·')
  .map(item => item.trim())
const footerContent = defaultSettings.footerContent
const userStore = useUserStore()
const route = useRoute()
const router = useRouter()
const { proxy } = getCurrentInstance()

const loginForm = ref({
  username: "admin",
  password: "admin123",
  rememberMe: false,
  code: "",
  uuid: ""
})

const loginRules = {
  username: [{ required: true, trigger: "blur", message: "请输入您的账号" }],
  password: [{ required: true, trigger: "blur", message: "请输入您的密码" }],
  code: [{ required: true, trigger: "change", message: "请输入验证码" }]
}

const codeUrl = ref("")
const loading = ref(false)
// 验证码开关
const captchaEnabled = ref(true)
// 注册开关
const register = ref(false)
const redirect = ref(undefined)

watch(route, (newRoute) => {
    redirect.value = newRoute.query && newRoute.query.redirect
}, { immediate: true })

function handleLogin() {
  proxy.$refs.loginRef.validate(valid => {
    if (valid) {
      loading.value = true
      // 勾选了需要记住密码设置在 cookie 中设置记住用户名和密码
      if (loginForm.value.rememberMe) {
        Cookies.set("username", loginForm.value.username, { expires: 30 })
        Cookies.set("password", encrypt(loginForm.value.password), { expires: 30 })
        Cookies.set("rememberMe", loginForm.value.rememberMe, { expires: 30 })
      } else {
        // 否则移除
        Cookies.remove("username")
        Cookies.remove("password")
        Cookies.remove("rememberMe")
      }
      // 调用action的登录方法
      userStore.login(loginForm.value).then(() => {
        const query = route.query
        const otherQueryParams = Object.keys(query).reduce((acc, cur) => {
          if (cur !== "redirect") {
            acc[cur] = query[cur]
          }
          return acc
        }, {})
        router.push({ path: redirect.value || "/", query: otherQueryParams })
      }).catch(() => {
        loading.value = false
        // 重新获取验证码
        if (captchaEnabled.value) {
          getCode()
        }
      })
    }
  })
}

function getCode() {
  getCodeImg().then(res => {
    captchaEnabled.value = res.captchaEnabled === undefined ? true : res.captchaEnabled
    if (captchaEnabled.value) {
      codeUrl.value = "data:image/gif;base64," + res.img
      loginForm.value.uuid = res.uuid
    }
  })
}

function getCookie() {
  const username = Cookies.get("username")
  const password = Cookies.get("password")
  const rememberMe = Cookies.get("rememberMe")
  loginForm.value = {
    username: username === undefined ? loginForm.value.username : username,
    password: password === undefined ? loginForm.value.password : decrypt(password),
    rememberMe: rememberMe === undefined ? false : Boolean(rememberMe)
  }
}

getCode()
getCookie()
</script>

<style lang="scss" scoped>
/* ============ 布局：左品牌 / 右表单 ============ */
.login {
  display: flex;
  min-height: 100vh;
  background: var(--c-canvas);
}

.login-brand {
  position: relative;
  flex: 1 1 56%;
  min-width: 0;
  display: flex;
  align-items: center;
  padding: 64px 72px;
  overflow: hidden;
  color: #ffffff;
  background:
    radial-gradient(120% 90% at 8% 0%, rgba(63, 157, 134, .5) 0%, rgba(63, 157, 134, 0) 55%),
    radial-gradient(80% 70% at 100% 100%, rgba(232, 166, 107, .22) 0%, rgba(232, 166, 107, 0) 60%),
    linear-gradient(155deg, #12705a 0%, #0a4237 58%, #073029 100%);
}

/* 细网格：呼应"校园 · 笔记纸"的质感 */
.login-brand::before {
  content: "";
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(255, 255, 255, .055) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255, 255, 255, .055) 1px, transparent 1px);
  background-size: 56px 56px;
  mask-image: radial-gradient(120% 100% at 20% 30%, #000 20%, transparent 78%);
  pointer-events: none;
}

.brand-inner {
  position: relative;
  z-index: 1;
  max-width: 520px;
  animation: brandIn .7s cubic-bezier(.22, .8, .25, 1) both;
}

.brand-mark {
  display: flex;
  align-items: center;
  gap: 14px;
}

.brand-mark__img {
  width: 44px;
  height: 44px;
  border-radius: 13px;
  box-shadow: 0 6px 20px rgba(0, 0, 0, .22);
}

.brand-mark__name {
  font-family: var(--c-font-serif);
  font-size: 34px;
  font-weight: 700;
  letter-spacing: .22em;
  line-height: 1;
}

.brand-desc {
  margin: 12px 0 0;
  font-size: 13px;
  letter-spacing: .3em;
  color: rgba(255, 255, 255, .62);
}

.brand-slogan {
  margin: 54px 0 0;
  font-family: var(--c-font-serif);
  font-size: 30px;
  font-weight: 600;
  line-height: 1.62;
  letter-spacing: .1em;
  color: #ffffff;
}

.brand-points {
  margin: 46px 0 0;
  padding: 0;
  list-style: none;
  border-top: 1px solid rgba(255, 255, 255, .16);
}

.brand-points li {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 15px 2px;
  font-size: 13.5px;
  letter-spacing: .06em;
  color: rgba(255, 255, 255, .82);
  border-bottom: 1px solid rgba(255, 255, 255, .1);
}

.brand-points__idx {
  font-family: var(--c-font-serif);
  font-size: 12px;
  letter-spacing: .1em;
  color: rgba(232, 166, 107, .92);
}

/* 右下角巨型水印字 */
.brand-watermark {
  position: absolute;
  right: -28px;
  bottom: -68px;
  font-family: var(--c-font-serif);
  font-size: 320px;
  line-height: 1;
  font-weight: 700;
  color: rgba(255, 255, 255, .05);
  pointer-events: none;
  user-select: none;
}

/* ============ 右侧表单 ============ */
.login-panel {
  flex: 0 0 44%;
  max-width: 560px;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 56px 48px;
  background: var(--c-card);
  border-left: 1px solid var(--c-line);
}

.panel-inner {
  width: 100%;
  max-width: 372px;
  animation: panelIn .7s .12s cubic-bezier(.22, .8, .25, 1) both;
}

.panel-title {
  margin: 0;
  font-family: var(--c-font-serif);
  font-size: 28px;
  font-weight: 700;
  letter-spacing: .14em;
  color: var(--c-ink);
}

.panel-sub {
  margin: 10px 0 34px;
  font-size: 13px;
  letter-spacing: .04em;
  color: var(--c-ink-3);
}

.login-form {
  .el-form-item {
    margin-bottom: 20px;
  }

  :deep(.el-input__wrapper) {
    padding: 1px 14px;
  }

  :deep(.el-input__inner) {
    height: 46px;
    letter-spacing: .06em;
  }
}

.input-icon {
  width: 15px;
  height: 15px;
  color: var(--c-ink-4);
}

.code-row {
  display: flex;
  gap: 12px;
  width: 100%;

  .el-input {
    flex: 1;
  }
}

.login-code {
  flex-shrink: 0;
  width: 116px;
  height: 48px;
  border: 1px solid var(--c-line);
  border-radius: 8px;
  overflow: hidden;
  cursor: pointer;
  background: var(--c-line-soft);
  transition: border-color .18s ease;

  &:hover {
    border-color: var(--c-primary-line);
  }
}

.login-code-img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.form-extra {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 22px;
}

.link-type {
  font-size: 13px;
  color: var(--c-primary);
  letter-spacing: .04em;

  &:hover {
    text-decoration: underline;
  }
}

.submit-btn {
  width: 100%;
  height: 46px;
  font-size: 15px;
  letter-spacing: .3em;
  font-weight: 600;
}

.panel-foot {
  margin: 40px 0 0;
  font-size: 11.5px;
  line-height: 1.7;
  letter-spacing: .04em;
  color: var(--c-ink-4);
}

@keyframes brandIn {
  from { opacity: 0; transform: translateY(18px); }
  to   { opacity: 1; transform: none; }
}

@keyframes panelIn {
  from { opacity: 0; transform: translateY(22px); }
  to   { opacity: 1; transform: none; }
}

/* ============ 响应式 ============ */
@media (max-width: 1024px) {
  .login-brand {
    padding: 48px 44px;
  }

  .brand-watermark {
    font-size: 220px;
  }
}

@media (max-width: 860px) {
  .login {
    flex-direction: column;
  }

  .login-brand {
    flex: none;
    padding: 40px 28px 44px;
  }

  .brand-slogan {
    margin-top: 32px;
    font-size: 24px;
  }

  .brand-points {
    margin-top: 28px;
  }

  .brand-watermark {
    display: none;
  }

  .login-panel {
    flex: 1;
    max-width: none;
    border-left: none;
    border-top: 1px solid var(--c-line);
    padding: 40px 28px 56px;
  }
}

/* 暗色模式下品牌区保持深色，表单区走暗色令牌 */
html.dark .login-panel {
  background: var(--c-card);
}
</style>
