import { defineConfig } from 'vitest/config'
import vue from '@vitejs/plugin-vue'
import path from 'path'

// 前端单元测试配置（与构建用的 vite.config.js 分离，避免把构建期压缩/图标插件带入测试）
//
// plugins：仅挂 @vitejs/plugin-vue，使 .vue 单文件组件能在测试里被编译。
// 刻意不引入 vite.config.js 里的压缩、图标、自动导入等构建期插件——测试只需要能跑业务逻辑。
//
// 运行环境：
//   - 默认 node（纯逻辑层用例足够）；
//   - 需要 DOM 的 .vue 组件用例在文件首行用 `// @vitest-environment jsdom` 声明式切换，
//     不使用 environmentMatchGlobs（Vitest 4 已移除该配置项），按文件就近声明更直观。
//
// 覆盖率（v8 provider）：
//   - include 白名单限定在"可脱离浏览器单测"的纯逻辑层：校园淘接口封装 + 通用工具函数，
//     外加两个校园淘列表页（订单/售后）——它们的分页与概览联动是本轮新增行为，须进门槛；
//   - 排除 src/utils/generator/**（代码生成器运行期工具，非业务运行时路径）；
//   - thresholds 采用"棘轮基线"：以实测值向下取整设定，后续只允许上调，不允许下调。
//     阈值比实测值留出若干百分点余量：门禁的职责是拦住"覆盖率塌方"，不是拦掉无关改动的正常抖动。
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '~': path.resolve(__dirname, './'),
      '@': path.resolve(__dirname, './src')
    }
  },
  test: {
    include: ['tests/unit/**/*.test.js'],
    environment: 'node',
    coverage: {
      provider: 'v8',
      reporter: ['text', 'html', 'lcov'],
      reportsDirectory: './coverage',
      include: [
        'src/api/campus/**/*.js',
        'src/utils/**/*.js',
        'src/views/campus/order/index.vue',
        'src/views/campus/aftersale/index.vue',
        'src/views/campus/mall/index.vue',
        'src/views/campus/mall/detail.vue',
        'src/views/campus/review/index.vue',
        'src/views/campus/address/index.vue',
        // v1.0.18 三张空表落地：收藏页 / 站内信页 / 收藏与搜索历史在商城页的挂载
        'src/views/campus/favorite/index.vue',
        'src/views/campus/message/index.vue',
        // v1.0.20 P1-4：购物车页（勾选联动结算）与商品管理页（发布/修改/上下架/加购）
        'src/views/campus/cart/index.vue',
        'src/views/campus/product/index.vue'
      ],
      exclude: ['**/node_modules/**', 'src/utils/generator/**'],
      thresholds: {
        // 棘轮基线：2026-10-17 实测 语句 71.62% / 分支 68.10% / 函数 76.32% / 行 73.27%
        // 上一版基线（语句 45 / 分支 45 / 函数 55 / 行 40）在实测值下方留出约 4~5 个百分点，
        // 本次按实测整体上调；后续只允许上调，不允许下调。
        lines: 68,
        functions: 72,
        statements: 67,
        branches: 64
      }
    }
  }
})
