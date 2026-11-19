<template>
  <div class="page messages-page">

    <!-- 图片放大预览：点击遮罩或按 Esc 关闭 -->
    <div v-if="lightboxUrl" class="lightbox-mask" role="dialog" aria-label="图片预览" @click="lightboxUrl = ''">
      <img :src="lightboxUrl" alt="聊天图片" @click.stop />
    </div>

    <main class="msg-main">
      <div class="msg-shell" :class="{ 'chat-open': selectedUserId }">
        <!-- 会话列表 -->
        <aside class="sidebar">
          <div class="sidebar-head">
            <h2 class="sidebar-title">消息中心</h2>
            <span v-if="unreadCount > 0" class="badge badge-danger">{{ unreadCount }}</span>
          </div>
          <div class="tabs">
            <button class="tab" :class="{ active: activeTab === 'received' }" type="button" @click="activeTab = 'received'">收件箱</button>
            <button class="tab" :class="{ active: activeTab === 'sent' }" type="button" @click="activeTab = 'sent'">已发送</button>
          </div>

          <div class="conv-list">
            <div v-if="loading" class="empty"><p class="loading-text">[ LOADING… ]</p></div>
            <div v-else-if="displayConversations.length === 0" class="empty">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" /></svg>
              <p>{{ activeTab === 'sent' ? '还没有发出过消息' : '暂无消息' }}</p>
            </div>
            <button
              v-for="conv in displayConversations"
              :key="conv.userId"
              class="conv"
              :class="{ active: selectedUserId === conv.userId }"
              type="button"
              @click="selectConversation(conv)"
            >
              <div class="conv-avatar">
                <img :src="conv.avatar || defaultAvatar" :alt="conv.username" />
                <span v-if="conv.unreadCount > 0" class="msg-badge">{{ conv.unreadCount }}</span>
              </div>
              <div class="conv-info">
                <div class="conv-head">
                  <span class="conv-name">
                    {{ conv.username }}
                    <VerifiedBadge :verified="conv.studentVerified" size="sm" />
                  </span>
                  <span class="conv-time">{{ formatTime(conv.lastMessageTime) }}</span>
                </div>
                <p class="conv-preview">{{ conv.lastMessage }}</p>
              </div>
            </button>
          </div>
        </aside>

        <!-- 聊天面板 -->
        <section class="chat">
          <div v-if="!selectedUserId" class="chat-empty">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" /></svg>
            <h3>选择对话</h3>
            <p>选择一个联系人开始聊天</p>
          </div>

          <template v-else>
            <div class="chat-head">
              <!-- 窄屏（会话列表被收起）时用于返回列表 -->
              <button class="chat-back" type="button" aria-label="返回会话列表" @click="closeChat">←</button>
              <div class="chat-user">
                <div class="chat-avatar"><img :src="currentConversation?.avatar || defaultAvatar" :alt="currentConversation?.username" /></div>
                <div class="chat-user-info">
                  <span class="chat-username">
                    {{ currentConversation?.username }}
                    <VerifiedBadge :verified="currentConversation?.studentVerified" size="sm" />
                  </span>
                  <span class="chat-status">{{ isNewChat ? '新会话' : '在线' }}</span>
                </div>
              </div>
              <button v-if="contextProduct" type="button" class="head-close" aria-label="关闭商品上下文" @click="contextProduct = null">×</button>
            </div>

            <!-- 商品上下文小卡片：让双方清楚在聊哪件商品 -->
            <div v-if="contextProduct" class="ctx-card">
              <div class="ctx-thumb">
                <img v-if="contextProduct.images" :src="firstImage(contextProduct.images, '')" :alt="contextProduct.title" />
                <span v-else class="ctx-thumb-fallback" aria-hidden="true">
                  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
                    <rect x="3" y="3" width="18" height="18" rx="2" />
                    <circle cx="8.5" cy="8.5" r="1.5" /><path d="M21 15l-5-5L5 21" />
                  </svg>
                </span>
              </div>
              <div class="ctx-info">
                <span class="ctx-label">正在咨询的商品</span>
                <span class="ctx-title">{{ contextProduct.title }}</span>
              </div>
              <span class="ctx-price">¥{{ Number(contextProduct.price ?? 0).toFixed(2) }}</span>
            </div>

            <div class="chat-body" ref="messagesContainer">
              <!-- 新会话且无任何消息时的引导 -->
              <div v-if="isNewChat && currentMessages.length === 0" class="chat-intro">
                <p class="chat-intro-title">你们还没聊过</p>
                <p class="chat-intro-desc">发第一条消息开始沟通吧，问问商品细节、约个交易时间地点。</p>
              </div>
              <div
                v-for="(msg, index) in currentMessages"
                :key="msg.id || index"
                class="message"
                :class="{ sent: msg.senderId === currentUserId, received: msg.senderId !== currentUserId }"
              >
                <div v-if="msg.senderId !== currentUserId" class="message-avatar">
                  <img :src="currentConversation?.avatar || defaultAvatar" alt="头像" />
                </div>
                <div class="message-content">
                  <!-- 图片消息：气泡内嵌图片（点击放大），配文可选 -->
                  <div v-if="isImage(msg)" class="message-bubble msg-image">
                    <img
                      class="msg-image-thumb"
                      :src="msg.content"
                      alt="图片消息"
                      loading="lazy"
                      @load="onMessageImageLoad"
                      @click="lightboxUrl = msg.content"
                    />
                    <p v-if="msg.caption">{{ msg.caption }}</p>
                  </div>
                  <div v-else class="message-bubble"><p>{{ msg.content }}</p></div>
                  <span class="message-time">{{ formatMessageTime(msg.createdTime) }}</span>
                </div>
              </div>
            </div>

            <div class="chat-input">
              <!-- 图片上传的 file input 隐藏，由旁边的图片按钮触发 -->
              <input
                ref="imageInput"
                type="file"
                accept="image/jpeg,image/png,image/gif,image/webp,.jpg,.jpeg,.png,.gif,.webp"
                class="chat-image-input"
                :disabled="imageUploading"
                aria-hidden="true"
                tabindex="-1"
                @change="onImageFileChange"
              />
              <button
                class="chat-image-btn"
                type="button"
                title="发送图片（输入框里的文字会作为配文）"
                :disabled="imageUploading || !selectedUserId"
                aria-label="发送图片"
                @click="pickImage"
              >
                <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6">
                  <rect x="3" y="3" width="18" height="18" rx="2" />
                  <circle cx="8.5" cy="8.5" r="1.5" />
                  <path d="M21 15l-5-5L5 21" />
                </svg>
              </button>
              <input
                type="text"
                v-model="newMessage"
                placeholder="输入消息…"
                aria-label="消息输入"
                @keyup.enter="sendMessage"
              />
              <button class="btn btn-primary" type="button" :disabled="!newMessage.trim() || imageUploading" @click="sendMessage">发送</button>
            </div>
          </template>
        </section>
      </div>
    </main>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount, nextTick, watch } from 'vue'
