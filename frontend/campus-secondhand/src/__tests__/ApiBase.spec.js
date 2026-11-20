import { describe, it, expect, vi, afterEach } from 'vitest'
import {
  API_BASE,
  API_ORIGIN,
  assetUrl,
  getSavedServerUrl,
  installUploadsRewriter,
  normalizeServerUrl,
  saveServerUrl,
  SERVER_URL_KEY
} from '@/utils/apiBase'

describe('apiBase：后端地址配置', () => {
  afterEach(() => localStorage.clear())

  it('未配置 VITE_API_BASE_URL 时保持同源相对路径（Web 行为不变）', () => {
    expect(API_ORIGIN).toBe('')
    expect(API_BASE).toBe('/api')
  })

  it('规范化服务器地址：去结尾斜杠，只接受 http(s)', () => {
    expect(normalizeServerUrl(' http://192.168.1.20:8080/ ')).toBe('http://192.168.1.20:8080')
    expect(normalizeServerUrl('https://api.example.com')).toBe('https://api.example.com')
    expect(normalizeServerUrl('192.168.1.20:8080')).toBe('')
    expect(normalizeServerUrl('ftp://x')).toBe('')
    expect(normalizeServerUrl('http://x/?a=1')).toBe('')
    expect(normalizeServerUrl('')).toBe('')
  })

  it('浏览器里忽略 localStorage 中的运行时地址（只在原生 App 生效）', () => {
    localStorage.setItem(SERVER_URL_KEY, 'http://10.0.2.2:8080')
    expect(getSavedServerUrl()).toBe('')
  })

  it('saveServerUrl：非法地址不保存，空串清除', () => {
    expect(saveServerUrl('not a url')).toBe(false)
    expect(localStorage.getItem(SERVER_URL_KEY)).toBeNull()
    expect(saveServerUrl('http://10.0.2.2:8080/')).toBe(true)
    expect(localStorage.getItem(SERVER_URL_KEY)).toBe('http://10.0.2.2:8080')
    expect(saveServerUrl('')).toBe(true)
    expect(localStorage.getItem(SERVER_URL_KEY)).toBeNull()
  })

  it('assetUrl：只给 /uploads/ 路径加后端 origin', () => {
    const o = 'http://10.0.2.2:8080'
    expect(assetUrl('/uploads/2026/11/a.jpg', o)).toBe('http://10.0.2.2:8080/uploads/2026/11/a.jpg')
    expect(assetUrl('/sample/phone.svg', o)).toBe('/sample/phone.svg')
    expect(assetUrl('data:image/png;base64,AAAA', o)).toBe('data:image/png;base64,AAAA')
    expect(assetUrl('/uploads/a.jpg', '')).toBe('/uploads/a.jpg')
  })
})

describe('installUploadsRewriter', () => {
  it('origin 为空（Web）时不做任何事', () => {
    const root = document.createElement('div')
    root.innerHTML = '<img src="/uploads/a.jpg">'
    installUploadsRewriter(root, '')()
    expect(root.querySelector('img').getAttribute('src')).toBe('/uploads/a.jpg')
  })

  it('改写已有、新增和 src 变化的 /uploads 图片，其他图片不动', async () => {
    const root = document.createElement('div')
    root.innerHTML = '<img id="a" src="/uploads/a.jpg"><img id="s" src="/sample/x.svg">'
    const stop = installUploadsRewriter(root, 'http://h:8080')
    expect(root.querySelector('#a').getAttribute('src')).toBe('http://h:8080/uploads/a.jpg')
    expect(root.querySelector('#s').getAttribute('src')).toBe('/sample/x.svg')

    const added = document.createElement('div')
    added.innerHTML = '<img id="b" src="/uploads/b.jpg">'
    root.appendChild(added)
    root.querySelector('#s').setAttribute('src', '/uploads/c.jpg')
    await vi.waitFor(() => {
      expect(root.querySelector('#b').getAttribute('src')).toBe('http://h:8080/uploads/b.jpg')
      expect(root.querySelector('#s').getAttribute('src')).toBe('http://h:8080/uploads/c.jpg')
    })
    stop()
  })
})
