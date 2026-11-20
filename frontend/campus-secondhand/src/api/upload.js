import { API_BASE as BASE } from '@/utils/apiBase'

/**
 * 图片上传（P1-12）。
 *
 * <p>把浏览器里压缩好的图片以 multipart 发给后端，换回一个可长期存库的相对路径
 * （{@code /uploads/2026/10/xxx.jpg}）。<b>取代此前"canvas 压成 base64 再塞进 JSON
 * 入库"的做法</b>——那种做法让一条商品/订单详情响应里带着整张图的 base64，
 * 还逼得请求体上限被抬到 50MB。</p>
 *
 * <p>注意这里不能用各 api 模块那套 {@code Content-Type: application/json} 的 fetch：
 * multipart 必须让浏览器自己带 boundary，手写 Content-Type 会让后端解析失败。</p>
 *
 * @param {Blob} blob 压缩后的图片二进制
 * @param {string} filename 仅作提示，服务端会重新生成文件名
 * @returns {Promise<string>} 形如 /uploads/2026/10/xxx.jpg 的路径
 */
export async function uploadImage(blob, filename = 'image.jpg') {
  const token = localStorage.getItem('token') || sessionStorage.getItem('token')
  if (!token) {
    throw new Error('请先登录后再上传图片')
  }

  const form = new FormData()
  form.append('file', blob, filename)

  let res
  try {
    res = await fetch(`${BASE}/upload/image`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${token}` },
      body: form
    })
  } catch {
    throw new Error('图片上传失败，请检查网络')
  }

  if (res.status === 401 || res.status === 403) {
    throw new Error('登录已过期，请重新登录后再上传')
  }
  const data = await res.json().catch(() => null)
  if (!res.ok || !data || data.code !== 200 || !data.data?.url) {
    throw new Error(data?.message || '图片上传失败')
  }
  return data.data.url
}

export default { uploadImage }
