import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'

/**
 * 回归：`VITE_API_BASE` 的语义是「部署前缀」，不含资源段。
 *
 * 曾经的 bug：`api/user.js` 与 `api/review.js` 写成
 * `import.meta.env.VITE_API_BASE || '/api/user'`。开发环境 `.env.development`
 * 设置了 `VITE_API_BASE=/api`，于是 `/profile` 被拼成 `/api/profile`、
 * 截图上传被拼成 `/api/student-verification/proof`，全部 404。
 * 结果是个人资料空白、学生认证截图上传失败、后台收不到申请。
 *
 * 这里通过 stub 环境变量 + 动态 import 复现当时的条件，锁死正确 URL。
 */
const jsonResponse = (body = { code: 200, data: {} }) => ({
  ok: true,
  status: 200,
  json: () => Promise.resolve(body),
  text: () => Promise.resolve(JSON.stringify(body)),
})

describe('api/user.js 的 base 拼接', () => {
  beforeEach(() => {
    vi.resetModules()
    localStorage.setItem('token', 'test-token')
    vi.stubGlobal('fetch', vi.fn(() => Promise.resolve(jsonResponse())))
  })

  afterEach(() => {
    vi.unstubAllEnvs()
    vi.unstubAllGlobals()
    localStorage.clear()
  })

  it('设置 VITE_API_BASE=/api 时，个人资料仍走 /api/user/profile', async () => {
    vi.stubEnv('VITE_API_BASE', '/api')
    const user = await import('../api/user')

    await user.getProfile()
    expect(fetch).toHaveBeenLastCalledWith('/api/user/profile', expect.anything())

    await user.updateProfile({ bio: 'x' })
    expect(fetch).toHaveBeenLastCalledWith('/api/user/profile', expect.anything())

    await user.getUserById(7)
    expect(fetch).toHaveBeenLastCalledWith('/api/user/user/7', expect.anything())
  })

  it('未设置 VITE_API_BASE 时，默认前缀仍是 /api/user', async () => {
    const user = await import('../api/user')

    await user.getProfile()
    expect(fetch).toHaveBeenLastCalledWith('/api/user/profile', expect.anything())
  })
})

describe('api/review.js 的 base 拼接', () => {
  beforeEach(() => {
    vi.resetModules()
    localStorage.setItem('token', 'test-token')
    vi.stubGlobal('fetch', vi.fn(() => Promise.resolve(jsonResponse())))
  })

  afterEach(() => {
    vi.unstubAllEnvs()
    vi.unstubAllGlobals()
    localStorage.clear()
  })

  it('设置 VITE_API_BASE=/api 时，学生认证接口都带 /user 前缀', async () => {
    vi.stubEnv('VITE_API_BASE', '/api')
    const review = await import('../api/review')

    await review.getMyScreenshotVerification()
    expect(fetch).toHaveBeenLastCalledWith(
      '/api/user/student-verification',
      expect.anything(),
    )

    await review.studentVerify('20230001', '张三')
    expect(fetch).toHaveBeenLastCalledWith(
      '/api/user/student-verify',
      expect.anything(),
    )

    await review.submitScreenshotVerification({ realName: '张三', agree: true })
    expect(fetch).toHaveBeenLastCalledWith(
      '/api/user/student-verification/screenshot',
      expect.anything(),
    )
  })

  it('设置 VITE_API_BASE=/api 时，截图上传走 /api/user/student-verification/proof', async () => {
    vi.stubEnv('VITE_API_BASE', '/api')
    const review = await import('../api/review')
    vi.stubGlobal('fetch', vi.fn(() =>
      Promise.resolve(jsonResponse({ code: 200, data: { proofId: 'proof-1' } }))))

    const proofId = await review.uploadVerificationProof(
      new File(['x'], 'shot.png', { type: 'image/png' }),
    )
    expect(proofId).toBe('proof-1')
    expect(fetch).toHaveBeenLastCalledWith(
      '/api/user/student-verification/proof',
      expect.anything(),
    )
  })

  it('未设置 VITE_API_BASE 时，默认前缀仍是 /api/user', async () => {
    const review = await import('../api/review')

    await review.getMyScreenshotVerification()
    expect(fetch).toHaveBeenLastCalledWith(
      '/api/user/student-verification',
      expect.anything(),
    )
  })
})
