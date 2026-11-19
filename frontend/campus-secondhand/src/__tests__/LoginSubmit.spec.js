import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import ElementPlus from 'element-plus'
import Login from '../views/Login.vue'
import Register from '../views/Register.vue'
import auth from '../api/auth'

vi.mock('../api/auth', () => ({
  default: {
    login: vi.fn(),
    register: vi.fn(),
    sendCode: vi.fn()
  }
}))

const AuthShellStub = { template: '<div><slot name="brand" /><slot /></div>' }

function makeRouter() {
  const blank = { template: '<div />' }
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', component: blank },
      { path: '/login', component: blank },
      { path: '/register', component: blank },
      { path: '/forgot', component: blank }
    ]
  })
}

async function mountView(View) {
  const router = makeRouter()
  await router.push('/login')
  await router.isReady()
  return mount(View, {
    attachTo: document.body,
    global: { plugins: [ElementPlus, router], stubs: { AuthShell: AuthShellStub } }
  })
}

describe('DEF-02 登录页回车提交', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    localStorage.clear()
    sessionStorage.clear()
  })

  it('用户名/密码与提交按钮在同一个 <form> 内，且按钮是 type="submit"（浏览器回车隐式提交的前提）', async () => {
    const wrapper = await mountView(Login)
    const form = wrapper.find('form')
    expect(form.exists()).toBe(true)
    expect(form.find('#login-username').exists()).toBe(true)
    expect(form.find('#login-password').exists()).toBe(true)
    const submit = form.find('button[type="submit"]')
    expect(submit.exists()).toBe(true)
    expect(submit.text()).toContain('登录')
    wrapper.unmount()
  })

  it('表单 submit（回车）触发登录请求', async () => {
    auth.login.mockResolvedValue({ token: 't', user: { id: 1, username: 'alice', role: 0 } })
    const wrapper = await mountView(Login)
    await wrapper.find('#login-username').setValue('alice')
    await wrapper.find('#login-password').setValue('secret1')

    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(auth.login).toHaveBeenCalledTimes(1)
    expect(auth.login).toHaveBeenCalledWith({ username: 'alice', password: 'secret1' })
    wrapper.unmount()
  })

  it('请求进行中连续回车只提交一次，按钮处于禁用态', async () => {
    let resolve
    auth.login.mockReturnValue(new Promise((r) => { resolve = r }))
    const wrapper = await mountView(Login)
    await wrapper.find('#login-username').setValue('alice')
    await wrapper.find('#login-password').setValue('secret1')

    const form = wrapper.find('form')
    await form.trigger('submit')
    await form.trigger('submit')
    await form.trigger('submit')
    await flushPromises()

    expect(auth.login).toHaveBeenCalledTimes(1)
    expect(wrapper.find('button[type="submit"]').attributes('disabled')).toBeDefined()
    expect(wrapper.find('button[type="submit"]').text()).toContain('登录中')

    resolve({ token: 't', user: { id: 1, username: 'alice', role: 0 } })
    await flushPromises()
    expect(wrapper.find('button[type="submit"]').attributes('disabled')).toBeUndefined()
    wrapper.unmount()
  })

  it('注册页同样可回车提交（submit 按钮 + form submit 触发注册）', async () => {
    auth.register.mockResolvedValue({})
    const wrapper = await mountView(Register)
    const form = wrapper.find('form')
    expect(form.find('button[type="submit"]').exists()).toBe(true)
    // 「获取验证码」按钮不能是 submit，否则回车会去发验证码
    const others = form.findAll('button').filter((b) => b.attributes('type') !== 'submit')
    expect(others.every((b) => b.attributes('type') === 'button')).toBe(true)
    wrapper.unmount()
  })
})
