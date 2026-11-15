import { describe, it, expect, vi, beforeEach } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import AdminProducts from '../views/Admin/AdminProducts.vue'
import admin from '@/api/admin'

vi.mock('@/api/admin', () => ({
  default: {
    get: vi.fn(),
    put: vi.fn(),
    post: vi.fn(),
    delete: vi.fn()
  }
}))

function pageResponse(page, total = 25) {
  return {
    data: {
      code: 200,
      data: {
        list: Array.from({ length: 10 }, (_, index) => ({
          id: (page - 1) * 10 + index + 1,
          title: `item-${index + 1}`,
          userId: 1,
          price: 10,
          category: 'other',
          status: 1,
          // 存量商品默认已通过审核，避免操作列渲染成审核按钮影响旧用例
          reviewStatus: 1,
          createdTime: '2026-10-01T00:00:00'
        })),
        total
      }
    }
  }
}

function categoriesResponse() {
  return {
    data: {
      code: 200,
      data: [
        { id: 1, code: 'books', name: '图书教材', status: 1, productCount: 3 }
      ]
    }
  }
}

describe('AdminProducts search', () => {
  beforeEach(() => {
    admin.get.mockReset()
    admin.get.mockImplementation((url, config) => {
      if (url === '/categories') return Promise.resolve(categoriesResponse())
      return Promise.resolve(pageResponse(config?.params?.page || 1))
    })
  })

  it('resets to page 1 and trims the keyword when searching from a later page', async () => {
    const wrapper = mount(AdminProducts, {
      global: {
        stubs: { RouterLink: { template: '<a><slot /></a>' } }
      }
    })
    await flushPromises()

    const nextButton = wrapper.findAll('.pagination-button')[2]
    await nextButton.trigger('click')
    await flushPromises()
    expect(admin.get).toHaveBeenLastCalledWith('/products', { params: { page: 2, size: 10 } })

    await wrapper.find('.search-input').setValue('  lamp  ')
    await wrapper.find('.search-button').trigger('click')
    await flushPromises()

    expect(admin.get).toHaveBeenLastCalledWith('/products', {
      params: { page: 1, size: 10, search: 'lamp' }
    })

    wrapper.unmount()
  })

  it('passes the review status filter to the backend so pending items can be found', async () => {
    const wrapper = mount(AdminProducts, {
      global: {
        stubs: { RouterLink: { template: '<a><slot /></a>' } }
      }
    })
    await flushPromises()

    // 三个筛选项依次为：分类、上下架状态、审核状态
    const reviewSelect = wrapper.findAll('.filter-select')[2]
    await reviewSelect.setValue(0)
    await flushPromises()

    expect(admin.get).toHaveBeenLastCalledWith('/products', {
      params: { page: 1, size: 10, reviewStatus: 0 }
    })

    wrapper.unmount()
  })
})
