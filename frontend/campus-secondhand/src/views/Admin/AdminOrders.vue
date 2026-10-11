<template>
  <div class="admin-orders">
    <h1>订单管理</h1>
    
    <div class="order-search">
      <input 
        type="text" 
        v-model="searchQuery" 
        placeholder="搜索订单..."
        class="search-input"
      />
      <select v-model="statusFilter" class="filter-select" @change="fetchOrders">
        <option value="">全部状态</option>
        <option :value="0">待支付</option>
        <option :value="1">待发货</option>
        <option :value="2">待收货</option>
        <option :value="3">已完成</option>
        <option :value="4">已取消</option>
      </select>
      <button @click="fetchOrders" class="search-btn">搜索</button>
    </div>
    
    <div class="order-list">
      <div class="order-item" v-for="order in orders" :key="order.id">
        <div class="order-header">
          <h3>订单 #{{ order.id }}</h3>
          <span class="order-status" :class="order.status">{{ orderStatusMap[order.status] }}</span>
        </div>
        <div class="order-info">
          <!--
            字段名对齐后端 toAdminOrderViews()：裸 Order 实体的 buyer/seller/product
            是 @TableField(exist=false)，不会序列化，此前读 order.user?.username /
            order.product?.name / order.createdAt 全是 undefined，
            每张卡片都显示"未知用户 / 未知商品 / ¥0 / 空白时间"。
          -->
          <p><strong>买家:</strong> {{ order.buyerName || '未知用户' }}</p>
          <p><strong>卖家:</strong> {{ order.sellerName || '未知卖家' }}</p>
          <p><strong>商品:</strong> {{ order.productTitle || '未知商品' }}</p>
          <p><strong>订单号:</strong> {{ order.orderNo }}</p>
          <p><strong>价格:</strong> ¥{{ order.price ?? 0 }}</p>
          <p><strong>创建时间:</strong> {{ formatDate(order.createdTime) }}</p>
        </div>
        <div class="order-actions">
          <!--
            按钮按"这个状态是否真的需要管理员介入"显示：
            待支付订单不能直接完成（后端会 400，那等于凭空造 GMV），
            "标记为已完成"只在待收货(2)时出现。
          -->
          <button 
            v-if="order.status === 2" 
            @click="updateOrderStatus(order.id, 3)"
            class="btn-primary"
          >
            标记为已完成
          </button>
          <button 
            v-if="order.status === 0 || order.status === 1 || order.status === 2" 
            @click="updateOrderStatus(order.id, 4)"
            class="btn-secondary"
          >
            取消订单
          </button>
          <button
            v-if="order.status === 3 || order.status === 4"
            @click="deleteOrder(order.id)"
            class="btn-danger"
          >
            删除订单
          </button>
          <span v-else class="order-hint">进行中的订单不可删除</span>
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
    
    <div v-if="orders.length === 0" class="no-orders">
      <p>暂无订单</p>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import admin from '../../api/admin'

const orders = ref([])
const searchQuery = ref('')
const statusFilter = ref('')
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)

// 后端 orders.status 为整数 0-4，此前误用字符串键导致状态标签空白、
// 操作按钮永不显示、状态更新必然 400（Integer.parseInt('COMPLETED') 抛异常）
const orderStatusMap = {
  0: '待支付',
  1: '待发货',
  2: '待收货',
  3: '已完成',
  4: '已取消'
}

const totalPages = computed(() => {
  return Math.ceil(total.value / pageSize.value)
})

const fetchOrders = async () => {
  try {
    const response = await admin.get('/orders', {
      params: {
        search: searchQuery.value,
        // 后端签名是 Integer status，空串会导致 400 类型转换失败
        ...(statusFilter.value !== '' && { status: Number(statusFilter.value) }),
        page: page.value,
        size: pageSize.value
      }
    })
    if (response.data && response.data.data) {
      orders.value = response.data.data.list
      total.value = response.data.data.total
    }
  } catch (error) {
    console.error('获取订单失败:', error)
  }
}

const updateOrderStatus = async (orderId, status) => {
  try {
    // 业务失败（HTTP 200 + code!=200）由 src/api/admin.js 的响应拦截器
    // 统一提示并 reject，这里只处理成功路径，不会再"看起来改成功了"
    await admin.put(`/orders/${orderId}/status`, {
      status: status
    })
    // 重新获取订单列表
    fetchOrders()
  } catch (error) {
    console.error('更新订单状态失败:', error)
  }
}

const deleteOrder = async (orderId) => {
  if (confirm('确定要删除这个订单吗？')) {
    try {
      await admin.delete(`/orders/${orderId}`)
      // 重新获取订单列表
      fetchOrders()
    } catch (error) {
      console.error('删除订单失败:', error)
    }
  }
}

const changePage = (newPage) => {
  if (newPage >= 1 && newPage <= totalPages.value) {
    page.value = newPage
    fetchOrders()
  }
}

const formatDate = (dateString) => {
  if (!dateString) return ''
  const date = new Date(dateString)
  return date.toLocaleString()
}

onMounted(() => {
  fetchOrders()
})
</script>

<style scoped>
.admin-orders {
  max-width: 1200px;
  margin: 0 auto;
  padding: 20px;
}

.admin-orders h1 {
  font-size: 2rem;
  color: #333;
  margin-bottom: 30px;
}

