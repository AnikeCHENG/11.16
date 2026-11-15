<template>
  <div class="admin-categories">
    <div class="page-head">
      <div>
        <h1>分类管理</h1>
        <p class="page-hint">
          这里的分类会直接出现在用户端「发布商品」下拉与「商品大厅」筛选里。
          <strong>标识</strong>创建后不可修改，商品按标识归属分类；改名只影响展示。
        </p>
      </div>
      <router-link to="/admin" class="back-link">返回控制面板</router-link>
    </div>

    <!-- 新增分类 -->
    <div class="category-form">
      <h3>添加分类</h3>
      <div class="form-grid">
        <div class="form-group">
          <label for="category-name">分类名称</label>
          <input
            id="category-name"
            v-model="newCategory.name"
            type="text"
            placeholder="例如：户外露营"
            class="form-input"
          />
        </div>
        <div class="form-group">
          <label for="category-code">分类标识</label>
          <input
            id="category-code"
            v-model="newCategory.code"
            type="text"
            placeholder="小写英文，如 outdoor"
            class="form-input"
          />
          <span class="field-hint">仅小写字母开头，可含数字、下划线、连字符，2~50 字符</span>
        </div>
        <div class="form-group">
          <label for="category-sort">排序权重</label>
          <input
            id="category-sort"
            v-model.number="newCategory.sortOrder"
            type="number"
            class="form-input"
          />
          <span class="field-hint">数字越小越靠前</span>
        </div>
        <div class="form-group">
          <label for="category-desc">分类描述</label>
          <input
            id="category-desc"
            v-model="newCategory.description"
            type="text"
            placeholder="选填，用于说明该分类收录什么"
            class="form-input"
          />
        </div>
      </div>
      <button @click="addCategory" class="btn-primary" :disabled="submitting">
        {{ submitting ? '提交中…' : '添加分类' }}
      </button>
    </div>

    <!-- 分类列表 -->
    <div class="category-list">
      <h3>分类列表 <span class="list-count">共 {{ categories.length }} 个</span></h3>

      <div v-if="loading" class="state-tip">加载中…</div>
      <div v-else-if="categories.length === 0" class="no-categories">暂无分类</div>

      <div
        v-for="category in categories"
        :key="category.id"
        class="category-item"
        :class="{ 'is-disabled': category.status === 0 }"
      >
        <div class="category-info">
          <div class="category-main">
            <span class="category-name">{{ category.name }}</span>
            <code class="category-code">{{ category.code || '—' }}</code>
            <span class="category-status" :class="category.status === 0 ? 'off' : 'on'">
              {{ category.status === 0 ? '已停用' : '启用中' }}
            </span>
          </div>
          <div class="category-meta">
            <span class="category-count">{{ category.productCount ?? 0 }} 个商品</span>
            <span class="category-sort">排序 {{ category.sortOrder ?? 0 }}</span>
            <span v-if="category.description" class="category-desc">{{ category.description }}</span>
          </div>
        </div>
        <div class="category-actions">
          <button
            @click="toggleStatus(category)"
            class="btn-ghost"
            :title="category.status === 0 ? '启用后用户端可见' : '停用后用户端不再显示'"
          >
            {{ category.status === 0 ? '启用' : '停用' }}
          </button>
          <button @click="editCategory(category)" class="btn-secondary">编辑</button>
          <button
            @click="deleteCategory(category)"
            class="btn-danger"
            :title="category.productCount > 0 ? '该分类下还有商品，无法删除' : '删除分类'"
          >
            删除
          </button>
        </div>
      </div>
    </div>

    <!-- 编辑分类模态框 -->
    <div v-if="editingCategory" class="modal" @click.self="editingCategory = null">
      <div class="modal-content">
        <h3>编辑分类</h3>
        <div class="form-grid">
          <div class="form-group">
            <label for="edit-category-name">分类名称</label>
            <input
              id="edit-category-name"
              v-model="editingCategory.name"
              type="text"
              class="form-input"
            />
          </div>
          <div class="form-group">
            <label for="edit-category-code">分类标识</label>
            <input
              id="edit-category-code"
              :value="editingCategory.code"
              type="text"
              class="form-input"
              disabled
            />
            <span class="field-hint">标识不可修改：已有商品正是按它归属该分类</span>
          </div>
          <div class="form-group">
            <label for="edit-category-sort">排序权重</label>
            <input
              id="edit-category-sort"
              v-model.number="editingCategory.sortOrder"
              type="number"
              class="form-input"
            />
          </div>
          <div class="form-group">
            <label for="edit-category-desc">分类描述</label>
            <input
              id="edit-category-desc"
              v-model="editingCategory.description"
              type="text"
              class="form-input"
            />
          </div>
        </div>
        <div class="modal-actions">
          <button @click="saveCategory" class="btn-primary" :disabled="submitting">
            {{ submitting ? '保存中…' : '保存' }}
          </button>
          <button @click="editingCategory = null" class="btn-secondary">取消</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import admin from '../../api/admin'

