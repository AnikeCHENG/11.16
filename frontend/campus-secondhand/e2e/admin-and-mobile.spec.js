import { test, expect } from '@playwright/test'

const env = globalThis.process?.env ?? {}
const USER = env.E2E_ADMIN_USER
const PASSWORD = env.E2E_ADMIN_PASSWORD
const API = env.E2E_API_URL || 'http://localhost:8080'

test.skip(!USER || !PASSWORD, '未设置 E2E_ADMIN_USER / E2E_ADMIN_PASSWORD')

/** 通过真实登录接口拿 token，写入 localStorage（与登录页「记住我」一致） */
async function loginAsAdmin(page, request) {
  const res = await request.post(`${API}/api/auth/login`, { data: { username: USER, password: PASSWORD } })
  const body = await res.json()
  expect(body.code).toBe(200)
  await page.goto('/login')
  await page.evaluate(({ token, user }) => {
    localStorage.setItem('token', token)
    localStorage.setItem('user', JSON.stringify(user))
    localStorage.setItem('userId', String(user.id))
  }, body.data)
}

async function expectNoHorizontalScroll(page) {
  const { scrollW, clientW } = await page.evaluate(() => ({
    scrollW: document.documentElement.scrollWidth,
    clientW: document.documentElement.clientWidth,
  }))
  expect(scrollW).toBeLessThanOrEqual(clientW)
}

async function expectInViewport(page, locator) {
  const box = await locator.boundingBox()
  const vp = page.viewportSize()
  expect(box).not.toBeNull()
  expect(box.x).toBeGreaterThanOrEqual(0)
  expect(box.x + box.width).toBeLessThanOrEqual(vp.width + 0.5)
  expect(box.y + box.height).toBeLessThanOrEqual(vp.height + 0.5)
}

test('后台各页：统一页头可见、无横向滚动', async ({ page, request }) => {
  await loginAsAdmin(page, request)
  for (const path of ['/admin/users', '/admin/products', '/admin/orders', '/admin/categories', '/admin/messages', '/admin/reports', '/admin/student-verifications']) {
    await page.goto(path)
    await expect(page.locator('.admin-page-header h1')).toBeVisible()
    await expectInViewport(page, page.locator('[data-test="admin-logout"]'))
    await expectNoHorizontalScroll(page)
  }
})

test('学生认证：审核弹窗的拒绝/通过无需滚动即可点到', async ({ page, request }) => {
  await loginAsAdmin(page, request)
  await page.goto('/admin/student-verifications')
  await page.locator('.sv-table tbody tr, .empty-text, .error-container').first().waitFor()
  const review = page.locator('.review-button').first()
  test.skip(!(await review.count()), '当前没有待审核申请')
  await expectInViewport(page, review)
  await review.click()
  await expect(page.locator('[data-test="proof-error"], .proof-image, .form-hint').first()).toBeVisible()
  await expectInViewport(page, page.locator('.approve-button'))
  await expectInViewport(page, page.locator('.reject-button'))
  await page.locator('.close-button').click()
})

test('C 端顶栏：头像菜单在视口内，可退出登录', async ({ page, request }) => {
  await loginAsAdmin(page, request)
  await page.goto('/products')
  await expectNoHorizontalScroll(page)
  const avatar = page.locator('.nb-avatar')
  await expectInViewport(page, avatar)
  await avatar.click()
  await page.locator('.nb-menu-danger').click()
  await expect(page).toHaveURL(/\/login/)
})
