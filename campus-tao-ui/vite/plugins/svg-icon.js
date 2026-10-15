import fs from 'fs'
import path from 'path'

// 虚拟模块名，与 src/main.js 中的 `import 'virtual:svg-icons-register'` 对应
const SVG_ICONS_REGISTER = 'virtual:svg-icons-register'
const RESOLVED_SVG_ICONS_REGISTER = '\0' + SVG_ICONS_REGISTER
// 注入 sprite 的容器节点 id
const SVG_ICONS_DOM_ID = '__svg__icons__dom__'
const XMLNS = 'http://www.w3.org/2000/svg'
const XMLNS_LINK = 'http://www.w3.org/1999/xlink'

const ICON_DIRS = () => [path.resolve(process.cwd(), 'src/assets/icons/svg')]

/** 递归收集目录下的全部 .svg 文件 */
function collectSvgFiles(dir, files = []) {
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const fullPath = path.join(dir, entry.name)
    if (entry.isDirectory()) {
      collectSvgFiles(fullPath, files)
    } else if (entry.name.endsWith('.svg')) {
      files.push(fullPath)
    }
  }
  return files
}

/**
 * 由文件相对路径推导 symbol id，与 SvgIcon 组件的 `#icon-{iconClass}` 约定保持一致
 * 例：user.svg -> icon-user；子目录 a/b.svg -> icon-a-b
 */
function symbolIdOf(file, iconDir) {
  const segments = path
    .relative(iconDir, file)
    .split(path.sep)
    .join('/')
    .replace(/\.svg$/, '')
    .split('/')
  const name = segments.pop()
  const dirName = segments.join('-')
  return dirName ? `icon-${dirName}-${name}` : `icon-${name}`
}

/** 单个 svg 文件 -> sprite 中的 <symbol> */
function toSymbol(file, iconDir) {
  let content = fs.readFileSync(file, 'utf-8')
  // 首处硬编码描边色改为 currentColor，使图标能继承外部 fill / 文字颜色
  content = content.replace(/stroke="[a-zA-Z#0-9]*"/, 'stroke="currentColor"')
  const svgTag = (content.match(/<svg[^>]*>/) || [''])[0]
  const viewBoxAttr = /viewBox="([^"]+)"/.exec(svgTag)
  const width = /width="([^"]+)"/.exec(svgTag)
  const height = /height="([^"]+)"/.exec(svgTag)
  const viewBox = viewBoxAttr ? viewBoxAttr[1] : width && height ? `0 0 ${width[1]} ${height[1]}` : '0 0 1024 1024'
  // 去掉 xml 声明、根 <svg> 标签与闭合标签，保留内部图形内容
  const inner = content.replace(/[\s\S]*?<svg[^>]*>/, '').replace(/<\/svg>[\s\S]*$/, '')
  return `<symbol id="${symbolIdOf(file, iconDir)}" viewBox="${viewBox}">${inner}</symbol>`
}

/** 生成 `virtual:svg-icons-register` 的模块代码：把全部图标拼成一个 sprite 并注入 body */
function createRegisterModule() {
  const symbols = ICON_DIRS()
    .flatMap((dir) => collectSvgFiles(dir).map((file) => toSymbol(file, dir)))
    .join('')
  return `
if (typeof window !== 'undefined') {
  function loadSvg() {
    var body = document.body
    var svgDom = document.getElementById('${SVG_ICONS_DOM_ID}')
    if (!svgDom) {
      svgDom = document.createElementNS('${XMLNS}', 'svg')
      svgDom.style.position = 'absolute'
      svgDom.style.width = '0'
      svgDom.style.height = '0'
      svgDom.id = '${SVG_ICONS_DOM_ID}'
      svgDom.setAttribute('xmlns', '${XMLNS}')
      svgDom.setAttribute('xmlns:link', '${XMLNS_LINK}')
    }
    svgDom.innerHTML = ${JSON.stringify(symbols)}
    body.insertBefore(svgDom, body.lastChild)
  }
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', loadSvg)
  } else {
    loadSvg()
  }
}
export default {}
`
}

/**
 * 自研 SVG 图标 sprite 插件（零第三方依赖）
 * 取代 vite-plugin-svg-icons：后者依赖链 svg-baker -> micromatch -> braces，
 * 而 braces 的 ReDoS 通告至今无已修复版本，是前端构建期告警的唯一来源。
 */
export default function createSvgIcon() {
  return {
    name: 'campus:svg-icons',
    resolveId(id) {
      if (id === SVG_ICONS_REGISTER) {
        return RESOLVED_SVG_ICONS_REGISTER
      }
      return null
    },
    load(id) {
      if (id === RESOLVED_SVG_ICONS_REGISTER) {
        return createRegisterModule()
      }
      return null
    },
    configureServer(server) {
      const reload = (file) => {
        if (!file.endsWith('.svg')) return
        const mod = server.moduleGraph.getModuleById(RESOLVED_SVG_ICONS_REGISTER)
        if (mod) {
          server.moduleGraph.invalidateModule(mod)
        }
        server.ws.send({ type: 'full-reload' })
      }
      server.watcher.on('add', reload)
      server.watcher.on('change', reload)
      server.watcher.on('unlink', reload)
    }
  }
}
