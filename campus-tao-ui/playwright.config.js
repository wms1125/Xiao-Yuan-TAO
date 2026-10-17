import { defineConfig } from '@playwright/test'

/**
 * 浏览器端 E2E 配置（校园淘）
 *
 * 与 e2e_business.py 的分工：
 * - `e2e_business.py` 打 HTTP 接口，证明「后端业务逻辑对」；
 * - 本配置跑真实浏览器，证明「用户点下去对」——11 张人工截图只能证明"渲染出来了"。
 *
 * 两点刻意选择：
 * 1. **复用系统 Chrome（channel: 'chrome'）而不是下载 Chromium**。
 *    CI 的 ubuntu runner 自带 Chrome，本地 Windows 也已有；省掉 `playwright install`
 *    那一步几十兆的下载，也让 CI 不必依赖外部 CDN。
 * 2. **串行（workers: 1）**。用例共享同一个真实后端与同一套演示数据，
 *    并发会互相污染（比如两个用例同时收藏同一件商品）。
 */
export default defineConfig({
  testDir: './e2e',
  timeout: 60_000,
  expect: { timeout: 10_000 },
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: [['list']],
  // 产物目录默认 test-results（CI 上传这个目录）。
  // 本地调试时可以用 PW_OUTPUT_DIR 指到别处：Playwright 每轮开头会清空 outputDir，
  // 而某些本地环境对"一次性删大量文件"有限制，指向新目录就绕开了这个坑。
  outputDir: process.env.PW_OUTPUT_DIR || 'test-results',
  use: {
    baseURL: 'http://127.0.0.1:8088',
    channel: 'chrome',
    locale: 'zh-CN',
    timezoneId: 'Asia/Shanghai',
    // 失败留证据：报告要求「失败留 trace + video」
    trace: 'retain-on-failure',
    video: 'retain-on-failure',
    screenshot: 'only-on-failure'
  },
  webServer: {
    command: 'npm run dev',
    url: 'http://127.0.0.1:8088',
    reuseExistingServer: true,
    timeout: 180_000
  }
})
