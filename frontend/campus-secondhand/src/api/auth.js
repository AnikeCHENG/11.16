const BASE = import.meta.env.VITE_API_BASE || '/api'

import { clearSession } from '../utils/session'

async function request(path, options = {}) {
  try {
    const res = await fetch(`${BASE}${path}`, {
      headers: { 'Content-Type': 'application/json' },
      ...options,
    })
    const data = await res.json().catch(() => ({ message: '服务器返回非JSON' }))
    if (!res.ok) {
      const err = new Error(data?.message || `HTTP ${res.status}`)
      err.response = data
      throw err
    }

    if (data && typeof data.code === 'number') {
      if (data.code !== 200) {
        const err = new Error(data.message || '请求未成功')
        err.response = data
        throw err
      } else {
        // 后端返回的结构是 { code: 200, message: '成功', data: {...} }
        // 所以直接返回data.data部分
        return data.data
      }
    }

    return data
  } catch (e) {
    const err = new Error(e.message || 'Network request failed')
    err.cause = e
    throw err
  }
}

export async function login({ username, password }) {
  return await request('/auth/login', { method: 'POST', body: JSON.stringify({ username, password }) })
}

export async function sendCode({ email }) {
  return await request('/auth/send-code', { method: 'POST', body: JSON.stringify({ email }) })
}

export async function register({ username, email, password, confirmPassword, code }) {
  return await request('/auth/register', { method: 'POST', body: JSON.stringify({ username, email, password, confirmPassword, code }) })
}

/**
 * 忘记密码第一步：请求发送重置验证码。
 *
 * 后端刻意不区分"邮箱是否存在"，一律返回同一句话，所以前端也不要把响应当成
 * "邮箱确实注册过"的证据——直接展示后端给的 message 即可。
 */
export async function forgotPassword({ email }) {
  return await request('/auth/forgot-password', {
    method: 'POST',
    body: JSON.stringify({ email })
  })
}

/** 忘记密码第二步：用邮箱收到的验证码设置新密码 */
export async function resetPassword({ email, code, newPassword }) {
  return await request('/auth/reset-password', {
    method: 'POST',
    body: JSON.stringify({ email, code, newPassword })
  })
}

// 后端使用无状态 JWT，无退出接口；前端清除本地凭证即可视为退出
export async function logout() {
  // 必须同时清 localStorage 与 sessionStorage：
  // 登录页未勾「记住我」时 token 写在 sessionStorage，
  // 只清 localStorage 会导致「登出后仍是登录态」。
  // 身份键（token/user/userId/username/头像等）一并清理，
  // 否则下一位登录者会串用上一位残留的 userId。
  clearSession()
  return { code: 200, message: '已退出登录' }
}

export default { login, register, sendCode, forgotPassword, resetPassword, logout }
