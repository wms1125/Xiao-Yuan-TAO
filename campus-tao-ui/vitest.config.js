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
        'src/views/campus/aftersale/index.vue'
      ],
      exclude: ['**/node_modules/**', 'src/utils/generator/**'],
      thresholds: {
        // 2026-10-16 接入组件渲染测试（订单页/售后页）后实测 语句 50.17% / 分支 50.35% / 函数 60.93% / 行 51.27%
        // 原基线（语句 24 / 分支 23 / 函数 32 / 行 25）整体上调，行覆盖率按计划抬到 40%
        lines: 40,
        functions: 55,
        statements: 45,
        branches: 45
      }
    }
  }
})
