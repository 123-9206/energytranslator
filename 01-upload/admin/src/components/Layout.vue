<template>
  <div class="layout">
    <!-- Sidebar -->
    <aside class="sidebar">
      <div class="logo" @click="$router.push('/dashboard')">
        <el-icon :size="24"><Lightning /></el-icon>
        <span>能耗翻译官</span>
      </div>
      <div class="nav-label">运营管理</div>
      <el-menu
        :default-active="activeMenu"
        router
        class="nav-menu"
      >
        <el-menu-item index="/dashboard">
          <el-icon><Odometer /></el-icon>
          <span>运营总览</span>
        </el-menu-item>
        <el-menu-item index="/users">
          <el-icon><User /></el-icon>
          <span>用户管理</span>
        </el-menu-item>
        <el-menu-item index="/enterprise-review">
          <el-icon><Stamp /></el-icon>
          <span>企业审核</span>
        </el-menu-item>
        <el-menu-item index="/bill-check">
          <el-icon><DocumentChecked /></el-icon>
          <span>电费体检复核</span>
        </el-menu-item>
        <el-menu-item index="/devices">
          <el-icon><Monitor /></el-icon>
          <span>设备管理</span>
        </el-menu-item>
        <el-menu-item index="/energy">
          <el-icon><TrendCharts /></el-icon>
          <span>能耗分析</span>
        </el-menu-item>
        <el-menu-item index="/alerts">
          <el-icon><Bell /></el-icon>
          <span>告警中心</span>
        </el-menu-item>
        <el-menu-item index="/control">
          <el-icon><Switch /></el-icon>
          <span>设备控制</span>
        </el-menu-item>
        <el-menu-item index="/carbon">
          <el-icon><Sunny /></el-icon>
          <span>碳排放</span>
        </el-menu-item>
        <el-menu-item index="/green-power">
          <el-icon><WindPower /></el-icon>
          <span>绿电台账</span>
        </el-menu-item>
        <el-menu-item index="/policies">
          <el-icon><Stamp /></el-icon>
          <span>政策中心</span>
        </el-menu-item>
        <el-menu-item index="/vpp-settlements">
          <el-icon><Money /></el-icon>
          <span>VPP结算</span>
        </el-menu-item>
        <el-menu-item index="/circuit-safety">
          <el-icon><Warning /></el-icon>
          <span>电路安全</span>
        </el-menu-item>
        <el-menu-item index="/diagnosis">
          <el-icon><DataAnalysis /></el-icon>
          <span>AI诊断</span>
        </el-menu-item>
        <el-menu-item index="/wallet">
          <el-icon><Wallet /></el-icon>
          <span>老板钱包账</span>
        </el-menu-item>
        <el-menu-item index="/carbon-asset">
          <el-icon><Coin /></el-icon>
          <span>碳资产</span>
        </el-menu-item>
        <el-menu-item index="/partners">
          <el-icon><Connection /></el-icon>
          <span>合作售电分润</span>
        </el-menu-item>
        <el-menu-item index="/solution-matcher">
          <el-icon><MagicStick /></el-icon>
          <span>方案匹配器</span>
        </el-menu-item>
      </el-menu>
      <div class="nav-label">系统管理</div>
      <el-menu
        :default-active="activeMenu"
        router
        class="nav-menu"
      >
        <el-menu-item index="/system">
          <el-icon><Setting /></el-icon>
          <span>系统健康</span>
        </el-menu-item>
        <el-menu-item index="/audit">
          <el-icon><Document /></el-icon>
          <span>审计日志</span>
        </el-menu-item>
        <el-menu-item index="/settings">
          <el-icon><Tools /></el-icon>
          <span>系统设置</span>
        </el-menu-item>
      </el-menu>
    </aside>

    <!-- Main content -->
    <div class="main">
      <header class="topbar">
        <div class="topbar-left">
          <el-breadcrumb separator="/">
            <el-breadcrumb-item :to="{ path: '/dashboard' }">首页</el-breadcrumb-item>
            <el-breadcrumb-item>{{ currentTitle }}</el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <div class="topbar-right">
          <el-tag type="success" size="small" effect="dark">系统运行中</el-tag>
          <span class="server-time">{{ currentTime }}</span>
          <el-dropdown trigger="click">
            <span class="user-info">
              <el-icon :size="18"><Avatar /></el-icon>
              <span>{{ auth.username }}</span>
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="$router.push('/settings')">
                  <el-icon><Tools /></el-icon> 系统设置
                </el-dropdown-item>
                <el-dropdown-item divided @click="handleLogout">
                  <el-icon><SwitchButton /></el-icon> 退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </header>
      <div class="content">
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, ref, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const activeMenu = computed(() => route.path)
const currentTitle = computed(() => route.meta?.title || '')

const currentTime = ref('')
let timer = null

const updateTime = () => {
  const now = new Date()
  currentTime.value = now.toLocaleString('zh-CN', {
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', second: '2-digit',
  })
}

onMounted(() => { updateTime(); timer = setInterval(updateTime, 1000) })
onUnmounted(() => { clearInterval(timer) })

const handleLogout = () => {
  auth.logout()
  router.push('/login')
}
</script>

<style scoped>
.layout {
  display: flex;
  height: 100vh;
  overflow: hidden;
}

.sidebar {
  width: 220px;
  background: var(--bg-secondary);
  border-right: 1px solid var(--border);
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  overflow-y: auto;
}

.logo {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 20px 20px 16px;
  font-size: 18px;
  font-weight: 700;
  color: var(--accent);
  cursor: pointer;
  border-bottom: 1px solid var(--border);
  margin-bottom: 8px;
}

.nav-label {
  padding: 16px 20px 4px;
  font-size: 11px;
  text-transform: uppercase;
  letter-spacing: 1px;
  color: var(--text-secondary);
  opacity: 0.5;
}

.nav-menu {
  background: transparent !important;
  border: none !important;
}

.main {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  height: 56px;
  background: var(--bg-secondary);
  border-bottom: 1px solid var(--border);
  flex-shrink: 0;
}

.topbar-right {
  display: flex;
  align-items: center;
  gap: 16px;
}

.server-time {
  font-size: 12px;
  color: var(--text-secondary);
  font-family: monospace;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  color: var(--text-primary);
  font-size: 13px;
  padding: 4px 8px;
  border-radius: 6px;
}

.user-info:hover {
  background: var(--bg-tertiary);
}

.content {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
}

.fade-enter-active, .fade-leave-active {
  transition: opacity 0.15s ease;
}
.fade-enter-from, .fade-leave-to {
  opacity: 0;
}
</style>