import { useRoute } from 'vue-router'
import {
  getConversations,
  getConversation,
  getSentMessages,
  sendMessage as sendMessageApi,
  markAsRead
} from '../api/message'
import { getUserById } from '../api/user'
import { getProductDetail } from '../api/product'
import { uploadImage } from '../api/upload'
import { firstImage } from '../utils/images'
import { ElMessage } from 'element-plus'
import { useNotificationStore } from '../stores/notification'
import { useMessageNotify } from '../composables/useMessageNotify'
import VerifiedBadge from '../components/VerifiedBadge.vue'

const route = useRoute()
const notificationStore = useNotificationStore()
/**
 * 未读数走单例：App.vue 已在 useMessageNotify 上启动 10 秒轮询。
 * 此处此前另发两次 /messages/unread-count，与全局轮询并行，
 * 是同一份数据的第二个请求源。
 */
const { poll: pollUnread } = useMessageNotify()
const loading = ref(true)
const activeTab = ref('received')
const conversations = ref([])
/** 「已发送」列表：按收件人聚合，每个收件人只保留最近一条 */
const sentConversations = ref([])
const displayConversations = computed(() =>
  activeTab.value === 'sent' ? sentConversations.value : conversations.value
)
const selectedUserId = ref(null)
const currentMessages = ref([])
const newMessage = ref('')
/** 全站未读数由 AppNavbar 消费，这里读同一 store，不再持有第二份 */
const unreadCount = computed(() => notificationStore.unreadCount || 0)
const messagesContainer = ref(null)
/**
 * 当前登录用户 ID。
 *
 * 注意：此前这里读 `localStorage.getItem('userId')`，但该键全项目从未被写入，
 * 导致 currentUserId 恒为 0——自己发的消息被渲染成「收到」（左侧白气泡），
 * 且每条消息都被误判为未读。改为从存储的 user JSON 解析。
 */
function resolveCurrentUserId() {
  const direct = parseInt(localStorage.getItem('userId') || '', 10)
  if (Number.isFinite(direct) && direct > 0) return direct
  for (const store of [localStorage, sessionStorage]) {
    try {
      const raw = store.getItem('user')
      if (!raw) continue
      const parsed = JSON.parse(raw)
      const id = parseInt(parsed?.id, 10)
      if (Number.isFinite(id) && id > 0) return id
    } catch { /* 忽略解析失败 */ }
  }
  return 0
}

