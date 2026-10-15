/**
 * ESLint 扁平配置（ESLint 10 + eslint-plugin-vue 10）。
 *
 * 设计取舍（原始数据与决策过程见 campus-tao/ai-log/014）：
 * - 规则集用 vue 的 essential（而非 recommended / strongly-recommended）：essential 只拦
 *   “真会出错”的写法（未定义标识符、语法错、模板结构错、prop 被直接改写等），
 *   不掺入命名与排版口味；排版一律交给 Prettier（eslint-config-prettier 关闭全部冲突规则），
 *   避免出现“linter 说左、formatter 说右”的死循环。
 * - 自动导入标识符（ref / computed / useDict 等）在构建期由 unplugin-auto-import 产出
 *   .eslintrc-auto-import.json，此处登记为全局变量；否则 no-undef 会对每个页面误报，
 *   规则开得再多也只会被噪音淹没。
 * - 基线格式化已先行落地（见上一提交），故本配置首次就要求“0 error”，不做全目录豁免。
 */
import js from '@eslint/js'
import pluginVue from 'eslint-plugin-vue'
import eslintConfigPrettier from 'eslint-config-prettier'
import globals from 'globals'
import { defineConfig, globalIgnores } from 'eslint/config'
import { createRequire } from 'node:module'

const require = createRequire(import.meta.url)
const autoImportGlobals = require('./.eslintrc-auto-import.json').globals

export default defineConfig([
  // 构建产物、覆盖率报告与随包静态资源不在检查范围内
  globalIgnores(['dist/**', 'coverage/**', 'node_modules/**', 'public/**', 'src/assets/**']),

  {
    name: '校园淘/语言环境与全局变量',
    files: ['**/*.js', '**/*.vue'],
    languageOptions: {
      ecmaVersion: 2022,
      sourceType: 'module',
      globals: {
        ...globals.browser,
        ...globals.node,
        ...autoImportGlobals
      }
    }
  },

  js.configs.recommended,

  ...pluginVue.configs['flat/essential'],

  {
    name: '校园淘/规则裁剪（已留痕）',
    rules: {
      // 上游在 main.js 以单名注册了两个全局组件（Pagination / Editor，见 main.js:62/67），
      // 全站模板按 <pagination> / <editor> 引用。改名会牵动大量模板与引用，
      // 收益仅为满足命名约定（非正确性问题），故本项目显式关闭该约定类规则。
      'vue/multi-word-component-names': 'off'
    }
  },

  {
    name: '校园淘/上游脚手架既有债（保留可见，不作门禁）',
    files: [
      // 表单构建器 / 代码生成器 / 右侧工具栏：以 v-model 直接改写父组件传入的响应式配置对象
      'src/views/tool/**',
      'src/components/RightToolbar/**',
      'src/views/system/user/profile/userInfo.vue',
      // 定时任务 Cron 选择器：computed 内写入本地 ref 驱动 v-model
      'src/components/Crontab/**'
    ],
    rules: {
      // 这两条规则命中的是“上游脚手架既有的结构性写法”，改成 emit/defineModel 或
      // computed→watch 属于对第三方脚手架的重构（有回归风险），不属于“接入 lint”的范围。
      // 故降级为 warn：不阻断构建，但每次 lint 都会把明细打出来，作为后续专项重构的待办清单。
      'vue/no-mutating-props': 'warn',
      'vue/no-side-effects-in-computed-properties': 'warn'
    }
  },

  // 必须置于最后：关闭与 Prettier 冲突的纯排版类规则
  eslintConfigPrettier
])
