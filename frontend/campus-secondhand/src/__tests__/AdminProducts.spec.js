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
          createdTime: '2026-10-01T00:00:00'
        })),
        total
      }
    }
  }
}

describe('AdminProducts search', () => {
  beforeEach(() => {
    admin.get.mockReset()
    admin.get.mockImplementation((url, { params }) => Promise.resolve(pageResponse(params.page)))
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
})
