import { describe, it, expect, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import App from '../App.vue'
import AppNavbar from '@/components/AppNavbar.vue'

/**
 * App 根组件的挂载契约。
 *
 * <p><b>此前这个测试是红的，而且红得有价值：</b>原用例是 `mount(App)` 后断言
 * 页面文本包含 `You did it!`——那是 Vite 脚手架模板的残留，本项目 App.vue
 * 里根本没有这句话，所以它永远不可能通过；同时因为没装 pinia，
 * 它会先在 `useMessageNotify() -> useNotificationStore()` 上抛
 * "getActivePinia() was called but there was no active Pinia"，
 * 连"渲染出错"和"断言过时"都分不清。</p>
 *
 * <p>现在改成验证 App 真正的职责：<b>依赖齐备时能挂载</b>，以及
 * <b>按路由 meta 决定是否渲染全局导航</b>（登录/注册/找回密码/404 不显示）。</p>
 */
function makeRouter() {
  const blank = { template: '<div />' }
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', name: 'home', component: { template: '<div>home</div>' } },
      {
        path: '/login',
        name: 'login',
        component: { template: '<div>login</div>' },
        meta: { navbar: false }
      },
      // AppNavbar 内含到这几个页面的 router-link；测试路由表若缺了它们，
      // 控制台会刷一片 "No match found for location"，淹掉真正的失败信息
      { path: '/plaza', component: blank },
      { path: '/products', component: blank },
      { path: '/messages', component: blank },
      { path: '/profile', component: blank }
    ]
  })
}

describe('App', () => {
  let router

  beforeEach(async () => {
    // App 的 setup 直接调用 useNotificationStore()，没有 active pinia 就会抛错
    setActivePinia(createPinia())
    router = makeRouter()
  })

  it('依赖齐备时能挂载，并按需渲染全局导航', async () => {
    await router.push('/')
    await router.isReady()

    const wrapper = mount(App, { global: { plugins: [router] } })

    expect(wrapper.findComponent(AppNavbar).exists()).toBe(true)
    expect(wrapper.text()).toContain('home')

    // 卸载以清掉 useMessageNotify 注册的轮询定时器
    wrapper.unmount()
  })

  it('meta.navbar === false 的路由不渲染导航（登录页有自己的头）', async () => {
    await router.push('/login')
    await router.isReady()

    const wrapper = mount(App, { global: { plugins: [router] } })

    expect(wrapper.findComponent(AppNavbar).exists()).toBe(false)
    expect(wrapper.text()).toContain('login')

    wrapper.unmount()
  })
})