/** 与后端 AdminController#CATEGORY_CODE_PATTERN 保持一致 */
const CODE_PATTERN = /^[a-z][a-z0-9_-]{1,49}$/

const categories = ref([])
const loading = ref(false)
const submitting = ref(false)

const emptyForm = () => ({ name: '', code: '', description: '', sortOrder: 0 })
const newCategory = ref(emptyForm())
const editingCategory = ref(null)

const fetchCategories = async () => {
  loading.value = true
  try {
    const response = await admin.get('/categories')
    categories.value = response.data.data || []
  } catch (error) {
    // 拦截器已弹出具体原因，这里只保留页面级兜底提示
    console.error('获取分类失败:', error)
  } finally {
    loading.value = false
  }
}

const addCategory = async () => {
  const name = newCategory.value.name.trim()
  const code = newCategory.value.code.trim().toLowerCase()
  if (!name) {
    ElMessage.warning('请输入分类名称')
    return
  }
  if (!CODE_PATTERN.test(code)) {
    ElMessage.warning('分类标识须以小写字母开头，只能包含字母/数字/下划线/连字符，长度 2~50')
    return
  }

  submitting.value = true
  try {
    await admin.post('/categories', {
      name,
      code,
      description: newCategory.value.description.trim(),
      sortOrder: Number(newCategory.value.sortOrder) || 0,
      status: 1
    })
    ElMessage.success('分类已添加，用户端立即可选')
    newCategory.value = emptyForm()
    await fetchCategories()
  } catch (error) {
    console.error('添加分类失败:', error)
  } finally {
    submitting.value = false
  }
}

const editCategory = (category) => {
  editingCategory.value = { ...category }
}

const saveCategory = async () => {
  if (!editingCategory.value.name?.trim()) {
    ElMessage.warning('请输入分类名称')
    return
  }
  submitting.value = true
  try {
    // 不提交 code：后端明确拒绝修改标识，改名/排序/描述才是允许的操作
    await admin.put(`/categories/${editingCategory.value.id}`, {
      name: editingCategory.value.name.trim(),
      description: editingCategory.value.description?.trim() || '',
      sortOrder: Number(editingCategory.value.sortOrder) || 0
    })
    ElMessage.success('已保存，用户端名称同步更新')
    editingCategory.value = null
    await fetchCategories()
  } catch (error) {
    console.error('更新分类失败:', error)
  } finally {
    submitting.value = false
  }
}

const toggleStatus = async (category) => {
  const next = category.status === 0 ? 1 : 0
  if (next === 0) {
    try {
      await ElMessageBox.confirm(
        `停用后「${category.name}」不再出现在用户端的发布与筛选中，已有商品不受影响。确定停用吗？`,
        '停用分类',
        { type: 'warning', confirmButtonText: '停用', cancelButtonText: '取消' }
      )
    } catch {
      return
    }
  }
  try {
    await admin.put(`/categories/${category.id}`, { status: next })
    ElMessage.success(next === 0 ? '已停用' : '已启用')
    await fetchCategories()
  } catch (error) {
    console.error('切换分类状态失败:', error)
  }
}

