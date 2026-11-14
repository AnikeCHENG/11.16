<template>
  <div class="admin-sv">
    <div class="admin-header">
      <h1>学生认证审核（支付宝截图辅助认证）</h1>
      <router-link to="/admin" class="back-link">返回控制面板</router-link>
    </div>

    <!-- 筛选 -->
    <div class="filter-section">
      <div class="filter-form">
        <input
          v-model="searchQuery"
          type="text"
          placeholder="按学校名称搜索"
          class="search-input"
          @keyup.enter="fetchList"
        />
        <select v-model="statusFilter" class="filter-select" @change="handleFilterChange">
          <option value="">所有状态</option>
          <option value="pending">待审核</option>
          <option value="approved">审核通过</option>
          <option value="rejected">审核拒绝</option>
          <option value="expired">已过期</option>
        </select>
        <button class="search-button" @click="fetchList">搜索</button>
      </div>
      <p class="compliance-hint">
        列表页姓名与学号已脱敏；点击「审核」可查看完整信息与认证截图。
        截图与学籍资料仅用于学生身份审核。
      </p>
    </div>

    <!-- 列表 -->
    <div class="table-container">
      <div v-if="loading" class="loading-container">
        <div class="loading-spinner"></div>
        <p>加载中...</p>
      </div>
      <div v-else-if="error" class="error-container">
        <p>{{ error }}</p>
        <button class="retry-button" @click="fetchList">重试</button>
      </div>
      <div v-else-if="records.length === 0" class="empty-text">暂无认证申请</div>
      <table v-else class="sv-table">
        <thead>
          <tr>
            <th>ID</th>
            <th>申请人</th>
            <th>姓名</th>
            <th>学校</th>
            <th>学号</th>
            <th>入学年份</th>
            <th>提交时间</th>
            <th>状态</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="record in records" :key="record.id">
            <td>{{ record.id }}</td>
            <td>{{ record.username || `用户${record.userId}` }}</td>
            <td>{{ record.realName }}</td>
            <td>
              <span class="school-text" :title="record.school">{{ record.school }}</span>
            </td>
            <td>{{ record.studentNo || '—' }}</td>
            <td>{{ record.enrollmentYear }}</td>
            <td>{{ formatDate(record.createdAt) }}</td>
            <td>
              <span class="status-badge" :class="getStatusClass(record.status)">
                {{ getStatusText(record.status) }}
              </span>
              <p v-if="record.status === 'rejected'" class="reject-line" :title="record.rejectReason">
                理由：{{ record.rejectReason }}
              </p>
            </td>
            <td>
              <button
                v-if="record.status === 'pending'"
                class="action-button review-button"
                @click="openReviewModal(record)"
              >
                审核
              </button>
              <button class="action-button detail-button" @click="openReviewModal(record)">详情</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- 分页 -->
    <div v-if="!loading && !error && total > 0" class="pagination">
      <button class="pagination-button" @click="changePage(1)" :disabled="page === 1">首页</button>
      <button class="pagination-button" @click="changePage(page - 1)" :disabled="page === 1">上一页</button>
      <span class="pagination-info">
        第 {{ page }} 页，共 {{ totalPages }} 页，总计 {{ total }} 条
      </span>
      <button class="pagination-button" @click="changePage(page + 1)" :disabled="page === totalPages">下一页</button>
      <button class="pagination-button" @click="changePage(totalPages)" :disabled="page === totalPages">末页</button>
    </div>

    <!-- 审核/详情弹窗 -->
    <div v-if="showModal" class="modal-overlay" @click.self="closeModal">
      <div class="modal-content">
        <div class="modal-header">
          <h2>{{ isPending ? '审核认证申请' : '认证申请详情' }} #{{ detail?.id }}</h2>
          <button class="close-button" @click="closeModal" aria-label="关闭">&times;</button>
        </div>
        <div class="modal-body">
          <div v-if="detailLoading" class="loading-container">
            <div class="loading-spinner"></div>
          </div>
          <template v-else-if="detail">
            <div class="detail-summary">
              <div class="detail-row">
                <span class="detail-label">申请人:</span>
                <span class="detail-value">{{ detail.username || `用户${detail.userId}` }}</span>
              </div>
              <div class="detail-row">
                <span class="detail-label">真实姓名:</span>
                <span class="detail-value">{{ detail.realName }}</span>
              </div>
              <div class="detail-row">
                <span class="detail-label">学校:</span>
                <span class="detail-value">{{ detail.school }}</span>
              </div>
              <div class="detail-row">
                <span class="detail-label">学号:</span>
                <span class="detail-value">{{ detail.studentNo || '未填写' }}</span>
              </div>
              <div class="detail-row">
                <span class="detail-label">入学年份:</span>
                <span class="detail-value">{{ detail.enrollmentYear }}</span>
              </div>
              <div class="detail-row">
                <span class="detail-label">学院/专业:</span>
                <span class="detail-value">{{ detail.department || '未填写' }}</span>
              </div>
              <div class="detail-row">
                <span class="detail-label">备注:</span>
                <span class="detail-value">{{ detail.remark || '未填写' }}</span>
              </div>
              <div class="detail-row">
                <span class="detail-label">提交时间:</span>
                <span class="detail-value">{{ formatDate(detail.createdAt) }}</span>
              </div>
            </div>

            <div class="form-group">
              <label class="form-label">支付宝学生认证结果截图</label>
              <img
                v-if="proofUrl"
                :src="proofUrl"
                alt="认证截图"
                class="proof-image"
              />
              <p v-else class="form-hint">{{ detail.proofImage ? '截图加载中…' : '无截图' }}</p>
              <p class="form-hint">
                请核对截图中的学校名称与认证状态与申请人填写信息一致。
                截图与资料仅用于学生身份审核，且不通过公开 URL 暴露。
              </p>
            </div>

            <template v-if="isPending">
              <div class="form-group">
                <label class="form-label">拒绝理由（驳回时必填，将展示给申请人）</label>
                <textarea
                  v-model="rejectReason"
                  class="reason-input"
                  rows="3"
                  placeholder="例如：截图模糊无法辨认学校名称 / 截图认证状态为未通过 / 请上传本人认证结果页"
                ></textarea>
                <p v-if="reasonError" class="field-error">{{ reasonError }}</p>
              </div>
            </template>
            <div v-else class="form-group">
              <div class="detail-row">
                <span class="detail-label">审核结果:</span>
                <span class="detail-value">
                  <span class="status-badge" :class="getStatusClass(detail.status)">
                    {{ getStatusText(detail.status) }}
                  </span>
                  <template v-if="detail.status === 'rejected'">（理由：{{ detail.rejectReason }}）</template>
                </span>
              </div>
            </div>
          </template>
        </div>
        <div v-if="isPending && detail" class="modal-footer">
          <button class="reject-button" @click="submitReview('reject')" :disabled="submitting">
            {{ submitting ? '提交中…' : '拒绝' }}
          </button>
          <button class="approve-button" @click="submitReview('approve')" :disabled="submitting">
            {{ submitting ? '提交中…' : '通过' }}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import admin from '../../api/admin'
