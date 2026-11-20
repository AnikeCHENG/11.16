<template>
  <AuthShell>
    <div class="glass-card">
      <header class="auth-head">
        <BrandLogo :size="44" class="auth-head__logo" />
        <h1 class="auth-title">重置密码</h1>
        <p class="auth-sub">
          {{ codeSent ? '输入邮箱收到的验证码，设置新密码' : '输入注册邮箱，我们会发送验证码' }}
        </p>
      </header>

      <!-- K-11：真实 submit 按钮 + @submit.prevent，输入框内按回车即可提交（第一步发验证码 / 第二步重置密码） -->
      <el-form :model="form" :rules="rules" ref="resetForm" class="auth-form stagger" @submit.prevent="onSubmit">
        <div class="field">
          <label class="field-label" for="reset-email">邮箱</label>
          <div class="email-row">
            <div class="field-icon" aria-hidden="true">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
                <rect x="2" y="4" width="20" height="16" rx="2" />
                <path d="m2 7 10 6 10-6" />
              </svg>
            </div>
            <input
              id="reset-email"
              v-model="form.email"
              class="input"
              type="email"
              :disabled="codeSent"
              placeholder="name@example.com"
            />
            <button
              type="button"
              class="btn btn-outline code-btn"
              :disabled="sendingCode || countdown > 0 || codeSent"
              @click="onSendCode"
            >
              {{ sendingCode ? '发送中…' : countdown > 0 ? `${countdown}s 后重发` : '获取验证码' }}
            </button>
          </div>
        </div>

        <template v-if="codeSent">
          <div class="field">
            <label class="field-label" for="reset-code">邮箱验证码</label>
            <div class="field-icon" aria-hidden="true">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
                <path d="M4 4h16v16H4z" />
                <path d="M8 9h8M8 13h5" />
              </svg>
            </div>
            <input
              id="reset-code"
              v-model="form.code"
              class="input"
              type="text"
              inputmode="numeric"
              maxlength="6"
              placeholder="6 位验证码"
            />
          </div>

          <div class="field">
            <label class="field-label" for="reset-password">新密码</label>
            <div class="field-icon" aria-hidden="true">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
                <rect x="3" y="11" width="18" height="11" rx="2" />
                <path d="M7 11V7a5 5 0 0 1 10 0v4" />
              </svg>
            </div>
            <div class="input-affix">
              <input
                id="reset-password"
                v-model="form.password"
                class="input"
                :type="showPassword ? 'text' : 'password'"
                placeholder="至少 6 位"
              />
              <button
                type="button"
                class="affix-btn"
                :aria-label="showPassword ? '隐藏密码' : '显示密码'"
                @click="showPassword = !showPassword"
              >
                <svg v-if="!showPassword" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
                  <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z" />
                  <circle cx="12" cy="12" r="3" />
                </svg>
                <svg v-else viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
                  <path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24" />
                  <line x1="1" y1="1" x2="23" y2="23" />
                </svg>
              </button>
            </div>
          </div>

          <div class="field">
            <label class="field-label" for="reset-confirm">确认新密码</label>
            <div class="field-icon" aria-hidden="true">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
                <path d="M12 2 4 6v6c0 5 3.4 8.6 8 10 4.6-1.4 8-5 8-10V6z" />
                <path d="m9 12 2 2 4-4" />
              </svg>
            </div>
            <input
              id="reset-confirm"
              v-model="form.confirm"
              class="input"
              type="password"
              placeholder="再次输入新密码"
            />
          </div>
        </template>

        <button
          v-if="!codeSent"
          type="submit"
          class="btn btn-primary btn-block"
          :disabled="sendingCode"
        >
          {{ sendingCode ? '发送中…' : '发送验证码' }}
        </button>
        <button
          v-else
          type="submit"
          class="btn btn-primary btn-block"
          :disabled="loading"
        >
          {{ loading ? '重置中…' : '重置密码' }}
        </button>
      </el-form>

      <p class="auth-foot">
        <router-link to="/login" class="text-link">返回登录</router-link>
        <span class="auth-sep">·</span>
        <router-link to="/register" class="text-link">立即注册</router-link>
      </p>
    </div>
  </AuthShell>
