<template>
  <div class="admin-sv">
    <AdminPageHeader title="学生认证审核（支付宝截图辅助认证）" />

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
            <td data-label="ID">{{ record.id }}</td>
            <td data-label="申请人">{{ record.username || `用户${record.userId}` }}</td>
            <td data-label="姓名">{{ record.realName }}</td>
            <td data-label="学校">
              <span class="school-text" :title="record.school">{{ record.school }}</span>
            </td>
            <td data-label="学号">{{ record.studentNo || '—' }}</td>
            <td data-label="入学年份">{{ record.enrollmentYear }}</td>
            <td data-label="提交时间">{{ formatDate(record.createdAt) }}</td>
            <td data-label="状态">
              <span class="status-badge" :class="getStatusClass(record.status)">
                {{ getStatusText(record.status) }}
              </span>
              <p v-if="record.status === 'rejected'" class="reject-line" :title="record.rejectReason">
                理由：{{ record.rejectReason }}
              </p>
            </td>
            <td data-label="操作" class="actions-cell">
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
          <div v-else-if="detailError" class="error-container" role="alert">
            <p>{{ detailError }}</p>
            <button class="retry-button" @click="loadDetail">重试</button>
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
              <div v-if="proofLoading" class="proof-state" data-test="proof-loading">
                <div class="loading-spinner small"></div>
                <span>截图加载中…</span>
              </div>
              <div v-else-if="proofError" class="proof-error" role="alert" data-test="proof-error">
                <p class="proof-error-title">截图加载失败：{{ proofError }}</p>
                <button type="button" class="retry-button proof-retry" data-test="proof-retry" @click="retryProof">
                  重新加载
                </button>
                <p v-if="isPending" class="form-hint">
                  截图无法取回时无法核对身份，可直接驳回并要求申请人重新提交截图。
                </p>
              </div>
              <img
                v-else-if="proofUrl"
                :src="proofUrl"
                alt="认证截图"
                class="proof-image"
                data-test="proof-image"
              />
              <p v-else class="form-hint">无截图</p>
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
import AdminPageHeader from '@/components/AdminPageHeader.vue'
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
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
const detailError = ref('')
const submitting = ref(false)
const rejectReason = ref('')
const reasonError = ref('')
/**
 * 截图地址：新数据存于私有目录，只能带鉴权取回再转 objectURL；
 * 历史数据若是 /uploads/... 公开路径，直接作为 <img src>。
 */
const proofUrl = ref('')
/** proofUrl 是否为本组件创建的 objectURL（只有它需要 revoke） */
let proofIsObjectUrl = false
const proofLoading = ref(false)
const proofError = ref('')
/** 每次打开/切换记录自增；迟到的响应据此丢弃，避免串到别的记录上 */
let requestSeq = 0

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
  requestSeq += 1
  currentRecord.value = record
  detail.value = null
  rejectReason.value = ''
  reasonError.value = ''
  resetProof()
  showModal.value = true
  await loadDetail()
}

/** 详情接口返回未脱敏的姓名与学号，供管理员与截图比对 */
const loadDetail = async () => {
  const record = currentRecord.value
  if (!record) return
  const seq = requestSeq
  detailLoading.value = true
  detailError.value = ''
  try {
    const response = await admin.get(`/student-verifications/${record.id}`)
    if (seq !== requestSeq) return
    detail.value = response.data?.data || null
    if (!detail.value) detailError.value = '未获取到申请详情'
  } catch (err) {
    if (seq !== requestSeq) return
    detailError.value = err?.response?.data?.message || err?.message || '获取详情失败'
  } finally {
    if (seq === requestSeq) detailLoading.value = false
  }
  // 详情与截图分开加载：截图失败不影响查看资料与驳回
  if (seq === requestSeq && detail.value) {
    await loadProof()
  }
}

/** 历史数据可能存的是公开路径（/uploads/...）或完整 URL，可直接渲染 */
const isPublicProofPath = (value) =>
  typeof value === 'string' && (/^\/?uploads\//.test(value) || /^https?:\/\//.test(value))

/**
 * 从错误响应里取后端提示。
 * <p>proof 接口按 blob 请求，失败时 body 也是 Blob：可能是 ApiResponse JSON，
 * 也可能是纯文本（当前后端 400 直接回 "图片不存在" 字节）。</p>
 */
const readBlobText = (blob) => {
  if (typeof blob.text === 'function') return blob.text()
  // 旧浏览器 / jsdom 的 Blob 没有 text()，退回 FileReader
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(String(reader.result || ''))
    reader.onerror = () => reject(reader.error)
    reader.readAsText(blob)
  })
}

const isBlob = (value) =>
  !!value &&
  ((typeof Blob !== 'undefined' && value instanceof Blob) || typeof value.text === 'function')

const extractErrorMessage = async (err) => {
  const data = err?.response?.data
  let text = ''
  if (isBlob(data)) {
    try {
      text = (await readBlobText(data)).trim()
    } catch {
      text = ''
    }
  } else if (typeof data === 'string') {
    text = data.trim()
  } else if (data && typeof data.message === 'string') {
    return data.message
  }
  if (text) {
    try {
      const parsed = JSON.parse(text)
      if (parsed && typeof parsed.message === 'string' && parsed.message) return parsed.message
    } catch {
      // 非 JSON：短文本直接作为提示，HTML 错误页等长内容不展示
      if (text.length <= 200 && !text.startsWith('<')) return text
    }
  }
  const status = err?.response?.status
  if (status === 404) return '图片不存在'
  if (status === 403) return '无权限：需要管理员身份'
  if (!err?.response) return '网络错误，请检查网络连接'
  return err?.message || '未知错误'
}