import { ElMessage } from 'element-plus'

const records = ref([])
const loading = ref(false)
const error = ref('')
const searchQuery = ref('')
const statusFilter = ref('')
const page = ref(1)
const pageSize = ref(10)
const total = ref(0)

const showModal = ref(false)
const currentRecord = ref(null)
const detail = ref(null)
const detailLoading = ref(false)
const submitting = ref(false)
const rejectReason = ref('')
const reasonError = ref('')
/** 截图的 objectURL：截图存于私有目录，只能带鉴权取回，不能直接用 <img src> */
const proofUrl = ref('')

const totalPages = computed(() => Math.ceil(total.value / pageSize.value))
const isPending = computed(() => currentRecord.value?.status === 'pending')

/**
 * 分页契约与项目基线一致（common/PageResult）：
 * 请求发 size，响应读 res.data.list / res.data.total。
 */
const fetchList = async () => {
  try {
    loading.value = true
    error.value = ''
    const response = await admin.get('/student-verifications', {
      params: {
        search: searchQuery.value || undefined,
        status: statusFilter.value === '' ? undefined : statusFilter.value,
        page: page.value,
        size: pageSize.value
      }
    })
    if (response.data && response.data.data) {
      records.value = response.data.data.list || []
      total.value = response.data.data.total || 0
    } else {
      records.value = []
      total.value = 0
    }
  } catch (err) {
    console.error('获取认证申请列表失败:', err)
    error.value = err?.response?.data?.message || '网络错误，请检查网络连接'
  } finally {
    loading.value = false
  }
}

const handleFilterChange = () => {
  page.value = 1
  fetchList()
}

const changePage = (newPage) => {
  if (newPage >= 1 && newPage <= totalPages.value) {
    page.value = newPage
    fetchList()
  }
}