.order-search {
  margin-bottom: 30px;
  display: flex;
  gap: 10px;
  align-items: center;
}

.search-input {
  flex: 1;
  padding: 10px;
  border: 1px solid #ddd;
  border-radius: 5px;
  font-size: 16px;
}

.filter-select {
  padding: 10px;
  border: 1px solid #ddd;
  border-radius: 5px;
  font-size: 16px;
  background-color: white;
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

.order-list {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.order-item {
  background-color: #f9f9f9;
  border-radius: 10px;
  padding: 20px;
  box-shadow: 0 2px 5px rgba(0, 0, 0, 0.1);
}

.order-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 15px;
}

.order-header h3 {
  font-size: 1.2rem;
  color: #333;
  margin: 0;
}

.order-status {
  padding: 5px 15px;
  border-radius: 20px;
  font-size: 14px;
  font-weight: bold;
}

.order-status.PENDING {
  background-color: #ffeb3b;
  color: #333;
}

.order-status.COMPLETED {
  background-color: #4CAF50;
  color: white;
}

.order-status.CANCELLED {
  background-color: #f44336;
  color: white;
}

.order-info {
  margin-bottom: 15px;
}

.order-info p {
  margin: 5px 0;
  color: #666;
}

.order-actions {
  display: flex;
  gap: 10px;
}

.btn-primary {
  padding: 8px 16px;
  background-color: #4CAF50;
  color: white;
  border: none;
  border-radius: 5px;
  cursor: pointer;
  font-size: 14px;
}

.btn-primary:hover {
  background-color: #45a049;
}

.btn-secondary {
  padding: 8px 16px;
  background-color: #2196F3;
  color: white;
  border: none;
  border-radius: 5px;
  cursor: pointer;
  font-size: 14px;
}

.btn-secondary:hover {
  background-color: #0b7dda;
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

.no-orders {
  text-align: center;
  padding: 50px;
  color: #999;
  font-size: 1.2rem;
}
</style>

<!-- ===== 简约浅色主题覆盖 ===== -->
<style scoped>
.admin-orders { max-width: var(--container); padding: var(--space-6); }
.admin-orders h1 { font-size: var(--text-2xl); font-weight: var(--weight-semibold); letter-spacing: -0.02em; color: var(--text); margin-bottom: var(--space-5); }
.order-search { display: flex; gap: var(--space-3); margin-bottom: var(--space-5); }
.search-input, .filter-select { flex: 1; padding: 10px 14px; border: 1px solid var(--border-strong); border-radius: var(--radius); font-size: var(--text-base); color: var(--text); background: var(--surface); }
.filter-select { flex: 0 0 auto; }
.search-input:focus, .filter-select:focus { outline: none; border-color: var(--accent); box-shadow: 0 0 0 3px var(--accent-soft); }
.search-btn { padding: 10px 20px; background: var(--accent); color: #fff; border: none; border-radius: var(--radius); font-size: var(--text-base); font-weight: var(--weight-medium); }
.search-btn:hover { background: var(--accent-hover); }

.order-list { display: flex; flex-direction: column; gap: var(--space-4); }
.order-item { background: var(--surface); border: 1px solid var(--border); border-radius: var(--radius-lg); padding: var(--space-5); box-shadow: none; }
.order-header h3 { font-size: var(--text-base); font-weight: var(--weight-semibold); color: var(--text); }
.order-status { padding: 4px 14px; border-radius: var(--radius-full); font-size: var(--text-xs); font-weight: var(--weight-medium); }
.order-status.PENDING { background: var(--warning-soft); color: var(--warning); }
.order-status.COMPLETED { background: var(--success-soft); color: var(--success); }
.order-status.CANCELLED { background: var(--danger-soft); color: var(--danger); }
.order-info p { margin: 4px 0; color: var(--text-2); font-size: var(--text-sm); }
.order-actions { display: flex; gap: var(--space-2); }
.btn-primary { padding: 7px 16px; background: var(--accent); color: #fff; border: none; border-radius: var(--radius-sm); font-size: var(--text-sm); font-weight: var(--weight-medium); }
.btn-primary:hover { background: var(--accent-hover); }
.btn-secondary { padding: 7px 16px; background: var(--surface); color: var(--text); border: 1px solid var(--border-strong); border-radius: var(--radius-sm); font-size: var(--text-sm); }
.btn-secondary:hover { background: var(--surface-2); }
.btn-danger { padding: 7px 16px; background: var(--danger-soft); color: var(--danger); border: none; border-radius: var(--radius-sm); font-size: var(--text-sm); }
.btn-danger:hover { background: var(--danger); color: #fff; }

.pagination { display: flex; align-items: center; justify-content: center; gap: var(--space-2); margin-top: var(--space-5); padding-top: var(--space-5); border-top: 1px solid var(--border); }
.pagination-button { padding: 6px 14px; background: var(--surface); color: var(--text); border: 1px solid var(--border-strong); border-radius: var(--radius-sm); font-size: var(--text-sm); }
.pagination-button:hover:not(:disabled) { background: var(--surface-2); transform: none; }
.pagination-button:disabled { opacity: 0.5; }
.pagination-info { font-size: var(--text-sm); color: var(--text-2); }
.no-orders { text-align: center; padding: var(--space-12); color: var(--text-3); font-size: var(--text-base); }
</style>