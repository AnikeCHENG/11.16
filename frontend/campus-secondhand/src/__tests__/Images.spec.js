import { describe, it, expect } from 'vitest'
import { parseImages, firstImage } from '../utils/images'

/**
 * 商品多图串的解析。
 *
 * <p>{@code products.images} 是逗号分隔的多图串，但每张都可能是 Base64 Data URL
 * （{@code data:image/jpeg;base64,AAAA...}）——base64 载荷自身也含逗号。
 * 裸 {@code split(',')} 会把 URL 截断成 {@code data:image/jpeg;base64}，图片彻底失效。</p>
 */
describe('parseImages', () => {
  it('普通路径列表按逗号切分', () => {
    expect(parseImages('/uploads/a.jpg,/uploads/b.jpg')).toEqual([
      '/uploads/a.jpg',
      '/uploads/b.jpg',
    ])
  })

  it('单张路径原样返回', () => {
    expect(parseImages('/uploads/only.jpg')).toEqual(['/uploads/only.jpg'])
  })

  it('单张 base64 Data URL 不被截断', () => {
    const b64 = 'data:image/jpeg;base64,/9j/4AAQSkZJRgABAQAAAQABAAD'
    expect(parseImages(b64)).toEqual([b64])
  })

  it('多张 base64 Data URL 按 base64, 之后的分隔符切分', () => {
    const a = 'data:image/jpeg;base64,AAAA'
    const b = 'data:image/png;base64,BBBB'
    expect(parseImages(`${a},${b}`)).toEqual([a, b])
  })

  it('路径与 base64 混排也能正确切分', () => {
    const path = '/uploads/cover.jpg'
    const b64 = 'data:image/jpeg;base64,AAAA'
    expect(parseImages(`${path},${b64}`)).toEqual([path, b64])
  })

  it('空值与空白返回空数组', () => {
    expect(parseImages(null)).toEqual([])
    expect(parseImages(undefined)).toEqual([])
    expect(parseImages('')).toEqual([])
    expect(parseImages('   ')).toEqual([])
  })

  it('已是数组时直接清洗返回', () => {
    expect(parseImages(['/a.jpg', ' /b.jpg ', ''])).toEqual(['/a.jpg', '/b.jpg'])
  })
})

describe('firstImage', () => {
  it('取多图串的第一张', () => {
    expect(firstImage('/uploads/a.jpg,/uploads/b.jpg')).toBe('/uploads/a.jpg')
  })

  it('base64 图片不会被截断', () => {
    const b64 = 'data:image/png;base64,QQ=='
    expect(firstImage(b64)).toBe(b64)
  })

  it('取不到时返回占位图', () => {
    expect(firstImage(null)).toBe('/sample/phone.svg')
    expect(firstImage('')).toBe('/sample/phone.svg')
  })

  it('可以指定自定义占位图', () => {
    expect(firstImage(null, '')).toBe('')
  })
})
