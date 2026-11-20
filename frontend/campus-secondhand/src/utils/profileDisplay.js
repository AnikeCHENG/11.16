/**
 * 资料页的纯展示逻辑。
 *
 * <p>抽成独立模块是因为这里最容易再次出现「编造数据」：
 * 此前徽章写死「已认证」、加入时间写死「2025年1月」，
 * 未认证的用户和新注册的用户都会看到假信息。</p>
 */

/**
 * 把后端 createdTime 格式化为「YYYY年M月」。
 *
 * <p>缺字段、空串、非法日期一律返回空串，由调用方隐藏整行——
 * 宁可少显示一行，也不能回退到某个编造的时间。</p>
 *
 * @param {string|Date|null|undefined} createdTime
 * @returns {string} 如「2026年11月」；无法解析时为 ''
 */
export function formatJoinDate(createdTime) {
  if (createdTime === null || createdTime === undefined || createdTime === '') return ''

  if (createdTime instanceof Date) {
    if (Number.isNaN(createdTime.getTime())) return ''
    return `${createdTime.getFullYear()}年${createdTime.getMonth() + 1}月`
  }

  if (typeof createdTime === 'string') {
    const trimmed = createdTime.trim()
    if (!trimmed) return ''

    // 优先按年月前缀解析，避免 new Date('2026-11-20') 被当成 UTC 后
    // 在负时区回退到 10 月。后端 LocalDateTime 的无时区字符串也走这里。
    const match = trimmed.match(/^(\d{4})-(\d{1,2})/)
    if (match) {
      const year = Number(match[1])
      const month = Number(match[2])
      if (year > 0 && month >= 1 && month <= 12) return `${year}年${month}月`
      return ''
    }

    const date = new Date(trimmed)
    if (Number.isNaN(date.getTime())) return ''
    return `${date.getFullYear()}年${date.getMonth() + 1}月`
  }

  return ''
}

/**
 * 认证徽章的文案：已认证 > 审核中 > 未认证。
 *
 * <p>已通过认证优先于待审核：用户可能先提交过截图、后来完成了学号认证，
 * 此时不应再显示「审核中」。</p>
 *
 * @param {{ studentVerified?: boolean, shotStatus?: { status?: string }|null }} [state]
 * @returns {'已认证'|'审核中'|'未认证'}
 */
export function verificationStatusLabel({ studentVerified, shotStatus } = {}) {
  if (studentVerified === true) return '已认证'
  if (shotStatus && shotStatus.status === 'pending') return '审核中'
  return '未认证'
}
