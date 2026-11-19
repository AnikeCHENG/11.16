<template>
  <div class="admin-messages">
    <AdminPageHeader title="消息管理" />
    
    <div class="message-search">
      <input 
        type="text" 
        v-model="searchQuery" 
        placeholder="搜索消息..."
        class="search-input"
      />
      <button @click="fetchMessages" class="search-btn">搜索</button>
    </div>
    
    <div v-if="error" class="error-container" role="alert">
      <p>{{ error }}</p>
      <button class="pagination-button" @click="fetchMessages">重试</button>
    </div>

    <div class="message-list">
      <div class="message-item" v-for="message in messages" :key="message.id">
        <div class="message-header">
          <h3>消息 #{{ message.id }}</h3>
          <span class="message-date">{{ formatDate(message.createdTime) }}</span>
        </div>
        <div class="message-info">
          <p class="message-sender"><strong>发送者:</strong> {{ userLabel(message.sender, message.senderId) }}</p>
          <p class="message-receiver"><strong>接收者:</strong> {{ userLabel(message.receiver, message.receiverId) }}</p>
          <p><strong>内容:</strong> <template v-if="message.msgType === 1">[图片]<template v-if="message.caption"> {{ message.caption }}</template></template><template v-else>{{ message.content }}</template></p>
        </div>
        <div class="message-actions">
          <button @click="deleteMessage(message.id)" class="btn-danger">
            删除消息
          </button>
        </div>
      </div>
    </div>
    
    <!-- 分页 -->
    <div v-if="total > 0" class="pagination">
      <button 
        class="pagination-button" 
        @click="changePage(1)" 
        :disabled="page === 1"
      >
        首页
      </button>
      <button 
        class="pagination-button" 
        @click="changePage(page - 1)" 
        :disabled="page === 1"
      >
        上一页
      </button>
      <span class="pagination-info">
        第 {{ page }} 页，共 {{ totalPages }} 页，总计 {{ total }} 条
      </span>
      <button 
        class="pagination-button" 
        @click="changePage(page + 1)" 
        :disabled="page === totalPages"
      >
        下一页
      </button>
      <button 
        class="pagination-button" 
        @click="changePage(totalPages)" 
        :disabled="page === totalPages"
      >
        末页
      </button>
    </div>
    
    <div v-if="!error && !loading && messages.length === 0" class="no-messages">
      <p>暂无消息</p>
    </div>
  </div>
</template>

<script setup>
import AdminPageHeader from '@/components/AdminPageHeader.vue'
import { ref, computed, onMounted } from 'vue'
import admin from '../../api/admin'

const messages = ref([])
const searchQuery = ref('')
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)
const loading = ref(false)
const error = ref('')

const totalPages = computed(() => {
  return Math.ceil(total.value / pageSize.value)
})

