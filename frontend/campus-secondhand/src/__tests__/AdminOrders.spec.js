import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import AdminOrders from '../views/Admin/AdminOrders.vue'
import admin from '@/api/admin'

vi.mock('@/api/admin', () => ({
  default: {
    get: vi.fn(),
    put: vi.fn(),
    post: vi.fn(),
    delete: vi.fn()
  }
}))

function order(id, status) {
  return {
    id,
    status,
    orderNo: `ORD-${id}`,
    buyerName: '买家',
    sellerName: '卖家',
    productTitle: '测试商品',
    price: 10,
    createdTime: '2026-10-01T00:00:00'
  }
}

describe('AdminOrders actions', () => {
  beforeEach(() => {
    admin.get.mockReset()
  })

  it('only shows the cancel button for unpaid orders', async () => {
    admin.get.mockResolvedValue({
      data: {
        code: 200,
        data: { total: 3, list: [order(1, 0), order(2, 1), order(3, 2)] }
      }
    })

    const wrapper = mount(AdminOrders)
    await flushPromises()

    const items = wrapper.findAll('.order-item')
    expect(items).toHaveLength(3)

    // 待支付：可以取消（走 adminCancelUnpaid）
    expect(items[0].text()).toContain('取消订单')
    // 已支付：取消必然 400，界面不应给出这个入口，应引导走退款
    expect(items[1].text()).not.toContain('取消订单')
    expect(items[2].text()).not.toContain('取消订单')
    expect(items[1].text()).toContain('强制退款')

    wrapper.unmount()
  })
})
