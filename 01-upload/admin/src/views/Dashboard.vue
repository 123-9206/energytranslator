<template>
  <div class="cockpit">
    <!-- Particle background -->
    <canvas ref="particleRef" class="particles-canvas"></canvas>

    <div class="cockpit-inner">
      <!-- ===== Top bar ===== -->
      <div class="topbar">
        <div class="logo">
          <div class="logo-mark">⚡</div>
          <b>能耗翻译官</b>
          <small>ENTERGY TRANSLATOR · 企业能源驾驶舱</small>
        </div>
        <div class="top-mid">
          <span class="live-status" :class="{ ok: collection.collecting }">
            <span class="dot"></span>
            {{ collection.collecting ? '采集正常 · MQTT 在线' : '采集未连接' }}
          </span>
          <span>设备 {{ collection.deviceTotal }} 台 · 在线 {{ collection.deviceOnline }}</span>
          <span>当前时段 <b :style="{ color: periodColor }">{{ periodText }}</b></span>
        </div>
        <div class="top-right">
          <span class="alert-badge" v-if="activeAlarmCount > 0">⚠ 活跃告警 {{ activeAlarmCount }}</span>
          <span class="clock">{{ clockText }}</span>
        </div>
      </div>

      <!-- ===== Main: three columns ===== -->
      <div class="main">
        <!-- Left column -->
        <div class="col left-col">
          <div class="panel">
            <div class="panel-head">
              <span class="panel-title"><i class="bar"></i>设备总览</span>
              <span class="panel-extra">{{ collection.deviceTotal }} 台 · {{ collection.deviceOnline }} 在线</span>
            </div>
            <div class="device-list">
              <div class="device-item" v-for="d in deviceList" :key="d.id || d.name">
                <span class="device-status" :class="d.statusClass"></span>
                <span class="device-name">{{ d.name }}</span>
                <span class="device-power">{{ d.power }}</span>
              </div>
            </div>
          </div>
          <div class="panel flex-1">
            <div class="panel-head">
              <span class="panel-title"><i class="bar"></i>实时告警</span>
              <span class="panel-extra">查看全部</span>
            </div>
            <div class="alarm-list" v-if="alarmList.length">
              <div class="alarm-item" v-for="a in alarmList" :key="a.id || a.message">
                <span class="alarm-level" :class="a.levelClass">{{ a.levelText }}</span>
                <div class="alarm-content">
                  <div class="alarm-text">{{ a.message }}</div>
                  <div class="alarm-time">{{ a.timeText }}</div>
                </div>
              </div>
            </div>
            <el-empty v-else description="暂无活跃告警" :image-size="50" />
          </div>
        </div>

        <!-- Center column -->
        <div class="center-col">
          <!-- KPI cards -->
          <div class="kpi-row">
            <div class="kpi-card" v-for="k in kpis" :key="k.key" :style="{ '--glow': k.color }">
              <div class="kpi-label">
                {{ k.label }}
                <span class="kpi-tag" :style="{ color: k.color, borderColor: k.color + '55' }">{{ k.tag }}</span>
              </div>
              <div class="kpi-value">
                <count-up :target="k.value" :decimals="k.decimals" :color="k.color" />
                <small class="kpi-unit" v-if="k.unit">{{ k.unit }}</small>
              </div>
              <div class="kpi-trend" v-if="k.trend">{{ k.trend }}</div>
            </div>
          </div>

          <!-- Power chart -->
          <div class="panel flex-1">
            <div class="panel-head">
              <span class="panel-title"><i class="bar"></i>实时功率曲线</span>
              <span class="panel-extra">24 小时 · kW · 峰值 {{ powerMax }} kW</span>
            </div>
            <div class="chart-area" ref="powerChartRef"></div>
          </div>

          <!-- Energy flow -->
          <div class="panel flow-panel">
            <div class="panel-head">
              <span class="panel-title"><i class="bar"></i>能源流向 · 源网荷储</span>
              <span class="panel-extra">{{ flowSummary }}</span>
            </div>
            <div class="flow-svg-box">
              <svg viewBox="0 0 760 170" preserveAspectRatio="xMidYMid meet">
                <defs>
                  <marker id="arr-flow" viewBox="0 0 8 8" refX="7" refY="4" markerWidth="7" markerHeight="7" orient="auto">
                    <path d="M0,0 L8,4 L0,8 Z" fill="rgba(0,229,255,.6)" />
                  </marker>
                  <marker id="arrg-flow" viewBox="0 0 8 8" refX="7" refY="4" markerWidth="7" markerHeight="7" orient="auto">
                    <path d="M0,0 L8,4 L0,8 Z" fill="rgba(0,255,157,.6)" />
                  </marker>
                </defs>
                <!-- Flow lines -->
                <path class="flowline" d="M 90 40 C 220 40, 300 80, 420 85" marker-end="url(#arr-flow)" />
                <path class="flowline g" d="M 90 85 C 220 85, 300 85, 420 85" marker-end="url(#arrg-flow)" />
                <path class="flowline o" d="M 90 130 C 220 130, 300 90, 420 85" marker-end="url(#arr-flow)" />
                <!-- Source nodes -->
                <rect class="src-node" x="12" y="22" width="78" height="36" rx="8" />
                <text class="node-label" x="51" y="37">市电电网</text>
                <text class="node-val" x="51" y="52">{{ flowData.grid }} kW</text>

                <rect class="src-node t2" x="12" y="67" width="78" height="36" rx="8" />
                <text class="node-label" x="51" y="82">分布式光伏</text>
                <text class="node-val" x="51" y="97">{{ flowData.pv }} kW</text>

                <rect class="src-node t3" x="12" y="112" width="78" height="36" rx="8" />
                <text class="node-label" x="51" y="127">储能系统</text>
                <text class="node-val" x="51" y="142">{{ flowData.storage }}</text>

                <!-- Busbar -->
                <rect class="src-node busbar" x="420" y="67" width="90" height="36" rx="8" />
                <text class="node-label" x="465" y="82">能源总线</text>
                <text class="node-val" x="465" y="97">{{ flowData.total }} kW</text>

                <!-- To load -->
                <path class="flowline g" d="M 510 85 C 600 85, 620 85, 670 85" marker-end="url(#arrg-flow)" />

                <!-- Load node -->
                <rect class="src-node load" x="672" y="67" width="86" height="36" rx="8" />
                <text class="node-label" x="715" y="82">企业负荷</text>
                <text class="node-val" x="715" y="97">{{ flowData.total }} kW</text>

                <!-- Distribution -->
                <text class="node-label sub" x="450" y="130">空调 32% · 产线 41% · 空压 14% · 其他 13%</text>
              </svg>
            </div>
          </div>
        </div>

        <!-- Right column -->
        <div class="col right-col">
          <!-- AI Score -->
          <div class="panel">
            <div class="panel-head">
              <span class="panel-title"><i class="bar"></i>AI 能效综合评分</span>
              <span class="panel-extra">{{ scoreLevel }}</span>
            </div>
            <div class="score-chart-area" ref="scoreChartRef"></div>
          </div>

          <!-- VPP Gauge -->
          <div class="panel">
            <div class="panel-head">
              <span class="panel-title"><i class="bar"></i>VPP 虚拟电厂</span>
              <span class="panel-extra">详情</span>
            </div>
            <div class="vpp-gauge-area" ref="vppGaugeRef"></div>
            <div class="vpp-info">{{ vppInfoText }}</div>
          </div>

          <!-- Policy recommendations -->
          <div class="panel flex-1">
            <div class="panel-head">
              <span class="panel-title"><i class="bar"></i>政策匹配 · AI 推荐</span>
              <span class="panel-extra">{{ policies.length }}/{{ policies.length }} 可申报</span>
            </div>
            <div class="policy-list" v-if="policies.length">
              <div class="policy-item" v-for="(p, i) in policies" :key="i" :style="{ background: p.bg, borderColor: p.border }">
                <b :style="{ color: p.color }">{{ p.name }}</b> · 预估 {{ p.estimate }}
                <div class="policy-desc">{{ p.desc }}</div>
              </div>
            </div>
            <el-empty v-else description="暂无政策数据" :image-size="50" />
          </div>
        </div>
      </div>

      <!-- ===== Bottom: four panels ===== -->
      <div class="bottom">
        <div class="panel">
          <div class="panel-head"><span class="panel-title"><i class="bar"></i>设备能耗排行</span></div>
          <div class="mini-chart" ref="rankChartRef"></div>
        </div>
        <div class="panel">
          <div class="panel-head"><span class="panel-title"><i class="bar"></i>近 7 天能耗趋势</span></div>
          <div class="mini-chart" ref="weekChartRef"></div>
        </div>
        <div class="panel">
          <div class="panel-head"><span class="panel-title"><i class="bar"></i>碳排放趋势</span></div>
          <div class="mini-chart" ref="carbonChartRef"></div>
        </div>
        <div class="panel">
          <div class="panel-head">
            <span class="panel-title"><i class="bar"></i>AI 节能建议</span>
            <span class="panel-extra">{{ topRecs.length }} 条</span>
          </div>
          <div class="suggestion-list" v-if="topRecs.length">
            <div class="suggestion-item" v-for="(r, i) in topRecs" :key="i">
              <span class="suggestion-tag" :class="r.severity">{{ r.tagText }}</span>
              <span class="suggestion-text">{{ r.title }}{{ r.description ? ' · ' + r.description : '' }}</span>
            </div>
          </div>
          <el-empty v-else description="暂无AI建议" :image-size="50" />
        </div>
      </div>

      <!-- Footer -->
      <div class="cockpit-foot">能耗翻译官 v2.0 · 苏ICP备2026033411号-1 · 实时数据驾驶舱</div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onUnmounted, computed, nextTick, h, watch } from 'vue'
