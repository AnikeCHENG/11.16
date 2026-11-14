<script setup>
/**
 * 全站统一顶部导航（单一数据源）。
 *
 * <p>此前 9 个页面各写一份导航：项数 3~7 不等、背景有白有暗、高度与内边距
 * 各有差异。逐页修是治标——第 10 个新页面还会再犯。这里抽成唯一组件，
 * 由 App.vue 全局渲染，新增页面自动获得同一套导航。</p>
 *
 * <p>同时收拢两处状态：消息未读数（原本 App.vue 单例轮询 + Messages.vue
 * 两次直调）与购物车角标（原本只在 Home.vue 持有）。</p>
 */
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useNotificationStore } from '@/stores/notification'
import { useCartStore } from '@/stores/cart'
import { useUserStore } from '@/stores/user'
import { useMessageNotify } from '@/composables/useMessageNotify'
import VerifiedBadge from '@/components/VerifiedBadge.vue'
import { logout } from '@/api/auth'

const route = useRoute()
const router = useRouter()
const notificationStore = useNotificationStore()
const cartStore = useCartStore()
const userStore = useUserStore()
/** 消息提示音开关此前挂在 Home 的头部里，随导航统一后搬到这里。
 *  它属于账号级设置，放在头像菜单里比放在首页更合理。 */
const { soundEnabled, toggleSound } = useMessageNotify()

const NAV_ITEMS = [
  { to: '/', label: '首页' },
  { to: '/plaza', label: '大厅' },
  { to: '/products', label: '商品' },
  { to: '/messages', label: '消息' },
  { to: '/profile', label: '我的' }
]

/**
 * 实际渲染的导航项：常规 5 项 + 管理员专属的「管理后台」。
 *
 * <p>入口只因 userStore.isAdmin（role === 1 且身份已经服务端确认）出现，
 * C 端登录用户看不到、也无从点入；即便手输 /admin，路由守卫与后端
 * AdminAuthInterceptor 还会各拦一道（前端隐藏只是体验，不是安全边界）。</p>
 *
 * <p>追加在末尾而不是替换任何一项：常规用户的 5 项导航零位移。</p>
 */
const navItems = computed(() => {
  const items = NAV_ITEMS.slice()
  if (userStore.isAdmin) {
    items.push({ to: '/admin', label: '管理后台', admin: true })
  }
  return items
})

const isLoggedIn = ref(
  Boolean(localStorage.getItem('token') || sessionStorage.getItem('token'))
)
/**
 * 头像占位字母。
 *
 * <p>数据来自 useUserStore（登录时 setFromLogin，路由守卫再 fetchMe 校验），
 * 而不是 localStorage.getItem('username') —— 登录页实际存的是
 * {@code localStorage.setItem('user', JSON.stringify(user))}，键名是 'user'
 * 而非 'username'。之前读错了键，导致头像恒为占位符「?」。</p>
 *
 * <p>另：{@code <script setup>} 的模板作用域不暴露 localStorage，
 * 在模板里直接写 localStorage 会让整条导航渲染中断（Vue 会抛
 * "was accessed during render but is not defined on instance"）。</p>
 */
const usernameInitial = computed(() => (userStore.username || '?').charAt(0))
/** 真实头像图片。与「我的」页、活跃用户列表用的是同一个字段，视觉才统一。 */
const userAvatar = computed(() => userStore.avatar || '')
const menuOpen = ref(false)
const scrolled = ref(false)

/**
 * 首页导航保持浅色。
 *
 * <p>Home 是全站唯一自带独立深色 Hero（VideoBackground + 深色底 + 白字）的
 * 页面，深色顶栏与它之间会失去"亮条压暗块"的层次关系。其余页面透出星空
 * 壁纸，暗玻璃与壁纸同源，两者是同一层关系。故此处按路由切换外观，
 * 而非按背景色自适应（后者会让"每页一样"这条硬需求失效）。</p>
 */
const isHome = computed(() => route.path === '/')
/** /post 页上"发布"指向自己，给激活态而不是可点的普通胶囊 */
const isPublishPage = computed(() => route.path === '/post')

const unreadCount = computed(() => notificationStore.unreadCount || 0)
const cartCount = computed(() => cartStore.count || 0)

function onScroll() {
  scrolled.value = window.scrollY > 8
}

function onCartAdd() {
  cartStore.bump()
}

function onCartCount(e) {
  cartStore.setCount(e.detail)
}

