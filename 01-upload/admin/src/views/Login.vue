<template>
  <div class="login-page">
    <div class="login-bg"></div>
    <div class="login-card">
      <div class="login-header">
        <el-icon :size="40" color="#38bdf8"><Lightning /></el-icon>
        <h2>能耗翻译官</h2>
        <p>运营管理后台</p>
      </div>
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        @keyup.enter="handleLogin"
        size="large"
      >
        <el-form-item prop="username">
          <el-input
            v-model="form.username"
            placeholder="用户名"
            :prefix-icon="User"
            autocomplete="off"
          />
        </el-form-item>
        <el-form-item prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="密码"
            :prefix-icon="Lock"
            show-password
          />
        </el-form-item>
        <el-form-item>
          <el-button
            type="primary"
            :loading="auth.loading"
            @click="handleLogin"
            class="login-btn"
          >
            {{ auth.loading ? '登录中...' : '登 录' }}
          </el-button>
        </el-form-item>
        <p v-if="auth.error" class="login-error">{{ auth.error }}</p>
      </el-form>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { User, Lock } from '@element-plus/icons-vue'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const formRef = ref(null)

const form = reactive({
  username: '',
  password: '',
})

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

const handleLogin = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    const success = await auth.login(form.username, form.password)
    if (success) {
      const redirect = route.query.redirect || '/dashboard'
      router.push(redirect)
    }
  })
}
</script>

<style scoped>
.login-page {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  position: relative;
  overflow: hidden;
}

.login-bg {
  position: absolute;
  inset: 0;
  background:
    radial-gradient(ellipse at 20% 50%, rgba(56, 189, 248, 0.08) 0%, transparent 60%),
    radial-gradient(ellipse at 80% 20%, rgba(167, 139, 250, 0.06) 0%, transparent 60%),
    radial-gradient(ellipse at 50% 80%, rgba(74, 222, 128, 0.05) 0%, transparent 60%);
}

.login-card {
  position: relative;
  width: 400px;
  padding: 48px 40px;
  background: var(--bg-secondary);
  border: 1px solid var(--border);
  border-radius: 16px;
  z-index: 1;
}

.login-header {
  text-align: center;
  margin-bottom: 36px;
}

.login-header h2 {
  margin: 12px 0 4px;
  font-size: 22px;
  color: var(--text-primary);
}

.login-header p {
  color: var(--text-secondary);
  font-size: 13px;
}

.login-btn {
  width: 100%;
  height: 44px;
  font-size: 15px;
}

.login-error {
  color: var(--danger);
  text-align: center;
  font-size: 13px;
  margin-top: -8px;
}
</style>
