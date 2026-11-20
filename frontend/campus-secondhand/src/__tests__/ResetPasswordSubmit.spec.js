import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createRouter, createMemoryHistory } from 'vue-router'
import ElementPlus from 'element-plus'
import ResetPassword from '../views/ResetPassword.vue'
import auth from '../api/auth'

vi.mock('../api/auth', () => ({
  default: {
    forgotPassword: vi.fn(),
    resetPassword: vi.fn()
  }
}))

const AuthShellStub = { template: '<div><slot /></div>' }

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

async function mountView() {
  const router = makeRouter()
  await router.push('/forgot')
  await router.isReady()
  return mount(ResetPassword, {
    attachTo: document.body,
    global: { plugins: [ElementPlus, router], stubs: { AuthShell: AuthShellStub } }
  })
}

/** 走完第一步：填入邮箱并提交，使 codeSent 变为 true */
async function goToStep2(wrapper) {
  auth.forgotPassword.mockResolvedValue({})
  await wrapper.find('#reset-email').setValue('user@example.com')
  await wrapper.find('form').trigger('submit')
  await flushPromises()
}

describe('K-11 重置密码页回车提交', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('第一步：邮箱与「发送验证码」按钮在同一个 <form> 内，且按钮是 type="submit"', async () => {
    const wrapper = await mountView()
    const form = wrapper.find('form')
    expect(form.exists()).toBe(true)
    expect(form.find('#reset-email').exists()).toBe(true)
    const submit = form.find('button[type="submit"]')
    expect(submit.exists()).toBe(true)
    expect(submit.text()).toContain('发送验证码')
    wrapper.unmount()
  })

  it('第一步：表单 submit（回车）触发发送验证码请求', async () => {
    auth.forgotPassword.mockResolvedValue({})
    const wrapper = await mountView()
    await wrapper.find('#reset-email').setValue('user@example.com')

    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(auth.forgotPassword).toHaveBeenCalledTimes(1)
    expect(auth.forgotPassword).toHaveBeenCalledWith({ email: 'user@example.com' })
    wrapper.unmount()
  })

  it('验证码发送成功后：「重置密码」是 type="submit"，「获取验证码」仍是 type="button"', async () => {
    const wrapper = await mountView()
    await goToStep2(wrapper)

    const form = wrapper.find('form')
    const submit = form.find('button[type="submit"]')
    expect(submit.exists()).toBe(true)
    expect(submit.text()).toContain('重置密码')

    const codeBtn = form.find('.code-btn')
    expect(codeBtn.exists()).toBe(true)
    expect(codeBtn.attributes('type')).toBe('button')
    // 发送成功后进入 60s 倒计时，文案会变为「Ns 后重发」，因此只断言其不是 submit
    expect(codeBtn.attributes('type')).not.toBe('submit')
    wrapper.unmount()
  })

  it('第二步：表单 submit（回车）调用 auth.resetPassword 且载荷正确', async () => {
    const wrapper = await mountView()
    await goToStep2(wrapper)

    auth.resetPassword.mockResolvedValue({})
    await wrapper.find('#reset-code').setValue('123456')
    await wrapper.find('#reset-password').setValue('newsecret')
    await wrapper.find('#reset-confirm').setValue('newsecret')

    await wrapper.find('form').trigger('submit')
    await flushPromises()

    expect(auth.resetPassword).toHaveBeenCalledTimes(1)
    expect(auth.resetPassword).toHaveBeenCalledWith({
      email: 'user@example.com',
      code: '123456',
      newPassword: 'newsecret'
    })
    wrapper.unmount()
  })

  it('第二步：请求进行中连续回车只提交一次，按钮禁用并显示「重置中」', async () => {
    const wrapper = await mountView()
    await goToStep2(wrapper)

    let resolve
    auth.resetPassword.mockReturnValue(new Promise((r) => { resolve = r }))
    await wrapper.find('#reset-code').setValue('123456')
    await wrapper.find('#reset-password').setValue('newsecret')
    await wrapper.find('#reset-confirm').setValue('newsecret')

    const form = wrapper.find('form')
    await form.trigger('submit')
    await form.trigger('submit')
    await form.trigger('submit')
    await flushPromises()

    expect(auth.resetPassword).toHaveBeenCalledTimes(1)
    const submit = wrapper.find('button[type="submit"]')
    expect(submit.attributes('disabled')).toBeDefined()
    expect(submit.text()).toContain('重置中')

    resolve({})
    await flushPromises()
    expect(wrapper.find('button[type="submit"]').attributes('disabled')).toBeUndefined()
    wrapper.unmount()
  })
})