import * as echarts from 'echarts'
import dayjs from 'dayjs'
import { deviceAPI } from '../api/devices'
import { dashboardAPI } from '../api/dashboard'
import { adminAPI } from '../api/admin'
import { vppSettlementAPI } from '../api/vppSettlements'
import { alarmAPI } from '../api/alarms'
import { policyAPI } from '../api/policy'
import { vppAPI } from '../api/vpp'
import { carbonAPI } from '../api/carbon'

/* ================= 工具 ================= */
const round = (n, d = 1) => {
  if (n == null || isNaN(n)) return 0
  return Number(Number(n).toFixed(d))
}
const fmt = (n, d = 0) => {
  if (n == null || isNaN(n)) return '--'
  return Number(n).toLocaleString('zh-CN', { maximumFractionDigits: d })
}
const getValue = (result) => result?.status === 'fulfilled' ? result.value : null

/* ================= CountUp 组件 ================= */
const CountUp = {
  props: { target: Number, decimals: { type: Number, default: 0 }, color: String },
  setup(props) {
    const display = ref(0)
    let raf = null
    const run = () => {
      if (raf) cancelAnimationFrame(raf)
      const step = () => {
        const diff = (props.target || 0) - display.value
        if (Math.abs(diff) < 0.01) { display.value = props.target || 0; return }
        display.value += diff * 0.18
        raf = requestAnimationFrame(step)
      }
      step()
    }
    onMounted(run)
    watch(() => props.target, run)
    onUnmounted(() => { if (raf) cancelAnimationFrame(raf) })
    return () => h('span', { style: { color: props.color || '#00e5ff', textShadow: `0 0 18px ${props.color || '#00e5ff'}66` } },
      fmt(display.value, props.decimals))
  }
}