const fetchMessages = async () => {
  loading.value = true
  error.value = ''
  try {
    const response = await admin.get('/messages', {
      params: {
        search: searchQuery.value,
        page: page.value,
        size: pageSize.value
      }
    })
    if (response.data && response.data.data) {
      messages.value = response.data.data.list
      total.value = response.data.data.total
    }
  } catch (err) {
    console.error('获取消息失败:', err)
    error.value = err?.response?.data?.message || err?.message || '获取消息失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

const deleteMessage = async (messageId) => {
  if (confirm('确定要删除这个消息吗？')) {
    try {
      await admin.delete(`/messages/${messageId}`)
      // 重新获取消息列表
      fetchMessages()
    } catch (error) {
      console.error('删除消息失败:', error)
    }
  }
}

const changePage = (newPage) => {
  if (newPage >= 1 && newPage <= totalPages.value) {
    page.value = newPage
    fetchMessages()
  }
}

/**
 * 后端 /api/admin/messages 返回的 sender/receiver 目前恒为 null，只有 senderId/receiverId。
 * 有用户名用用户名，否则退化为「用户 #id」，不再一律显示「未知用户」。
 */
const userLabel = (user, id) => {
  if (user?.username) return user.username
  if (id !== null && id !== undefined) return `用户 #${id}`
  return '未知用户'
}

const formatDate = (dateString) => {
  if (!dateString) return ''
  const date = new Date(dateString)
  return date.toLocaleString()
}

onMounted(() => {
  fetchMessages()
})
</script>

<style scoped>
.admin-messages {
  max-width: 1200px;
  margin: 0 auto;
  padding: 20px;
}

.message-search {
  margin-bottom: 30px;
  display: flex;
  gap: 10px;
}

.search-input {
  flex: 1;
  padding: 10px;
  border: 1px solid #ddd;
  border-radius: 5px;
  font-size: 16px;
}

.search-btn {
  padding: 10px 20px;
  background-color: #4CAF50;
  color: white;
  border: none;
  border-radius: 5px;
  cursor: pointer;
  font-size: 16px;
}

.search-btn:hover {
  background-color: #45a049;
}

.message-list {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.message-item {
  background-color: #f9f9f9;
  border-radius: 10px;
  padding: 20px;
  box-shadow: 0 2px 5px rgba(0, 0, 0, 0.1);
}

.message-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 15px;
}

.message-header h3 {
  font-size: 1.2rem;
  color: #333;
  margin: 0;
}

.message-date {
  font-size: 14px;
  color: #666;
}

.message-info {
  margin-bottom: 15px;
}

.message-info p {
  margin: 5px 0;
  color: #666;
}

.message-actions {
  display: flex;
  gap: 10px;
}

.btn-danger {
  padding: 8px 16px;
  background-color: #f44336;
  color: white;
  border: none;
  border-radius: 5px;
  cursor: pointer;
  font-size: 14px;
}

.btn-danger:hover {
  background-color: #da190b;
}

/* 分页样式 */
.pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  margin-top: 30px;
  padding-top: 20px;
  border-top: 1px solid #eee;
}

.pagination-button {
  padding: 8px 16px;
  background-color: #f5f5f5;
  color: #333;
  border: none;
  border-radius: 5px;
  cursor: pointer;
  font-size: 14px;
  transition: all 0.3s ease;
}

.pagination-button:hover:not(:disabled) {
  background-color: #e0e0e0;
  transform: translateY(-1px);
}

.pagination-button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.pagination-info {
  font-size: 14px;
  color: #666;
  font-weight: 500;
}

.no-messages {
  text-align: center;
  padding: 50px;
  color: #999;
  font-size: 1.2rem;
}
</style>

<!-- ===== 简约浅色主题覆盖 ===== -->
<style scoped>
.admin-messages { max-width: var(--container); padding: var(--space-6); }
.message-search { display: flex; gap: var(--space-3); margin-bottom: var(--space-5); }
.search-input { flex: 1; padding: 10px 14px; border: 1px solid var(--border-strong); border-radius: var(--radius); font-size: var(--text-base); color: var(--text); background: var(--surface); }
.search-input:focus { outline: none; border-color: var(--accent); box-shadow: 0 0 0 3px var(--accent-soft); }
.search-btn { padding: 10px 20px; background: var(--accent); color: #fff; border: none; border-radius: var(--radius); font-size: var(--text-base); font-weight: var(--weight-medium); }
.search-btn:hover { background: var(--accent-hover); }
.message-list { display: flex; flex-direction: column; gap: var(--space-4); }
.message-item { background: var(--surface); border: 1px solid var(--border); border-radius: var(--radius-lg); padding: var(--space-5); box-shadow: none; }
.message-header h3 { font-size: var(--text-base); font-weight: var(--weight-semibold); color: var(--text); }
.message-date { font-size: var(--text-sm); color: var(--text-2); }
.message-info p { margin: 4px 0; color: var(--text-2); font-size: var(--text-sm); }
.btn-danger { padding: 7px 16px; background: var(--danger-soft); color: var(--danger); border: none; border-radius: var(--radius-sm); font-size: var(--text-sm); }
.btn-danger:hover { background: var(--danger); color: #fff; }
.pagination { display: flex; align-items: center; justify-content: center; gap: var(--space-2); margin-top: var(--space-5); padding-top: var(--space-5); border-top: 1px solid var(--border); }
.pagination-button { padding: 6px 14px; background: var(--surface); color: var(--text); border: 1px solid var(--border-strong); border-radius: var(--radius-sm); font-size: var(--text-sm); }
.pagination-button:hover:not(:disabled) { background: var(--surface-2); transform: none; }
.pagination-button:disabled { opacity: 0.5; }
.pagination-info { font-size: var(--text-sm); color: var(--text-2); }
.no-messages { text-align: center; padding: var(--space-12); color: var(--text-3); font-size: var(--text-base); }
.message-info p { overflow-wrap: anywhere; }
.error-container { display: flex; flex-direction: column; align-items: center; gap: var(--space-3); padding: var(--space-8) 0; color: var(--danger); }

@media (max-width: 640px) {
  .admin-messages { padding: var(--space-4); }
  .message-search { flex-direction: column; align-items: stretch; }
  .search-input, .search-btn { width: 100%; box-sizing: border-box; }
  .pagination { flex-wrap: wrap; }
  .pagination-info { flex-basis: 100%; text-align: center; order: -1; }
}
</style>
