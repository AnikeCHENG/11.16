/**
 * 首页 Hero 数字格式化。
 *
 * <p>只接受有限数字：接口失败、字段缺失或返回非数字时一律显示「—」，
 * 绝不退回硬编码的假数据，也不把无效值伪装成 0。</p>
 */
export function formatHeroStat(value) {
  if (typeof value !== 'number' || !Number.isFinite(value)) return '—'
  return value.toLocaleString('en-US')
}
