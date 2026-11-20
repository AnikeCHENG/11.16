import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import AdminMessages from '../views/Admin/AdminMessages.vue'
import admin from '@/api/admin'

vi.mock('@/api/admin', () => ({
  default: {
    get: vi.fn(),
    put: vi.fn(),
    post: vi.fn(),
    delete: vi.fn()
  }
}))

// 与后端 /api/admin/messages 实测返回一致：createdTime 字段，sender/receiver 为 null
function messagesResponse() {
  return {
    data: {
      code: 200,
      data: {
        total: 2,
        page: 1,
        size: 10,
        list: [
          {
            id: 6,
            senderId: 11,
            receiverId: 3,
            content: '你好，在吗？',
            msgType: 0,
            createdTime: '2026-11-16T13:06:57',
            sender: null,
            receiver: null
          },
          {
            id: 5,
            senderId: 3,
            receiverId: 11,
            content: 'hi',
            msgType: 0,
            createdTime: '2026-11-16T13:00:00',
            sender: { username: 'alice' },
            receiver: { username: 'admin1' }
          }
        ]
      }
    }
  }
}

describe('AdminMessages', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    admin.get.mockResolvedValue(messagesResponse())
  })

  it('使用统一页头（标题 + 返回 + 退出）', async () => {
    const wrapper = mount(AdminMessages)
    await flushPromises()
    expect(wrapper.find('.admin-page-header h1').text()).toBe('消息管理')
    expect(wrapper.find('[data-test="admin-back"]').attributes('href')).toBe('/admin')
    expect(wrapper.find('[data-test="admin-logout"]').exists()).toBe(true)
    wrapper.unmount()
  })

  it('提交时间读 createdTime，不再空白', async () => {
    const wrapper = mount(AdminMessages)
    await flushPromises()
    const dates = wrapper.findAll('.message-date').map((n) => n.text())
    expect(dates[0]).toBe(new Date('2026-11-16T13:06:57').toLocaleString())
    expect(dates.every((d) => d.length > 0)).toBe(true)
    wrapper.unmount()
  })

  it('无用户对象时显示「用户 #id」，有用户名时显示用户名', async () => {
    const wrapper = mount(AdminMessages)
    await flushPromises()
    const items = wrapper.findAll('.message-item')
    expect(items[0].find('.message-sender').text()).toContain('用户 #11')
    expect(items[0].find('.message-receiver').text()).toContain('用户 #3')
    expect(items[1].find('.message-sender').text()).toContain('alice')
    expect(items[1].find('.message-receiver').text()).toContain('admin1')
    expect(wrapper.text()).not.toContain('未知用户')
    wrapper.unmount()
  })

  it('请求失败显示错误块并可重试', async () => {
    admin.get.mockRejectedValueOnce(new Error('Network Error'))
    const wrapper = mount(AdminMessages)
    await flushPromises()
    expect(wrapper.find('.error-container').text()).toContain('Network Error')

    await wrapper.find('.error-container button').trigger('click')
    await flushPromises()
    expect(admin.get).toHaveBeenCalledTimes(2)
    expect(wrapper.find('.error-container').exists()).toBe(false)
    expect(wrapper.findAll('.message-item')).toHaveLength(2)
    wrapper.unmount()
  })
})