const currentUserId = ref(resolveCurrentUserId())

/** 图片消息上传中标记：上传期间禁用图片按钮与发送按钮 */
const imageUploading = ref(false)
/** 图片放大预览的 URL；空串表示未打开 */
const lightboxUrl = ref('')
/** 隐藏的图片 file input（由图片按钮触发） */
const imageInput = ref(null)

/** 上传前与后端 UploadService 白名单一致的本地图额类型/大小限制 */
const IMAGE_MAX_BYTES = 5 * 1024 * 1024

/** 该消息是否为图片消息（后端 msgType=1；乐观渲染时也可能带 type='image'） */
function isImage(msg) {
  return msg?.msgType === 1 || msg?.msgType === '1' || msg?.type === 'image'
}

function pickImage() {
  imageInput.value?.click()
}

/**
 * 选择图片 → 上传 → 以「图片消息」发送。
 *
 * <p>交互约定：发送前输入框里已打的字自动作为配文一并发出（可不打），
 * 与微信「先打字再发图」的习惯一致。上传失败不清空输入框，配文不丢。</p>
 */
async function onImageFileChange(event) {
  const file = event.target.files?.[0]
  event.target.value = '' // 允许重选同一张图
  if (!file) return
  if (!selectedUserId.value) {
    ElMessage.warning('先选择聊天对象再发送图片')
    return
  }
  if (!/^image\/(jpeg|png|gif|webp)$/i.test(file.type || '')) {
    ElMessage.error('只支持 JPG / PNG / GIF / WebP 格式的图片')
    return
  }
  if (file.size > IMAGE_MAX_BYTES) {
    ElMessage.error('图片不能超过 5MB')
    return
  }

  const caption = newMessage.value.trim()
  imageUploading.value = true
  try {
    // 与商品图片同一条上传管线：落盘 /uploads/**，返回可直接访问的路径
    const url = await uploadImage(file, file.name || 'chat-image.png')
    const res = await sendMessageApi({
      receiverId: selectedUserId.value,
      content: url,
      caption,
      type: 'image',
      productId: contextProductId.value
    })
    if (res.code === 200) {
      newMessage.value = ''
      pushLocalMessage(res, url, { msgType: 1, caption })
      await nextTick()
      scrollToBottom()
    } else {
      ElMessage.error(res.message || '图片发送失败')
    }
  } catch (e) {
    ElMessage.error(e?.message || e?.cause?.message || '图片发送失败，请检查网络')
  } finally {
    imageUploading.value = false
  }
}

/** 发送成功后的本地乐观追加：文本/图片消息共用，保持与后端返回同构 */
function pushLocalMessage(res, content, extra = {}) {
  const msg = {
    id: res.data?.id || Date.now(),
    senderId: currentUserId.value,
    receiverId: selectedUserId.value,
    content,
    productId: contextProductId.value,
    msgType: extra.msgType || 0,
    caption: extra.caption || '',
    createdTime: new Date().toISOString(),
    isRead: 0
  }
  currentMessages.value.push(msg)
  // 「已发送」列表同步更新预览，切到该标签时不必等下一次拉取
  const preview = messagePreview(msg)
  const sentEntry = sentConversations.value.find(c => c.userId === msg.receiverId)
  if (sentEntry) {
    sentEntry.lastMessage = preview
    sentEntry.lastMessageTime = msg.createdTime
    sentConversations.value = [sentEntry, ...sentConversations.value.filter(c => c.userId !== msg.receiverId)]
  } else {
    sentConversations.value = [{
      userId: msg.receiverId,
      username: currentConversation.value?.username || ('用户' + msg.receiverId),
      avatar: currentConversation.value?.avatar || defaultAvatar,
      lastMessage: preview,
      lastMessageTime: msg.createdTime,
      unreadCount: 0,
      messages: []
    }, ...sentConversations.value]
  }
  // 新会话在列表中还不存在，首条消息发出后应立刻可见
  if (isNewChat.value && draftConversation.value) {
    conversations.value = [
      { ...draftConversation.value, lastMessage: messagePreview(msg), lastMessageTime: msg.createdTime },
      ...conversations.value
    ]
    draftConversation.value = null
  }
  return msg
}

/** 会话预览文案：与后端 MessageService#previewOf 对齐 */
function messagePreview(msg) {
  return isImage(msg) ? (msg.caption ? `[图片] ${msg.caption}` : '[图片]') : msg.content
}

