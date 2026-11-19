<template>
  <!-- 仅原生 App 显示：现场演示时把 App 指向另一台后端，无需重新打包 -->
  <div class="server-setting">
    <button v-if="!editing" type="button" class="server-setting__toggle" @click="startEdit">
      服务器地址：<span class="server-setting__value">{{ current || '未设置' }}</span>
      <span class="text-link">修改</span>
    </button>
    <form v-else class="server-setting__form" @submit.prevent="save">
      <label for="server-url" class="server-setting__label">服务器地址</label>
      <input
        id="server-url"
        v-model="draft"
        class="server-setting__input"
        type="url"
        inputmode="url"
        autocomplete="off"
        placeholder="例如 http://192.168.1.20:8080"
      />
      <p v-if="message" class="server-setting__msg" :class="{ 'is-error': isError }" role="status">{{ message }}</p>
      <div class="server-setting__actions">
        <button type="button" class="btn btn-outline" :disabled="testing" @click="test">
          {{ testing ? '连接中…' : '测试连接' }}
        </button>
        <button type="submit" class="btn btn-primary">保存并重新加载</button>
      </div>
      <div class="server-setting__actions">
        <button type="button" class="text-link" @click="resetDefault">恢复默认（{{ buildDefault || '未配置' }}）</button>
        <button type="button" class="text-link" @click="editing = false">取消</button>
      </div>
    </form>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { API_ORIGIN, getBuildServerUrl, normalizeServerUrl, saveServerUrl } from '@/utils/apiBase'

const props = defineProps({
  /** 便于测试注入；默认整页刷新，让所有接口模块按新地址重新初始化 */
  reload: { type: Function, default: () => window.location.reload() }
})

const current = API_ORIGIN
const buildDefault = getBuildServerUrl()
const editing = ref(false)
const draft = ref('')
const message = ref('')
const isError = ref(false)
const testing = ref(false)

function startEdit() {
  draft.value = current
  message.value = ''
  editing.value = true
}

function fail(text) {
  message.value = text
  isError.value = true
}

async function test() {
  const url = normalizeServerUrl(draft.value)
  if (!url) return fail('地址格式不正确，应为 http(s)://IP或域名[:端口]')
  testing.value = true
  message.value = ''
  try {
    const ctrl = new AbortController()
    const timer = setTimeout(() => ctrl.abort(), 5000)
    const res = await fetch(`${url}${import.meta.env.VITE_API_BASE || '/api'}/categories`, { signal: ctrl.signal })
    clearTimeout(timer)
    const body = await res.json().catch(() => null)
    if (res.ok && body && body.code === 200) {
      message.value = '连接成功'
      isError.value = false
    } else {
      fail(`服务器有响应，但不是本平台后端（HTTP ${res.status}）`)
    }
  } catch {
    fail('无法连接：请确认手机与服务器在同一网络、后端已启动、端口已放行')
  } finally {
    testing.value = false
  }
}

function save() {
  if (!saveServerUrl(draft.value)) return fail('地址格式不正确，应为 http(s)://IP或域名[:端口]')
  props.reload()
}

function resetDefault() {
  saveServerUrl('')
  props.reload()
}
</script>

<style scoped>
.server-setting {
  margin-top: var(--space-4, 16px);
  font-size: 13px;
  color: var(--color-text-muted, #6b7280);
}
.server-setting__toggle {
  background: none;
  border: 0;
  padding: 0;
  color: inherit;
  font: inherit;
  cursor: pointer;
  text-align: left;
  word-break: break-all;
}
.server-setting__value {
  font-family: ui-monospace, monospace;
  margin-right: 6px;
}
.server-setting__form {
  display: grid;
  gap: 8px;
}
.server-setting__label {
  font-weight: 600;
}
.server-setting__input {
  width: 100%;
  min-width: 0;
  box-sizing: border-box;
  padding: 8px 10px;
  border: 1px solid #d1d5db;
  border-radius: 8px;
  font: inherit;
}
.server-setting__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.server-setting__actions .text-link {
  background: none;
  border: 0;
  padding: 0;
  cursor: pointer;
  font: inherit;
}
.server-setting__msg {
  margin: 0;
  color: #15803d;
}
.server-setting__msg.is-error {
  color: #dc2626;
}
</style>
