// 评价与学生认证相关接口
//
// 路径归属两个不同前缀：订单评价在 /api/orders，用户信息在 /api/user，
// 因此这里各自定义 BASE，与 user.js / order.js 保持同样的写法。
// 之所以不抽公共 request 到一起，是因为现有 api 模块各自维护 BASE 已是既定风格，
// 单独抽一层反而要改动全部 8 个模块，超出本次范围。

const USER_BASE = import.meta.env.VITE_API_BASE || '/api/user'
const ORDER_BASE = import.meta.env.VITE_ORDER_BASE || '/api/orders'

async function request(base, path, options = {}) {
  let token = localStorage.getItem('token') || sessionStorage.getItem('token')
  const headers = { 'Content-Type': 'application/json' }
  if (token) headers['Authorization'] = `Bearer ${token}`

  const res = await fetch(`${base}${path}`, { ...options, headers })
  if (!res.ok) throw new Error(`请求失败：${res.status}`)
  const text = await res.text()
  if (!text) return { code: res.status, data: null, message: '' }
  try {
    return JSON.parse(text)
  } catch {
    // 后端返回非 JSON（如反向代理的 HTML 错误页）时不要把解析异常抛给调用方
    return { code: res.status, data: null, message: '服务器返回了无法解析的内容' }
  }
}

/** 提交订单评价：仅买家本人、仅已完成订单、一单一评 */
export async function submitReview(orderId, rating, content) {
  return await request(ORDER_BASE, `/${orderId}/review`, {
    method: 'POST',
    body: JSON.stringify({ rating, content })
  })
}

/** 订单评价回显；未评价时后端返回 code=200 且 data=null */
export async function getOrderReview(orderId) {
  return await request(ORDER_BASE, `/${orderId}/review`, { method: 'GET' })
}

/**
 * 卖家评价统计。
 *
 * <p>路径与 UserController 现有风格一致（该Controller 下用户详情为
 * {@code /api/user/user/{id}}），因此这里同样是 {@code /api/user/user/{id}/review-stats}。</p>
 */
export async function getReviewStats(userId) {
  return await request(USER_BASE, `/user/${userId}/review-stats`, { method: 'GET' })
}

/** 卖家收到的文字评价（默认最近 5 条），商品详情页展示 */
export async function getSellerReviews(userId, limit = 5) {
  return await request(USER_BASE, `/user/${userId}/reviews?limit=${limit}`, { method: 'GET' })
}

/** 学生认证：当前为模拟校验，接口预留，未来可对接教务系统或改为人工审核 */
export async function studentVerify(studentNo, realName) {
  return await request(USER_BASE, '/student-verify', {
    method: 'POST',
    body: JSON.stringify({ studentNo, realName })
  })
}

/**
 * 上传认证截图到私有目录，返回不透明的文件名标识。
 *
 * <p>不用 api/upload.js 的公开上传：那里落在 /uploads/** 静态目录，
 * 任何拿到 URL 的人都能直接打开认证截图。认证截图只有审核人员能看。</p>
 *
 * @param {File} file 选中的截图文件
 * @returns {Promise<string>} 私有文件名标识
 */
export async function uploadVerificationProof(file) {
  const token = localStorage.getItem('token') || sessionStorage.getItem('token')
  if (!token) {
    throw new Error('请先登录后再上传图片')
  }

  const form = new FormData()
  form.append('file', file, file.name)

  let res
  try {
    res = await fetch(`${USER_BASE}/student-verification/proof`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${token}` },
      body: form
    })
  } catch {
    throw new Error('截图上传失败，请检查网络')
  }

  const data = await res.json().catch(() => null)
  if (!res.ok || !data || data.code !== 200 || !data.data?.proofId) {
    throw new Error(data?.message || '截图上传失败')
  }
  return data.data.proofId
}

/**
 * 提交「支付宝学生认证截图」辅助认证申请（辅助方式，需管理员人工审核）。
 *
 * proofImage 是先经 uploadVerificationProof() 上传换回的私有文件名标识。
 * agree 表示用户已勾选「资料仅用于本平台学生身份审核」，后端会强制校验。
 */
export async function submitScreenshotVerification(payload) {
  return await request(USER_BASE, '/student-verification/screenshot', {
    method: 'POST',
    body: JSON.stringify(payload)
  })
}

/** 查询我的支付宝截图辅助认证进度（最新一条申请；未提交过时 data 为 null） */
export async function getMyScreenshotVerification() {
  return await request(USER_BASE, '/student-verification', { method: 'GET' })
}