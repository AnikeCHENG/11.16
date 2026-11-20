import { API_BASE as BASE } from '@/utils/apiBase'

async function request(path, options = {}) {
  try {
    const res = await fetch(`${BASE}${path}`, {
      headers: {
        'Content-Type': 'application/json',
      },
      ...options,
    })
    const data = await res.json().catch(() => ({ message: '服务器返回非JSON' }))
    if (!res.ok) {
      const err = new Error(data?.message || `HTTP ${res.status}`)
      err.response = data
      throw err
    }
    if (data && typeof data.code === 'number' && data.code !== 200) {
      const err = new Error(data.message || '请求未成功')
      err.response = data
      throw err
    }
    return data
  } catch (e) {
    const err = new Error('Network request failed')
    err.cause = e
    throw err
  }
}

export async function getPublicStats() {
  return await request('/stats/public')
}

export default { getPublicStats }
