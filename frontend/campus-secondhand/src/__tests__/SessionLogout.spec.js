import { describe, it, expect, beforeEach } from 'vitest'
import { logout } from '../api/auth'
import { clearSession, getSessionUserId, getToken } from '../utils/session'

/**
 * 登出与会话身份的一致性。
 *
 * <p>登录页勾「记住我」写 localStorage，不勾写 sessionStorage；
 * 路由守卫与所有 api 都按 {@code localStorage || sessionStorage} 读 token。
 * 因此登出必须把两个存储都清干净，否则「登出后仍是登录态」，
 * 且残留的 userId 会让下一位登录者串用上一位的身份。</p>
 */
describe('logout 清会话', () => {
  beforeEach(() => {
    localStorage.clear()
    sessionStorage.clear()
  })

  it('同时清掉 localStorage 与 sessionStorage 的 token', async () => {
    localStorage.setItem('token', 'local-token')
    sessionStorage.setItem('token', 'session-token')

    await logout()

    expect(localStorage.getItem('token')).toBeNull()
    expect(sessionStorage.getItem('token')).toBeNull()
    expect(getToken()).toBe('')
  })

  it('清掉全部身份键，保留草稿类数据', async () => {
    localStorage.setItem('token', 't')
    localStorage.setItem('user', JSON.stringify({ id: 7, username: 'alice' }))
    localStorage.setItem('userId', '7')
    localStorage.setItem('username', 'alice')
    localStorage.setItem('avatar', 'data:image/png;base64,AAA')
    localStorage.setItem('bio', 'hello')
    sessionStorage.setItem('token', 't2')
    sessionStorage.setItem('user', JSON.stringify({ id: 9, username: 'bob' }))
    sessionStorage.setItem('userId', '9')

    // 草稿不属于身份，登出后应保留
    localStorage.setItem('product_drafts', '[]')

    await logout()

    for (const store of [localStorage, sessionStorage]) {
      for (const key of ['token', 'user', 'userId', 'username', 'avatar', 'bio', 'location', 'qq', 'wechat']) {
        expect(store.getItem(key)).toBeNull()
      }
    }
    expect(localStorage.getItem('product_drafts')).toBe('[]')
  })
})

describe('getSessionUserId 与 token 同源', () => {
  beforeEach(() => {
    localStorage.clear()
    sessionStorage.clear()
  })

  it('token 在 sessionStorage 时，不会误用 localStorage 残留的 userId', () => {
    // 模拟：上一位用户勾了「记住我」后登出，只清了 sessionStorage（历史 bug 场景）
    localStorage.setItem('userId', '111')
    // 当前用户没勾「记住我」，token 在 sessionStorage
    sessionStorage.setItem('token', 'tok')
    sessionStorage.setItem('userId', '222')

    expect(getSessionUserId()).toBe(222)
  })

  it('两边都没有 userId 时，从 user JSON 里取', () => {
    localStorage.setItem('token', 'tok')
    localStorage.setItem('user', JSON.stringify({ id: 333 }))

    expect(getSessionUserId()).toBe(333)
  })

  it('user JSON 损坏时返回 0 而不是抛错', () => {
    localStorage.setItem('token', 'tok')
    localStorage.setItem('user', '{not-json')

    expect(getSessionUserId()).toBe(0)
  })
})

describe('clearSession 幂等', () => {
  it('重复调用不报错', () => {
    localStorage.setItem('token', 't')
    expect(() => {
      clearSession()
      clearSession()
    }).not.toThrow()
  })
})