/* ================= 数据状态 ================= */
const kpis = reactive([
  { key: 'energy', label: '今日总能耗', tag: 'kWh', value: 0, decimals: 1, unit: '', color: '#00e5ff', trend: '较昨日 ↓ 3.2% · 目标进度 68%' },
  { key: 'cost', label: '今日电费', tag: 'CNY', value: 0, decimals: 0, unit: '元', color: '#00ff9d', trend: '尖峰占比 42% · 可优化 ¥8,600' },
  { key: 'carbon', label: '今日碳排放', tag: 'kg', value: 0, decimals: 0, unit: 'kg', color: '#8b5cf6', trend: '绿电抵扣 1,850 kg · ↓ 6.1%' },
  { key: 'vpp', label: 'VPP 累计收益', tag: 'CNY', value: 0, decimals: 0, unit: '元', color: '#ffb020', trend: '本月响应 2 次 · ↑ ¥4,500' },
])

const collection = reactive({
  deviceTotal: 0, deviceOnline: 0, deviceOffline: 0, onlineRate: 0,
  protocols: {}, lastCollectTime: null, collecting: false
})
const scoreLevel = ref('--')
const topRecs = ref([])
const alarmList = ref([])
const activeAlarmCount = ref(0)
const policies = ref([])
const powerMax = ref(0)
const vppInfoText = ref('可调容量 -- kW · 本月响应 ¥--')
const flowData = reactive({ grid: 412, pv: 152, storage: '98 kW 放电', total: 662 })
const flowSummary = ref('市电 62% · 光伏 23% · 储能 15%')
const carbonTrendData = ref([])

/* 能流节点数据 */
const deviceList = ref([])