const defaultAvatar = "data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 40 40'%3E%3Crect width='40' height='40' fill='%230b6e54'/%3E%3Ccircle cx='20' cy='16' r='6' fill='%23ffffff'/%3E%3Cpath d='M8 36c0-6.6 5.4-12 12-12s12 5.4 12 12' fill='%23ffffff'/%3E%3C/svg%3E"

/**
 * 当前会话对象。
 *
 * 新会话（对方从未发过消息）不在 conversations 列表里，
 * 此处回落到 draftConversation，保证聊天区能渲染出昵称与头像。
 */
const draftConversation = ref(null)

/** 商品上下文：从商品页/订单页跳转时带入 productId */
const contextProduct = ref(null)
const contextProductId = computed(() => {
  const pid = route.query.productId
  const n = parseInt(pid, 10)
  return Number.isFinite(n) && n > 0 ? n : null
})

const currentConversation = computed(() => {
  if (!selectedUserId.value) return null
  return conversations.value.find(c => c.userId === selectedUserId.value) || draftConversation.value
})

/** 是否为尚未产生过历史消息的新会话 */
const isNewChat = computed(() =>
  selectedUserId.value != null &&
  !conversations.value.some(c => c.userId === selectedUserId.value)
)


/**
 * 加载「已发送」列表。
 *
 * <p>此前切换到「已发送」标签只改了 activeTab，列表仍然渲染收件箱，
 * 看起来像点了没反应。这里按收件人聚合成会话，点击即可打开与该用户的会话。</p>
 */
async function fetchSentConversations(options = {}) {
  try {
    const res = await getSentMessages()
    if (res.code !== 200 || !Array.isArray(res.data)) return
    const map = new Map()
    for (const m of res.data) {
      const uid = m.receiverId
      if (!uid || map.has(uid)) continue // 后端按时间倒序，首条即最近一条
      map.set(uid, {
        userId: uid,
        username: m.receiver?.username || ('用户' + uid),
        avatar: m.receiver?.avatar || defaultAvatar,
        lastMessage: messagePreview(m),
        lastMessageTime: m.createdTime,
        unreadCount: 0,
        messages: []
      })
    }
    sentConversations.value = [...map.values()]
  } catch (e) {
    if (!options.silent) console.error('加载已发送消息失败:', e)
  }
}

async function fetchMessages(options = {}) {
  if (!options.silent) loading.value = true
  try {
    pollUnread()
    const convRes = await getConversations()
    if (convRes.code === 200 && convRes.data) {
      conversations.value = convRes.data.map(c => ({
        userId: c.other_user_id || c.otherUserId || c.userId,
        username: c.username || ('用户' + (c.other_user_id || c.otherUserId || c.userId)),
        avatar: c.avatar || defaultAvatar,
        lastMessage: c.lastMessage || '',
        lastMessageTime: c.lastMessageTime || c.last_message_time || '',
        unreadCount: c.unreadCount || c.unread_count || 0,
        messages: []
      }))
    }
  } catch (e) {
    console.error('加载消息失败:', e)
  } finally {
    if (!options.silent) loading.value = false
  }
}

/**
 * 从查询参数打开与某个用户的会话。
 *
 * 此前只在 conversations 里 find，找不到就静默返回，
 * 导致「从未聊过的人」根本无法开新会话——这正是私信发不出去的根因。
 */
async function openChatWith(userId, options = {}) {
  const uid = parseInt(userId, 10)
  if (!Number.isFinite(uid) || uid <= 0) return
  if (uid === currentUserId.value) {
    ElMessage.warning('不能和自己聊天')
    return
  }

  selectedUserId.value = uid

  const existing = conversations.value.find(c => c.userId === uid)
  if (existing) {
    draftConversation.value = null
    await selectConversation(existing)
  } else {
    // 新会话：先按用户 ID 取昵称头像兜底，再拉历史（返回空数组属正常）
    await loadDraftConversation(uid, options.username)
    try {
      const res = await getConversation(uid)
      currentMessages.value = (res.code === 200 && res.data ? res.data : [])
        .sort((a, b) => new Date(a.createdTime) - new Date(b.createdTime))
    } catch (e) {
      console.error('加载新会话历史失败:', e)
      currentMessages.value = []
    }
    await nextTick()
    scrollToBottom()
  }

  // 预填问候语，便于直接发送；用户可自由修改
  if (options.productId || contextProductId.value) {
    prefillGreeting()
  }
}

