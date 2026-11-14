const BASE = import.meta.env.VITE_API_BASE || '/api'

async function request(path, options = {}) {
  const token = localStorage.getItem('token') || sessionStorage.getItem('token')
  try {
    const headers = {
      'Content-Type': 'application/json'
    }
    if (token) {
      headers['Authorization'] = `Bearer ${token}`
    }
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
    // 保留服务端/业务错误文案：此前统一改写成 'Network request failed'，
    // 用户看到的是网络故障，实际可能是「未登录」「订单状态已变化」等业务原因。
    // response 同样要带出去：调用方靠它读 code 区分 401/400/业务码，
    // 重新 new Error 只会丢掉内层挂上的字段。
    const err = new Error(e.message || 'Network request failed')
    err.cause = e
    err.response = e.response
    throw err
  }
}

/**
 * 与我相关的订单（我买到的 + 我卖出的），服务端分页。
 *
 * <p>返回 data 结构：{ list: [], total, page, size }。</p>
 *
 * <p><b>不再返回演示数据。</b>此前无 token 或请求失败时会兜底到一组写死的
 * MOCK_ORDERS，后果是：未登录也能看到「我的订单」，接口挂了页面照样有数据，
 * 用户以为订单真的存在。失败就应当失败——由调用方展示空态/错误提示。</p>
 */
export async function getOrderList(page = 1, size = 10) {
  return await request(`/orders/my?page=${page}&size=${size}`)
}

// 发货：仅卖家、仅「待发货」订单，后端用 `WHERE id=? AND status=1` 的条件更新推进
export async function shipOrder(orderId) {
  return await request(`/orders/${orderId}/ship`, {
    method: 'POST'
  })
}

// 确认收货：仅买家、仅「待收货」订单，后端条件更新 `status=2 -> 3`
//
// 此前这里发的是 `PUT /api/orders/{id}` + `{status: 3}`：那个通用端点只校验
// 「调用者是买卖双方之一」，于是买家能把刚下单的待支付订单直接改成已完成。
// 后端已删除该端点，改为下面这个语义明确、角色与前置状态都绑死的接口。
export async function confirmOrder(orderId) {
  return await request(`/orders/${orderId}/confirm`, {
    method: 'POST'
  })
}

// 创建订单（收银台入口）
export async function createOrder(productId) {
  try {
    return await request('/orders/create', {
      method: 'POST',
      body: JSON.stringify({ productId })
    })
  } catch (error) {
    console.error('创建订单失败:', error)
    throw error
  }
}

// 获取收银台详情（含商品快照、金额明细、卖家信息、剩余支付秒数）
export async function getOrderDetail(orderId) {
  return await request(`/orders/${orderId}`, { method: 'GET' })
}

// 模拟支付：仅待支付且未过期的订单可支付
// 参数名与后端约定为 pay_method；后端不做白名单校验，传什么记什么
export async function payOrder(orderId, payMethod) {
  return await request(`/orders/${orderId}/pay`, {
    method: 'POST',
    body: JSON.stringify({ pay_method: payMethod })
  })
}

// 取消订单（商品恢复在售）
export async function cancelOrderById(orderId) {
  return await request(`/orders/${orderId}/cancel`, { method: 'POST' })
}

// 买家申请退款（仅已支付未收货：1 待发货 / 2 待收货）
export async function applyRefund(orderId, reason) {
  return await request(`/orders/${orderId}/refund/apply`, {
    method: 'POST',
    body: JSON.stringify({ reason })
  })
}

// 卖家同意退款（仅「退款中」订单）
export async function approveRefund(orderId, reply) {
  return await request(`/orders/${orderId}/refund/approve`, {
    method: 'POST',
    body: JSON.stringify({ reply })
  })
}

// 卖家拒绝退款（仅「退款中」订单）
export async function rejectRefund(orderId, reply) {
  return await request(`/orders/${orderId}/refund/reject`, {
    method: 'POST',
    body: JSON.stringify({ reply })
  })
}

// 买家撤销退款申请（仅「退款中」订单）
export async function cancelRefundRequest(orderId) {
  return await request(`/orders/${orderId}/refund/cancel`, { method: 'POST' })
}

/**
 * 批量支付：逐笔支付，每笔须 status=0 且属当前用户。
 * Body { orderIds: [...], payMethod }，返回 { tradeNo, totalAmount }。
 *
 * <p>参数名必须与后端 {@code OrderController#batchPay} 的契约一致：以
 * {@code payMethod} 为准。此前这里发的是 {@code pay_method}，而后端按
 * {@code payMethod} 读取，结果支付方式被静默丢成默认的 alipay——用户选
 * 「余额支付」时余额不会扣，订单却被标记为已支付。</p>
 */
export async function batchPayOrders(orderIds, payMethod = 'alipay') {
  return await request('/orders/batch-pay', {
    method: 'POST',
    body: JSON.stringify({ orderIds, payMethod })
  })
}
