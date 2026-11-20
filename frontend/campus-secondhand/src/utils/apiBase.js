import { Capacitor } from '@capacitor/core'

/**
 * 后端地址的唯一出处（Web 与 Android 安装包共用）。
 *
 * <p><b>为什么需要：</b>Web 端靠同源相对路径 `/api`、`/uploads`，由 Vite 代理或
 * 反向代理转发到后端。打包进 Android（Capacitor）后，页面跑在 WebView 的
 * `http://localhost` 上，相对路径会打到 App 自己，后端根本收不到请求。</p>
 *
 * <p>后端地址（origin）的优先级：</p>
 * <ol>
 *   <li>仅原生 App：登录页「服务器地址」里保存的值（localStorage `apiServerUrl`），
 *       现场演示换了网络不必重新打包；</li>
 *   <li>构建时环境变量 `VITE_API_BASE_URL`（例如 `http://192.168.1.20:8080`）；</li>
 *   <li>都没有时为空串 —— 与以前完全一样，走相对路径。Web 构建不设该变量即可。</li>
 * </ol>
 *
 * <p>`VITE_API_BASE`（默认 `/api`）仍表示接口路径前缀，语义不变。</p>
 */

export const SERVER_URL_KEY = 'apiServerUrl'

/** 是否运行在 Capacitor 原生壳里（浏览器、单测里都是 false） */
export function isNativeApp() {
  try {
    return Capacitor.isNativePlatform()
  } catch {
    return false
  }
}

/**
 * 规范化用户输入的服务器地址：去空白、去结尾斜杠；只接受 http(s)://host[:port][/path]。
 *
 * @param {string} raw
 * @returns {string} 规范化后的地址；不合法时返回空串
 */
export function normalizeServerUrl(raw) {
  const text = String(raw || '').trim().replace(/\/+$/, '')
  if (!text) return ''
  try {
    const u = new URL(text)
    if (u.protocol !== 'http:' && u.protocol !== 'https:') return ''
    if (u.search || u.hash) return ''
    return text
  } catch {
    return ''
  }
}

/** 运行时覆盖值（仅原生 App 生效） */
export function getSavedServerUrl() {
  if (!isNativeApp()) return ''
  try {
    return normalizeServerUrl(localStorage.getItem(SERVER_URL_KEY))
  } catch {
    return ''
  }
}

/** 构建时配置的后端地址 */
export function getBuildServerUrl() {
  return normalizeServerUrl(import.meta.env.VITE_API_BASE_URL)
}

/**
 * 保存运行时服务器地址；传空串表示恢复为打包时的默认地址。
 *
 * @returns {boolean} 地址合法并已保存（或已清除）时为 true
 */
export function saveServerUrl(raw) {
  if (!String(raw || '').trim()) {
    localStorage.removeItem(SERVER_URL_KEY)
    return true
  }
  const url = normalizeServerUrl(raw)
  if (!url) return false
  localStorage.setItem(SERVER_URL_KEY, url)
  return true
}

/** 当前生效的后端 origin；空串表示同源相对路径 */
export function resolveApiOrigin() {
  return getSavedServerUrl() || getBuildServerUrl()
}

/** 模块加载时确定；改了服务器地址后由调用方刷新页面使其生效 */
export const API_ORIGIN = resolveApiOrigin()

/** 接口前缀，例如 `/api` 或 `http://192.168.1.20:8080/api` */
export const API_BASE = `${API_ORIGIN}${import.meta.env.VITE_API_BASE || '/api'}`

/**
 * 把后端返回的相对资源路径（`/uploads/...`）转成可访问的地址。
 * 同源部署（API_ORIGIN 为空）时原样返回；data:/blob:/http(s): 以及前端自带的
 * `/sample/...` 等静态资源不处理。
 */
export function assetUrl(path, origin = API_ORIGIN) {
  if (!path || !origin || typeof path !== 'string') return path
  return path.startsWith('/uploads/') ? origin + path : path
}

/**
 * 原生 App 内让 `<img src="/uploads/...">` 指向后端。
 *
 * <p>图片路径散落在 30 多处模板里（商品图、头像、聊天图片……），且这些路径还会被
 * 原样提交回后端保存，不能在数据层改写。这里在 DOM 层统一处理：只在配置了后端
 * origin 时启用，把 `src` 以 `/uploads/` 开头的 img 改成绝对地址。Web 构建
 * （origin 为空）不安装，行为与以前完全一致。</p>
 *
 * @returns {() => void} 卸载函数
 */
export function installUploadsRewriter(root = document.documentElement, origin = API_ORIGIN) {
  if (!origin || typeof MutationObserver === 'undefined') return () => {}
  const fix = (img) => {
    const src = img.getAttribute('src')
    const next = assetUrl(src, origin)
    if (next !== src) img.setAttribute('src', next)
  }
  const scan = (node) => {
    if (node.nodeType !== 1) return
    if (node.tagName === 'IMG') fix(node)
    node.querySelectorAll?.('img[src^="/uploads/"]').forEach(fix)
  }
  scan(root)
  const observer = new MutationObserver((records) => {
    for (const r of records) {
      if (r.type === 'attributes') fix(r.target)
      else r.addedNodes.forEach(scan)
    }
  })
  observer.observe(root, { subtree: true, childList: true, attributes: true, attributeFilter: ['src'] })
  return () => observer.disconnect()
}
