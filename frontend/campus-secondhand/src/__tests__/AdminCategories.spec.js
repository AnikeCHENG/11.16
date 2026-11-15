import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { ElMessage, ElMessageBox } from 'element-plus'
import AdminCategories from '../views/Admin/AdminCategories.vue'
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
  ElMessage: {
    success: vi.fn(),
    warning: vi.fn(),
    error: vi.fn()
  },
  ElMessageBox: {
    confirm: vi.fn()
  }
}))

function categoriesResponse() {
  return {
    data: {
      code: 200,
      data: [
        { id: 1, code: 'books', name: '图书教材', status: 1, sortOrder: 1, productCount: 3, description: '二手教材' },
        { id: 2, code: 'empty', name: '空分类', status: 0, sortOrder: 2, productCount: 0, description: null }
      ]
    }
  }
}

describe('AdminCategories', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    admin.get.mockResolvedValue(categoriesResponse())
    admin.post.mockResolvedValue({ data: { code: 200 } })
    admin.put.mockResolvedValue({ data: { code: 200 } })
    admin.delete.mockResolvedValue({ data: { code: 200 } })
  })

  it('展示真实商品数与分类标识（修复按中文名统计恒为 0）', async () => {
    const wrapper = mount(AdminCategories, {
      global: { stubs: { RouterLink: { template: '<a><slot /></a>' } } }
    })
    await flushPromises()

    const codes = wrapper.findAll('.category-code').map(node => node.text())
    expect(codes).toEqual(['books', 'empty'])
    expect(wrapper.text()).toContain('3 个商品')
    expect(wrapper.text()).toContain('0 个商品')

    wrapper.unmount()
  })

  it('编辑时分类标识不可修改（商品按标识归属）', async () => {
    const wrapper = mount(AdminCategories, {
      global: { stubs: { RouterLink: { template: '<a><slot /></a>' } } }
    })
    await flushPromises()

    await wrapper.findAll('.btn-secondary')[0].trigger('click')
    const codeInput = wrapper.find('#edit-category-code')
    expect(codeInput.attributes('disabled')).toBeDefined()

    wrapper.unmount()
  })

  it('分类下仍有商品时拒绝删除，不调用后端', async () => {
    const wrapper = mount(AdminCategories, {
      global: { stubs: { RouterLink: { template: '<a><slot /></a>' } } }
    })
    await flushPromises()

    await wrapper.findAll('.btn-danger')[0].trigger('click')
    await flushPromises()

    expect(ElMessage.warning).toHaveBeenCalledWith(expect.stringContaining('3 件商品'))
    expect(admin.delete).not.toHaveBeenCalled()

    wrapper.unmount()
  })

  it('删除空分类需要二次确认后才请求后端', async () => {
    ElMessageBox.confirm.mockResolvedValue('confirm')
    const wrapper = mount(AdminCategories, {
      global: { stubs: { RouterLink: { template: '<a><slot /></a>' } } }
    })
    await flushPromises()

    await wrapper.findAll('.btn-danger')[1].trigger('click')
    await flushPromises()

    expect(ElMessageBox.confirm).toHaveBeenCalled()
    expect(admin.delete).toHaveBeenCalledWith('/categories/2')

    wrapper.unmount()
  })
})
