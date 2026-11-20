import { describe, it, expect } from 'vitest'
import { formatJoinDate, verificationStatusLabel } from '../utils/profileDisplay'

/**
 * 资料页展示逻辑。
 *
 * <p>这两个函数是为了消灭两处真实存在过的假数据：徽章永远写「已认证」、
 * 加入时间永远写「2025年1月」。测试重点不在格式化本身，而在
 * <b>拿不到真实数据时绝不编造</b>。</p>
 */
describe('formatJoinDate', () => {
  it('ISO 日期时间格式化为 YYYY年M月，月份不带前导零', () => {
    expect(formatJoinDate('2026-11-20T09:30:00')).toBe('2026年11月')
    expect(formatJoinDate('2026-01-05T00:00:00Z')).toBe('2026年1月')
  })

  it('普通 YYYY-MM-DD 也按年月展示', () => {
    expect(formatJoinDate('2026-11-20')).toBe('2026年11月')
  })

  it('接受 Date 对象', () => {
    expect(formatJoinDate(new Date(2026, 10, 20))).toBe('2026年11月')
    expect(formatJoinDate(new Date(2025, 0, 1))).toBe('2025年1月')
  })

  it('非法值返回空串，绝不回退到 2025', () => {
    expect(formatJoinDate('')).toBe('')
    expect(formatJoinDate('   ')).toBe('')
    expect(formatJoinDate(null)).toBe('')
    expect(formatJoinDate(undefined)).toBe('')
    expect(formatJoinDate('not-a-date')).toBe('')
    expect(formatJoinDate('2026-13-01')).toBe('')
    expect(formatJoinDate(new Date('invalid'))).toBe('')
  })
})

describe('verificationStatusLabel', () => {
  it('已认证优先，即使截图申请还在审核中', () => {
    expect(
      verificationStatusLabel({ studentVerified: true, shotStatus: { status: 'pending' } }),
    ).toBe('已认证')
  })

  it('未认证 + 待审核截图 → 审核中', () => {
    expect(
      verificationStatusLabel({ studentVerified: false, shotStatus: { status: 'pending' } }),
    ).toBe('审核中')
  })

  it('未认证且无申请 → 未认证', () => {
    expect(verificationStatusLabel({ studentVerified: false, shotStatus: null })).toBe('未认证')
    expect(verificationStatusLabel({ studentVerified: false })).toBe('未认证')
    expect(verificationStatusLabel()).toBe('未认证')
  })

  it('被拒 / 过期 → 未认证', () => {
    expect(
      verificationStatusLabel({ studentVerified: false, shotStatus: { status: 'rejected' } }),
    ).toBe('未认证')
    expect(
      verificationStatusLabel({ studentVerified: false, shotStatus: { status: 'expired' } }),
    ).toBe('未认证')
  })
})
