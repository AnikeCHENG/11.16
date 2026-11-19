import axios from 'axios'
import { ElMessage } from 'element-plus'

/**
 * 管理端专用的 axios 实例。
 *
 * <p><b>此前的问题：</b>7 个 `Admin/*.vue` 各自 `import axios from 'axios'`，
 * 把 `/api/admin/...` 硬编码在调用处（共 20 处），并各自手拼 `Authorization`
 * 头——项目里连一个 `axios.create` 都没有（grep `axios.create|baseURL` 零命中）。
 * 三个后果：</p>
 * <ol>
 *   <li>部署前缀不是 `/api` 时，整个后台 404，而 C 端照常工作，问题极难定位；</li>
 *   <li>改一次鉴权要改 20 处；没有 token 时那些调用会发出字面量 `Bearer null`，
 *       报错与"真的没权限"长得一模一样；</li>
 *   <li>后端把业务失败也包成 HTTP 200（真正的结果在 `ApiResponse.code` 里），
 *       axios 因此不抛异常，界面看起来"改成功了"，其实服务器拒绝了。</li>
 * </ol>
 *
 * <p>现在 baseURL、token 注入、失败提示三件事都在这一处。
 * C 端各 `api/*.js` 仍维持原有的 fetch 风格（那是既定的、刻意的分工），
 * 本实例只服务管理端。</p>
 */
const admin = axios.create({
  // 必须带 /admin 后缀。此前这里写的是 VITE_API_BASE || '/api'，
  // 而 20 处调用传的是 '/users'、'/stats' 这类子路径——请求全打到
  // C 端的 /api/users…，管理员满屏 404（"进不了管理后台"的根因之一）。
  baseURL: `${import.meta.env.VITE_API_BASE || '/api'}/admin`,
  timeout: 15000,
  // 实例级默认 JSON。AdminUsers / AdminProducts 的状态切换传的是裸数字
  // （admin.put(url, 1)），axios 对非对象载荷不会自动补 application/json，
  // dispatchRequest 反而把它兜成 x-www-form-urlencoded，后端
  // @RequestBody Integer 无法解析。定死 JSON 后数字被序列化成 "1"，
  // 对象载荷的行为不变（本来是 application/json）。
  headers: { 'Content-Type': 'application/json' }
})

admin.interceptors.request.use((config) => {
  const token = localStorage.getItem('token') || sessionStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  } else {
    // 显式删掉：调用处若还残留手拼的 Authorization，无 token 时会是
    // 字面量 'Bearer null'，服务端只会回 403，排查时会被误导成权限问题
    delete config.headers.Authorization
  }
  return config
})

admin.interceptors.response.use(
  (response) => {
    const body = response.data
    if (body && typeof body.code === 'number' && body.code !== 200) {
      const message = body.message || '请求未成功'
      ElMessage.error(message)
      return Promise.reject(new Error(message))
    }
    return response
  },
  (error) => {
    // silent：调用方自行在界面上展示失败原因（例如认证截图按 blob 取回失败），不再弹全局 toast
    if (error.config?.silent) {
      return Promise.reject(error)
    }
    const status = error.response?.status
    const message =
      error.response?.data?.message ||
      (status === 403 ? '无权限：需要管理员身份' : null) ||
      error.message ||
      '网络请求失败'
    ElMessage.error(message)
    return Promise.reject(error)
  }
)

export default admin
