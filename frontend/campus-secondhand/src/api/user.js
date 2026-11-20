// 用户相关接口的统一前缀。
//
// <p><b>必须自己拼 `/user`，不能直接复用 VITE_API_BASE：</b>该变量的语义是
// 「部署前缀」（开发环境为 `/api`，部署时可能是 `/prefix/api`），并不包含
// 具体资源段。此前写成 `VITE_API_BASE || '/api/user'`，一旦 .env 里设置了
// `/api`，`/profile` 就变成 `/api/profile`（后端真实路径是 `/api/user/profile`），
// 个人资料、余额、认证状态全部 404。</p>
import { API_BASE } from '@/utils/apiBase'
const BASE = `${API_BASE}/user`

async function request(path, options = {}) {
  let token = localStorage.getItem('token')
  if (!token) {
    token = sessionStorage.getItem('token')
  }

  const headers = {
    'Content-Type': 'application/json'
  }

  if (token) {
    headers['Authorization'] = `Bearer ${token}`
  }

  try {
    const res = await fetch(`${BASE}${path}`, {
      headers,
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
    const err = new Error(e.message || 'Network request failed')
    err.cause = e
    throw err
  }
}

export async function getProfile() {
  return await request('/profile')
}

export async function updateProfile(profileData) {
  return await request('/profile', {
    method: 'PUT',
    body: JSON.stringify(profileData)
  })
}

export async function getUserById(userId) {
  return await request(`/user/${userId}`)
}

export default { getProfile, updateProfile, getUserById }
