import { test, expect } from '@playwright/test'

const env = globalThis.process?.env ?? {}
const USER = env.E2E_ADMIN_USER
const PASSWORD = env.E2E_ADMIN_PASSWORD
const API = env.E2E_API_URL || 'http://localhost:8080'

test.skip(!USER || !PASSWORD, '未设置 E2E_ADMIN_USER / E2E_ADMIN_PASSWORD')

test('DEF-02：登录页在密码框按回车即可登录', async ({ page }) => {
  await page.goto('/login')
  await page.locator('#login-username').fill(USER)
  await page.locator('#login-password').fill(PASSWORD)
  await page.locator('#login-password').press('Enter')
  await expect(page).not.toHaveURL(/\/login/)
  const token = await page.evaluate(() => localStorage.getItem('token') || sessionStorage.getItem('token'))
  expect(token).toBeTruthy()
})

test('DEF-01：商品详情卖家有评价时无横向滚动，「联系卖家」在视口内', async ({ page, request }) => {
  const res = await request.post(`${API}/api/auth/login`, { data: { username: USER, password: PASSWORD } })
  const { data } = await res.json()
  const list = await (await request.get(`${API}/api/products/list?page=1&size=20`)).json()
  // 选一件不属于当前账号的商品，确保显示「联系卖家」
  const product = list.data.list.find((p) => p.userId !== data.user.id)
  test.skip(!product, '没有可用的他人商品')

  // 种子数据没有评价：拦截好评率接口，稳定复现「有评价」的那一行（星级 + 好评率 + 条数）
  await page.route('**/api/user/user/*/review-stats', (route) =>
    route.fulfill({
      json: { code: 200, message: 'ok', data: { total: 128, good: 127, averageRating: 4.9, goodRate: 99.2, hasReview: true } },
    }),
  )

  await page.goto('/login')
  await page.evaluate(({ token, user }) => {
    localStorage.setItem('token', token)
    localStorage.setItem('user', JSON.stringify(user))
    localStorage.setItem('userId', String(user.id))
  }, data)
  await page.goto(`/products/${product.id}`)
  await expect(page.locator('.seller-rating__text')).toContainText('好评率')

  const contact = page.locator('.seller .btn')
  await contact.scrollIntoViewIfNeeded()
  const { scrollW, clientW } = await page.evaluate(() => ({
    scrollW: document.documentElement.scrollWidth,
    clientW: document.documentElement.clientWidth,
  }))
  expect(scrollW).toBeLessThanOrEqual(clientW)
  const box = await contact.boundingBox()
  expect(box.x).toBeGreaterThanOrEqual(0)
  expect(box.x + box.width).toBeLessThanOrEqual(clientW + 0.5)
})
