/**
 * 会话与身份的单一出入口。
 *
 * <p><b>为什么需要这个模块</b>：登录页在勾选「记住我」时把 token 写进
 * localStorage，未勾选时写进 sessionStorage；路由守卫和所有 api 模块读 token
 * 都是 {@code localStorage || sessionStorage}。于是同一个用户可能同时在
 * 两个存储里留有凭证，只清一个就等于没登出——这是此前 logout() 漏删
 * sessionStorage.token 的根因。</p>
 *
 * <p><b>身份串号</b>：登出若不清 userId / user，下一位登录者登录后
 * {@code resolveUserId()} 会先命中上一位用户残留的 localStorage.userId，
 * 导致「是不是我的商品」「我是买家还是卖家」判错。这里让 userId 始终与
 * <b>持有 token 的那个存储</b>保持同源，取值顺序由 token 的来源决定，
 * 不再固定 localStorage 优先。</p */

/** 与登录身份相关的键，登出时必须全部清掉 */
const IDENTITY_KEYS = [
  'token',
  'user',
  'userId',
  'username',
  'avatar',
  'bio',
  'location',
  'qq',
  'wechat',
]

/** 草稿、搜索历史等与身份无关的数据，登出时保留 */
const NON_IDENTITY_KEYS = ['product_draft', 'product_drafts', 'editing_draft']

/**
 * 取当前 token。
 *
 * @returns {string} 没有登录时返回空串
 */
export function getToken() {
  return localStorage.getItem('token') || sessionStorage.getItem('token') || ''
}

/**
 * 清空登录身份。
 *
 * <p>两个存储都要清：未勾「记住我」时 token 在 sessionStorage，
 * 只清 localStorage 等于没登出。</p>
 */
export function clearSession() {
  for (const store of [localStorage, sessionStorage]) {
    for (const key of IDENTITY_KEYS) {
      store.removeItem(key)
    }
  }
  sessionStorage.removeItem('justLoggedIn')
}

/**
 * 取当前登录用户 ID。
 *
 * <p><b>与 token 同源</b>：先看哪个存储持有 token，就只从那个存储取 userId /
 * user，避免残留的另一个存储里的旧身份被误用。</p>
 *
 * @returns {number} 未登录或取不到时返回 0
 */
export function getSessionUserId() {
  const stores = []
  if (localStorage.getItem('token')) stores.push(localStorage)
  if (sessionStorage.getItem('token')) stores.push(sessionStorage)
  // 两个存储都没有 token 时（未登录或异常态），仍按原顺序兜底一次
  if (stores.length === 0) stores.push(localStorage, sessionStorage)

  for (const store of stores) {
    const direct = parseInt(store.getItem('userId') || '', 10)
    if (Number.isFinite(direct) && direct > 0) return direct
  }
  for (const store of stores) {
    try {
      const raw = store.getItem('user')
      if (!raw) continue
      const id = parseInt(JSON.parse(raw)?.id, 10)
      if (Number.isFinite(id) && id > 0) return id
    } catch {
      /* 数据损坏，忽略 */
    }
  }
  return 0
}

/**
 * 取当前登录用户名（仅供展示用，不作为权限依据）。
 *
 * @returns {string}
 */
export function getSessionUsername() {
  const stores = []
  if (localStorage.getItem('token')) stores.push(localStorage)
  if (sessionStorage.getItem('token')) stores.push(sessionStorage)
  if (stores.length === 0) stores.push(localStorage, sessionStorage)

  for (const store of stores) {
    const name = store.getItem('username')
    if (name) return name
    try {
      const raw = store.getItem('user')
      if (!raw) continue
      const parsed = JSON.parse(raw)
      if (parsed?.username) return parsed.username
    } catch {
      /* 数据损坏，忽略 */
    }
  }
  return ''
}

export { IDENTITY_KEYS, NON_IDENTITY_KEYS }
export default { getToken, clearSession, getSessionUserId, getSessionUsername }
