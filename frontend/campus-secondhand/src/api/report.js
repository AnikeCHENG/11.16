import { API_BASE as BASE } from '@/utils/apiBase'

/**
 * 提交举报（C 端）。
 *
 * <p>后端对业务失败也返回 HTTP 200 + {@code code!=200}，因此这里不按
 * HTTP 状态抛错，而是把 ApiResponse 原样交给调用方判断；只有网络故障
 * 或响应不是 JSON 时才抛异常。</p>
 *
 * @param {number|string} targetId 被举报对象 ID
 * @param {'PRODUCT'|'USER'} targetType 对象类型
 * @param {string} reason 举报原因
 */
export async function submitReport(targetId, targetType, reason) {
  const token = localStorage.getItem('token') || sessionStorage.getItem('token')
  if (!token) {
    throw new Error('请先登录后再举报')
  }

  let res
  try {
    res = await fetch(`${BASE}/reports`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${token}`
      },
      body: JSON.stringify({ targetId, targetType, reason })
    })
  } catch (e) {
    const err = new Error('网络异常，举报未提交')
    err.cause = e
    throw err
  }

  const data = await res.json().catch(() => null)
  if (!data) {
    throw new Error('服务器返回异常，举报未提交')
  }
  return data
}
