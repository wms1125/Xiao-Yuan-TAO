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
    dts: false
  })
}
