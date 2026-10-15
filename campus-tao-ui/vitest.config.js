import { defineConfig } from 'vitest/config'
import path from 'path'

// 前端单元测试配置（与构建用的 vite.config.js 分离，避免把构建期压缩/图标插件带入测试）
export default defineConfig({
  resolve: {
    alias: {
      '~': path.resolve(__dirname, './'),
      '@': path.resolve(__dirname, './src')
    }
  },
  test: {
    include: ['tests/unit/**/*.test.js'],
    environment: 'node'
  }
})
