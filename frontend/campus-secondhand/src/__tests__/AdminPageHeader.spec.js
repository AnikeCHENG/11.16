import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import AdminPageHeader from '../components/AdminPageHeader.vue'
import { logout } from '@/api/auth'
import { useUserStore } from '@/stores/user'
import { useCartStore } from '@/stores/cart'
import { useNotificationStore } from '@/stores/notification'

vi.mock('@/api/auth', () => ({
  logout: vi.fn(async () => ({ code: 200 }))
}))

function makeRouter() {
  const blank = { template: '<div />' }
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', component: blank },
      { path: '/admin', component: blank },
      { path: '/admin/users', component: blank },
      { path: '/login', component: blank }
    ]
  })
}

async function mountHeader(props = {}, slots = {}) {
  const router = makeRouter()
  await router.push('/admin/users')
  await router.isReady()
  const wrapper = mount(AdminPageHeader, {
    props: { title: '用户管理', ...props },
    slots,
    global: { plugins: [router] }
  })
  return { wrapper, router }
}

describe('AdminPageHeader', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
  })

  it('渲染 ADMIN 小标、标题与副标题', async () => {
    const { wrapper } = await mountHeader({ subtitle: '副标题' })
    expect(wrapper.find('.aph-eyebrow').text()).toBe('ADMIN')
    expect(wrapper.find('h1').text()).toBe('用户管理')
    expect(wrapper.find('.aph-subtitle').text()).toBe('副标题')
  })

  it('返回控制面板是真实 <a href="/admin">，点击走 SPA 路由', async () => {
    const { wrapper, router } = await mountHeader()
    const back = wrapper.find('[data-test="admin-back"]')
    expect(back.element.tagName).toBe('A')
    expect(back.attributes('href')).toBe('/admin')
    expect(back.text()).toBe('返回控制面板')

    await back.trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.path).toBe('/admin')
  })

  it('back=false 时不渲染返回链接，actions 插槽保留', async () => {
    const { wrapper } = await mountHeader(
      { back: false },
      { actions: '<a class="btn home-link" href="/">返回首页</a>' }
    )
    expect(wrapper.find('[data-test="admin-back"]').exists()).toBe(false)
    expect(wrapper.find('.home-link').text()).toBe('返回首页')
    expect(wrapper.find('[data-test="admin-logout"]').exists()).toBe(true)
  })

  it('退出登录：调用 logout()、清 store，并跳转 /login', async () => {
    const { wrapper, router } = await mountHeader()
    const userStore = useUserStore()
    const cartStore = useCartStore()
    const notificationStore = useNotificationStore()
    const userReset = vi.spyOn(userStore, 'reset')
    const cartReset = vi.spyOn(cartStore, 'reset')
    const unreadReset = vi.spyOn(notificationStore, 'resetUnreadCount')

    await wrapper.find('[data-test="admin-logout"]').trigger('click')
    await flushPromises()

    expect(logout).toHaveBeenCalledTimes(1)
    expect(userReset).toHaveBeenCalledTimes(1)
    expect(cartReset).toHaveBeenCalledTimes(1)
    expect(unreadReset).toHaveBeenCalledTimes(1)
    expect(router.currentRoute.value.path).toBe('/login')
  })

  it('logout 接口抛错也完成本地清理并跳转', async () => {
    logout.mockRejectedValueOnce(new Error('boom'))
    const { wrapper, router } = await mountHeader()
    const userReset = vi.spyOn(useUserStore(), 'reset')

    await wrapper.find('[data-test="admin-logout"]').trigger('click')
    await flushPromises()

    expect(userReset).toHaveBeenCalled()
    expect(router.currentRoute.value.path).toBe('/login')
  })
})
