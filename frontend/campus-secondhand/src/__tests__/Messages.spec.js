import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'

/**
 * 聊天页回归测试。
 *
 * <p>背景：用户反馈「无法正常聊天」。实测根因有两处，且都不在后端：</p>
 * <ol>
 *   <li>布局塌陷：.msg-main 是 flex 项，子元素 .msg-shell 用 height:100% 解析失败，
 *       面板只剩约 110px 高，输入框被裁掉；</li>
 *   <li>窄窗口下 @media (max-width:860px) 直接把会话列表 display:none，
 *       既看不到联系人也没有任何入口。</li>
 * </ol>
 *
 * <p>这里用 mock 接口把「列表 → 打开会话 → 发送 → 渲染」整条链路固定下来，
 * 并把「选中会话后进入 chat-open 状态（窄屏下用返回键切回列表）」作为契约。</p>
 */
vi.mock('../api/message', () => ({
  getConversations: vi.fn(),
  getConversation: vi.fn(),
  getSentMessages: vi.fn(),
  getUnreadCount: vi.fn(),
  sendMessage: vi.fn(),
  markAsRead: vi.fn(),
  markAllAsRead: vi.fn(),
  deleteMessage: vi.fn()
}))
vi.mock('../api/user', () => ({ getUserById: vi.fn() }))
vi.mock('../api/product', () => ({ getProductDetail: vi.fn() }))
vi.mock('../api/upload', () => ({ uploadImage: vi.fn() }))

import Messages from '../views/Messages.vue'
import * as messageApi from '../api/message'

const ME = 11
const OTHER = 28

function makeRouter() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/messages', component: { template: '<div />' } }]
  })
}

async function mountMessages() {
  const router = makeRouter()
  await router.push('/messages')
  await router.isReady()
  const wrapper = mount(Messages, {
    global: {
      plugins: [router],
      stubs: { VerifiedBadge: true }
    }
  })
  await flushPromises()
  return wrapper
}

describe('Messages 聊天页', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
    localStorage.setItem('token', 'test-token')
    localStorage.setItem('userId', String(ME))
    localStorage.setItem('user', JSON.stringify({ id: ME, username: 'admin1' }))

    messageApi.getUnreadCount.mockResolvedValue({ code: 200, data: 0 })
    messageApi.markAsRead.mockResolvedValue({ code: 200 })
    messageApi.getSentMessages.mockResolvedValue({ code: 200, data: [] })
    messageApi.getConversations.mockResolvedValue({
      code: 200,
      data: [{
        userId: OTHER,
        username: 'demo_student',
        avatar: null,
        lastMessage: '在吗',
        lastMessageTime: '2026-11-16T13:00:00',
        unreadCount: 1
      }]
    })
    messageApi.getConversation.mockResolvedValue({
      code: 200,
      data: [{
        id: 1,
        senderId: OTHER,
        receiverId: ME,
        content: '在吗',
        msgType: 0,
        caption: null,
        isRead: 0,
        createdTime: '2026-11-16T13:00:00'
      }]
    })
  })

  it('会话列表能渲染，点击后进入聊天态并显示返回键', async () => {
    const wrapper = await mountMessages()

    const conv = wrapper.find('.conv')
    expect(conv.exists()).toBe(true)
    expect(conv.text()).toContain('demo_student')

    await conv.trigger('click')
    await flushPromises()

    // 窄屏下靠 chat-open 在「列表 / 聊天」之间切换
    expect(wrapper.find('.msg-shell').classes()).toContain('chat-open')
    expect(wrapper.find('.chat-back').exists()).toBe(true)
    // 对方消息渲染在左侧
    expect(wrapper.find('.message.received').exists()).toBe(true)

    // 返回键切回列表
    await wrapper.find('.chat-back').trigger('click')
    expect(wrapper.find('.msg-shell').classes()).not.toContain('chat-open')

    wrapper.unmount()
  })

  it('发送文本后本地追加自己一侧的气泡（右侧 sent）', async () => {
    messageApi.sendMessage.mockResolvedValue({ code: 200, data: { id: 99 } })
    const wrapper = await mountMessages()

    await wrapper.find('.conv').trigger('click')
    await flushPromises()

    const input = wrapper.find('.chat-input input[type="text"]')
    await input.setValue('你好，这件商品还在吗？')
    const sendBtn = wrapper.findAll('.chat-input button').find(b => b.text().trim() === '发送')
    await sendBtn.trigger('click')
    await flushPromises()

    expect(messageApi.sendMessage).toHaveBeenCalledWith(
      expect.objectContaining({ receiverId: OTHER, content: '你好，这件商品还在吗？' })
    )
    const sent = wrapper.findAll('.message.sent')
    expect(sent.length).toBe(1)
    expect(sent[0].text()).toContain('你好，这件商品还在吗？')
    expect(input.element.value).toBe('')

    wrapper.unmount()
  })

  it('图片消息渲染为图片气泡，配文一并显示', async () => {
    messageApi.getConversation.mockResolvedValue({
      code: 200,
      data: [{
        id: 2,
        senderId: OTHER,
        receiverId: ME,
        content: '/uploads/2026/11/abc.png',
        msgType: 1,
        caption: '看图',
        isRead: 1,
        createdTime: '2026-11-16T13:01:00'
      }]
    })
    const wrapper = await mountMessages()

    await wrapper.find('.conv').trigger('click')
    await flushPromises()

    const thumb = wrapper.find('.msg-image-thumb')
    expect(thumb.exists()).toBe(true)
    expect(thumb.attributes('src')).toBe('/uploads/2026/11/abc.png')
    expect(wrapper.find('.msg-image p').text()).toBe('看图')

    // 点击图片打开放大预览
    await thumb.trigger('click')
    expect(wrapper.find('.lightbox-mask').exists()).toBe(true)

    wrapper.unmount()
  })

  it('切到「已发送」时按收件人聚合并展示最近一条', async () => {
    messageApi.getSentMessages.mockResolvedValue({
      code: 200,
      data: [
        { id: 7, receiverId: OTHER, content: '第二条', msgType: 0, createdTime: '2026-11-16T13:05:00', receiver: { id: OTHER, username: 'demo_student' } },
        { id: 6, receiverId: OTHER, content: '第一条', msgType: 0, createdTime: '2026-11-16T13:04:00', receiver: { id: OTHER, username: 'demo_student' } }
      ]
    })
    const wrapper = await mountMessages()

    const sentTab = wrapper.findAll('.tab').find(t => t.text().trim() === '已发送')
    await sentTab.trigger('click')
    await flushPromises()

    const convs = wrapper.findAll('.conv')
    expect(convs.length).toBe(1)
    expect(convs[0].text()).toContain('demo_student')
    expect(convs[0].text()).toContain('第二条')

    wrapper.unmount()
  })
})
