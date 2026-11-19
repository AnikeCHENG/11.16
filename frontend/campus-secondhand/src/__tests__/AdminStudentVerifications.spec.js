import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import AdminStudentVerifications from '../views/Admin/AdminStudentVerifications.vue'
import admin from '@/api/admin'

vi.mock('@/api/admin', () => ({
  default: {
    get: vi.fn(),
    put: vi.fn(),
    post: vi.fn(),
    delete: vi.fn()
  }
}))

vi.mock('element-plus', () => ({
  ElMessage: { success: vi.fn(), warning: vi.fn(), error: vi.fn() }
}))

const record13 = {
  id: 13,
  userId: 28,
  username: 'demo_student',
  realName: '示***',
  school: '示例大学',
  studentNo: '2026****01',
  enrollmentYear: 2024,
  proofImage: '12149fe003854d9ca244dd3682d3d9a8.png',
  status: 'pending',
  createdAt: '2026-11-15T17:46:19'
}

const listResponse = (list = [record13]) => ({
  data: { code: 200, data: { list, total: list.length, page: 1, size: 10 } }
})
const detailResponse = (overrides = {}) => ({
  data: { code: 200, data: { ...record13, realName: '示例', studentNo: '202600000001', ...overrides } }
})

// 后端 400 时 body 是纯文本字节「图片不存在」，以 Blob 返回
function proof400() {
  const err = new Error('Request failed with status code 400')
  err.response = { status: 400, data: new Blob(['图片不存在'], { type: 'application/octet-stream' }) }
  return err
}

let proofImpl
function routeGet() {
  admin.get.mockImplementation((url) => {
    if (url === '/student-verifications') return Promise.resolve(listResponse())
    if (/\/proof$/.test(url)) return proofImpl(url)
    return Promise.resolve(detailResponse())
  })
}

// FileReader 回调是宏任务，flushPromises 之外还要让出几轮事件循环
async function settle() {
  for (let i = 0; i < 5; i++) {
    await flushPromises()
    await new Promise((resolve) => setTimeout(resolve, 0))
  }
}

const proofCalls = () => admin.get.mock.calls.filter(([url]) => /\/proof$/.test(url))

describe('AdminStudentVerifications', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    URL.createObjectURL = vi.fn(() => 'blob:mock-proof')
    URL.revokeObjectURL = vi.fn()
    proofImpl = () => Promise.reject(proof400())
    routeGet()
  })

  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('列表每格带 data-label，审核/详情按钮存在于操作格（卡片布局依赖）', async () => {
    const wrapper = mount(AdminStudentVerifications)
    await flushPromises()

    const cells = wrapper.findAll('tbody td')
    expect(cells.length).toBeGreaterThan(0)
    expect(cells.every((td) => td.attributes('data-label'))).toBe(true)

    const actions = wrapper.find('td.actions-cell')
    expect(actions.attributes('data-label')).toBe('操作')
    expect(actions.find('.review-button').text()).toBe('审核')
    expect(actions.find('.detail-button').text()).toBe('详情')
    expect(wrapper.find('.admin-page-header h1').exists()).toBe(true)
    wrapper.unmount()
  })

  it('截图 400：显示后端原因、重新加载与驳回提示，而不是永远转圈', async () => {
    const wrapper = mount(AdminStudentVerifications)
    await flushPromises()
    await wrapper.find('.review-button').trigger('click')
    await settle()

    const errorBox = wrapper.find('[data-test="proof-error"]')
    expect(errorBox.exists()).toBe(true)
    expect(errorBox.text()).toContain('截图加载失败：图片不存在')
    expect(errorBox.find('[data-test="proof-retry"]').text()).toBe('重新加载')
    expect(errorBox.text()).toContain('驳回并要求申请人重新提交')
    expect(wrapper.find('[data-test="proof-loading"]').exists()).toBe(false)
    expect(wrapper.find('.proof-image').exists()).toBe(false)
    // 拒绝/通过仍然可用
    expect(wrapper.find('.reject-button').exists()).toBe(true)
    expect(wrapper.find('.approve-button').exists()).toBe(true)
    // 请求带 silent，不再弹全局 toast
    expect(proofCalls()[0][1]).toMatchObject({ responseType: 'blob', silent: true })
    wrapper.unmount()
  })

  it('解析 ApiResponse JSON 形态的 Blob 错误', async () => {
    proofImpl = () => {
      const err = new Error('Request failed with status code 400')
      err.response = {
        status: 400,
        data: new Blob([JSON.stringify({ code: 400, message: '认证申请不存在' })], { type: 'application/json' })
      }
      return Promise.reject(err)
    }
    const wrapper = mount(AdminStudentVerifications)
    await flushPromises()
    await wrapper.find('.review-button').trigger('click')
    await settle()
    expect(wrapper.find('[data-test="proof-error"]').text()).toContain('截图加载失败：认证申请不存在')
    wrapper.unmount()
  })

  it('重新加载会再次请求截图，成功后显示图片', async () => {
    const wrapper = mount(AdminStudentVerifications)
    await flushPromises()
    await wrapper.find('.review-button').trigger('click')
    await settle()
    expect(proofCalls()).toHaveLength(1)

    proofImpl = () => Promise.resolve({ data: new Blob(['png'], { type: 'image/png' }) })
    await wrapper.find('[data-test="proof-retry"]').trigger('click')
    await settle()

    expect(proofCalls()).toHaveLength(2)
    expect(wrapper.find('[data-test="proof-error"]').exists()).toBe(false)
    expect(wrapper.find('.proof-image').attributes('src')).toBe('blob:mock-proof')
    wrapper.unmount()
  })

  it('关闭弹窗与卸载时释放 objectURL', async () => {
    proofImpl = () => Promise.resolve({ data: new Blob(['png'], { type: 'image/png' }) })
    const wrapper = mount(AdminStudentVerifications)
    await flushPromises()
    await wrapper.find('.review-button').trigger('click')
    await settle()
    expect(wrapper.find('.proof-image').exists()).toBe(true)

    await wrapper.find('.close-button').trigger('click')
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:mock-proof')

    await wrapper.find('.detail-button').trigger('click')
    await flushPromises()
    URL.revokeObjectURL.mockClear()
    wrapper.unmount()
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:mock-proof')
  })

  it('历史 /uploads/... 路径直接作为公开 URL 渲染，不请求 proof 接口', async () => {
    admin.get.mockImplementation((url) => {
      if (url === '/student-verifications') return Promise.resolve(listResponse())
      if (/\/proof$/.test(url)) return proofImpl(url)
      return Promise.resolve(detailResponse({ proofImage: '/uploads/2025/01/old.png' }))
    })
    const wrapper = mount(AdminStudentVerifications)
    await flushPromises()
    await wrapper.find('.review-button').trigger('click')
    await settle()

    expect(proofCalls()).toHaveLength(0)
    expect(wrapper.find('.proof-image').attributes('src')).toBe('/uploads/2025/01/old.png')
    wrapper.unmount()
  })
})
