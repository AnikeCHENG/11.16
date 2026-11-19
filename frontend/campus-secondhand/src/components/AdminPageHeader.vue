<template>
  <header class="admin-page-header">
    <div class="aph-text">
      <p v-if="eyebrow" class="aph-eyebrow">{{ eyebrow }}</p>
      <h1 class="aph-title">{{ title }}</h1>
      <p v-if="subtitle" class="aph-subtitle">{{ subtitle }}</p>
      <slot name="subtitle" />
    </div>
    <div class="aph-actions">
      <slot name="actions" />
      <a
        v-if="back"
        :href="backTo"
        class="aph-btn aph-back"
        data-test="admin-back"
        @click.prevent="goBack"
      >返回控制面板</a>
      <button
        type="button"
        class="aph-btn aph-logout"
        data-test="admin-logout"
        :disabled="loggingOut"
        @click="onLogout"
      >
        {{ loggingOut ? '退出中…' : '退出登录' }}
      </button>
    </div>
  </header>
</template>

<script setup>
import { inject, ref } from 'vue'
import { routerKey } from 'vue-router'
import { logout } from '@/api/auth'
import { useUserStore } from '@/stores/user'
import { useCartStore } from '@/stores/cart'
import { useNotificationStore } from '@/stores/notification'

/**
 * 后台页统一页头：ADMIN 小标 + 标题 + 副标题 + 右侧操作区。
 *
 * <p>固定两个动作：「返回控制面板」（back=false 可关闭）与「退出登录」。
 * 返回用真实 &lt;a href&gt;，保留中键/新标签页打开，点击时走 SPA 路由。
 * 退出链路与 AppNavbar.onLogout 保持一致。</p>
 */
const props = defineProps({
  title: { type: String, required: true },
  subtitle: { type: String, default: '' },
  eyebrow: { type: String, default: 'ADMIN' },
  back: { type: Boolean, default: true },
  backTo: { type: String, default: '/admin' }
})

// 用 inject 而不是 useRouter()：单测里直接 mount 页面时可能没有 router，避免告警
const router = inject(routerKey, null)
const loggingOut = ref(false)

function navigate(path) {
  if (router) {
    router.push(path)
  } else {
    window.location.assign(path)
  }
}

function goBack() {
  navigate(props.backTo)
}

async function onLogout() {
  if (loggingOut.value) return
  loggingOut.value = true
  // auth.logout() 内部已清 localStorage/sessionStorage
  try {
    await logout()
  } catch {
    // 登出接口失败也要完成本地清理
  }
  // 与 AppNavbar 相同的清理顺序；store 在此处才取，避免无 pinia 的场景在 setup 阶段报错
  useCartStore().reset()
  useNotificationStore().resetUnreadCount()
  // 必须清身份缓存，否则下一位登录者的 role 会被串用
  useUserStore().reset()
  loggingOut.value = false
  navigate('/login')
}
</script>

<style scoped>
.admin-page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  gap: var(--space-4, 16px);
  margin-bottom: var(--space-5, 24px);
  padding-bottom: var(--space-4, 16px);
  border-bottom: 1px solid var(--border, #e0e0e0);
  min-width: 0;
}

.aph-text { min-width: 0; flex: 1 1 auto; }

.aph-eyebrow {
  margin: 0 0 4px;
  font-size: var(--text-xs, 12px);
  font-weight: var(--weight-semibold, 600);
  letter-spacing: 0.12em;
  color: var(--text-3, #9a9a92);
}

.aph-title {
  margin: 0;
  font-size: var(--text-2xl, 28px);
  font-weight: var(--weight-semibold, 600);
  letter-spacing: -0.02em;
  color: var(--text, #333);
  overflow-wrap: anywhere;
}

.aph-subtitle {
  margin: var(--space-1, 4px) 0 0;
  color: var(--text-2, #666);
  font-size: var(--text-sm, 14px);
}

.aph-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  align-items: center;
  gap: var(--space-2, 8px);
  flex: 0 1 auto;
}

.aph-btn,
.aph-actions :slotted(.btn) {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 8px 14px;
  font-size: var(--text-sm, 14px);
  line-height: 1.2;
  white-space: nowrap;
  text-decoration: none;
  color: var(--text, #333);
  background: var(--surface, #fff);
  border: 1px solid var(--border-strong, #e0e0e0);
  border-radius: var(--radius, 8px);
  cursor: pointer;
  font-family: inherit;
}

.aph-btn:hover { background: var(--surface-2, #f5f5f5); }

.aph-logout { color: var(--danger, #c0392b); }
.aph-logout:hover:not(:disabled) { background: var(--danger-soft, rgba(192, 57, 43, 0.08)); }
.aph-logout:disabled { opacity: 0.6; cursor: not-allowed; }

@media (max-width: 640px) {
  .admin-page-header {
    flex-direction: column;
    align-items: stretch;
    gap: var(--space-3, 12px);
  }

  .aph-title { font-size: var(--text-xl, 22px); }

  .aph-actions { justify-content: flex-start; }
}
</style>