/** 合成一个会话对象，供新会话时渲染头像昵称 */
async function loadDraftConversation(uid, fallbackName) {
  draftConversation.value = {
    userId: uid,
    username: fallbackName || '用户' + uid,
    avatar: defaultAvatar,
    lastMessage: '',
    lastMessageTime: '',
    unreadCount: 0,
    messages: []
  }
  try {
    const res = await getUserById(uid)
    if (res.code === 200 && res.data) {
      draftConversation.value = {
        ...draftConversation.value,
        username: res.data.username || draftConversation.value.username,
        avatar: res.data.avatar || defaultAvatar
      }
    }
  } catch (e) {
    console.error('获取用户信息失败:', e)
  }
}

/** 拉取跳转时携带的商品，用于顶部小卡片与问候语 */
async function loadContextProduct() {
  const pid = contextProductId.value
  if (!pid) {
    contextProduct.value = null
    return
  }
  try {
    const res = await getProductDetail(pid)
    if (res.code === 200 && res.data) contextProduct.value = res.data
  } catch (e) {
    console.error('获取商品信息失败:', e)
    contextProduct.value = null
  }
}

/** 预填「你好，请问「商品标题」还在吗？」 */
function prefillGreeting() {
  const title = contextProduct.value?.title
  newMessage.value = title
    ? `你好，请问「${title}」还在吗？`
    : '你好，在吗？'
}

async function selectConversation(conv) {
  selectedUserId.value = conv.userId
  draftConversation.value = null
  try {
    const res = await getConversation(conv.userId)
    if (res.code === 200 && res.data) {
      currentMessages.value = res.data.sort((a, b) => new Date(a.createdTime) - new Date(b.createdTime))
      await nextTick()
      scrollToBottom()
      const unreadMessages = currentMessages.value.filter(m => m.senderId !== currentUserId.value && m.isRead === 0)
      for (const msg of unreadMessages) { try { await markAsRead(msg.id) } catch { console.log('标记已读失败') } }
      // 会话列表里的角标要立即消失，不能等下一轮 9 秒的列表刷新
      const convEntry = conversations.value.find(c => c.userId === conv.userId)
      if (convEntry) convEntry.unreadCount = 0
      // 角标要立刻递减，不能等下一轮 10 秒轮询：按实际已读条数本地扣减，再强制回源校正
      notificationStore.decrementUnreadCount(unreadMessages.length)
      pollUnread(true)
    }
  } catch (e) {
    console.error('加载对话失败:', e)
  }
}

async function sendMessage() {
  if (!newMessage.value.trim() || !selectedUserId.value) return
  const content = newMessage.value.trim()
  newMessage.value = ''
  try {
    // 带上 productId，后端会把消息挂到商品上下文中
    const res = await sendMessageApi({
      receiverId: selectedUserId.value,
      content,
      productId: contextProductId.value
    })
    if (res.code === 200) {
      pushLocalMessage(res, content)
      await nextTick()
      scrollToBottom()
    } else {
      ElMessage.error(res.message || '发送失败')
      newMessage.value = content
    }
  } catch (e) {
    ElMessage.error(e?.message || e?.cause?.message || '发送失败')
    newMessage.value = content
  }
}

function scrollToBottom() { if (messagesContainer.value) messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight }

/** 返回会话列表（窄屏下会话列表被收起，用返回键切回） */
function closeChat() {
  selectedUserId.value = null
  currentMessages.value = []
  draftConversation.value = null
}

/** 用户当前是否停留在消息底部：只在底部时才自动滚动，避免打断翻看历史 */
function isNearBottom() {
  const el = messagesContainer.value
  if (!el) return true
  return el.scrollHeight - el.scrollTop - el.clientHeight < 80
}

/** 图片解码完成后高度才确定，若用户仍在底部则补一次滚动 */
function onMessageImageLoad() {
  if (isNearBottom()) scrollToBottom()
}

/**
 * 刷新当前打开的会话。
 *
 * <p>此前聊天页只在打开会话时拉一次历史，对方再发消息必须手动切换会话/刷新页面才能看到，
 * 两个人同时在线时看起来就是「发不出去、收不到」。这里改为 3 秒轮询当前会话，
 * 收到的新消息就地追加并标记已读。</p>
 */
const CHAT_POLL_MS = 3000
let chatTimer = null
let refreshingChat = false
let pollTicks = 0

