import { describe, it, expect, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import App from '../App.vue'
import { useUserStore } from '@/stores/user'
import admin from '@/api/admin'

/**
 * 「管理员能进、能用管理后台」的两道闸门。
 *
 * <p><b>此前两个真实故障：</b>①admin.js 的 baseURL 是 '/api' 而调用处传
 * '/users' 等子路径，全部打到 C 端路径 404；②状态切换传裸数字
 * （admin.put(url, 1)），axios 对非对象载荷不补 JSON Content-Type
 * （反而被兜成 x-www-form-urlencoded），后端 @RequestBody Integer
 * 解析失败。这组用例把两者钉死，并验证顶栏入口只因 role=1 而出现。</p>
 */
function makeRouter() {
  const blank = { template: '<div />' }
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', name: 'home', component: { template: '<div>home</div>' } },
      // AppNavbar 内的 router-link 目标；缺了它们控制台会刷 "No match found"
      { path: '/plaza', component: blank },
      { path: '/products', component: blank },
      { path: '/messages', component: blank },
      { path: '/profile', component: blank },
      { path: '/cart', component: blank },
      { path: '/post', component: blank },
      { path: '/drafts', component: blank },
      { path: '/admin', component: blank }
    ]
  })
}

async function mountAppAtHome() {
  const router = makeRouter()
  await router.push('/')
  await router.isReady()
  const wrapper = mount(App, { global: { plugins: [router] } })
  return wrapper
}

describe('管理员后台入口', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    localStorage.clear()
    sessionStorage.clear()
  })

  it('admin 实例 baseURL 指向 /api/admin（漏 /admin 后缀时全部 404）', () => {
    expect(admin.defaults.baseURL).toBe('/api/admin')
  })

  it('裸数字 body 经真实管线被序列化成 JSON 并带 application/json', async () => {
    const originalAdapter = admin.defaults.adapter
    let captured = null
    admin.defaults.adapter = async (config) => {
      captured = config
      return { data: { code: 200 }, status: 200, statusText: 'OK', headers: {}, config }
    }
    try {
      await admin.put('/users/999/status', 1)
      expect(captured.data).toBe('1')
      expect(captured.headers.getContentType()).toContain('application/json')
    } finally {
      admin.defaults.adapter = originalAdapter
    }
  })

  it('管理员登录后顶栏出现「管理后台」入口', async () => {
    localStorage.setItem('token', 'fake-token')
    const userStore = useUserStore()
    userStore.username = 'admin'
    userStore.role = 1
    userStore.verified = true

    const wrapper = await mountAppAtHome()
    const adminLink = wrapper.find('a[href="/admin"]')
    expect(adminLink.exists()).toBe(true)
    expect(adminLink.text()).toContain('管理后台')

    wrapper.unmount()
  })

  it('普通登录用户看不到「管理后台」入口', async () => {
    localStorage.setItem('token', 'fake-token')
    const userStore = useUserStore()
    userStore.role = 0
    userStore.verified = true

    const wrapper = await mountAppAtHome()
    expect(wrapper.find('a[href="/admin"]').exists()).toBe(false)

    wrapper.unmount()
  })

  it('role=1 但身份未经服务端确认（verified=false）时入口同样不出现', async () => {
    localStorage.setItem('token', 'fake-token')
    const userStore = useUserStore()
    userStore.role = 1
    userStore.verified = false

    const wrapper = await mountAppAtHome()
    expect(wrapper.find('a[href="/admin"]').exists()).toBe(false)

    wrapper.unmount()
  })
})
