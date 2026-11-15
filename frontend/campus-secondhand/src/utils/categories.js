import { ref } from 'vue'
import { getCategories } from '../api/category'

/**
 * 分类选项的本地兜底。
 *
 * <p>接口不可用时仍要能发布商品/筛选：宁可展示历史分类，也不要让下拉框空着。
 * 值必须与后端 products.category 存的 slug 一致，不能写中文。</p>
 */
export const FALLBACK_CATEGORIES = [
  { value: 'books', label: '教材书籍', emoji: '📚' },
  { value: 'electronics', label: '电子产品', emoji: '💻' },
  { value: 'transport', label: '出行工具', emoji: '🚲' },
  { value: 'gaming', label: '游戏数码', emoji: '🎮' },
  { value: 'clothing', label: '服饰穿搭', emoji: '👕' },
  { value: 'living', label: '生活用品', emoji: '🧺' },
  { value: 'sports', label: '运动户外', emoji: '⚽' },
  { value: 'beauty', label: '美妆护肤', emoji: '💄' },
  { value: 'stationery', label: '学习用品', emoji: '✏️' },
  { value: 'other', label: '其他', emoji: '📦' }
]

const EMOJI_BY_CODE = Object.fromEntries(
  FALLBACK_CATEGORIES.map(item => [item.value, item.emoji])
)

/** 跨组件共享的分类选项（响应式：任一处加载完成，其它页面立即拿到） */
export const categoryOptions = ref(FALLBACK_CATEGORIES.slice())

let loaded = false
let inflight = null

/**
 * 加载分类（模块级缓存，避免每个页面重复请求）。
 * 接口失败时回退到 {@link FALLBACK_CATEGORIES}，不阻塞页面。
 */
export async function loadCategories(force = false) {
  if (loaded && !force) {
    return categoryOptions.value
  }
  if (inflight) {
    return inflight
  }
  inflight = getCategories()
    .then(list => {
      if (Array.isArray(list) && list.length > 0) {
        categoryOptions.value = list.map(item => ({
          value: item.code,
          label: item.name,
          emoji: EMOJI_BY_CODE[item.code] || '📦',
          id: item.id,
          sortOrder: item.sortOrder,
          status: item.status
        }))
      } else {
        categoryOptions.value = FALLBACK_CATEGORIES.slice()
      }
      return categoryOptions.value
    })
    .catch(() => {
      categoryOptions.value = FALLBACK_CATEGORIES.slice()
      return categoryOptions.value
    })
    .finally(() => {
      loaded = true
      inflight = null
    })
  return inflight
}

/** 分类代码 → 中文名；查不到回退原值，避免不同未知分类被混成一个「其他」 */
export function categoryLabel(code) {
  if (!code) return '其他'
  return categoryOptions.value.find(item => item.value === code)?.label || code
}

/** 分类代码 → emoji（无图商品占位）；查不到给中性 📦 */
export function categoryEmoji(code) {
  return categoryOptions.value.find(item => item.value === code)?.emoji || '📦'
}