function go(path) {
  menuOpen.value = false
  router.push(path)
}

async function onLogout() {
  menuOpen.value = false
  // auth.logout() 内部已清 localStorage/sessionStorage，无需重复
  try {
    await logout()
  } catch {
    // 登出接口失败也要完成本地清理，否则用户会卡在"看似已登出却还在登录态"
  }
  cartStore.reset()
  notificationStore.resetUnreadCount()
  // 登出后必须清身份缓存，否则下一位登录者的 role 会被串用
  userStore.reset()
  isLoggedIn.value = false
  router.push('/login')
}

// 登出/登录在其他页面发生时（例如 Messages 页标记已读），同步顶栏状态
function syncAuth() {
  isLoggedIn.value = Boolean(
    localStorage.getItem('token') || sessionStorage.getItem('token')
  )
  // 登录页会 setFromLogin 到 store，但那只发生在该页面的内存里；
  // 从别处登出/登录时这里补拉一次，避免顶栏与实际登录态脱节
  if (isLoggedIn.value) userStore.fetchMe()
}

onMounted(() => {
  onScroll()
  window.addEventListener('scroll', onScroll, { passive: true })
  window.addEventListener('storage', syncAuth)
  window.addEventListener('cart:add', onCartAdd)
  window.addEventListener('cart:count', onCartCount)
  cartStore.refresh()
})

// 必须逐个解绑：SPA 走缓存的返回/前进会重新挂载，残留监听会累积
onBeforeUnmount(() => {
  window.removeEventListener('scroll', onScroll)
  window.removeEventListener('storage', syncAuth)
  window.removeEventListener('cart:add', onCartAdd)
  window.removeEventListener('cart:count', onCartCount)
})
</script>

<template>
  <!--
    data-plaza-dark 是与 Plaza.vue / Products.vue 同一套暗色 token 机制
    （属性名相同，便于三页样式互相借鉴）。首页不要这套，走 --navbar-light 分支。
  -->
  <div class="app-navbar" :class="{ 'is-light': isHome, 'is-scrolled': scrolled }" data-plaza-dark>
    <nav class="navbar-inner" aria-label="主导航">
      <router-link to="/" class="nb-logo" aria-label="校园二手首页">
        <span class="nb-logo-mark" aria-hidden="true">♻</span>
        <span class="nb-logo-text">校园二手</span>
      </router-link>

      <ul class="nb-links">
        <li v-for="item in navItems" :key="item.to">
          <router-link
            :to="item.to"
            class="nb-link"
            :class="{ 'nb-link--admin': item.admin }"
            :aria-label="item.admin ? '管理后台' : undefined"
            :title="item.admin ? '管理后台' : undefined"
          >
            <svg
              v-if="item.admin"
              class="nb-admin-icon"
              viewBox="0 0 24 24" fill="none" stroke="currentColor"
              stroke-width="2" stroke-linecap="round" stroke-linejoin="round"
              aria-hidden="true"
            >
              <path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" />
            </svg>
            <span v-if="item.admin" class="nb-admin-label">{{ item.label }}</span>
            <template v-else>{{ item.label }}</template>
            <span
              v-if="item.to === '/messages' && unreadCount > 0"
              class="nb-badge nb-badge--danger"
            >{{ unreadCount > 99 ? '99+' : unreadCount }}</span>
          </router-link>
        </li>
      </ul>

      <div class="nb-actions">
        <template v-if="isLoggedIn">
          <router-link to="/cart" class="nb-icon-btn" aria-label="购物车">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" aria-hidden="true">
              <circle cx="9" cy="21" r="1.5" /><circle cx="20" cy="21" r="1.5" />
              <path d="M1 1h4l2.7 13.4a2 2 0 0 0 2 1.6h9.7a2 2 0 0 0 2-1.6L23 6H6" />
            </svg>
            <span v-if="cartCount > 0" class="nb-badge">{{ cartCount > 99 ? '99+' : cartCount }}</span>
          </router-link>

          <router-link
            to="/post"
            class="nb-publish"
            :class="{ 'is-active': isPublishPage }"
            :aria-current="isPublishPage ? 'page' : undefined"
          >{{ isPublishPage ? '发布中' : '发布' }}</router-link>

          <div class="nb-user">
            <!--
              学生认证徽章：放在头像左侧而不是叠在头像上做角标。
              角标会盖住用户头像（任意图片），对比度不可控；左侧方案复用
              VerifiedBadge 已验证过的样式，也不占右侧布局宽度，5 项导航零位移。
              showLabel 默认 false，这里只出 🎓 图标 + title + 读屏文本。
            -->
            <VerifiedBadge v-if="userStore.studentVerified" :verified="true" size="sm" />
            <button
              class="nb-avatar"
              type="button"
              :aria-expanded="menuOpen"
              aria-haspopup="menu"
              :aria-label="userStore.studentVerified
                ? `账号菜单：${userStore.username || '未登录'}（已通过学生认证）`
                : `账号菜单：${userStore.username || '未登录'}`"
              @click="menuOpen = !menuOpen"
            >
              <img v-if="userAvatar" :src="userAvatar" :alt="`${userStore.username} 的头像`" />
              <template v-else>{{ usernameInitial }}</template>
            </button>
            <div v-if="menuOpen" class="nb-menu" role="menu">
              <button type="button" role="menuitem" @click="go('/profile')">我的</button>
              <!-- Drafts 此前在全站没有任何入口，页面可达性为零 -->
              <button type="button" role="menuitem" @click="go('/drafts')">我的草稿</button>
              <button
                type="button"
                role="menuitemcheckbox"
                :aria-checked="soundEnabled"
                @click="toggleSound"
              >消息提示音{{ soundEnabled ? ' 开' : ' 关' }}</button>
              <button type="button" role="menuitem" class="nb-menu-danger" @click="onLogout">退出</button>
            </div>
          </div>
        </template>

        <router-link v-else to="/login" class="nb-login">登录</router-link>
      </div>
    </nav>
  </div>

  <!--
    占位块：导航是 fixed，已脱离文档流。占位与导航同生共死，
    由 AppNavbar 自行承担 64px 补偿，避免逐页写 padding-top——
    那等于把"每页各写一份"的病根从导航搬到间距上。
  -->
  <div class="app-navbar-spacer" aria-hidden="true"></div>
