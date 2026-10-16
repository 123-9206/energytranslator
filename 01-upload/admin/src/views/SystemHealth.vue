<template>
  <div class="system-page">
    <div class="kpi-grid">
      <div class="kpi-card" v-for="s in statusCards" :key="s.label">
        <div class="kpi-icon" :style="{background: s.bg}">
          <el-icon :size="22" :color="s.color"><component :is="s.icon" /></el-icon>
        </div>
        <div>
          <div class="kpi-val" :style="{color: s.color}">{{ s.value }}</div>
          <div class="kpi-lbl">{{ s.label }}</div>
        </div>
      </div>
    </div>

    <div class="chart-row">
      <el-card shadow="never">
        <template #header><span>API 响应时间</span></template>
        <div class="chart-wrap" ref="apiChartRef"></div>
      </el-card>
      <el-card shadow="never">
        <template #header><span>数据采集状态</span></template>
        <div class="chart-wrap" ref="collectChartRef"></div>
      </el-card>
    </div>

    <el-card shadow="never" style="margin-top:20px">
      <template #header><span>服务状态</span></template>
      <el-table :data="services" style="width:100%" size="small">
        <el-table-column prop="name" label="服务" width="180" />
        <el-table-column label="状态" width="100">
          <template #default="{row}">
            <el-tag :type="row.status === 'up' ? 'success' : 'danger'" size="small">
              {{ row.status === 'up' ? '运行中' : '异常' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="version" label="版本" width="120" />
        <el-table-column prop="detail" label="详情" min-width="200" show-overflow-tooltip />
      </el-table>
    </el-card>

    <el-card shadow="never" style="margin-top:20px">
      <template #header><span>JVM 指标</span></template>
      <el-descriptions :column="4" border size="small">
        <el-descriptions-item label="堆内存使用">{{ jvm.heapUsed || '--' }}</el-descriptions-item>
        <el-descriptions-item label="最大堆内存">{{ jvm.heapMax || '--' }}</el-descriptions-item>
        <el-descriptions-item label="可用处理器">{{ jvm.processors || '--' }}</el-descriptions-item>
        <el-descriptions-item label="Java版本">{{ jvm.javaVersion || '--' }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-card shadow="never" style="margin-top:20px">
      <template #header>
        <div class="card-header"><span>数据库统计</span></div>
      </template>
      <el-descriptions :column="3" border size="small">
        <el-descriptions-item label="总设备数">{{ mongo.deviceCount || '--' }}</el-descriptions-item>
        <el-descriptions-item label="总用户数">{{ mongo.userCount || '--' }}</el-descriptions-item>
        <el-descriptions-item label="总数据点数">{{ mongo.dataPointCount || '--' }}</el-descriptions-item>
        <el-descriptions-item label="数据库状态">{{ mongo.status || '--' }}</el-descriptions-item>
        <el-descriptions-item label="服务版本">{{ healthData.version || '--' }}</el-descriptions-item>
        <el-descriptions-item label="数据时间">{{ healthData.timestamp || '--' }}</el-descriptions-item>
      </el-descriptions>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onUnmounted, nextTick } from 'vue'
import * as echarts from 'echarts'
import { adminAPI } from '../api/admin'

const apiChartRef = ref(null)
const collectChartRef = ref(null)

let charts = {}

const healthData = reactive({ version: '', timestamp: '', status: '' })

const statusCards = reactive([
  { label: 'API 服务', value: '检测中', color: '#fbbf24', bg: 'rgba(251,191,36,.15)', icon: 'CircleCheck' },
  { label: 'MongoDB', value: '检测中', color: '#fbbf24', bg: 'rgba(251,191,36,.15)', icon: 'Coin' },
  { label: 'EMQX', value: '--', color: '#94a3b8', bg: 'rgba(148,163,184,.15)', icon: 'Connection' },
  { label: '数据采集', value: '--', color: '#94a3b8', bg: 'rgba(148,163,184,.15)', icon: 'Refresh' },
])

const services = ref([])

const jvm = reactive({
  heapUsed: '--', heapMax: '--', processors: '--', javaVersion: '--',
})

const mongo = reactive({
  deviceCount: '--', userCount: '--', dataPointCount: '--', status: '--',
})

const initChart = (ref, opts) => {
  const inst = echarts.init(ref, null, { width: ref.clientWidth, height: ref.clientHeight || 280 })
  inst.setOption({ ...opts, backgroundColor: 'transparent' })
  return inst
}

const destroyChart = (key) => { if (charts[key]) { charts[key].dispose(); delete charts[key] } }
const handleResize = () => Object.values(charts).forEach(c => c?.resize())

const loadHealth = async () => {
  try {
    const res = await adminAPI.getSystemHealth()
    const data = res.data || res

    // 更新全局健康数据
    if (data.status) healthData.status = data.status
    if (data.version) healthData.version = data.version
    if (data.timestamp) healthData.timestamp = new Date(data.timestamp).toLocaleString()

    // KPI 卡片: API 服务
    statusCards[0].value = data.status === 'UP' ? '正常' : '异常'
    statusCards[0].color = data.status === 'UP' ? '#4ade80' : '#ef4444'
    statusCards[0].bg = data.status === 'UP' ? 'rgba(74,222,128,.15)' : 'rgba(239,68,68,.15)'

    // KPI 卡片: MongoDB
    statusCards[1].value = data.mongodb?.status === 'UP' ? '正常' : '异常'
    statusCards[1].color = data.mongodb?.status === 'UP' ? '#4ade80' : '#ef4444'
    statusCards[1].bg = data.mongodb?.status === 'UP' ? 'rgba(74,222,128,.15)' : 'rgba(239,68,68,.15)'

    // KPI 卡片: EMQX
    const emqxOk = data.emqx?.status === 'UP'
    statusCards[2].value = emqxOk ? '正常' : (data.emqx?.status || '--')
    statusCards[2].color = emqxOk ? '#4ade80' : (data.emqx?.status === 'DOWN' ? '#ef4444' : '#94a3b8')
    statusCards[2].bg = emqxOk ? 'rgba(74,222,128,.15)' : (data.emqx?.status === 'DOWN' ? 'rgba(239,68,68,.15)' : 'rgba(148,163,184,.15)')

    // KPI 卡片: 数据采集
    const totalDev = data.collectStats?.totalDevices || 0
    const onlineDev = data.collectStats?.onlineDevices || 0
    statusCards[3].value = totalDev > 0 ? onlineDev + '/' + totalDev : '--'
    statusCards[3].color = totalDev > 0 && onlineDev >= totalDev ? '#4ade80' : (totalDev > 0 ? '#fbbf24' : '#94a3b8')
    statusCards[3].bg = totalDev > 0 && onlineDev >= totalDev ? 'rgba(74,222,128,.15)' : (totalDev > 0 ? 'rgba(251,191,36,.15)' : 'rgba(148,163,184,.15)')

    // 服务列表
    services.value = [
      { name: 'Energy Translator Backend', status: data.status === 'UP' ? 'up' : 'down', version: data.version || '--', detail: 'Spring Boot 3.2 / port 8080' },
      { name: 'MongoDB', status: data.mongodb?.status === 'UP' ? 'up' : 'down', version: '7.0', detail: data.mongodb?.error || '127.0.0.1:27017' },
      { name: 'EMQX MQTT Broker', status: data.emqx?.status === 'UP' ? 'up' : 'down', version: data.emqx?.version || '5.8.9', detail: data.emqx?.status === 'UP' ? '127.0.0.1:1883 / Dashboard :18083' : (data.emqx?.error || 'Service unavailable') },
      { name: 'Nginx', status: data.nginx?.status === 'UP' ? 'up' : 'down', version: data.nginx?.port ? (data.nginx.port === 443 ? 'HTTPS' : 'HTTP') : '--', detail: data.nginx?.detail || 'HTTPS reverse proxy / SSL enabled' },
    ]

    // JVM 指标
    if (data.jvm) {
      jvm.heapUsed = data.jvm.heapUsed || '--'
      jvm.heapMax = data.jvm.heapMax || '--'
      jvm.processors = data.jvm.availableProcessors || '--'
      jvm.javaVersion = data.jvm.javaVersion || '--'
    }

    // MongoDB 统计
    if (data.mongodb) {
      mongo.deviceCount = data.mongodb.deviceCount ?? '--'
      mongo.userCount = data.mongodb.userCount ?? '--'
      mongo.dataPointCount = data.mongodb.dataPointCount ?? '--'
      mongo.status = data.mongodb.status || '--'
    }

    // API 响应时间图表（真实数据）
    await nextTick()
    destroyChart('api')
    if (apiChartRef.value) {
      const apiMetrics = data.apiMetrics
      const hasRealData = apiMetrics?.labels && apiMetrics.labels.length > 0
      const labels = hasRealData ? apiMetrics.labels : Array.from({length: 24}, (_, i) => `${i}:00`)
      const times = hasRealData ? apiMetrics.values : Array(24).fill(0)
      charts.api = initChart(apiChartRef.value, {
        tooltip: { trigger: 'axis' },
        grid: { left: '3%', right: '4%', bottom: '3%', top: 10, containLabel: true },
        xAxis: { type: 'category', data: labels, axisLabel: { color: '#94a3b8' }, axisLine: { lineStyle: { color: '#334155' } } },
        yAxis: { type: 'value', name: 'ms', splitLine: { lineStyle: { color: '#334155', type: 'dashed' } }, axisLabel: { color: '#94a3b8' } },
        series: [{
          data: times, type: 'line', smooth: true,
          lineStyle: { color: '#4ade80', width: 2 }, itemStyle: { color: '#4ade80' },
        }],
      })
    }

    // 数据采集率图表（真实数据）
    destroyChart('collect')
    if (collectChartRef.value) {
      const collectMetrics = data.collectionMetrics
      const hasRealData = collectMetrics?.labels && collectMetrics.labels.length > 0
      const labels = hasRealData ? collectMetrics.labels : ['一','二','三','四','五','六','日']
      const rates = hasRealData ? collectMetrics.values : Array(7).fill(0)
      // 自适应 Y 轴范围（值可能是百分比 0-100 或比率 0-1）
      const allRates = rates.length > 0 ? rates : [0]
      const maxVal = Math.max(...allRates)
      const yMax = maxVal <= 1 ? 1 : 100
      const yMin = yMax === 1 ? 0 : Math.max(0, Math.floor(Math.min(...allRates) / 10) * 10 - 10)
      charts.collect = initChart(collectChartRef.value, {
        tooltip: { trigger: 'axis', valueFormatter: (v) => yMax === 1 ? (v * 100).toFixed(1) + '%' : v + '%' },
        grid: { left: '3%', right: '4%', bottom: '3%', top: 10, containLabel: true },
        xAxis: { type: 'category', data: labels, axisLabel: { color: '#94a3b8' }, axisLine: { lineStyle: { color: '#334155' } } },
        yAxis: { type: 'value', min: yMin, max: yMax, name: '%', splitLine: { lineStyle: { color: '#334155', type: 'dashed' } }, axisLabel: { color: '#94a3b8' } },
        series: [{
          data: rates, type: 'bar', barWidth: 30,
          itemStyle: { color: (params) => params.value >= (yMax === 1 ? 0.95 : 95) ? '#4ade80' : '#fbbf24', borderRadius: [4, 4, 0, 0] },
        }],
      })
    }
  } catch (e) {
    console.error('Health load error:', e)
    statusCards[0].value = '不可用'
    statusCards[0].color = '#ef4444'
    statusCards[0].bg = 'rgba(239,68,68,.15)'
  }
}

onMounted(() => {
  loadHealth()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  Object.values(charts).forEach(c => c?.dispose())
})
</script>

<style scoped>
.kpi-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 20px;
}
.kpi-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 20px;
  background: var(--bg-secondary);
  border: 1px solid var(--border);
  border-radius: 12px;
}
.kpi-icon {
  width: 44px; height: 44px;
  border-radius: 10px;
  display: flex; align-items: center; justify-content: center;
}
.kpi-val { font-size: 20px; font-weight: 700; }
.kpi-lbl { font-size: 12px; color: var(--text-secondary); }
.chart-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
  margin-bottom: 20px;
}
.chart-wrap { width: 100%; height: 280px; }
.card-header { display: flex; align-items: center; justify-content: space-between; }
</style>
