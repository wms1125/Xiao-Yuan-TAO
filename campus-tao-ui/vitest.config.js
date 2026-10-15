import { defineConfig } from 'vitest/config'
import path from 'path'

// 前端单元测试配置（与构建用的 vite.config.js 分离，避免把构建期压缩/图标插件带入测试）
//
// 覆盖率（v8 provider）：
//   - include 白名单限定在"可脱离浏览器单测"的纯逻辑层：校园淘接口封装 + 通用工具函数；
//   - 排除 src/utils/generator/**（代码生成器运行期工具，非业务运行时路径）；
//   - .vue 视图组件的渲染测试需要 jsdom + @vue/test-utils，暂未纳入本门槛，另行规划；
//   - thresholds 采用"棘轮基线"：以实测值向下取整设定，后续只允许上调，不允许下调。
export default defineConfig({
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
      include: ['src/api/campus/**/*.js', 'src/utils/**/*.js'],
      exclude: ['**/node_modules/**', 'src/utils/generator/**'],
      thresholds: {
        // 2026-10-15 实测 语句 24.25% / 分支 23.61% / 函数 32.65% / 行 25.24%，向下取整作为棘轮基线
        lines: 25,
        functions: 32,
        statements: 24,
        branches: 23
      }
    }
  }
})