</template>

<style scoped>
.app-navbar {
  position: fixed; top: 0; left: 0; right: 0; z-index: 100;
  height: var(--header-h);
  background: rgba(10, 16, 19, 0.55);
  -webkit-backdrop-filter: blur(18px) saturate(140%);
  backdrop-filter: blur(18px) saturate(140%);
  border-bottom: 1px solid rgba(255, 255, 255, 0.08);
  transition: background var(--dur-slow) var(--ease), box-shadow var(--dur-slow) var(--ease);
}
/* 滚动后加深并浮起：未滚动时半透明让壁纸多露一点，
   滚动后需要把下方内容压住，否则文字会与滑过的卡片叠在一起 */
.app-navbar.is-scrolled {
  background: rgba(8, 13, 16, 0.82);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.35);
}

/* 首页浅色分支：Home 有独立深色 Hero，浅色顶栏与其形成亮/暗层次 */
.app-navbar.is-light {
  background: rgba(255, 255, 255, 0.85);
  border-bottom-color: rgba(15, 15, 16, 0.08);
}
.app-navbar.is-light.is-scrolled {
  background: rgba(255, 255, 255, 0.95);
  box-shadow: 0 8px 24px rgba(15, 15, 16, 0.12);
}

.app-navbar-spacer { height: var(--header-h); }

/* 暗色 token：与 Plaza.vue / Products.vue 逐条对齐。
   首页分支需要在浅底上工作，故同时提供浅色覆盖。 */
.app-navbar {
  --text: rgba(255, 255, 255, 0.92);
  --text-2: rgba(255, 255, 255, 0.60);
  --text-3: rgba(255, 255, 255, 0.38);
  --border: rgba(255, 255, 255, 0.10);
  --surface-3: rgba(255, 255, 255, 0.03);
  --accent: #2dd4bf;
  --accent-soft: rgba(45, 212, 191, 0.14);
  --danger: #f87171;
}
.app-navbar.is-light {
  --text: #1b1b18;
  --text-2: #6b6b64;
  --text-3: #9a9a92;
  --border: #e7e7e2;
  --surface-3: #f3f3f0;
  --accent: #0b6e54;
  --accent-soft: rgba(11, 110, 84, 0.08);
}

.navbar-inner {
  max-width: var(--container);
  height: 100%;
  margin: 0 auto;
  padding: 0 var(--space-6);
  display: flex; align-items: center; gap: var(--space-6);
}