const openReviewModal = async (record) => {
  currentRecord.value = record
  detail.value = null
  rejectReason.value = ''
  reasonError.value = ''
  releaseProofUrl()
  showModal.value = true
  // 详情接口返回未脱敏的姓名与学号，供管理员与截图比对
  try {
    detailLoading.value = true
    const response = await admin.get(`/student-verifications/${record.id}`)
    detail.value = response.data?.data || null
    await loadProof(record.id)
  } catch (err) {
    console.error('获取认证申请详情失败:', err)
    ElMessage.error(err?.response?.data?.message || '获取详情失败')
  } finally {
    detailLoading.value = false
  }
}

/**
 * 取回认证截图。
 *
 * <p>截图存放在私有目录，没有公开 URL，只能带管理员凭证取回二进制，
 * 再用 objectURL 交给 <img>。admin 实例已自动注入 Authorization 头。</p>
 */
const loadProof = async (id) => {
  try {
    const response = await admin.get(`/student-verifications/${id}/proof`, {
      responseType: 'blob'
    })
    proofUrl.value = URL.createObjectURL(response.data)
  } catch (err) {
    console.error('获取认证截图失败:', err)
    proofUrl.value = ''
  }
}

/** 释放上一张截图的 objectURL，避免内存泄漏 */
const releaseProofUrl = () => {
  if (proofUrl.value) {
    URL.revokeObjectURL(proofUrl.value)
    proofUrl.value = ''
  }
}

const closeModal = () => {
  if (submitting.value) return
  showModal.value = false
  currentRecord.value = null
  detail.value = null
  reasonError.value = ''
  releaseProofUrl()
}

/**
 * 提交审核结果。拒绝时理由必填（后端同样强制校验）。
 * 通过后该用户即获得学生认证权限（is_student_verified = 1），
 * 若用户已有既有认证，后端保持原状不会降级。
 */
const submitReview = async (action) => {
  if (action === 'reject' && !rejectReason.value.trim()) {
    reasonError.value = '拒绝时必须填写拒绝理由'
    return
  }
  reasonError.value = ''
  submitting.value = true
  try {
    const response = await admin.put(`/student-verifications/${currentRecord.value.id}/review`, {
      action,
      reason: action === 'reject' ? rejectReason.value.trim() : ''
    })
    if (response.data && response.data.code === 200) {
      ElMessage.success(response.data.message || '审核完成')
      showModal.value = false
      currentRecord.value = null
      await fetchList()
    }
  } catch (err) {
    // 失败提示由 api/admin.js 的响应拦截器统一弹出
    console.error('审核失败:', err)
  } finally {
    submitting.value = false
  }
}

const getStatusText = (status) => {
  switch (status) {
    case 'pending': return '待审核'
    case 'approved': return '审核通过'
    case 'rejected': return '审核拒绝'
    case 'expired': return '已过期'
    default: return '未知'
  }
}

const getStatusClass = (status) => {
  switch (status) {
    case 'pending': return 'status-pending'
    case 'approved': return 'status-approved'
    case 'rejected': return 'status-rejected'
    case 'expired': return 'status-expired'
    default: return ''
  }
}

const formatDate = (dateString) => {
  if (!dateString) return ''
  const date = new Date(dateString)
  if (Number.isNaN(date.getTime())) return ''
  return date.toLocaleString()
}

onMounted(() => {
  fetchList()
})
</script>

<style scoped>
.admin-sv {
  max-width: var(--container, 1400px);
  margin: 0 auto;
  padding: var(--space-6, 24px);
  font-family: var(--font-sans, -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Arial, sans-serif);
}

.admin-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--space-5, 32px);
  padding-bottom: var(--space-4, 16px);
  border-bottom: 1px solid var(--border, #e0e0e0);
}

