const BASE = import.meta.env.VITE_API_BASE || '/api'

/**
 * 公开分类列表（只返回启用项，按后台维护的排序）。
 *
 * <p>修复前用户端发布下拉、筛选 chips 全部是写死的数组，后台分类管理
 * 改什么都不影响用户端。现在统一从这里读，后台增删改才能真正生效。</p>
 *
 * @returns {Promise<Array<{id:number, code:string, name:string, sortOrder:number}>>}
 */
export async function getCategories() {
  const res = await fetch(`${BASE}/categories`)
  const data = await res.json().catch(() => null)
  if (!res.ok || !data || data.code !== 200) {
    throw new Error(data?.message || `分类加载失败（HTTP ${res.status}）`)
  }
  return data.data || []
}

export default { getCategories }