/* ================= 时钟 ================= */
const clockText = ref('--:--:--')
let clockTimer = null
const updateClock = () => {
  const d = new Date(), p = n => String(n).padStart(2, '0')
  clockText.value = `${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
}

/* ================= 时段 ================= */
const periodText = computed(() => {
  const h = new Date().getHours()
  if (h >= 8 && h < 12) return '峰'
  if (h >= 12 && h < 17) return '平'
  if (h >= 17 && h < 21) return '峰'
  return '谷'
})
const periodColor = computed(() => {
  const p = periodText.value
  if (p === '峰') return '#ffb020'
  if (p === '平') return '#00e5ff'
  return '#00ff9d'
})

/* ================= 粒子背景 ================= */
const particleRef = ref(null)
let particleRaf = null
const initParticles = () => {
  const cv = particleRef.value
  if (!cv) return
  const ctx = cv.getContext('2d')
  let ps = [], W, H
  const rs = () => {
    W = cv.width = cv.offsetWidth
    H = cv.height = cv.offsetHeight
    ps = Array.from({ length: 46 }, () => ({
      x: Math.random() * W, y: Math.random() * H,
      r: Math.random() * 1.4 + .4,
      vx: (Math.random() - .5) * .25, vy: (Math.random() - .5) * .25,
      c: Math.random() > .6 ? [0, 255, 157] : [0, 229, 255]
    }))
  }
  rs()
  const tick = () => {
    ctx.clearRect(0, 0, W, H)
    ps.forEach(p => {
      p.x += p.vx; p.y += p.vy
      if (p.x < 0 || p.x > W) p.vx *= -1
      if (p.y < 0 || p.y > H) p.vy *= -1
      ctx.beginPath()
      ctx.arc(p.x, p.y, p.r, 0, 7)
      ctx.fillStyle = `rgba(${p.c},.45)`
      ctx.fill()
    })
    particleRaf = requestAnimationFrame(tick)
  }
  tick()
  const ro = new ResizeObserver(rs)
  ro.observe(cv)
}

/* ================= 图表引用 ================= */
const powerChartRef = ref(null)
const scoreChartRef = ref(null)
const vppGaugeRef = ref(null)
const rankChartRef = ref(null)
const weekChartRef = ref(null)
const carbonChartRef = ref(null)
let charts = {}

/* ================= ECharts 通用 ================= */
const AXIS = {
  axisLine: { lineStyle: { color: 'rgba(120,170,220,.25)' } },
  axisLabel: { color: '#5f7392', fontSize: 10 },
  axisTick: { show: false }
}
const SPLIT = { splitLine: { lineStyle: { color: 'rgba(120,170,220,.07)' } } }
const TIP = {
  backgroundColor: 'rgba(10,20,35,.92)',
  borderColor: 'rgba(0,229,255,.3)',
  textStyle: { color: '#cfe0f2', fontSize: 11 },
  borderWidth: 1
}
const GLOW = { shadowColor: 'rgba(0,229,255,.5)', shadowBlur: 12 }

/* ================= 数据加载 ================= */
const loadData = async () => {
  const [devicesRes, dashRes, carbonDashRes, aiRes, vppRes, alarmRes, policyRes, carbonTrendRes] = await Promise.allSettled([
    deviceAPI.list({ size: 1000 }),
    dashboardAPI.getDashboard('today').catch(() => ({})),
    dashboardAPI.getCarbonDashboard('today').catch(() => ({})),
    adminAPI.getAIAnalysis().catch(() => ({})),
    vppSettlementAPI.getAllSettlements({ pageSize: 1000 }).catch(() => ({})),
    alarmAPI.list({ status: 'active', size: 10 }).catch(() => ({})),
    policyAPI.getAvailablePolicies().catch(() => ({})),
    carbonAPI.getTrend({ period: 'month' }).catch(() => ({})),
  ])

  /* ---- 设备 ---- */
  const devRes = getValue(devicesRes) || {}
  const devices = devRes?.data?.content || devRes?.content || []
  const online = devices.filter(d => d.status === 'online').length
  collection.deviceTotal = devices.length
  collection.deviceOnline = online
  collection.deviceOffline = devices.filter(d => d.status === 'offline').length
  collection.onlineRate = devices.length ? round(online * 100 / devices.length, 1) : 0
  collection.protocols = {}
  devices.forEach(d => { collection.protocols[d.protocol] = (collection.protocols[d.protocol] || 0) + 1 })
  collection.collecting = devices.length > 0 && online > 0

  /* 设备列表 */
  deviceList.value = devices.map(d => ({
    id: d.id,
    name: d.name || d.deviceName || '未命名设备',
    statusClass: d.status === 'online' ? 'on' : d.status === 'maintenance' ? 'mt' : 'off',
    power: d.status === 'online' ? `${round(d.currentPower || d.power || 0, 1)} kW` : (d.status === 'maintenance' ? '维护' : '0 kW')
  }))

  /* ---- 今日能耗/电费/碳排 ---- */
  const dashRaw = getValue(dashRes) || {}
  const dash = dashRaw?.data || dashRaw
  kpis[0].value = round(Number(dash.todayUsage) || 0, 1)
  kpis[1].value = round(Number(dash.todayCost) || 0, 0)

  const carbonDashRaw = getValue(carbonDashRes) || {}
  const carbonDash = carbonDashRaw?.data || carbonDashRaw
  kpis[2].value = round(Number(carbonDash.todayCarbon) || 0, 0)

  /* ---- VPP 已结算收益 ---- */
  const vppRaw = getValue(vppRes) || {}
  const vppList = vppRaw?.data?.list || vppRaw?.list || vppRaw?.data?.content || []
  const paidSum = vppList.filter(s => s.status === 'paid')
    .reduce((s, x) => s + (Number(x.summary?.totalAmount) || Number(x.totalAmount) || 0), 0)
  kpis[3].value = round(paidSum, 0)
  vppInfoText.value = `已结算收益 ¥${fmt(paidSum)} · 本月响应 ${vppList.filter(s => s.status === 'paid').length} 次`

  /* ---- 告警 ---- */
  const alarmRaw = getValue(alarmRes) || {}
  const alarmData = alarmRaw?.data?.content || alarmRaw?.data?.list || alarmRaw?.data || []
  const alarmArr = Array.isArray(alarmData) ? alarmData : []
  activeAlarmCount.value = alarmArr.length
  alarmList.value = alarmArr.slice(0, 5).map(a => {
    const sev = (a.severity || a.level || '').toUpperCase()
    const levelClass = sev.includes('HIGH') || sev.includes('CRITICAL') ? 'h' : sev.includes('MEDIUM') ? 'm' : 'l'
    const levelText = levelClass === 'h' ? '高' : levelClass === 'm' ? '中' : '低'
    const message = a.message || a.ruleName || a.description || '告警'
    const triggerInfo = a.triggerValue && a.thresholdValue ? `触发值 ${a.triggerValue} / 阈值 ${a.thresholdValue}` : ''
    return {
      id: a.id,
      levelClass,
      levelText,
      message: message + (triggerInfo ? ' ' + triggerInfo : ''),
      timeText: a.timestamp ? dayjs(a.timestamp).format('MM-DD HH:mm') : '--'
    }
  })

  /* ---- AI 聚合分析 ---- */
  const aiRaw = getValue(aiRes) || {}
  const ai = aiRaw?.data || aiRaw || {}
  const diag = ai.diagnosis || {}
  scoreLevel.value = diag.scoreLevel || '--'
  topRecs.value = (diag.recommendations || []).slice(0, 4).map(r => ({
    ...r,
    tagText: r.severity === 'critical' ? '异常' : r.severity === 'warning' ? '优化' : '建议',
  }))

  const pc = ai.powerCurve || {}
  powerMax.value = pc.maxPower || 0
  collection.lastCollectTime = ai.collection?.lastCollectTime || null

  /* 能流数据 */
  const pcPoints = pc.points || []
  const totalPower = pcPoints.length ? Math.max(...pcPoints.map(p => Number(p.power) || 0)) : 0
  if (totalPower > 0) {
    flowData.grid = round(totalPower * 0.62, 0)
    flowData.pv = round(totalPower * 0.23, 0)
    flowData.storage = `${round(totalPower * 0.15, 0)} kW 放电`
    flowData.total = totalPower
  }

  /* ---- 政策 ---- */
  const policyRaw = getValue(policyRes) || {}
  const policyData = policyRaw?.data?.content || policyRaw?.data?.list || policyRaw?.data || []
  const policyArr = Array.isArray(policyData) ? policyData : []
  const policyColors = [
    { color: '#00ff9d', bg: 'rgba(0,255,157,.07)', border: 'rgba(0,255,157,.25)' },
    { color: '#00e5ff', bg: 'rgba(0,229,255,.06)', border: 'rgba(0,229,255,.22)' },
    { color: '#b7a2ff', bg: 'rgba(139,92,246,.07)', border: 'rgba(139,92,246,.25)' },
    { color: '#ffd166', bg: 'rgba(255,209,102,.07)', border: 'rgba(255,209,102,.22)' },
  ]
  policies.value = policyArr.slice(0, 4).map((p, i) => {
    const c = policyColors[i % policyColors.length]
    const name = p.policyName || p.name || p.title || '政策'
    const estimate = p.estimatedBenefit ? `¥${p.estimatedBenefit}` : p.estimate || '待评估'
    const desc = [p.region, p.department, p.description].filter(Boolean).join(' · ') || '详见政策详情'
    return { name, estimate, desc, ...c }
  })
  /* 如果没有真实政策数据，用演示数据 */
  if (policies.value.length === 0) {
    policies.value = [
      { name: '需求响应补贴', estimate: '¥2.4万/年', desc: '苏州工业园区 · 聚合商 ≤50元/kW · 6/30 截止', ...policyColors[0] },
      { name: '绿电交易奖励', estimate: '¥1.8万/年', desc: '射阳县 · 绿电交易完成率奖励 20万池', ...policyColors[1] },
      { name: '节能诊断补贴', estimate: '¥0.6万', desc: '省级工业节能监察 · 免费上门诊断', ...policyColors[2] },
    ]
  }

  /* ---- 碳排趋势 ---- */
  const carbonTrendRaw = getValue(carbonTrendRes) || {}
  const carbonTrend = carbonTrendRaw?.data?.trend || carbonTrendRaw?.data || []
  carbonTrendData.value = Array.isArray(carbonTrend) ? carbonTrend : []

  await nextTick()
  renderAllCharts(ai)
}

/* ================= 图表渲染 ================= */
const destroyChart = (key) => { if (charts[key]) { charts[key].dispose(); delete charts[key] } }

const renderPowerChart = (ai) => {
  const pc = ai.powerCurve?.points || []
  if (!powerChartRef.value) return
  destroyChart('power')
  charts.power = echarts.init(powerChartRef.value)
  charts.power.setOption({
    tooltip: { trigger: 'axis', ...TIP },
    grid: { left: 44, right: 14, top: 18, bottom: 22 },
    xAxis: { type: 'category', data: pc.map(p => p.hour), ...AXIS, boundaryGap: false },
    yAxis: { type: 'value', ...AXIS, ...SPLIT, splitNumber: 4 },
    series: [{
      type: 'line', data: pc.map(p => p.power), smooth: true, symbol: 'none',
      lineStyle: { width: 2, color: '#00e5ff', shadowColor: 'rgba(0,229,255,.6)', shadowBlur: 10 },
      areaStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: 'rgba(0,229,255,.32)' },
          { offset: 1, color: 'rgba(0,229,255,.02)' },
        ]),
      },
    }],
  })
}

const renderScoreChart = (ai) => {
  const score = ai.diagnosis?.overallScore || 0
  if (!scoreChartRef.value) return
  destroyChart('score')
  charts.score = echarts.init(scoreChartRef.value)
  const color = score >= 75 ? '#00ff9d' : score >= 60 ? '#ffd166' : '#ff5d73'
  charts.score.setOption({
    series: [{
      type: 'pie', radius: ['72%', '88%'], center: ['50%', '50%'], silent: true,
      startAngle: 90, clockwise: true,
      label: { show: false }, labelLine: { show: false },
      data: [
        { value: score, itemStyle: { color, shadowColor: color, shadowBlur: 14 } },
        { value: Math.max(100 - score, 0), itemStyle: { color: 'rgba(148,163,184,.12)' } },
      ],
    }],
    graphic: [
      { type: 'text', left: 'center', top: '36%', style: { text: String(score), fill: color, fontSize: 30, fontWeight: 700, textShadowColor: color, textShadowBlur: 14 } },
      { type: 'text', left: 'center', top: '60%', style: { text: scoreLevel.value, fill: 'rgba(232,241,255,.55)', fontSize: 11 } },
    ],
  })
}

const renderVppGauge = () => {
  if (!vppGaugeRef.value) return
  destroyChart('vpp')
  charts.vpp = echarts.init(vppGaugeRef.value)
  const vppValue = kpis[3].value || 0
  charts.vpp.setOption({
    series: [{
      type: 'gauge', startAngle: 210, endAngle: -30, min: 0, max: Math.max(vppValue * 2, 30000),
      radius: '92%', center: ['50%', '58%'],
      progress: { show: true, width: 10, itemStyle: { color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [{ offset: 0, color: '#00e5ff' }, { offset: 1, color: '#00ff9d' }]) } },
      axisLine: { lineStyle: { width: 10, color: [[1, 'rgba(120,170,220,.12)']] } },
      axisTick: { show: false }, splitLine: { show: false }, axisLabel: { show: false },
      pointer: { show: false }, anchor: { show: false },
      detail: { valueAnimation: true, offsetCenter: [0, 0], fontSize: 20, color: '#7fe7ff', fontFamily: 'Consolas', formatter: `{value}` },
      title: { offsetCenter: [0, 30], fontSize: 10, color: '#5f7392' },
      data: [{ value: vppValue, name: '已结算收益 ¥' }],
    }],
  })
}

const renderRankChart = (ai) => {
  const diag = ai.diagnosis?.deviceDiagnosis || []
  if (!rankChartRef.value) return
  destroyChart('rank')
  charts.rank = echarts.init(rankChartRef.value)
  const names = diag.slice(0, 5).map(d => d.deviceName || d.name || '--').reverse()
  const vals = diag.slice(0, 5).map(d => Number(d.avgDailyEnergy) || 0).reverse()
  charts.rank.setOption({
    grid: { left: 8, right: 58, top: 8, bottom: 8, containLabel: true },
    tooltip: { ...TIP },
    xAxis: { type: 'value', ...AXIS, show: false },
    yAxis: { type: 'category', data: names, ...AXIS, axisLabel: { color: '#9fd4ff', fontSize: 10 } },
    series: [{
      type: 'bar', data: vals, barWidth: 9,
      label: { show: true, position: 'right', color: '#5f7392', fontSize: 9, formatter: '{c}' },
      itemStyle: { borderRadius: [0, 5, 5, 0], color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [{ offset: 0, color: 'rgba(0,229,255,.25)' }, { offset: 1, color: '#00e5ff' }]) },
    }],
  })
}

const renderWeekChart = (ai) => {
  const points = ai.forecast?.points || []
  const actual = points.filter(p => p.type === 'actual')
  if (!weekChartRef.value) return
  destroyChart('week')
  charts.week = echarts.init(weekChartRef.value)
  const labels = actual.length ? actual.map(p => p.label) : ['08-06', '08-07', '08-08', '08-09', '08-10', '08-11', '08-12']
  const data = actual.length ? actual.map(p => Number(p.value) || 0) : [112.3, 108.9, 121.5, 98.4, 115.2, 124.6, 119.3]
  charts.week.setOption({
    grid: { left: 40, right: 10, top: 14, bottom: 20 },
    tooltip: { ...TIP },
    xAxis: { type: 'category', data: labels, ...AXIS },
    yAxis: { type: 'value', ...AXIS, ...SPLIT, splitNumber: 3 },
    series: [{
      type: 'bar', data, barWidth: '46%',
      itemStyle: { borderRadius: [4, 4, 0, 0], color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [{ offset: 0, color: '#3d8bff' }, { offset: 1, color: 'rgba(61,139,255,.15)' }]) },
    }],
  })
}

const renderCarbonChart = () => {
  if (!carbonChartRef.value) return
  destroyChart('carbon')
  charts.carbon = echarts.init(carbonChartRef.value)
  const trend = carbonTrendData.value
  let labels, data
  if (trend.length) {
    labels = trend.map(d => d.month || d.date || d.label || '--')
    data = trend.map(d => Number(d.value) || Number(d.carbon) || 0)
  } else {
    labels = Array.from({ length: 12 }, (_, i) => (i < 9 ? '0' : '') + (i + 1) + '月')
    data = [66, 63, 70, 58, 64, 72, 68, 65, 61, 59, 63, 60].map(v => v * 1000)
  }
  charts.carbon.setOption({
    grid: { left: 36, right: 10, top: 14, bottom: 20 },
    tooltip: { ...TIP },
    xAxis: { type: 'category', data: labels, ...AXIS },
    yAxis: { type: 'value', ...AXIS, ...SPLIT, splitNumber: 3 },
    series: [{
      type: 'line', data, smooth: true, symbol: 'circle', symbolSize: 4,
      lineStyle: { width: 2, color: '#00ff9d', shadowColor: 'rgba(0,255,157,.5)', shadowBlur: 8 },
      itemStyle: { color: '#00ff9d' },
      areaStyle: { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [{ offset: 0, color: 'rgba(0,255,157,.25)' }, { offset: 1, color: 'rgba(0,255,157,.02)' }]) },
    }],
  })
}

const renderAllCharts = (ai) => {
  renderPowerChart(ai)
  renderScoreChart(ai)
  renderVppGauge()
  renderRankChart(ai)
  renderWeekChart(ai)
  renderCarbonChart()
  handleResize()
}

const handleResize = () => Object.values(charts).forEach(c => c?.resize())

/* ================= 生命周期 ================= */
onMounted(() => {
  updateClock()
  clockTimer = setInterval(updateClock, 1000)
  initParticles()
  loadData()
  window.addEventListener('resize', handleResize)
})
onUnmounted(() => {
  if (clockTimer) clearInterval(clockTimer)
  if (particleRaf) cancelAnimationFrame(particleRaf)
  window.removeEventListener('resize', handleResize)
  Object.values(charts).forEach(c => c?.dispose())
})
</script>

<style scoped>
/* ============ 驾驶舱专属配色 ============ */
.cockpit {
  --bg0: #070b14;
  --bg1: #0b1220;
  --cyan: #00e5ff;
  --green: #00ff9d;
  --purple: #8b5cf6;
  --orange: #ffb020;
  --red: #ff4d5e;
  --blue: #3d8bff;
  --ink: #c9d8ea;
  --sub: #5f7392;
  --line: rgba(120, 170, 220, .14);
  --card: rgba(13, 24, 40, .72);
  background:
    radial-gradient(1100px 500px at 20% -10%, rgba(0, 229, 255, .07), transparent 60%),
    radial-gradient(900px 500px at 85% -10%, rgba(139, 92, 246, .08), transparent 60%),
    radial-gradient(700px 400px at 50% 110%, rgba(0, 255, 157, .05), transparent 60%),
    linear-gradient(180deg, var(--bg0), var(--bg1));
  border: 1px solid var(--line);
  border-radius: 14px;
  padding: 10px 14px 10px;
  color: var(--ink);
  min-height: calc(100vh - 100px);
  position: relative;
  overflow: hidden;
}
/* Grid background */
.cockpit::before {
  content: '';
  position: absolute;
  inset: 0;
  pointer-events: none;
  opacity: .5;
  background-image:
    linear-gradient(rgba(90, 150, 220, .05) 1px, transparent 1px),
    linear-gradient(90deg, rgba(90, 150, 220, .05) 1px, transparent 1px);
  background-size: 42px 42px;
}
.particles-canvas {
  position: absolute;
  inset: 0;
  pointer-events: none;
  z-index: 0;
}
.cockpit-inner {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: calc(100vh - 120px);
}

/* ============ Top bar ============ */
.topbar {
  height: 58px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 18px;
  border-radius: 12px;
  position: relative;
  flex-shrink: 0;
  background: linear-gradient(90deg, rgba(0, 229, 255, .05), rgba(139, 92, 246, .04), rgba(0, 255, 157, .05));
  border: 1px solid var(--line);
  margin-bottom: 10px;
}
.logo { display: flex; align-items: center; gap: 10px; }
.logo-mark {
  width: 34px; height: 34px; border-radius: 9px;
  display: grid; place-items: center; font-size: 18px;
  background: linear-gradient(135deg, rgba(0, 229, 255, .18), rgba(0, 255, 157, .14));
  border: 1px solid rgba(0, 229, 255, .4);
  box-shadow: 0 0 14px rgba(0, 229, 255, .25);
}
.logo b { font-size: 18px; letter-spacing: 1px; color: #e8f4ff; }
.logo small { color: var(--sub); margin-left: 8px; font-size: 11px; letter-spacing: 2px; }
.top-mid { display: flex; gap: 26px; font-size: 13px; color: var(--sub); }
.live-status { color: var(--green); display: flex; align-items: center; gap: 6px; }
.live-status .dot {
  width: 8px; height: 8px; border-radius: 50%;
  background: var(--green); box-shadow: 0 0 8px var(--green);
  animation: pulse 1.6s infinite;
}
.live-status:not(.ok) { color: var(--orange); }
.live-status:not(.ok) .dot { background: var(--orange); box-shadow: 0 0 8px var(--orange); }
@keyframes pulse { 0%, 100% { opacity: 1; } 50% { opacity: .35; } }
.top-right { display: flex; align-items: center; gap: 18px; font-size: 13px; }
.clock {
  font-family: Consolas, monospace; font-size: 20px; color: var(--cyan);
  letter-spacing: 1px; text-shadow: 0 0 10px rgba(0, 229, 255, .4);
}
.alert-badge {
  background: rgba(255, 77, 94, .12);
  border: 1px solid rgba(255, 77, 94, .35);
  color: #ff8d99; padding: 4px 12px; border-radius: 14px; font-size: 12px;
}

/* ============ Main three columns ============ */
.main {
  flex: 1; display: flex; gap: 12px; min-height: 0;
}
.col {
  width: 300px;
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-height: 0;
}
.center-col {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 12px;
  min-width: 0;
}
.flex-1 { flex: 1 !important; }

/* ============ Panel ============ */
.panel {
  background: var(--card);
  border: 1px solid var(--line);
  border-radius: 12px;
  padding: 12px 14px;
  position: relative;
  overflow: hidden;
  flex-shrink: 0;
  backdrop-filter: blur(6px);
}
.panel::before {
  content: '';
  position: absolute;
  top: 0; left: 0; right: 0; height: 1px;
  background: linear-gradient(90deg, transparent, rgba(0, 229, 255, .5), transparent);
}
.panel-head {
  display: flex; align-items: center; justify-content: space-between;
  font-size: 13px; color: #9fd4ff; margin-bottom: 8px; letter-spacing: 1px;
}
.panel-title { display: flex; align-items: center; gap: 7px; }
.panel-title .bar {
  width: 3px; height: 13px;
  background: linear-gradient(180deg, var(--cyan), transparent);
  border-radius: 2px; display: inline-block; vertical-align: -2px;
}
.panel-extra { font-size: 11px; color: var(--sub); cursor: pointer; }

/* ============ KPI ============ */
.kpi-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  flex-shrink: 0;
}
.kpi-card {
  background: var(--card);
  border: 1px solid var(--line);
  border-radius: 12px;
  padding: 12px 14px;
  position: relative;
  overflow: hidden;
  transition: transform .2s, border-color .2s;
}
.kpi-card:hover { transform: translateY(-2px); border-color: rgba(0, 229, 255, .3); }
.kpi-card::after {
  content: '';
  position: absolute; right: -24px; top: -24px;
  width: 80px; height: 80px; border-radius: 50%;
  background: radial-gradient(circle, var(--glow, rgba(0, 229, 255, .15)), transparent 70%);
}
.kpi-label {
  font-size: 12px; color: var(--sub);
  display: flex; justify-content: space-between; align-items: center;
}
.kpi-tag {
  font-size: 10px; padding: 1px 7px; border-radius: 999px;
  border: 1px solid; letter-spacing: .5px;
}
.kpi-value {
  font-size: 30px; font-family: Consolas, monospace; font-weight: 700;
  color: #eaf6ff; margin: 4px 0 2px;
  display: flex; align-items: baseline; gap: 3px;
}
.kpi-unit { font-size: 13px; color: var(--sub); font-weight: 400; }
.kpi-trend { font-size: 11px; color: var(--sub); }

/* ============ Device list ============ */
.device-list { display: flex; flex-direction: column; gap: 7px; }
.device-item {
  display: flex; align-items: center; gap: 8px;
  padding: 7px 9px; border-radius: 8px;
  background: rgba(120, 170, 220, .05);
  border: 1px solid transparent;
  transition: border-color .2s;
}
.device-item:hover { border-color: rgba(0, 229, 255, .25); }
.device-status {
  width: 8px; height: 8px; border-radius: 50%; flex-shrink: 0;
}
.device-status.on { background: var(--green); box-shadow: 0 0 6px var(--green); }
.device-status.off { background: var(--sub); }
.device-status.mt { background: var(--orange); box-shadow: 0 0 6px var(--orange); }
.device-name {
  flex: 1; font-size: 12px; color: #cfe0f2;
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}
.device-power { font-family: Consolas, monospace; font-size: 12px; color: var(--cyan); }

/* ============ Alarm list ============ */
.alarm-list { display: flex; flex-direction: column; gap: 0; }
.alarm-item {
  display: flex; gap: 7px; align-items: flex-start;
  padding: 6px 4px;
  border-bottom: 1px dashed rgba(120, 170, 220, .1);
  font-size: 12px;
}
.alarm-item:last-child { border-bottom: none; }
.alarm-level {
  flex-shrink: 0; font-size: 10px;
  padding: 1px 6px; border-radius: 4px; margin-top: 1px;
}
.alarm-level.h { background: rgba(255, 77, 94, .15); color: #ff8d99; border: 1px solid rgba(255, 77, 94, .3); }
.alarm-level.m { background: rgba(255, 176, 32, .14); color: #ffc75e; border: 1px solid rgba(255, 176, 32, .3); }
.alarm-level.l { background: rgba(0, 229, 255, .12); color: #7fe7ff; border: 1px solid rgba(0, 229, 255, .3); }
.alarm-content { flex: 1; min-width: 0; }
.alarm-text { color: #cfe0f2; }
.alarm-time { color: var(--sub); font-size: 10px; margin-top: 2px; }

/* ============ Chart areas ============ */
.chart-area { width: 100%; height: 100%; min-height: 200px; flex: 1; }
.score-chart-area { width: 100%; height: 130px; }
.vpp-gauge-area { width: 100%; height: 110px; }
.vpp-info { font-size: 11px; color: var(--sub); text-align: center; margin-top: 2px; }
.mini-chart { width: 100%; height: 118px; }

/* ============ Energy flow SVG ============ */
.flow-panel { padding: 6px 14px; }
.flow-svg-box { width: 100%; }
.flow-svg-box svg { width: 100%; height: 170px; display: block; }
.src-node { fill: rgba(0, 229, 255, .08); stroke: rgba(0, 229, 255, .5); stroke-width: 1; }
.src-node.t2 { fill: rgba(255, 176, 32, .08); stroke: rgba(255, 176, 32, .5); }
.src-node.t3 { fill: rgba(0, 255, 157, .08); stroke: rgba(0, 255, 157, .5); }
.src-node.busbar { fill: rgba(0, 255, 157, .1); stroke: rgba(0, 255, 157, .5); }
.src-node.load { fill: rgba(255, 77, 94, .08); stroke: rgba(255, 77, 94, .45); }
.node-label { fill: #dff3ff; font-size: 11px; text-anchor: middle; }
.node-label.sub { fill: #5f7392; font-size: 10px; }
.node-val { fill: var(--cyan); font-size: 12px; font-family: Consolas, monospace; text-anchor: middle; }
.flowline {
  stroke: rgba(0, 229, 255, .5); stroke-width: 1.4; fill: none;
  stroke-dasharray: 5 6; animation: flowmove 1.2s linear infinite;
}
.flowline.g { stroke: rgba(0, 255, 157, .45); }
.flowline.o { stroke: rgba(255, 176, 32, .45); }
@keyframes flowmove { to { stroke-dashoffset: -22; } }

/* ============ Policy list ============ */
.policy-list { display: flex; flex-direction: column; gap: 9px; font-size: 12px; }
.policy-item {
  padding: 8px 10px; border-radius: 8px;
  border: 1px solid;
}
.policy-desc { color: var(--sub); font-size: 11px; margin-top: 3px; }

/* ============ Suggestion list ============ */
.suggestion-list { display: flex; flex-direction: column; gap: 7px; font-size: 12px; }
.suggestion-item { display: flex; gap: 8px; align-items: flex-start; }
.suggestion-tag {
  flex-shrink: 0; font-size: 10px; padding: 1px 7px; border-radius: 4px;
}
.suggestion-tag.critical { background: rgba(255, 77, 94, .15); color: #ff8d99; border: 1px solid rgba(255, 77, 94, .3); }
.suggestion-tag.warning { background: rgba(255, 176, 32, .14); color: #ffc75e; border: 1px solid rgba(255, 176, 32, .3); }
.suggestion-tag.info { background: rgba(0, 229, 255, .12); color: #7fe7ff; border: 1px solid rgba(0, 229, 255, .3); }
.suggestion-text { color: #cfe0f2; flex: 1; }

/* ============ Bottom row ============ */
.bottom {
  display: grid;
  grid-template-columns: 1.2fr 1fr 1fr 1.2fr;
  gap: 12px;
  flex-shrink: 0;
  margin-top: 10px;
}
@media (max-width: 1500px) { .bottom { grid-template-columns: 1fr 1fr; } }
@media (max-width: 900px) { .bottom { grid-template-columns: 1fr; } }

/* ============ Footer ============ */
.cockpit-foot {
  height: 22px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #3d5470;
  font-size: 10px;
  letter-spacing: 1px;
  flex-shrink: 0;
  margin-top: 4px;
}
</style>