.admin-header h1 {
  font-size: var(--text-2xl, 24px);
  font-weight: var(--weight-semibold, 600);
  color: var(--text, #333);
  margin: 0;
}

.back-link {
  text-decoration: none;
  color: var(--text, #333);
  background: var(--surface, #f5f5f5);
  border: 1px solid var(--border-strong, #e0e0e0);
  border-radius: var(--radius, 8px);
  padding: 8px 14px;
  font-size: var(--text-sm, 14px);
}

.back-link:hover { background: var(--surface-2, #e0e0e0); }

.filter-section { margin-bottom: var(--space-4, 24px); }

.filter-form {
  display: flex;
  gap: var(--space-3, 12px);
  align-items: center;
  flex-wrap: wrap;
}

.compliance-hint {
  margin: var(--space-2, 8px) 0 0;
  font-size: var(--text-xs, 12px);
  color: var(--text-3, #999);
}

.search-input,
.filter-select {
  padding: 10px 14px;
  border: 1px solid var(--border-strong, #e0e0e0);
  border-radius: var(--radius, 8px);
  font-size: var(--text-base, 14px);
  color: var(--text, #333);
  background: var(--surface, #fff);
}

.search-input { flex: 1; min-width: 200px; }

.search-input:focus,
.filter-select:focus {
  outline: none;
  border-color: var(--accent, #10b981);
  box-shadow: 0 0 0 3px var(--accent-soft, rgba(16, 185, 129, 0.1));
}

.search-button {
  padding: 10px 20px;
  background: var(--accent, #10b981);
  color: #fff;
  border: none;
  border-radius: var(--radius, 8px);
  font-size: var(--text-base, 14px);
  font-weight: var(--weight-medium, 500);
  cursor: pointer;
}

.search-button:hover { background: var(--accent-hover, #059669); }

.loading-container,
.error-container,
.empty-text {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 60px 0;
}

.loading-spinner {
  width: 40px;
  height: 40px;
  border: 3px solid var(--surface-3, #f3f3f3);
  border-top-color: var(--accent, #10b981);
  border-radius: 50%;
  animation: spin 1s linear infinite;
  margin-bottom: 16px;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

.error-container { color: var(--danger, #ef4444); }
.empty-text { color: var(--text-3, #9a9a92); }

.retry-button {
  margin-top: 16px;
  padding: 8px 16px;
  background: var(--surface, #f5f5f5);
  color: var(--text, #333);
  border: 1px solid var(--border-strong, #e0e0e0);
  border-radius: var(--radius-sm, 6px);
  cursor: pointer;
}

.table-container {
  overflow-x: auto;
  margin-bottom: var(--space-4, 24px);
  background: var(--surface, #fff);
  border: 1px solid var(--border, #e0e0e0);
  border-radius: var(--radius-lg, 12px);
}

.sv-table {
  width: 100%;
  border-collapse: collapse;
}

.sv-table th,
.sv-table td {
  padding: var(--space-3, 12px) var(--space-4, 16px);
  text-align: left;
  border-bottom: 1px solid var(--border, #f0f0f0);
  font-size: var(--text-sm, 14px);
}

.sv-table th {
  background: var(--surface-2, #f9fafb);
  font-weight: var(--weight-medium, 600);
  color: var(--text-2, #333);
}

.sv-table td { color: var(--text, #333); }
.sv-table tr:last-child td { border-bottom: none; }
.sv-table tr:hover td { background: var(--surface-2, #f9f9f9); }

.school-text {
  display: inline-block;
  max-width: 160px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: bottom;
}

.status-badge {
  padding: 3px 12px;
  border-radius: var(--radius-full, 16px);
  font-size: var(--text-xs, 12px);
  font-weight: var(--weight-medium, 500);
  display: inline-block;
}

.status-pending { background: var(--warning-soft, rgba(251, 191, 36, 0.1)); color: var(--warning, #b45309); }
.status-approved { background: var(--accent-soft, rgba(16, 185, 129, 0.1)); color: var(--accent, #059669); }
.status-rejected { background: var(--danger-soft, rgba(239, 68, 68, 0.1)); color: var(--danger, #dc2626); }
.status-expired { background: var(--surface-3, rgba(107, 114, 128, 0.1)); color: var(--text-3, #4b5563); }

.reject-line {
  margin: 6px 0 0;
  font-size: var(--text-xs, 12px);
  color: var(--text-3, #9a9a92);
  max-width: 180px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.action-button {
  padding: 6px 12px;
  margin-right: 6px;
  border: none;
  border-radius: var(--radius-sm, 6px);
  cursor: pointer;
  font-size: var(--text-xs, 12px);
  font-weight: var(--weight-medium, 500);
  color: #fff;
}

.review-button { background: var(--accent, #10b981); }
.review-button:hover { background: var(--accent-hover, #059669); }
.detail-button { background: var(--info, #3b82f6); }
.detail-button:hover { background: #2563eb; }

.pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-3, 12px);
  margin-top: var(--space-4, 24px);
  padding-top: var(--space-4, 16px);
  border-top: 1px solid var(--border, #e0e0e0);
}

.pagination-button {
  padding: 6px 14px;
  background: var(--surface, #f5f5f5);
  color: var(--text, #333);
  border: 1px solid var(--border-strong, #e0e0e0);
  border-radius: var(--radius-sm, 6px);
  font-size: var(--text-sm, 14px);
  cursor: pointer;
}

.pagination-button:hover:not(:disabled) { background: var(--surface-2, #e0e0e0); }
.pagination-button:disabled { opacity: 0.5; cursor: not-allowed; }
.pagination-info { font-size: var(--text-sm, 14px); color: var(--text-2, #666); }

/* 弹窗 */
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(20, 20, 18, 0.35);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.modal-content {
  background: var(--surface, #fff);
  border: 1px solid var(--border, #e0e0e0);
  border-radius: var(--radius-lg, 12px);
  width: 90%;
  max-width: 640px;
  max-height: 85vh;
  overflow-y: auto;
  box-shadow: var(--shadow-md, 0 25px 50px -12px rgba(0, 0, 0, 0.5));
}

.modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20px 24px;
  border-bottom: 1px solid var(--border, #e0e0e0);
}

.modal-header h2 {
  font-size: 20px;
  font-weight: var(--weight-semibold, 600);
  color: var(--text, #333);
  margin: 0;
}

.close-button {
  background: none;
  border: none;
  font-size: 24px;
  cursor: pointer;
  color: var(--text-2, #666);
  padding: 0;
  width: 32px;
  height: 32px;
  border-radius: var(--radius-sm, 6px);
}

.close-button:hover { background: var(--surface-3, #f5f5f5); color: var(--text, #333); }

.modal-body { padding: 24px; }

.detail-summary {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 14px 16px;
  margin-bottom: 20px;
  background: var(--surface-2, #f9fafb);
  border: 1px solid var(--border, #e0e0e0);
  border-radius: var(--radius, 8px);
}

.detail-row { display: flex; align-items: flex-start; gap: 16px; }

.detail-label {
  font-weight: var(--weight-medium, 600);
  color: var(--text, #333);
  min-width: 76px;
  flex-shrink: 0;
}

.detail-value { flex: 1; color: var(--text-2, #666); line-height: 1.5; }

.form-group { margin-bottom: 20px; }

.form-label {
  display: block;
  font-size: var(--text-sm, 14px);
  font-weight: var(--weight-medium, 500);
  color: var(--text, #333);
  margin-bottom: 10px;
}

.proof-image {
  max-width: 100%;
  max-height: 360px;
  border: 1px solid var(--border, #e0e0e0);
  border-radius: var(--radius, 8px);
}

.form-hint {
  margin: 8px 0 0;
  font-size: var(--text-xs, 12px);
  color: var(--text-3, #9a9a92);
}

.reason-input {
  width: 100%;
  padding: 10px 14px;
  border: 1px solid var(--border-strong, #e0e0e0);
  border-radius: var(--radius, 8px);
  font-size: var(--text-sm, 14px);
  font-family: inherit;
  resize: vertical;
  color: var(--text, #333);
}

.reason-input:focus {
  outline: none;
  border-color: var(--accent, #10b981);
  box-shadow: 0 0 0 3px var(--accent-soft, rgba(16, 185, 129, 0.1));
}

.field-error { margin: 8px 0 0; font-size: var(--text-xs, 12px); color: var(--danger, #ef4444); }

.modal-footer {
  padding: 20px 24px;
  border-top: 1px solid var(--border, #e0e0e0);
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

.reject-button,
.approve-button {
  padding: 10px 20px;
  color: #fff;
  border: none;
  border-radius: var(--radius-sm, 6px);
  font-size: var(--text-sm, 14px);
  font-weight: var(--weight-medium, 500);
  cursor: pointer;
}

.reject-button { background: var(--danger, #ef4444); }
.reject-button:hover:not(:disabled) { background: #dc2626; }
.approve-button { background: var(--accent, #10b981); }
.approve-button:hover:not(:disabled) { background: var(--accent-hover, #059669); }
.reject-button:disabled,
.approve-button:disabled { opacity: 0.6; cursor: not-allowed; }

@media (max-width: 768px) {
  .admin-sv { padding: 16px; }
  .filter-form { flex-direction: column; align-items: stretch; }
  .search-input { min-width: auto; }
  .sv-table th, .sv-table td { padding: 8px 12px; font-size: 12px; }
  .pagination { flex-wrap: wrap; }
}
</style>
