import autoImport from 'unplugin-auto-import/vite'

export default function createAutoImport() {
  return autoImport({
    imports: [
      'vue',
      'vue-router',
      'pinia',
      // 视图 <script setup> 中的裸标识符必须由自动导入提供。
      // 注意：模板里可直接解析 globalProperties（parseTime/resetForm 等），
      // 但脚本块内不行——此前遗漏了 useDict，导致所有调用字典的页面 setup 抛
      // ReferenceError 而白屏（首页/不取字典的页面不受影响）。
      {
        '@/utils/dict': ['useDict'],
        '@/utils/ruoyi': ['selectDictLabel', 'selectDictLabels']
      }
    ],
    // 产出 .eslintrc-auto-import.json：把自动导入的标识符（ref/computed/watch/
    // useDict 等）登记为 ESLint 全局变量。若缺这一步，flat config 下的 no-undef
    // 会对每个使用自动导入的页面报“未定义”，ESLint 将形同虚设。
    // 该文件由构建产出并随仓库提交，供 eslint.config.js 通过 createRequire 读取。
    eslintrc: {
      enabled: true,
      filepath: './.eslintrc-auto-import.json'
    },
    dts: false
  })
}