</template>

<script setup>
import { reactive, ref, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import auth from '../api/auth'
import AuthShell from '../components/AuthShell.vue'
import BrandLogo from '../components/BrandLogo.vue'

const router = useRouter()
const resetForm = ref(null)
const loading = ref(false)
const sendingCode = ref(false)
const showPassword = ref(false)
/** 验证码是否已发出：决定展示"填邮箱"还是"填验证码+新密码" */
const codeSent = ref(false)
const countdown = ref(0)
let countdownTimer = null

const form = reactive({ email: '', code: '', password: '', confirm: '' })

const rules = {
  email: [
    { required: true, message: '请输入邮箱地址', trigger: 'blur' },
    { type: 'email', message: '请输入有效的邮箱地址', trigger: 'blur' }
  ],
  code: [{ required: true, message: '请输入验证码', trigger: 'blur' }],
  password: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, message: '密码长度不能少于 6 位', trigger: 'blur' }
  ],
  confirm: [{ required: true, message: '请再次输入新密码', trigger: 'blur' }]
}

const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

function startCountdown(seconds = 60) {
  countdown.value = seconds
  clearInterval(countdownTimer)
  countdownTimer = setInterval(() => {
    countdown.value -= 1
    if (countdown.value <= 0) {
      clearInterval(countdownTimer)
      countdownTimer = null
    }
  }, 1000)
}

/**
 * 第一步：请求发送重置验证码。
 *
 * 后端无论邮箱是否注册都回同一句话（防账号枚举），所以这里也只提示
 * 统一话术，不能写成"邮箱不存在"或"邮箱已注册"。
 */
async function onSendCode() {
  if (sendingCode.value || countdown.value > 0) return
  if (!emailRegex.test(form.email)) {
    ElMessage.error('请输入正确的邮箱格式')
    return
  }
  sendingCode.value = true
  try {
    await auth.forgotPassword({ email: form.email })
    ElMessage.success('如果该邮箱已注册，验证码已发送，请查收')
    codeSent.value = true
    startCountdown(60)
  } catch (err) {
    ElMessage.error(err.message || '验证码发送失败，请稍后重试')
  } finally {
    sendingCode.value = false
  }
}

/** 表单 submit（回车）统一入口：按当前步骤分发到发验证码或重置密码 */
function onSubmit() {
  if (codeSent.value) {
    onReset()
  } else {
    onSendCode()
  }
}

/** 第二步：校验验证码并写入新密码 */
function onReset() {
  // 请求进行中再按回车不重复提交
  if (loading.value) return
  // 同步置位：校验回调是异步的，若等到回调里再置 loading，快速连按两次回车会发两次请求
  loading.value = true
  resetForm.value.validate((valid) => {
    if (!valid) {
      loading.value = false
      return
    }
    if (form.password !== form.confirm) {
      ElMessage.error('两次输入的密码不一致')
      loading.value = false
      return
    }
    auth
      .resetPassword({
        email: form.email,
        code: form.code,
        newPassword: form.password
      })
      .then(() => {
        ElMessage.success('密码已重置，请用新密码登录')
        setTimeout(() => router.push({ path: '/login' }), 1500)
      })
      .catch((err) => {
        ElMessage.error(err.message || '重置失败，请稍后重试')
      })
      .finally(() => {
        loading.value = false
      })
  })
}

onUnmounted(() => {
  clearInterval(countdownTimer)
})
</script>

<style scoped>
.auth-head__logo {
  margin-bottom: var(--space-4);
}

/* 邮箱行：图标 + 输入框 + 获取验证码按钮（与注册页保持一致） */
.email-row {
  position: relative;
  display: flex;
  gap: var(--space-3);
  align-items: stretch;
}
.email-row .input { flex: 1; min-width: 0; }
.email-row .code-btn {
  flex-shrink: 0;
  min-width: 118px;
  border-radius: var(--radius);
  white-space: nowrap;
  font-size: var(--text-sm);
}
.email-row .code-btn:disabled { opacity: 0.55; cursor: not-allowed; }
</style>
