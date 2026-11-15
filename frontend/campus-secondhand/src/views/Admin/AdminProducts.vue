<template>
  <div class="admin-products">
    <div class="admin-header">
      <h1>商品管理</h1>
      <router-link to="/admin" class="back-link">返回控制面板</router-link>
    </div>
    
    <!-- 搜索和筛选 -->
    <div class="filter-section">
      <div class="filter-form">
        <input 
          v-model="searchQuery" 
          type="text" 
          placeholder="搜索商品标题或描述" 
          class="search-input"
          @keyup.enter="handleSearch"
        />
        <select v-model="categoryFilter" class="filter-select" @change="handleFilterChange">
          <!--
            value 必须与库里存的一致：products.category 存的是 books / electronics 这类
            code（slug），中文只是分类表里的展示名。选项来自后台分类管理维护的
            /api/admin/categories，新增分类后这里自动出现，不用再改代码。
          -->
          <option value="">所有分类</option>
          <option v-for="cat in categoryOptions" :key="cat.code" :value="cat.code">
            {{ cat.name }}
          </option>
        </select>
        <select v-model="statusFilter" class="filter-select" @change="handleFilterChange">
          <!-- 语义以 ProductStatus 枚举为准：0=已下架 1=在售 2=已售出 -->
          <option value="">所有状态</option>
          <option :value="1">在售</option>
          <option :value="2">已售出</option>
          <option :value="0">已下架</option>
        </select>
        <select v-model="reviewFilter" class="filter-select" @change="handleFilterChange">
          <!-- 审核是独立维度：0=待审核 1=已通过 2=未通过 -->
          <option value="">全部审核状态</option>
          <option :value="0">待审核</option>
          <option :value="2">未通过</option>
          <option :value="1">已通过</option>
        </select>
        <button class="search-button" @click="handleSearch">搜索</button>
        <button
          class="batch-approve-button"
          :disabled="selectedReviewableIds.length === 0 || batchSubmitting"
          @click="batchApprove"
        >
          批量通过{{ selectedReviewableIds.length ? `（${selectedReviewableIds.length}）` : '' }}
        </button>
      </div>
    </div>
    
    <!-- 商品列表 -->
    <div class="products-table-container">
      <div v-if="loading" class="loading-container">
        <div class="loading-spinner"></div>
        <p>加载中...</p>
      </div>
      <div v-else-if="error" class="error-container">
        <p>{{ error }}</p>
        <button class="retry-button" @click="fetchProducts">重试</button>
      </div>
      <table v-else class="products-table">
        <thead>
          <tr>
            <th class="check-col">
              <input
                type="checkbox"
                :checked="allReviewableSelected"
                :disabled="reviewableOnPage.length === 0"
                title="全选本页待审核/未通过商品"
                @change="toggleSelectAll($event.target.checked)"
              />
            </th>
            <th>ID</th>
            <th>标题</th>
            <th>卖家</th>
            <th>价格</th>
            <th>分类</th>
            <th>审核</th>
            <th>状态</th>
            <th>创建时间</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-if="products.length === 0">
            <td colspan="10" class="empty-cell">没有找到匹配的商品</td>
          </tr>
          <tr v-for="product in products" :key="product.id">
            <td class="check-col">
              <input
                v-if="canReview(product)"
                type="checkbox"
                :value="product.id"
                :checked="selectedIds.includes(product.id)"
                @change="toggleSelect(product.id)"
              />
            </td>
            <td>{{ product.id }}</td>
            <td class="product-title">{{ product.title }}</td>
            <td>{{ product.userId }}</td>
            <td>¥{{ product.price }}</td>
            <td>{{ product.category || '未分类' }}</td>
            <td>
              <span class="review-badge" :class="getReviewClass(product.reviewStatus)">
                {{ getReviewText(product.reviewStatus) }}
              </span>
              <span
                v-if="product.reviewStatus === 2 && product.reviewReason"
                class="review-reason"
                :title="product.reviewReason"
              >
                {{ product.reviewReason }}
              </span>
            </td>
            <td>
              <span class="status-badge" :class="getStatusClass(product.status)">
                {{ getStatusText(product.status) }}
              </span>
            </td>
            <td>{{ formatDate(product.createdTime) }}</td>
            <td>
              <div class="action-buttons">
                <button 
                  class="action-button view-button"
                  @click="viewProductDetail(product)"
                  title="查看详情"
                >
                  详情
                </button>
                <template v-if="canReview(product)">
                  <button
                    class="action-button action-approve"
                    :disabled="reviewSubmittingId === product.id"
                    @click="approveProduct(product)"
                  >
                    通过
                  </button>
                  <button
                    class="action-button action-reject"
                    :disabled="reviewSubmittingId === product.id"
                    @click="openRejectDialog(product)"
                  >
                    拒绝
                  </button>
                </template>
                <button 
                  v-else
                  class="action-button" 
                  :class="getActionClass(product.status)"
                  @click="toggleProductStatus(product.id, product.status)"
                >
                  {{ getStatusAction(product.status) }}
                </button>
              </div>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
    
    <!-- 分页 -->
    <div v-if="!loading && !error && total > 0" class="pagination">
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
    
    <!-- 商品详情模态框 -->
    <div v-if="showDetailModal" class="modal-overlay" @click.self="closeDetailModal">
      <div class="modal-content">
        <div class="modal-header">
          <h2>商品详情</h2>
          <button class="close-button" @click="closeDetailModal">&times;</button>
        </div>
        <div class="modal-body">
          <div v-if="selectedProduct" class="product-detail">
            <div class="detail-row">
              <span class="detail-label">ID:</span>
              <span class="detail-value">{{ selectedProduct.id }}</span>
            </div>
            <div class="detail-row">
              <span class="detail-label">标题:</span>
              <span class="detail-value">{{ selectedProduct.title }}</span>
            </div>
            <div class="detail-row">
              <span class="detail-label">描述:</span>
              <span class="detail-value">{{ selectedProduct.description || '无' }}</span>
            </div>
            <div class="detail-row">
              <span class="detail-label">价格:</span>
              <span class="detail-value">¥{{ selectedProduct.price }}</span>
            </div>
            <div class="detail-row">
              <span class="detail-label">原价:</span>
              <span class="detail-value">¥{{ selectedProduct.originalPrice || '未设置' }}</span>
            </div>
            <div class="detail-row">
              <span class="detail-label">分类:</span>
              <span class="detail-value">{{ selectedProduct.category || '未分类' }}</span>
            </div>
            <div class="detail-row">
              <span class="detail-label">成色:</span>
              <span class="detail-value">{{ selectedProduct.condition || '未设置' }}</span>
            </div>
            <div class="detail-row">
              <span class="detail-label">卖家ID:</span>
              <span class="detail-value">{{ selectedProduct.userId }}</span>
            </div>
            <div class="detail-row">
              <span class="detail-label">状态:</span>
              <span class="detail-value">{{ getStatusText(selectedProduct.status) }}</span>
            </div>
            <div class="detail-row">
              <span class="detail-label">审核:</span>
              <span class="detail-value">{{ getReviewText(selectedProduct.reviewStatus) }}</span>
            </div>
            <div v-if="selectedProduct.reviewReason" class="detail-row">
              <span class="detail-label">拒绝理由:</span>
              <span class="detail-value">{{ selectedProduct.reviewReason }}</span>
            </div>
            <div v-if="selectedProduct.reviewedTime" class="detail-row">
              <span class="detail-label">审核时间:</span>
              <span class="detail-value">{{ formatDate(selectedProduct.reviewedTime) }}</span>
            </div>
            <div class="detail-row">
              <span class="detail-label">浏览次数:</span>
              <span class="detail-value">{{ selectedProduct.viewCount || 0 }}</span>
            </div>
            <div class="detail-row">
              <span class="detail-label">创建时间:</span>
              <span class="detail-value">{{ formatDate(selectedProduct.createdTime) }}</span>
            </div>
            <div class="detail-row">
              <span class="detail-label">更新时间:</span>
              <span class="detail-value">{{ formatDate(selectedProduct.updatedTime) }}</span>
            </div>
            <div class="detail-row">
              <span class="detail-label">售出时间:</span>
              <span class="detail-value">{{ formatDate(selectedProduct.soldTime) || '未售出' }}</span>
            </div>
          </div>
        </div>
        <div class="modal-footer">
          <button class="close-modal-button" @click="closeDetailModal">关闭</button>
        </div>
      </div>
    </div>

    <!-- 拒绝审核：必须填写理由，卖家据此修改后重新提交 -->
    <div v-if="rejectTarget" class="modal-overlay" @click.self="closeRejectDialog">
      <div class="modal-content reject-modal">
        <div class="modal-header">
          <h2>拒绝审核</h2>
          <button class="close-button" @click="closeRejectDialog">&times;</button>
        </div>
        <div class="modal-body">
          <p class="reject-target">商品：{{ rejectTarget.title }}（ID {{ rejectTarget.id }}）</p>
          <label class="reject-label" for="reject-reason">拒绝理由（必填，卖家可见）</label>
          <textarea
            id="reject-reason"
            v-model="rejectReason"
            class="reject-textarea"
            rows="4"
            maxlength="255"
            placeholder="例如：商品图片与描述不符，请补充实拍图后重新提交"
          ></textarea>
          <div class="reject-counter">{{ rejectReason.length }} / 255</div>
        </div>
        <div class="modal-footer">
          <button class="cancel-modal-button" @click="closeRejectDialog">取消</button>
          <button
            class="confirm-reject-button"
            :disabled="!rejectReason.trim() || rejectSubmitting"
            @click="submitReject"
          >
            {{ rejectSubmitting ? '提交中…' : '确认拒绝' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import admin from '../../api/admin'

const products = ref([])
const loading = ref(false)
const error = ref('')
const searchQuery = ref('')
const categoryFilter = ref('')
const statusFilter = ref('')
/** 审核状态筛选：'' 全部 / 0 待审核 / 1 已通过 / 2 未通过 */
const reviewFilter = ref('')
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)
const showDetailModal = ref(false)
const selectedProduct = ref(null)
/** 分类筛选项，来自后台分类管理（含停用项，便于排查历史商品） */
const categoryOptions = ref([])

/** 批量通过选中的商品 id（只允许勾选待审核/未通过的商品） */
const selectedIds = ref([])
const batchSubmitting = ref(false)
/** 单条审核进行中的商品 id，用于禁用按钮防重复点击 */
const reviewSubmittingId = ref(null)

/** 拒绝弹窗状态 */
const rejectTarget = ref(null)
const rejectReason = ref('')
const rejectSubmitting = ref(false)

const totalPages = computed(() => {
  return Math.ceil(total.value / pageSize.value)
})

/** 当前页可审核（待审核或未通过）的商品 */
const reviewableOnPage = computed(() => products.value.filter(canReview))

const allReviewableSelected = computed(() => {
  const list = reviewableOnPage.value
  return list.length > 0 && list.every(p => selectedIds.value.includes(p.id))
})

/** 实际会被提交的 id：过滤掉翻页后已不在当前页/已变为已通过的选择 */
const selectedReviewableIds = computed(() => {
  const reviewable = new Set(reviewableOnPage.value.map(p => p.id))
  return selectedIds.value.filter(id => reviewable.has(id))
})

/**
 * 该商品当前是否可审核。
 *
 * <p>只有待审核(0)与未通过(2)可审：已通过的商品重复审核没有意义，
 * 后端也会直接 400。前端隐藏按钮避免管理员点出一堆报错。</p>
 */
function canReview(product) {
  return product.reviewStatus === 0 || product.reviewStatus === 2
}

const fetchProducts = async () => {
  try {
    loading.value = true
    error.value = ''
    const params = {
      page: page.value,
      size: pageSize.value
    }
    const search = searchQuery.value.trim()
    if (search) params.search = search
    if (categoryFilter.value) params.category = categoryFilter.value
    if (statusFilter.value !== '') params.status = statusFilter.value
    if (reviewFilter.value !== '') params.reviewStatus = reviewFilter.value
    const response = await admin.get('/products', { params })
    if (response.data.code === 200) {
      products.value = response.data.data.list
      total.value = response.data.data.total
      // 翻页/筛选后旧的选择已经不在列表里，留着会让批量按钮显示错误的件数
      selectedIds.value = []
    } else {
      error.value = response.data.message || '获取商品列表失败'
    }
  } catch (err) {
    console.error('获取商品列表失败:', err)
    error.value = '网络错误，请检查网络连接'
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  page.value = 1
  fetchProducts()
}

/** 分类筛选项随分类管理变化，不写死在前端 */
const fetchCategoryOptions = async () => {
  try {
    const response = await admin.get('/categories')
    if (response.data.code === 200) {
      categoryOptions.value = (response.data.data || []).filter(c => c.code)
    }
  } catch (err) {
    console.error('获取分类选项失败:', err)
  }
}

const handleFilterChange = () => {
  page.value = 1
  fetchProducts()
}

const toggleProductStatus = async (productId, currentStatus) => {
  try {
    let newStatus
    // 商品状态 0=下架 1=在售 2=已售出
    switch (currentStatus) {
      case 0: // 下架 -> 上架
        newStatus = 1
        break
      case 1: // 在售 -> 下架
        newStatus = 0
        break
      case 2: // 已售出 -> 上架
        newStatus = 1
        break
      default:
        newStatus = 1
    }
    const response = await admin.put(`/products/${productId}/status`, newStatus)
    if (response.data.code === 200) {
      // 更新本地商品列表
      const product = products.value.find(p => p.id === productId)
      if (product) {
        product.status = newStatus
      }
      // 显示成功提示
      alert('状态更新成功')
    } else {
      alert('状态更新失败: ' + (response.data.message || '未知错误'))
    }
  } catch (err) {
    // 未通过审核的商品上架会被后端拦截（interceptor 已弹出原因），
    // 这里补一句操作指引，避免管理员反复尝试
    console.error('更新商品状态失败:', err)
    if (!err?.response) {
      ElMessage.warning('若商品尚未通过审核，请先在上方「审核」列点击「通过」')
    }
  }
}

/* ==================== 人工审核 ==================== */

function toggleSelect(productId) {
  const idx = selectedIds.value.indexOf(productId)
  if (idx === -1) {
    selectedIds.value.push(productId)
  } else {
    selectedIds.value.splice(idx, 1)
  }
}

function toggleSelectAll(checked) {
  selectedIds.value = checked ? reviewableOnPage.value.map(p => p.id) : []
}

/** 单条通过：审核通过后商品自动上架 */
async function approveProduct(product) {
  reviewSubmittingId.value = product.id
  try {
    const response = await admin.put(`/products/${product.id}/review`, { action: 'approve' })
    if (response.data.code === 200) {
      ElMessage.success(`「${product.title}」已通过审核并上架`)
      await fetchProducts()
    }
  } catch (err) {
    console.error('审核通过失败:', err)
  } finally {
    reviewSubmittingId.value = null
  }
}

function openRejectDialog(product) {
  rejectTarget.value = product
  rejectReason.value = ''
}

function closeRejectDialog() {
  rejectTarget.value = null
  rejectReason.value = ''
}

/** 拒绝：理由必填（后端同样校验），提交后商品保持下架 */
async function submitReject() {
  const reason = rejectReason.value.trim()
  if (!reason) {
    ElMessage.warning('请填写拒绝理由，卖家需要知道该改什么')
    return
  }
  rejectSubmitting.value = true
  try {
    const response = await admin.put(`/products/${rejectTarget.value.id}/review`, {
      action: 'reject',
      reason
    })
    if (response.data.code === 200) {
      ElMessage.success('已拒绝，卖家可修改后重新提交')
      closeRejectDialog()
      await fetchProducts()
    }
  } catch (err) {
    console.error('审核拒绝失败:', err)
  } finally {
    rejectSubmitting.value = false
  }
}

/** 批量通过：只处理待审核/未通过，已通过的会被后端跳过 */
async function batchApprove() {
  const ids = selectedReviewableIds.value
  if (ids.length === 0) {
    ElMessage.warning('请先勾选待审核或未通过的商品')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确定通过选中的 ${ids.length} 件商品吗？通过后将立即在商品大厅上架。`,
      '批量通过审核',
      { type: 'warning', confirmButtonText: '全部通过', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  batchSubmitting.value = true
  try {
    const response = await admin.put('/products/batch/review', { ids })
    if (response.data.code === 200) {
      const result = response.data.data || {}
      ElMessage.success(
        `已通过 ${result.approvedCount ?? 0} 件` +
        (result.skippedCount ? `，跳过 ${result.skippedCount} 件（已审核或不存在）` : '')
      )
      await fetchProducts()
    }
  } catch (err) {
    console.error('批量审核失败:', err)
  } finally {
    batchSubmitting.value = false
  }
}

const viewProductDetail = async (product) => {
  try {
    loading.value = true
    const response = await admin.get(`/products/${product.id}`)
    if (response.data.code === 200) {
      selectedProduct.value = response.data.data
      showDetailModal.value = true
    } else {
      alert('获取商品详情失败: ' + (response.data.message || '未知错误'))
    }
  } catch (err) {
    console.error('获取商品详情失败:', err)
    alert('网络错误，请检查网络连接')
  } finally {
    loading.value = false
  }
}

const closeDetailModal = () => {
  showDetailModal.value = false
  selectedProduct.value = null
}

const changePage = (newPage) => {
  if (newPage >= 1 && newPage <= totalPages.value) {
    page.value = newPage
    fetchProducts()
  }
}

const getStatusText = (status) => {
  switch (status) {
    case 0:
      return '已下架'
    case 1:
      return '在售'
    case 2:
      return '已售出'
    default:
      return '未知'
  }
}

const getStatusClass = (status) => {
  switch (status) {
    case 0:
      return 'status-offline'
    case 1:
      return 'status-available'
    case 2:
      return 'status-sold'
    default:
      return ''
  }
}

/** 审核状态文案：0=待审核 1=已通过 2=未通过（与 ProductReviewStatus 一致） */
const getReviewText = (reviewStatus) => {
  switch (reviewStatus) {
    case 0:
      return '待审核'
    case 1:
      return '已通过'
    case 2:
      return '未通过'
    default:
      // 历史数据未写 review_status 时后端默认 1，这里兜底成已通过
      return reviewStatus == null ? '已通过' : '未知'
  }
}

const getReviewClass = (reviewStatus) => {
  switch (reviewStatus) {
    case 0:
      return 'review-pending'
    case 1:
      return 'review-approved'
    case 2:
      return 'review-rejected'
    default:
      return reviewStatus == null ? 'review-approved' : ''
  }
}

const getStatusAction = (status) => {
  switch (status) {
    case 0:
      return '上架'
    case 1:
      return '下架'
    case 2:
      return '重新上架'
    default:
      return '操作'
  }
}

const getActionClass = (status) => {
  switch (status) {
    case 0:
      return 'action-up'
    case 1:
      return 'action-down'
    case 2:
      return 'action-up'
    default:
      return ''
  }
}

const formatDate = (dateString) => {
  if (!dateString) return ''
  const date = new Date(dateString)
  return date.toLocaleString()
}

onMounted(() => {
  fetchProducts()
  fetchCategoryOptions()
})
</script>

<style scoped>
.admin-products {
  max-width: 1400px;
  margin: 0 auto;
  padding: 24px;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
}

.admin-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 32px;
  padding-bottom: 16px;
  border-bottom: 1px solid #e0e0e0;
}

.admin-header h1 {
  font-size: 24px;
  font-weight: 600;
  color: #333;
  margin: 0;
}

.back-link {
  text-decoration: none;
  color: #333;
  padding: 10px 16px;
  background-color: #f5f5f5;
  border-radius: 8px;
  transition: all 0.3s ease;
  font-size: 14px;
  font-weight: 500;
}

.back-link:hover {
  background-color: #e0e0e0;
  transform: translateY(-1px);
}

/* 筛选部分 */
.filter-section {
  margin-bottom: 24px;
}

.filter-form {
  display: flex;
  gap: 12px;
  align-items: center;
  flex-wrap: wrap;
}

.search-input {
  flex: 1;
  min-width: 200px;
  padding: 10px 16px;
  border: 1px solid #e0e0e0;
  border-radius: 8px;
  font-size: 14px;
  transition: all 0.3s ease;
}

.search-input:focus {
  outline: none;
  border-color: #10b981;
  box-shadow: 0 0 0 3px rgba(16, 185, 129, 0.1);
}

.filter-select {
  padding: 10px 16px;
  border: 1px solid #e0e0e0;
  border-radius: 8px;
  font-size: 14px;
  background-color: white;
  transition: all 0.3s ease;
}

.filter-select:focus {
  outline: none;
  border-color: #10b981;
  box-shadow: 0 0 0 3px rgba(16, 185, 129, 0.1);
}

.search-button {
  padding: 10px 20px;
  background-color: #10b981;
  color: white;
  border: none;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.3s ease;
}

.search-button:hover {
  background-color: #059669;
  transform: translateY(-1px);
  box-shadow: 0 4px 12px rgba(16, 185, 129, 0.3);
}

/* 加载和错误状态 */
.loading-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 0;
}

.loading-spinner {
  width: 40px;
  height: 40px;
  border: 4px solid #f3f3f3;
  border-top: 4px solid #10b981;
  border-radius: 50%;
  animation: spin 1s linear infinite;
  margin-bottom: 16px;
}

@keyframes spin {
  0% { transform: rotate(0deg); }
  100% { transform: rotate(360deg); }
}

.error-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 0;
  color: #ef4444;
}

.retry-button {
  margin-top: 16px;
  padding: 8px 16px;
  background-color: #f5f5f5;
  color: #333;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.3s ease;
}

.retry-button:hover {
  background-color: #e0e0e0;
}

/* 商品表格 */
.products-table-container {
  overflow-x: auto;
  margin-bottom: 24px;
  border-radius: 12px;
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.1);
}

.products-table {
  width: 100%;
  border-collapse: collapse;
  background-color: white;
}

.products-table th,
.products-table td {
  padding: 12px 16px;
  text-align: left;
  border-bottom: 1px solid #f0f0f0;
}

.products-table th {
  background-color: #f9fafb;
  font-weight: 600;
  color: #333;
  font-size: 14px;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.products-table tr:hover {
  background-color: #f9f9f9;
}

.product-title {
  max-width: 200px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 状态标签 */
.status-badge {
  padding: 4px 12px;
  border-radius: 16px;
  font-size: 12px;
  font-weight: 500;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.status-available {
  background-color: rgba(16, 185, 129, 0.1);
  color: #059669;
}

.status-sold {
  background-color: rgba(59, 130, 246, 0.1);
  color: #1d4ed8;
}

.status-offline {
  background-color: rgba(107, 114, 128, 0.1);
  color: #4b5563;
}

/* 审核标签 */
.review-badge {
  display: inline-block;
  padding: 4px 10px;
  border-radius: 16px;
  font-size: 12px;
  font-weight: 500;
  white-space: nowrap;
}

.review-pending {
  background-color: rgba(245, 158, 11, 0.14);
  color: #b45309;
}

.review-approved {
  background-color: rgba(16, 185, 129, 0.12);
  color: #059669;
}

.review-rejected {
  background-color: rgba(239, 68, 68, 0.12);
  color: #b91c1c;
}

/* 拒绝理由可能是长句，限两行并保留完整内容在 title 中 */
.review-reason {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  max-width: 180px;
  margin-top: 4px;
  font-size: 12px;
  color: #b91c1c;
  line-height: 1.4;
}

/* 批量通过按钮：默认次要样式，有选中项时变主色 */
.batch-approve-button {
  padding: 10px 18px;
  border: 1px solid #4CAF50;
  border-radius: 6px;
  background: #fff;
  color: #2e7d32;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
}

.batch-approve-button:hover:not(:disabled) {
  background: #4CAF50;
  color: #fff;
}

.batch-approve-button:disabled {
  border-color: #ddd;
  color: #aaa;
  cursor: not-allowed;
}

.check-col {
  width: 36px;
  text-align: center;
}

.check-col input {
  width: 16px;
  height: 16px;
  cursor: pointer;
}

/* 通过/拒绝按钮与上下架按钮区分开，避免误点 */
.action-approve {
  background-color: rgba(16, 185, 129, 0.14);
  color: #059669;
}

.action-approve:hover:not(:disabled) {
  background-color: #059669;
  color: #fff;
}

.action-reject {
  background-color: rgba(239, 68, 68, 0.12);
  color: #b91c1c;
}

.action-reject:hover:not(:disabled) {
  background-color: #dc2626;
  color: #fff;
}

.action-button:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

/* 操作按钮 */
.action-buttons {
  display: flex;
  gap: 8px;
}

.action-button {
  padding: 6px 12px;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.3s ease;
  font-size: 12px;
  font-weight: 500;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.view-button {
  background-color: #f5f5f5;
  color: #333;
}

.view-button:hover {
  background-color: #e0e0e0;
}

.action上架 {
  background-color: #10b981;
  color: white;
}

.action上架:hover {
  background-color: #059669;
}

.action下架 {
  background-color: #ef4444;
  color: white;
}

.action下架:hover {
  background-color: #dc2626;
}

/* 分页 */
.pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  margin-top: 24px;
  padding-top: 16px;
  border-top: 1px solid #e0e0e0;
}

.pagination-button {
  padding: 8px 16px;
  background-color: #f5f5f5;
  color: #333;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.3s ease;
  font-size: 14px;
  font-weight: 500;
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

/* 模态框 */
.modal-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-color: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.modal-content {
  background-color: white;
  border-radius: 12px;
  width: 90%;
  max-width: 600px;
  max-height: 80vh;
  overflow-y: auto;
  box-shadow: 0 25px 50px -12px rgba(0, 0, 0, 0.5);
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px 24px;
  border-bottom: 1px solid #e0e0e0;
}

.modal-header h2 {
  font-size: 20px;
  font-weight: 600;
  color: #333;
  margin: 0;
}

.close-button {
  background: none;
  border: none;
  font-size: 24px;
  cursor: pointer;
  color: #666;
  padding: 0;
  width: 32px;
  height: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 6px;
  transition: all 0.3s ease;
}

.close-button:hover {
  background-color: #f5f5f5;
  color: #333;
}

.modal-body {
  padding: 24px;
}

.product-detail {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.detail-row {
  display: flex;
  align-items: flex-start;
  gap: 16px;
}

.detail-label {
  font-weight: 600;
  color: #333;
  min-width: 100px;
  flex-shrink: 0;
}

.detail-value {
  flex: 1;
  color: #666;
  line-height: 1.5;
}

.modal-footer {
  padding: 20px 24px;
  border-top: 1px solid #e0e0e0;
  display: flex;
  justify-content: flex-end;
}

.close-modal-button {
  padding: 10px 20px;
  background-color: #f5f5f5;
  color: #333;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.3s ease;
  font-size: 14px;
  font-weight: 500;
}

.close-modal-button:hover {
  background-color: #e0e0e0;
  transform: translateY(-1px);
}

/* ---- 拒绝审核弹窗 ---- */
.reject-modal {
  max-width: 520px;
  width: 100%;
}

.reject-target {
  margin: 0 0 16px;
  color: #555;
  font-size: 14px;
  word-break: break-all;
}

.reject-label {
  display: block;
  margin-bottom: 8px;
  font-size: 14px;
  font-weight: 600;
  color: #333;
}

.reject-textarea {
  width: 100%;
  box-sizing: border-box;
  padding: 10px 12px;
  border: 1px solid #ddd;
  border-radius: 6px;
  font-size: 14px;
  line-height: 1.6;
  font-family: inherit;
  resize: vertical;
}

.reject-textarea:focus {
  outline: none;
  border-color: #4CAF50;
  box-shadow: 0 0 0 3px rgba(76, 175, 80, 0.12);
}

.reject-counter {
  margin-top: 6px;
  text-align: right;
  font-size: 12px;
  color: #999;
}

.cancel-modal-button {
  padding: 10px 20px;
  margin-right: 10px;
  background-color: #f5f5f5;
  color: #333;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  font-size: 14px;
}

.cancel-modal-button:hover {
  background-color: #e0e0e0;
}

.confirm-reject-button {
  padding: 10px 20px;
  background-color: #dc2626;
  color: #fff;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  font-size: 14px;
  font-weight: 500;
}

.confirm-reject-button:disabled {
  background-color: #f0a3a3;
  cursor: not-allowed;
}

/* 响应式设计 */
@media (max-width: 768px) {
  .admin-products {
    padding: 16px;
  }
  
  .filter-form {
    flex-direction: column;
    align-items: stretch;
  }
  
  .search-input {
    min-width: auto;
  }
  
  .products-table-container {
    font-size: 12px;
  }
  
  .products-table th,
  .products-table td {
    padding: 8px 12px;
  }
  
  .action-buttons {
    flex-direction: column;
    gap: 4px;
  }
  
  .action-button {
    padding: 4px 8px;
    font-size: 10px;
  }
  
  .pagination {
    flex-wrap: wrap;
  }
  
  .pagination-button {
    padding: 6px 12px;
    font-size: 12px;
  }
}
</style>

<!-- ===== 简约浅色主题覆盖 ===== -->
<style scoped>
.admin-products { max-width: var(--container); padding: var(--space-6); font-family: var(--font-sans); }
.admin-header { border-bottom: 1px solid var(--border); padding-bottom: var(--space-4); margin-bottom: var(--space-5); }
.admin-header h1 { font-size: var(--text-2xl); font-weight: var(--weight-semibold); letter-spacing: -0.02em; color: var(--text); }
.back-link { color: var(--text); background: var(--surface); border: 1px solid var(--border-strong); border-radius: var(--radius); padding: 8px 14px; font-size: var(--text-sm); }
.back-link:hover { background: var(--surface-2); transform: none; }

.filter-form { gap: var(--space-3); }
.search-input, .filter-select { padding: 10px 14px; border: 1px solid var(--border-strong); border-radius: var(--radius); font-size: var(--text-base); color: var(--text); background: var(--surface); }
.search-input:focus, .filter-select:focus { border-color: var(--accent); box-shadow: 0 0 0 3px var(--accent-soft); }
.search-button { padding: 10px 20px; background: var(--accent); color: #fff; border: none; border-radius: var(--radius); font-size: var(--text-base); font-weight: var(--weight-medium); }
.search-button:hover { background: var(--accent-hover); transform: none; box-shadow: none; }

.products-table-container { background: var(--surface); border: 1px solid var(--border); border-radius: var(--radius-lg); box-shadow: none; }
.products-table { background: var(--surface); }
.products-table th, .products-table td { padding: var(--space-3) var(--space-4); border-bottom: 1px solid var(--border); font-size: var(--text-sm); }
.products-table th { background: var(--surface-2); color: var(--text-2); font-weight: var(--weight-medium); text-transform: none; letter-spacing: 0; }
.products-table td { color: var(--text); }
.products-table tr:last-child td { border-bottom: none; }
.products-table tr:hover { background: var(--surface-2); }
.products-table .empty-cell { padding: var(--space-8) var(--space-4); text-align: center; color: var(--text-2); }

.status-badge { padding: 3px 12px; border-radius: var(--radius-full); font-size: var(--text-xs); font-weight: var(--weight-medium); text-transform: none; letter-spacing: 0; }
.status-available { background: var(--accent-soft); color: var(--accent); }
.status-sold { background: var(--info-soft); color: var(--info); }
.status-offline { background: var(--warning-soft); color: var(--warning); }
.action-up { color: var(--accent); }
.action-down { color: var(--warning); }

.action-button { padding: 5px 12px; border-radius: var(--radius-sm); font-size: var(--text-xs); font-weight: var(--weight-medium); text-transform: none; letter-spacing: 0; border: 1px solid transparent; background: var(--surface-3); color: var(--text); }
.view-button { background: var(--surface-3); color: var(--text); }
.view-button:hover { background: var(--surface-2); }

.review-badge { padding: 3px 10px; border-radius: var(--radius-full); font-size: var(--text-xs); font-weight: var(--weight-medium); }
.review-pending { background: var(--warning-soft); color: var(--warning); }
.review-approved { background: var(--accent-soft); color: var(--accent); }
.review-rejected { background: var(--danger-soft); color: var(--danger); }
.review-reason { color: var(--danger); }

.batch-approve-button { background: var(--surface); color: var(--accent); border: 1px solid var(--accent); border-radius: var(--radius); font-size: var(--text-base); font-weight: var(--weight-medium); }
.batch-approve-button:hover:not(:disabled) { background: var(--accent); color: #fff; }
.batch-approve-button:disabled { border-color: var(--border); color: var(--text-3); }

.action-approve { background: var(--accent-soft); color: var(--accent); }
.action-approve:hover:not(:disabled) { background: var(--accent); color: #fff; }
.action-reject { background: var(--danger-soft); color: var(--danger); }
.action-reject:hover:not(:disabled) { background: var(--danger); color: #fff; }

.cancel-modal-button { background: var(--surface); color: var(--text); border: 1px solid var(--border-strong); border-radius: var(--radius-sm); }
.cancel-modal-button:hover { background: var(--surface-2); }
.confirm-reject-button { background: var(--danger); color: #fff; border-radius: var(--radius-sm); }
.reject-label, .reject-target { color: var(--text); }
.reject-textarea { background: var(--surface); color: var(--text); border: 1px solid var(--border-strong); border-radius: var(--radius); }
.reject-counter { color: var(--text-3); }
.action上架 { background: var(--accent-soft); color: var(--accent); }
.action上架:hover { background: var(--accent); color: #fff; }
.action下架 { background: var(--danger-soft); color: var(--danger); }
.action下架:hover { background: var(--danger); color: #fff; }

.pagination { border-top: 1px solid var(--border); }
.pagination-button { padding: 6px 14px; background: var(--surface); color: var(--text); border: 1px solid var(--border-strong); border-radius: var(--radius-sm); font-size: var(--text-sm); }
.pagination-button:hover:not(:disabled) { background: var(--surface-2); transform: none; }
.pagination-info { font-size: var(--text-sm); color: var(--text-2); }

.loading-spinner { border: 3px solid var(--surface-3); border-top-color: var(--accent); }
.error-container { color: var(--danger); }
.retry-button { background: var(--surface); color: var(--text); border: 1px solid var(--border-strong); border-radius: var(--radius-sm); }
.retry-button:hover { background: var(--surface-2); }

.modal-overlay { background: rgba(20,20,18,0.35); }
.modal-content { background: var(--surface); border: 1px solid var(--border); border-radius: var(--radius-lg); box-shadow: var(--shadow-md); }
.modal-header { border-bottom: 1px solid var(--border); }
.modal-header h2 { color: var(--text); font-weight: var(--weight-semibold); }
.close-button { color: var(--text-2); border-radius: var(--radius-sm); }
.close-button:hover { background: var(--surface-3); color: var(--text); }
.detail-label { color: var(--text); font-weight: var(--weight-medium); }
.detail-value { color: var(--text-2); }
.modal-footer { border-top: 1px solid var(--border); }
.close-modal-button { background: var(--surface); color: var(--text); border: 1px solid var(--border-strong); border-radius: var(--radius-sm); }
.close-modal-button:hover { background: var(--surface-2); transform: none; }
</style>