async function refreshCurrentConversation() {
  const uid = selectedUserId.value
  if (!uid || refreshingChat) return
  refreshingChat = true
  try {
    const res = await getConversation(uid)
    if (res.code !== 200 || !Array.isArray(res.data) || selectedUserId.value !== uid) return
    const incoming = res.data.slice().sort((a, b) => new Date(a.createdTime) - new Date(b.createdTime))
    // 保留本地乐观追加、服务端尚未返回的消息（其 id 为时间戳）
    const serverIds = new Set(incoming.map(m => m.id))
    const pendingLocal = currentMessages.value.filter(m => !serverIds.has(m.id) && Number(m.id) > 1e12)
    const prevMaxId = currentMessages.value.reduce((mx, m) => Math.max(mx, Number(m.id) || 0), 0)
    const fresh = incoming.filter(m => (Number(m.id) || 0) > prevMaxId && m.senderId !== currentUserId.value)
    const nearBottom = isNearBottom()

    currentMessages.value = [...incoming, ...pendingLocal]
      .sort((a, b) => new Date(a.createdTime) - new Date(b.createdTime))

    for (const m of fresh) {
      if (m.isRead === 0) {
        try { await markAsRead(m.id) } catch { /* 已读失败不影响显示 */ }
      }
    }
    if (fresh.length) {
      notificationStore.decrementUnreadCount(fresh.length)
      pollUnread(true)
      const convEntry = conversations.value.find(c => c.userId === uid)
      if (convEntry) convEntry.unreadCount = 0
    }
    if (nearBottom) {
      await nextTick()
      scrollToBottom()
    }
  } catch {
    /* 轮询失败静默，等下一轮 */
  } finally {
    refreshingChat = false
  }
}

function startChatPolling() {
  stopChatPolling()
  chatTimer = setInterval(() => {
    if (document.hidden) return
    refreshCurrentConversation()
    // 每 3 轮（约 9 秒）顺带刷新列表的预览与未读数
    if (++pollTicks % 3 === 0) {
      if (activeTab.value === 'sent') fetchSentConversations({ silent: true })
      else fetchMessages({ silent: true })
    }
  }, CHAT_POLL_MS)
}

function stopChatPolling() {
  if (chatTimer) { clearInterval(chatTimer); chatTimer = null }
}

function formatTime(time) {
  if (!time) return ''
  const date = new Date(time)
  const diff = new Date() - date
  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return Math.floor(diff / 60000) + '分钟前'
  if (diff < 86400000) return Math.floor(diff / 3600000) + '小时前'
  if (diff < 604800000) return Math.floor(diff / 86400000) + '天前'
  return date.toLocaleDateString('zh-CN')
}
function formatMessageTime(time) {
  if (!time) return ''
  return new Date(time).toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
}

watch(activeTab, async (tab) => {
  selectedUserId.value = null
  currentMessages.value = []
  draftConversation.value = null
  if (tab === 'sent') await fetchSentConversations()
  else await fetchMessages({ silent: true })
})

// Esc 关闭图片预览
function onWindowKeydown(e) {
  if (e.key === 'Escape') lightboxUrl.value = ''
}

onMounted(async () => {
  window.addEventListener('keydown', onWindowKeydown)
  await fetchMessages()
  const sellerId = route.query.sellerId || route.query.userId
  if (sellerId) {
    // 关键修复：openChatWith 会处理「列表里没有该会话」的情况
    await openChatWith(sellerId, { username: route.query.seller })
  } else {
    await loadContextProduct()
  }
  startChatPolling()
})

onBeforeUnmount(() => {
  window.removeEventListener('keydown', onWindowKeydown)
  stopChatPolling()
})
</script>

<style scoped>
.brand-mark svg { width: 17px; height: 17px; }
.icon-btn svg { width: 20px; height: 20px; }
.avatar img { width: 100%; height: 100%; object-fit: cover; }

.messages-page {
  display: flex;
  flex-direction: column;
  /* 撑满导航以下的视口，聊天面板才能拿到确定高度 */
  height: calc(100vh - var(--header-h) - var(--safe-top, 0px) - var(--safe-bottom, 0px));
  min-height: 420px;
  overflow: hidden;
}
.msg-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
  max-width: var(--container);
  width: 100%;
  margin: 0 auto;
  padding: var(--space-6);
}
/*
 * 注意：这里不能再写 height: 100%。
 * .msg-main 是 flex 项（flex: 1），其高度由 flex 布局撑开而非显式 height，
 * 子元素百分比高度会解析失败 → 面板塌成内容高度（约 110px），输入框被裁掉。
 * 改用 flex: 1 + min-height: 0，让面板正确占满。
 */
.msg-shell {
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: 320px 1fr;
  background: var(--surface);
  -webkit-backdrop-filter: blur(18px) saturate(160%);
  backdrop-filter: blur(18px) saturate(160%);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  overflow: hidden;
}

