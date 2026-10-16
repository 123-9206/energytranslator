import { createRouter, createWebHashHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/Login.vue'),
    meta: { title: '登录' },
  },
  {
    path: '/',
    component: () => import('../components/Layout.vue'),
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('../views/Dashboard.vue'),
        meta: { title: '运营总览', icon: 'Odometer' },
      },
      {
        path: 'users',
        name: 'Users',
        component: () => import('../views/Users.vue'),
        meta: { title: '用户管理', icon: 'User' },
      },
      {
        path: 'enterprise-review',
        name: 'EnterpriseReview',
        component: () => import('../views/EnterpriseReview.vue'),
        meta: { title: '企业审核', icon: 'Stamp' },
      },
      {
        path: 'bill-check',
        name: 'BillCheck',
        component: () => import('../views/BillCheck.vue'),
        meta: { title: '电费体检复核', icon: 'DocumentChecked' },
      },
      {
        path: 'devices',
        name: 'Devices',
        component: () => import('../views/Devices.vue'),
        meta: { title: '设备管理', icon: 'Monitor' },
      },
      {
        path: 'energy',
        name: 'Energy',
        component: () => import('../views/Energy.vue'),
        meta: { title: '能耗分析', icon: 'TrendCharts' },
      },
      {
        path: 'alerts',
        name: 'Alerts',
        component: () => import('../views/Alerts.vue'),
        meta: { title: '告警中心', icon: 'Bell' },
      },
      {
        path: 'control',
        name: 'Control',
        component: () => import('../views/Control.vue'),
        meta: { title: '设备控制', icon: 'Switch' },
      },
      {
        path: 'carbon',
        name: 'Carbon',
        component: () => import('../views/Carbon.vue'),
        meta: { title: '碳排放', icon: 'Sunny' },
      },
      {
        path: 'green-power',
        name: 'GreenPower',
        component: () => import('../views/GreenPower.vue'),
        meta: { title: '绿电台账', icon: 'WindPower' },
      },
      {
        path: 'policies',
        name: 'PolicyCenter',
        component: () => import('../views/PolicyCenter.vue'),
        meta: { title: '政策中心', icon: 'Stamp' },
      },
      {
        path: 'vpp-settlements',
        name: 'VppSettlements',
        component: () => import('../views/VppSettlements.vue'),
        meta: { title: 'VPP结算', icon: 'Money' },
      },
      {
        path: 'circuit-safety',
        name: 'CircuitSafety',
        component: () => import('../views/CircuitSafety.vue'),
        meta: { title: '电路安全', icon: 'Warning' },
      },
      {
        path: 'diagnosis',
        name: 'Diagnosis',
        component: () => import('../views/Diagnosis.vue'),
        meta: { title: 'AI诊断', icon: 'DataAnalysis' },
      },
      {
        path: 'wallet',
        name: 'Wallet',
        component: () => import('../views/Wallet.vue'),
        meta: { title: '老板钱包账', icon: 'Wallet' },
      },
      {
        path: 'carbon-asset',
        name: 'CarbonAsset',
        component: () => import('../views/CarbonAsset.vue'),
        meta: { title: '碳资产', icon: 'Coin' },
      },
      {
        path: 'partners',
        name: 'Partners',
        component: () => import('../views/Partners.vue'),
        meta: { title: '合作售电分润', icon: 'Connection' },
      },
      {
        path: 'solution-matcher',
        name: 'SolutionMatcher',
        component: () => import('../views/SolutionMatcher.vue'),
        meta: { title: '方案匹配器', icon: 'MagicStick' },
      },
      {
        path: 'system',
        name: 'SystemHealth',
        component: () => import('../views/SystemHealth.vue'),
        meta: { title: '系统健康', icon: 'Setting' },
      },
      {
        path: 'audit',
        name: 'AuditLog',
        component: () => import('../views/AuditLog.vue'),
        meta: { title: '审计日志', icon: 'Document' },
      },
      {
        path: 'settings',
        name: 'Settings',
        component: () => import('../views/Settings.vue'),
        meta: { title: '系统设置', icon: 'Tools' },
      },
    ],
  },
]

const router = createRouter({
  history: createWebHashHistory(),
  routes,
})

router.beforeEach((to, from, next) => {
  document.title = to.meta.title ? `${to.meta.title} - 能耗翻译官` : '能耗翻译官'
  const auth = useAuthStore()
  if (to.name !== 'Login' && !auth.token) {
    next({ name: 'Login', query: { redirect: to.fullPath } })
  } else {
    next()
  }
})

export default router
