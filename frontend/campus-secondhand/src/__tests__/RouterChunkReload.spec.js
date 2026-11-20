import { describe, it, expect, vi, beforeEach } from 'vitest'
import { isChunkLoadError, handleChunkLoadError, CHUNK_RELOAD_KEY } from '../router'

describe('懒加载 chunk 失效兜底', () => {
  beforeEach(() => {
    sessionStorage.clear()
  })

  it('识别 Chrome / Safari 的 chunk 加载失败报错', () => {
    expect(isChunkLoadError(new TypeError('Failed to fetch dynamically imported module: http://x/a.js'))).toBe(true)
    expect(isChunkLoadError(new TypeError('Importing a module script failed.'))).toBe(true)
    expect(isChunkLoadError(new Error('Request failed with status code 500'))).toBe(false)
  })

  it('同一路径每个会话只自动刷新一次', () => {
    const reload = vi.fn()
    const err = new TypeError('Failed to fetch dynamically imported module')
    const to = { fullPath: '/admin/messages' }

    expect(handleChunkLoadError(err, to, { reload })).toBe(true)
    expect(reload).toHaveBeenCalledWith('/admin/messages')
    expect(sessionStorage.getItem(CHUNK_RELOAD_KEY + '/admin/messages')).toBeTruthy()

    expect(handleChunkLoadError(err, to, { reload })).toBe(false)
    expect(reload).toHaveBeenCalledTimes(1)

    // 其他路径各自计数
    expect(handleChunkLoadError(err, { fullPath: '/admin/users' }, { reload })).toBe(true)
    expect(reload).toHaveBeenCalledTimes(2)
  })

  it('非 chunk 错误不刷新', () => {
    const reload = vi.fn()
    expect(handleChunkLoadError(new Error('boom'), { fullPath: '/' }, { reload })).toBe(false)
    expect(reload).not.toHaveBeenCalled()
  })
})
