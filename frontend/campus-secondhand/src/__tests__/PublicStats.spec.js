import { describe, it, expect } from 'vitest'
import { formatHeroStat } from '../utils/publicStats'

describe('formatHeroStat', () => {
  it('0 原样显示为 0', () => {
    expect(formatHeroStat(0)).toBe('0')
  })

  it('两位数不加分隔符', () => {
    expect(formatHeroStat(12)).toBe('12')
  })

  it('千位使用英文逗号分隔', () => {
    expect(formatHeroStat(2580)).toBe('2,580')
  })

  it('百万级同样按千位分组', () => {
    expect(formatHeroStat(1000000)).toBe('1,000,000')
  })

  it('非有限数字一律显示破折号', () => {
    expect(formatHeroStat(null)).toBe('—')
    expect(formatHeroStat(undefined)).toBe('—')
    expect(formatHeroStat(NaN)).toBe('—')
    expect(formatHeroStat('abc')).toBe('—')
  })
})