/* ---------- 左：Logo ---------- */
.nb-logo { display: inline-flex; align-items: center; gap: var(--space-2); text-decoration: none; flex-shrink: 0; }
.nb-logo-mark {
  width: 30px; height: 30px;
  display: inline-flex; align-items: center; justify-content: center;
  border-radius: var(--radius-sm);
  background: linear-gradient(135deg, #10b981, #38bdf8);
  color: #fff; font-size: 15px;
}
.nb-logo-text {
  font-size: var(--text-lg); font-weight: var(--weight-semibold);
  color: var(--text);
  background: linear-gradient(135deg, #2dd4bf, #38bdf8);
  -webkit-background-clip: text; background-clip: text;
  -webkit-text-fill-color: transparent;
}
/* 渐变文字必须保留 color 兜底：不支持 background-clip:text 的浏览器上
   文字会因 -webkit-text-fill-color:transparent 而彻底消失 */
@supports not ((-webkit-background-clip: text) or (background-clip: text)) {
  .nb-logo-text { -webkit-text-fill-color: currentColor; color: var(--text); }
}
/* 首页浅色分支的深绿→蓝渐变。
   必须用 background-image 而不是 background 简写：简写会把
   background-clip 重置为初始值 border-box，渐变便画在边框盒上而文字被
   -webkit-text-fill-color:transparent 掏空 —— Logo 直接消失。
   深色分支之所以正常，是因为那里 background 与 clip 写在同一条规则内、
   clip 在后。 */
.app-navbar.is-light .nb-logo-text {
  background-image: linear-gradient(135deg, #0b6e54, #2563eb);
  -webkit-background-clip: text;
  background-clip: text;
}

/* ---------- 中：导航项 ---------- */
.nb-links {
  display: flex; align-items: center; gap: var(--space-1);
  list-style: none; margin: 0; padding: 0;
  flex: 1;
}
.nb-link {
  position: relative;
  display: inline-flex; align-items: center; gap: 5px;
  padding: 8px 12px;
  font-size: var(--text-base);
  color: var(--text-2);
  text-decoration: none; white-space: nowrap;
  border-radius: var(--radius-sm);
  transition: color var(--dur-fast) var(--ease);
}
.nb-link:hover { color: var(--text); }
.nb-link.router-link-exact-active { color: var(--text); font-weight: var(--weight-medium); }
/* 当前项渐变下划线：用 ::after 而非 border-bottom——
   border 会占盒模型高度，导致按下时整行抖动 */
.nb-link.router-link-exact-active::after {
  content: "";
  position: absolute; left: 12px; right: 12px; bottom: 2px;
  height: 2px; border-radius: var(--radius-full);
  background: linear-gradient(90deg, #2dd4bf, #38bdf8);
}

/* ---------- 管理后台入口（仅管理员可见，见 navItems） ---------- */
/* 描边胶囊与常规文字项区分：它是全站唯一的管理入口，
   混在普通项里等同于把入口藏起来（用户反馈"进不了管理后台"）。
   用 inset 阴影而不是 border：不占盒模型，8px 纵向内边距保持不变，
   整条导航的高度不会因多出 1px 边框而与其它页面错位。 */
.nb-link--admin {
  margin-left: var(--space-2);
  color: var(--accent);
  box-shadow: inset 0 0 0 1px var(--accent);
  gap: 4px;
}
.nb-link--admin:hover { color: var(--accent); background: var(--accent-soft); }
.nb-admin-icon { width: 14px; height: 14px; flex-shrink: 0; }

/* ---------- 右：动作区 ---------- */
.nb-actions { display: flex; align-items: center; gap: var(--space-2); flex-shrink: 0; }

.nb-icon-btn {
  position: relative;
  width: 38px; height: 38px;
  display: inline-flex; align-items: center; justify-content: center;
  border: 1px solid transparent; border-radius: var(--radius);
  color: var(--text-2); text-decoration: none;
  transition: background var(--dur-fast) var(--ease), color var(--dur-fast) var(--ease);
}
.nb-icon-btn svg { width: 20px; height: 20px; }
.nb-icon-btn:hover { background: var(--surface-3); color: var(--text); }

.nb-badge {
  position: absolute; top: 3px; right: 3px;
  min-width: 16px; height: 16px; padding: 0 4px;
  border-radius: var(--radius-full);
  background: var(--accent); color: #04211d;
  font-size: 10px; line-height: 16px; font-weight: var(--weight-semibold);
  text-align: center;
  font-variant-numeric: tabular-nums;
}
.nb-badge--danger { background: var(--danger); color: #fff; }
/* 角标随路由切换不该弹跳，只有数值变化才动——用 cart 的 bump 语义区分过重 */
.nb-link .nb-badge { position: static; margin-left: 2px; }

/* 发布：渐变胶囊 */
.nb-publish {
  padding: 8px 16px;
  border-radius: var(--radius-full);
  background: linear-gradient(135deg, #10b981, #2dd4bf);
  color: #04211d;
  font-size: var(--text-sm); font-weight: var(--weight-semibold);
  text-decoration: none; white-space: nowrap;
  box-shadow: 0 4px 14px rgba(45, 212, 191, 0.28);
  transition: transform var(--dur) var(--ease), box-shadow var(--dur) var(--ease),
    opacity var(--dur) var(--ease);
}
.nb-publish:hover { transform: translateY(-1px); box-shadow: 0 6px 18px rgba(45, 212, 191, 0.38); }
/* /post 页上"发布"指向自己：给激活态而不是可点的普通胶囊 */
.nb-publish.is-active {
  background: transparent;
  border: 1px solid var(--accent);
  color: var(--accent);
  box-shadow: none;
  cursor: default;
  opacity: 0.85;
}
.nb-publish.is-active:hover { transform: none; box-shadow: none; }

.nb-login {
  padding: 8px 18px;
  border-radius: var(--radius-full);
  background: linear-gradient(135deg, #10b981, #2dd4bf);
  color: #04211d;
  font-size: var(--text-sm); font-weight: var(--weight-semibold);
  text-decoration: none;
}

/* ---------- 头像与下拉 ---------- */
/* position:relative 是给下方 .nb-menu 绝对定位用的；
   徽章与头像并排靠 inline-flex，徽章出现在前所以显示在头像左侧。 */
.nb-user { position: relative; display: inline-flex; align-items: center; gap: var(--space-2); }
.nb-avatar {
  width: 36px; height: 36px;
  display: inline-flex; align-items: center; justify-content: center;
  overflow: hidden;
  border: 1px solid var(--border); border-radius: var(--radius-full);
  background: var(--surface-3);
  color: var(--text); font-size: var(--text-sm); font-weight: var(--weight-semibold);
  cursor: pointer;
  transition: border-color var(--dur-fast) var(--ease);
}
.nb-avatar img { width: 100%; height: 100%; object-fit: cover; }
.nb-avatar:hover { border-color: var(--accent); }
.nb-menu {
  position: absolute; top: calc(100% + 8px); right: 0;
  z-index: 200;
  min-width: 132px; padding: var(--space-1);
  background: rgba(12, 19, 22, 0.97);
  border: 1px solid rgba(255, 255, 255, 0.12);
  border-radius: var(--radius);
  box-shadow: 0 12px 32px rgba(0, 0, 0, 0.5);
}
.app-navbar.is-light .nb-menu {
  background: #fff;
  border-color: var(--border);
  box-shadow: 0 12px 32px rgba(15, 15, 16, 0.18);
}
.nb-menu button {
  display: block; width: 100%;
  padding: 8px 12px;
  border: none; border-radius: var(--radius-sm);
  background: transparent;
  color: var(--text);
  font-size: var(--text-sm); text-align: left; cursor: pointer;
  transition: background var(--dur-fast) var(--ease);
}
.nb-menu button:hover { background: var(--accent-soft); }
.nb-menu-danger { color: var(--danger) !important; }

@media (max-width: 900px) {
  .nb-logo-text { display: none; }
  .navbar-inner { gap: var(--space-3); padding: 0 var(--space-4); }
}
@media (max-width: 640px) {
  .nb-links { gap: 0; }
  .nb-link { padding: 8px; font-size: var(--text-sm); }
  /* 窄屏上 6 项会挤爆一行：管理入口只留盾牌图标，
     语义由链接上的 aria-label / title 兜底 */
  .nb-link--admin .nb-admin-label { display: none; }
}

/* 降级为静态等价物 */
@media (prefers-reduced-motion: reduce) {
  .app-navbar,
  .nb-publish,
  .nb-link,
  .nb-icon-btn,
  .nb-menu button { transition: none; }
  .nb-publish:hover { transform: none; }
  /* 当前项下划线是"我在哪"的唯一标识，不能因降级而消失 */
  .nb-link.router-link-exact-active::after { transition: none; }
}
</style>