const deleteCategory = async (category) => {
  // 前端先拦一次给出更具体的原因；后端仍会再校验一遍（防止并发发布）
  if (category.productCount > 0) {
    ElMessage.warning(`该分类下还有 ${category.productCount} 件商品，请先迁移或停用分类`)
    return
  }
  try {
    await ElMessageBox.confirm(
      `确定删除分类「${category.name}」（${category.code}）吗？此操作不可撤销。`,
      '删除分类',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    await admin.delete(`/categories/${category.id}`)
    ElMessage.success('已删除')
    await fetchCategories()
  } catch (error) {
    console.error('删除分类失败:', error)
  }
}

onMounted(fetchCategories)
</script>

<style scoped>
.admin-categories {
  max-width: 1200px;
  margin: 0 auto;
  padding: 20px;
}

.page-head { display: flex; justify-content: space-between; align-items: flex-start; gap: 16px; margin-bottom: 24px; }
.back-link { color: #4CAF50; text-decoration: none; font-size: 14px; white-space: nowrap; }
.back-link:hover { text-decoration: underline; }

.admin-categories h1 {
  font-size: 2rem;
  color: #333;
  margin: 0 0 8px;
}

.page-hint { margin: 0; color: #666; font-size: 13px; line-height: 1.6; max-width: 720px; }

.category-form {
  background-color: #f9f9f9;
  border-radius: 10px;
  padding: 20px;
  margin-bottom: 30px;
  box-shadow: 0 2px 5px rgba(0, 0, 0, 0.1);
}

.category-form h3 {
  font-size: 1.2rem;
  color: #333;
  margin-bottom: 15px;
}

.form-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 16px; }

.form-group {
  margin-bottom: 15px;
}

.form-group label {
  display: block;
  margin-bottom: 5px;
  color: #666;
  font-weight: bold;
}

.form-input {
  width: 100%;
  padding: 10px;
  border: 1px solid #ddd;
  border-radius: 5px;
  font-size: 16px;
  box-sizing: border-box;
}

.form-input:disabled { background: #eee; color: #888; cursor: not-allowed; }

.field-hint { display: block; margin-top: 4px; font-size: 12px; color: #888; }

.btn-primary {
  padding: 10px 20px;
  background-color: #4CAF50;
  color: white;
  border: none;
  border-radius: 5px;
  cursor: pointer;
  font-size: 16px;
}

.btn-primary:disabled { opacity: 0.6; cursor: not-allowed; }
.btn-primary:hover:not(:disabled) { background-color: #45a049; }

.category-list {
  background-color: #f9f9f9;
  border-radius: 10px;
  padding: 20px;
  box-shadow: 0 2px 5px rgba(0, 0, 0, 0.1);
}

.category-list h3 {
  font-size: 1.2rem;
  color: #333;
  margin-bottom: 15px;
}

.list-count { font-size: 13px; font-weight: normal; color: #888; margin-left: 8px; }

.state-tip { padding: 30px; text-align: center; color: #888; }

.category-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  padding: 15px;
  background-color: white;
  border-radius: 5px;
  margin-bottom: 10px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
  flex-wrap: wrap;
}

.category-item.is-disabled { opacity: 0.65; }

.category-info { display: flex; flex-direction: column; gap: 6px; min-width: 0; }

.category-main { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }

.category-name { font-size: 16px; font-weight: bold; color: #333; }

.category-code {
  font-size: 12px;
  padding: 2px 7px;
  border-radius: 4px;
  background: #eef2f7;
  color: #47617e;
  font-family: ui-monospace, SFMono-Regular, Menlo, monospace;
}

.category-status { font-size: 12px; padding: 2px 7px; border-radius: 4px; }
.category-status.on { background: #e8f5ee; color: #0b6e54; }
.category-status.off { background: #fdecec; color: #c0392b; }

.category-meta { display: flex; gap: 14px; flex-wrap: wrap; font-size: 13px; color: #666; }
.category-desc { color: #888; }

.category-actions { display: flex; gap: 10px; flex-shrink: 0; }

.btn-ghost {
  padding: 8px 16px;
  background: #fff;
  color: #333;
  border: 1px solid #ccc;
  border-radius: 5px;
  cursor: pointer;
  font-size: 14px;
}
.btn-ghost:hover { background: #f0f0f0; }

.btn-secondary {
  padding: 8px 16px;
  background-color: #2196F3;
  color: white;
  border: none;
  border-radius: 5px;
  cursor: pointer;
  font-size: 14px;
}

.btn-secondary:hover { background-color: #0b7dda; }

.btn-danger {
  padding: 8px 16px;
  background-color: #f44336;
  color: white;
  border: none;
  border-radius: 5px;
  cursor: pointer;
  font-size: 14px;
}

.btn-danger:hover { background-color: #da190b; }

.no-categories {
  text-align: center;
  padding: 50px;
  color: #999;
  font-size: 1.2rem;
}

.modal {
  position: fixed;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background-color: rgba(0, 0, 0, 0.5);
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 1000;
}

.modal-content {
  background-color: white;
  border-radius: 10px;
  padding: 30px;
  width: 520px;
  max-width: 90%;
  box-shadow: 0 5px 15px rgba(0, 0, 0, 0.3);
}

.modal-content h3 {
  font-size: 1.2rem;
  color: #333;
  margin-bottom: 20px;
}

.modal-actions {
  display: flex;
  gap: 10px;
  justify-content: flex-end;
  margin-top: 20px;
}
</style>

<!-- ===== 简约浅色主题覆盖 ===== -->
<style scoped>
.admin-categories { max-width: var(--container); padding: var(--space-6); }
.admin-categories h1 { font-size: var(--text-2xl); font-weight: var(--weight-semibold); letter-spacing: -0.02em; color: var(--text); margin-bottom: var(--space-2); }
.page-hint { color: var(--text-2); }
.back-link { color: var(--accent); }
.category-form, .category-list { background: var(--surface); border: 1px solid var(--border); border-radius: var(--radius-lg); padding: var(--space-5); margin-bottom: var(--space-5); box-shadow: none; }
.category-form h3, .category-list h3 { font-size: var(--text-base); font-weight: var(--weight-semibold); color: var(--text); margin-bottom: var(--space-4); }
.form-group label { color: var(--text-2); font-weight: var(--weight-medium); font-size: var(--text-sm); }
.form-input { padding: 10px 14px; border: 1px solid var(--border-strong); border-radius: var(--radius); font-size: var(--text-base); color: var(--text); background: var(--surface); }
.form-input:focus { outline: none; border-color: var(--accent); box-shadow: 0 0 0 3px var(--accent-soft); }
.btn-primary { padding: 9px 18px; background: var(--accent); color: #fff; border: none; border-radius: var(--radius); font-size: var(--text-base); font-weight: var(--weight-medium); }
.btn-primary:hover:not(:disabled) { background: var(--accent-hover); }
.category-item { background: var(--surface-2); border: 1px solid var(--border); border-radius: var(--radius); padding: var(--space-4); margin-bottom: var(--space-2); box-shadow: none; }
.category-name { font-size: var(--text-base); font-weight: var(--weight-medium); color: var(--text); }
.category-count, .category-sort, .category-desc { font-size: var(--text-sm); color: var(--text-2); }
.category-code { background: var(--surface-3); color: var(--text-2); }
.category-status.on { background: var(--accent-soft); color: var(--accent); }
.category-status.off { background: var(--danger-soft); color: var(--danger); }
.btn-ghost { padding: 7px 16px; background: var(--surface); color: var(--text); border: 1px solid var(--border-strong); border-radius: var(--radius-sm); font-size: var(--text-sm); }
.btn-ghost:hover { background: var(--surface-3); }
.btn-secondary { padding: 7px 16px; background: var(--surface); color: var(--text); border: 1px solid var(--border-strong); border-radius: var(--radius-sm); font-size: var(--text-sm); }
.btn-secondary:hover { background: var(--surface-3); }
.btn-danger { padding: 7px 16px; background: var(--danger-soft); color: var(--danger); border: none; border-radius: var(--radius-sm); font-size: var(--text-sm); }
.btn-danger:hover { background: var(--danger); color: #fff; }
.no-categories { text-align: center; padding: var(--space-12); color: var(--text-3); font-size: var(--text-base); }
.modal { background: rgba(20,20,18,0.35); }
.modal-content { background: var(--surface); border: 1px solid var(--border); border-radius: var(--radius-lg); box-shadow: var(--shadow-md); }
.modal-content h3 { font-size: var(--text-lg); color: var(--text); }
.field-hint { color: var(--text-3); }
</style>
