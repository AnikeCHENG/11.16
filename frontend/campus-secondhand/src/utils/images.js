/**
 * 商品图片字段的解析工具。
 *
 * <p>{@code products.images} 是<b>逗号分隔的多图串</b>，但每一张都可能是
 * Base64 Data URL（{@code data:image/jpeg;base64,AAAA...}）——而 base64 载荷
 * 自身<b>也含逗号</b>。所以裸 {@code split(',')} 会把 URL 拦腰截断成
 * {@code data:image/jpeg;base64}，图片彻底失效。</p>
 *
 * <p>后端 {@code OrderService#firstImage} 用的是同一套思路：base64 字母表
 * {@code A-Za-z0-9+/=} 不含逗号，因此从 {@code base64,} 之后开始找下一个逗号，
 * 才是真正的多图分隔符。这里保持与后端一致。</p>
 */

/** Base64 Data URL 的载荷起始标记 */
const BASE64_MARKER = 'base64,'

/** Data URL 的协议头 */
const DATA_URL_HEAD = 'data:'

/**
 * 把多图串拆成图片列表。
 *
 * <p>算法：逐张扫描。遇到 {@code data:...;base64,} 就说明当前这张是 base64 图，
 * 它的结束位置是「{@code base64,} 之后的下一个逗号」；否则当前逗号就是分隔符。
 * base64 字母表不含逗号，也不会出现 {@code data:}，因此这个判定是可靠的。</p>
 *
 * @param {string|string[]|null|undefined} raw 逗号分隔的多图串，或已是数组
 * @returns {string[]} 图片地址列表（已去空白、过滤空项）
 */
export function parseImages(raw) {
  if (!raw) {
    return []
  }
  if (Array.isArray(raw)) {
    return raw.map((x) => String(x).trim()).filter(Boolean)
  }
  const text = String(raw).trim()
  if (!text) {
    return []
  }

  const parts = []
  let cursor = 0

  while (cursor < text.length) {
    const markerAt = text.indexOf(BASE64_MARKER, cursor)
    if (markerAt < 0) {
      // 剩下没有 base64 了，逗号就是普通分隔符
      for (const piece of text.slice(cursor).split(',')) {
        const t = piece.trim()
        if (t) parts.push(t)
      }
      break
    }

    // 这一张 base64 图的起点：marker 之前最近的 "data:"，且必须在最后一个逗号之后
    // （否则说明 "data:" 只是上一次切分留下的残片，属于普通路径部分）。
    const dataStart = text.lastIndexOf(DATA_URL_HEAD, markerAt)
    const commaBefore = text.lastIndexOf(',', markerAt)
    let imageStart = cursor
    if (dataStart > commaBefore) {
      imageStart = dataStart
      // data: 之前到 cursor 之间是若干张普通路径图
      for (const piece of text.slice(cursor, imageStart).split(',')) {
        const t = piece.trim()
        if (t) parts.push(t)
      }
    }

    // base64 图的结束：base64, 之后的下一个逗号
    const from = markerAt + BASE64_MARKER.length
    const sep = text.indexOf(',', from)
    if (sep < 0) {
      parts.push(text.slice(imageStart).trim())
      break
    }
    parts.push(text.slice(imageStart, sep).trim())
    cursor = sep + 1
  }

  return parts.filter(Boolean)
}

/**
 * 取第一张图。
 *
 * @param {string|string[]|null|undefined} raw 逗号分隔的多图串
 * @param {string} [fallback] 取不到时返回的占位图
 * @returns {string}
 */
export function firstImage(raw, fallback = '/sample/phone.svg') {
  const list = parseImages(raw)
  return list.length ? list[0] : fallback
}

export default { parseImages, firstImage }