.sidebar { border-right: 1px solid var(--border); display: flex; flex-direction: column; min-height: 0; }
.sidebar-head { display: flex; align-items: center; justify-content: space-between; padding: var(--space-4) var(--space-5); border-bottom: 1px solid var(--border); }
.sidebar-title { font-size: var(--text-lg); font-weight: var(--weight-semibold); }
.tabs { display: flex; gap: var(--space-2); padding: var(--space-3) var(--space-5); border-bottom: 1px solid var(--border); }
.tab { flex: 1; padding: 8px; font-size: var(--text-sm); color: var(--text-2); background: var(--surface-2); border: 1px solid var(--border); border-radius: var(--radius-sm); }
.tab:hover { color: var(--text); }
.tab.active { background: var(--accent); border-color: var(--accent); color: #fff; }

.conv-list { flex: 1; overflow-y: auto; padding: var(--space-2); }
.conv { display: flex; align-items: center; gap: var(--space-3); width: 100%; padding: var(--space-3); text-align: left; background: transparent; border: none; border-radius: var(--radius); transition: background var(--dur-fast) var(--ease); }
.conv:hover { background: var(--surface-2); }
.conv.active { background: var(--accent-soft); }
.conv-avatar { position: relative; width: 44px; height: 44px; border-radius: var(--radius-full); overflow: hidden; flex-shrink: 0; border: 1px solid var(--border); }
.conv-avatar img { width: 100%; height: 100%; object-fit: cover; }
.msg-badge { position: absolute; top: -2px; right: -2px; min-width: 16px; height: 16px; padding: 0 4px; background: var(--danger); color: #fff; font-size: 10px; border-radius: var(--radius-full); display: flex; align-items: center; justify-content: center; }
.conv-info { flex: 1; min-width: 0; }
.conv-head { display: flex; justify-content: space-between; align-items: baseline; gap: var(--space-2); }
.conv-name { font-size: var(--text-base); font-weight: var(--weight-medium); color: var(--text); }
.conv-time { font-size: var(--text-xs); color: var(--text-3); flex-shrink: 0; }
.conv-preview { margin-top: 2px; font-size: var(--text-sm); color: var(--text-2); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

.chat { display: flex; flex-direction: column; min-height: 0; }
.chat-empty { flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: var(--space-2); color: var(--text-3); }
.chat-empty svg { width: 44px; height: 44px; }
.chat-empty h3 { font-size: var(--text-lg); color: var(--text); }
.chat-empty p { font-size: var(--text-sm); }
.chat-head { display: flex; align-items: center; justify-content: space-between; padding: var(--space-4) var(--space-5); border-bottom: 1px solid var(--border); }
/* 返回会话列表：仅窄屏显示 */
.chat-back { display: none; width: 32px; height: 32px; margin-right: var(--space-2); font-size: 18px; line-height: 1; color: var(--text-2); background: var(--surface-2); border: 1px solid var(--border); border-radius: var(--radius-sm); cursor: pointer; flex-shrink: 0; }
.chat-back:hover { color: var(--text); background: var(--surface-3); }
@media (max-width: 860px) { .chat-back { display: block; } }
.chat-user { display: flex; align-items: center; gap: var(--space-3); }
.chat-avatar { width: 40px; height: 40px; border-radius: var(--radius-full); overflow: hidden; border: 1px solid var(--border); }
.chat-avatar img { width: 100%; height: 100%; object-fit: cover; }
.chat-user-info { display: flex; flex-direction: column; gap: 2px; }
.chat-username { font-size: var(--text-base); font-weight: var(--weight-medium); }
.chat-status { font-size: var(--text-xs); color: var(--success); }

.chat-body { flex: 1; overflow-y: auto; padding: var(--space-5); display: flex; flex-direction: column; gap: var(--space-4); background: var(--surface-2); }
.message { display: flex; gap: var(--space-3); max-width: 72%; }
.message.sent { align-self: flex-end; flex-direction: row-reverse; }
.message.received { align-self: flex-start; }
.message-avatar { width: 32px; height: 32px; border-radius: var(--radius-full); overflow: hidden; flex-shrink: 0; border: 1px solid var(--border); }
.message-avatar img { width: 100%; height: 100%; object-fit: cover; }
.message-content { display: flex; flex-direction: column; gap: 4px; }
.message.sent .message-content { align-items: flex-end; }
.message-bubble { padding: 10px 14px; background: var(--surface); border: 1px solid var(--border); border-radius: var(--radius-lg); border-top-left-radius: 4px; }
.message.sent .message-bubble { background: #2563eb; border-color: #2563eb; border-radius: var(--radius-lg); border-top-right-radius: 4px; }
.message-bubble p { font-size: var(--text-base); color: var(--text); line-height: var(--leading-normal); }
.message.sent .message-bubble p { color: #fff; }
.message-time { font-size: var(--text-xs); color: var(--text-3); padding: 0 4px; }

.chat-input { display: flex; gap: var(--space-3); padding: var(--space-4) var(--space-5); border-top: 1px solid var(--border); align-items: center; }
.chat-input input { flex: 1; padding: 10px 14px; font-size: var(--text-base); color: var(--text); background: var(--surface); border: 1px solid var(--border-strong); border-radius: var(--radius); outline: none; }
.chat-input input:focus { border-color: var(--accent); box-shadow: 0 0 0 3px var(--accent-soft); }

/* ---- 图片消息 ---- */
.chat-image-input { position: absolute; width: 1px; height: 1px; overflow: hidden; clip-path: inset(50%); white-space: nowrap; }
.chat-image-btn { display: flex; align-items: center; justify-content: center; width: 40px; height: 40px; flex-shrink: 0; color: var(--text-2); background: var(--surface); border: 1px solid var(--border-strong); border-radius: var(--radius); cursor: pointer; transition: color var(--dur-fast) var(--ease), border-color var(--dur-fast) var(--ease); }
.chat-image-btn svg { width: 20px; height: 20px; }
.chat-image-btn:hover:not(:disabled) { color: var(--accent); border-color: var(--accent); }
.chat-image-btn:disabled { opacity: 0.5; cursor: not-allowed; }

.msg-image { padding: 6px; }
.msg-image-thumb { display: block; max-width: 220px; max-height: 220px; width: auto; height: auto; border-radius: calc(var(--radius-lg) - 4px); cursor: zoom-in; }
.message.sent .msg-image { background: var(--surface); border-color: var(--border); }
.msg-image p { margin-top: 4px; font-size: var(--text-base); color: var(--text); line-height: var(--leading-normal); padding: 0 8px 2px; }

/* ---- 图片放大预览 ---- */
.lightbox-mask { position: fixed; inset: 0; z-index: 1200; display: flex; align-items: center; justify-content: center; padding: var(--space-6); background: rgba(0, 0, 0, 0.82); cursor: zoom-out; }
.lightbox-mask img { max-width: 92vw; max-height: 90vh; border-radius: var(--radius-lg); box-shadow: var(--shadow-lg); }

@media (max-width: 860px) {
  .msg-shell { grid-template-columns: 1fr; }
  /*
   * 此前这里直接 display: none 掉会话列表，窄窗口下既看不到联系人、
   * 也没有任何入口选择会话，聊天页等于不可用。
   * 改为「列表 / 聊天」二选一：未选会话看列表，选中后看聊天并能返回。
   */
  .sidebar { display: flex; }
  .chat { display: none; }
  .msg-shell.chat-open .sidebar { display: none; }
  .msg-shell.chat-open .chat { display: flex; }
}

/* ============ 商品上下文卡片 ============ */
.ctx-card {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  padding: var(--space-3) var(--space-5);
  background: var(--accent-soft);
  border-bottom: 1px solid var(--border);
}
.ctx-thumb {
  width: 44px;
  height: 44px;
  flex-shrink: 0;
  border-radius: var(--radius-sm);
  overflow: hidden;
  background: var(--surface);
  border: 1px solid var(--border);
  display: grid;
  place-items: center;
}
.ctx-thumb img { width: 100%; height: 100%; object-fit: cover; }
.ctx-thumb-fallback { color: var(--text-3); }
.ctx-thumb-fallback svg { width: 20px; height: 20px; }
.ctx-info { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 2px; }
.ctx-label { font-size: var(--text-xs); color: var(--accent); }
.ctx-title {
  font-size: var(--text-sm);
  color: var(--text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.ctx-price { font-family: var(--font-mono); font-size: var(--text-base); font-weight: var(--weight-semibold); color: var(--text); flex-shrink: 0; }

.head-close {
  width: 28px;
  height: 28px;
  font-size: 20px;
  line-height: 1;
  color: var(--text-3);
  background: transparent;
  border: 0;
  border-radius: var(--radius-sm);
  cursor: pointer;
}
.head-close:hover { color: var(--text); background: var(--surface-3); }

/* 新会话引导 */
.chat-intro {
  margin: auto;
  text-align: center;
  color: var(--text-3);
  padding: var(--space-8);
}
.chat-intro-title { font-size: var(--text-base); color: var(--text-2); margin-bottom: var(--space-2); }
.chat-intro-desc { font-size: var(--text-sm); }</style>