/**
 * 取回认证截图。admin 实例已自动注入 Authorization 头；
 * silent 让全局拦截器不再弹 toast，失败原因在弹窗内就地展示并可重试。
 */
const loadProof = async () => {
  const record = currentRecord.value
  const info = detail.value
  if (!record || !info) return
  const seq = requestSeq
  releaseProofUrl()
  proofError.value = ''

  if (!info.proofImage) return
  if (isPublicProofPath(info.proofImage)) {
    proofUrl.value = /^https?:\/\//.test(info.proofImage)
      ? info.proofImage
      : `/${info.proofImage.replace(/^\/+/, '')}`
    proofIsObjectUrl = false
    return
  }

  proofLoading.value = true
  try {
    const response = await admin.get(`/student-verifications/${record.id}/proof`, {
      responseType: 'blob',
      silent: true
    })
    const url = URL.createObjectURL(response.data)
    if (seq !== requestSeq) {
      URL.revokeObjectURL(url)
      return
    }
    proofUrl.value = url
    proofIsObjectUrl = true
  } catch (err) {
    const message = await extractErrorMessage(err)
    if (seq !== requestSeq) return
    proofError.value = message
  } finally {
    if (seq === requestSeq) proofLoading.value = false
  }
}

const retryProof = () => loadProof()

/** 释放截图 objectURL，避免内存泄漏；公开路径无需 revoke */
const releaseProofUrl = () => {
  if (proofUrl.value && proofIsObjectUrl) {
    URL.revokeObjectURL(proofUrl.value)
  }
  proofUrl.value = ''
  proofIsObjectUrl = false
}

const resetProof = () => {
  releaseProofUrl()
  proofLoading.value = false
  proofError.value = ''
}

const closeModal = () => {
  if (submitting.value) return
  requestSeq += 1
  showModal.value = false
  currentRecord.value = null
  detail.value = null
  detailError.value = ''
  detailLoading.value = false
  reasonError.value = ''
  resetProof()
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
      submitting.value = false
      closeModal()
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

onBeforeUnmount(() => {
  requestSeq += 1
  releaseProofUrl()
})
</script>

<style scoped>
.admin-sv {
  max-width: var(--container, 1400px);
  margin: 0 auto;
  padding: var(--space-6, 24px);
  font-family: var(--font-sans, -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Arial, sans-serif);
}

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
  /* 头/尾固定，只有 body 滚动：「拒绝/通过」始终可见 */
  display: flex;
  flex-direction: column;
  overflow: hidden;
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

.modal-body { padding: 24px; overflow-y: auto; flex: 1 1 auto; min-height: 0; -webkit-overflow-scrolling: touch; }
.modal-header, .modal-footer { flex-shrink: 0; }

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

.proof-state {
  display: flex;
  align-items: center;
  gap: 10px;
  color: var(--text-2, #666);
  font-size: var(--text-sm, 14px);
  padding: 12px 0;
}

.loading-spinner.small { width: 18px; height: 18px; border-width: 2px; margin: 0; }

.proof-error {
  padding: 12px 14px;
  border: 1px solid var(--danger, #ef4444);
  background: var(--danger-soft, rgba(239, 68, 68, 0.08));
  border-radius: var(--radius, 8px);
}

.proof-error-title {
  margin: 0;
  color: var(--danger, #dc2626);
  font-weight: var(--weight-medium, 500);
  font-size: var(--text-sm, 14px);
}

.proof-error .proof-retry { margin-top: 10px; }

@media (max-width: 640px) {
  .admin-sv { padding: 12px; }

  /* 列表改卡片：每行一张卡，审核/详情按钮始终在可视区 */
  .table-container { overflow-x: visible; background: transparent; border: none; }
  .sv-table thead { display: none; }
  .sv-table, .sv-table tbody, .sv-table tr, .sv-table td { display: block; width: 100%; }
  .sv-table tr {
    margin-bottom: 12px;
    padding: 6px 12px;
    background: var(--surface, #fff);
    border: 1px solid var(--border, #e0e0e0);
    border-radius: var(--radius-lg, 12px);
  }
  .sv-table tr:hover td { background: transparent; }
  .sv-table td {
    display: flex;
    justify-content: space-between;
    align-items: flex-start;
    gap: 12px;
    padding: 8px 0;
    font-size: 13px;
    text-align: right;
    border-bottom: 1px dashed var(--border, #f0f0f0);
    box-sizing: border-box;
  }
  .sv-table td::before {
    content: attr(data-label);
    flex-shrink: 0;
    color: var(--text-3, #9a9a92);
    text-align: left;
  }
  .sv-table td:last-child { border-bottom: none; }
  .school-text { max-width: 200px; }
  .sv-table td.actions-cell { justify-content: flex-end; gap: 8px; padding-top: 10px; }
  .sv-table td.actions-cell::before { display: none; }
  .sv-table td.actions-cell .action-button { flex: 1; margin-right: 0; padding: 10px 12px; font-size: 14px; }

  /* 弹窗全屏，底部操作栏 sticky + 安全区 */
  .modal-overlay { align-items: stretch; }
  .modal-content {
    width: 100%;
    max-width: none;
    height: 100%;
    height: 100dvh;
    max-height: none;
    border: none;
    border-radius: 0;
  }
  .modal-header { padding: 14px 16px; }
  .modal-header h2 { font-size: 17px; }
  .modal-body { padding: 16px; }
  .detail-row { gap: 10px; }
  .modal-footer {
    position: sticky;
    bottom: 0;
    background: var(--surface, #fff);
    padding: 12px 16px calc(12px + var(--safe-bottom, env(safe-area-inset-bottom, 0px)));
  }
  .modal-footer .reject-button,
  .modal-footer .approve-button { flex: 1; padding: 12px 16px; font-size: 15px; }
}
</style>
