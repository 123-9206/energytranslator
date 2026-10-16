<template>
  <div class="carbon-page">
    <div class="kpi-grid">
      <div class="kpi-card" v-for="c in carbonStats" :key="c.label">
        <div class="kpi-num" :style="{color: c.color}">{{ c.value }} <span class="unit">{{ c.unit }}</span></div>
        <div class="kpi-lbl">{{ c.label }}</div>
      </div>
    </div>

    <div class="chart-row">
      <el-card shadow="never">
        <template #header><span>碳排放趋势</span></template>
        <div class="chart-wrap" ref="trendChartRef"></div>
      </el-card>
      <el-card shadow="never">
        <template #header><span>碳排放来源</span></template>
        <div class="chart-wrap" ref="sourceChartRef"></div>
      </el-card>
    </div>

    <el-card shadow="never" style="margin-top:20px">
      <template #header>
        <div class="card-header">
          <span>碳排放报告</span>
          <el-select v-model="reportPeriod" size="small" style="width:110px" @change="loadReport">
            <el-option label="今日" value="today" />
            <el-option label="本周" value="week" />
            <el-option label="本月" value="month" />
            <el-option label="今年" value="year" />
          </el-select>
        </div>
      </template>
      <el-descriptions :column="3" border size="small" v-if="report">
        <el-descriptions-item label="总碳排放">{{ report.totalCarbon || 0 }} kg CO₂</el-descriptions-item>
        <el-descriptions-item label="今日排放">{{ report.todayCarbon || 0 }} kg</el-descriptions-item>
        <el-descriptions-item label="本月排放">{{ report.monthCarbon || 0 }} kg</el-descriptions-item>
        <el-descriptions-item label="年度排放">{{ report.yearCarbon || 0 }} kg</el-descriptions-item>
        <el-descriptions-item label="碳排放强度">{{ report.carbonIntensity || 0 }} kg/kWh</el-descriptions-item>
        <el-descriptions-item label="等效减排">{{ report.equivalentReduction || 0 }} kg</el-descriptions-item>
      </el-descriptions>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onUnmounted, nextTick } from 'vue'
import * as echarts from 'echarts'
import dayjs from 'dayjs'
import { carbonAPI } from '../api/carbon'

const trendChartRef = ref(null)
const sourceChartRef = ref(null)
const reportPeriod = ref('month')
const report = ref(null)

let charts = {}

const carbonStats = reactive([
  { label: '总碳排放', value: 0, unit: 'kg', color: '#a78bfa' },
  { label: '今日排放', value: 0, unit: 'kg', color: '#38bdf8' },
  { label: '碳强度', value: 0, unit: 'kg/kWh', color: '#fbbf24' },
  { label: '等效植树', value: 0, unit: '棵', color: '#4ade80' },
])

const formatNumber = (n) => {
  if (n == null) return '--'
  return Number(n).toLocaleString('zh-CN', { maximumFractionDigits: 1 })
}

const loadData = async () => {
  try {
    const [reportRes, trendRes, summaryRes] = await Promise.allSettled([
      carbonAPI.getReport({ period: reportPeriod.value }),
      carbonAPI.getTrend({ period: 'month' }),
      carbonAPI.getSummary(),
    ])

    const reportData = getValue(reportRes)?.data || getValue(reportRes) || {}
    report.value = reportData

    const trendData = getValue(trendRes)?.data || getValue(trendRes) || {}
    const summary = getValue(summaryRes)?.data || getValue(summaryRes) || {}

    carbonStats[0].value = formatNumber(reportData.totalCarbon || summary.totalCarbon || 0)
    carbonStats[1].value = formatNumber(reportData.todayCarbon || summary.todayCarbon || 0)
    carbonStats[2].value = formatNumber(reportData.carbonIntensity || 0.785)
    carbonStats[3].value = formatNumber(Math.round((reportData.totalCarbon || 0) / 18 * 100) / 100)

    const trends = trendData.trends || trendData.data || []

    await nextTick()

    // Trend chart
    destroyChart('trend')
    if (trendChartRef.value) {
      const labels = trends.map(t => dayjs(t.date).format('MM-DD'))
      const values = trends.map(t => t.value || 0)
      charts.trend = initChart(trendChartRef.value, {
        tooltip: { trigger: 'axis' },
        grid: { left: '3%', right: '4%', bottom: '3%', top: 10, containLabel: true },
        xAxis: { type: 'category', data: labels.length ? labels : ['暂无'], axisLine: { lineStyle: { color: '#334155' } }, axisLabel: { color: '#94a3b8' } },
        yAxis: { type: 'value', splitLine: { lineStyle: { color: '#334155', type: 'dashed' } }, axisLabel: { color: '#94a3b8' } },
        series: [{
          data: values.length ? values : [0], type: 'bar',
          itemStyle: {
            color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
              { offset: 0, color: '#a78bfa' }, { offset: 1, color: 'rgba(167,139,250,.2)' }
            ]),
            borderRadius: [4, 4, 0, 0],
          },
        }],
      })
    }

    // Source pie
    destroyChart('source')
    if (sourceChartRef.value) {
      const sources = reportData.sources || [{ name: '电力', value: 100, percentage: 100 }]
      charts.source = initChart(sourceChartRef.value, {
        tooltip: { trigger: 'item' },
        legend: { bottom: 0, textStyle: { color: '#94a3b8' } },
        series: [{
          type: 'pie', radius: ['45%', '72%'], center: ['50%', '45%'],
          data: sources.map(s => ({ value: s.value, name: s.name })),
          label: { color: '#94a3b8' },
          itemStyle: { borderColor: '#0f172a', borderWidth: 2 },
        }],
      })
    }
  } catch (e) {
    console.error('Carbon load error:', e)
  }
}

const loadReport = () => { loadData() }

const getValue = (r) => r?.status === 'fulfilled' ? r.value : null
const initChart = (ref, opts) => {
  const inst = echarts.init(ref, null, { width: ref.clientWidth, height: ref.clientHeight || 300 })
  inst.setOption({ ...opts, backgroundColor: 'transparent' })
  return inst
}
const destroyChart = (key) => { if (charts[key]) { charts[key].dispose(); delete charts[key] } }

const handleResize = () => Object.values(charts).forEach(c => c?.resize())

onMounted(() => {
  loadData()
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
  text-align: center;
  padding: 20px;
  background: var(--bg-secondary);
  border: 1px solid var(--border);
  border-radius: 12px;
}
.kpi-num { font-size: 26px; font-weight: 700; }
.kpi-num .unit { font-size: 12px; font-weight: 400; color: var(--text-secondary); }
.kpi-lbl { font-size: 12px; color: var(--text-secondary); margin-top: 4px; }
.chart-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
  margin-bottom: 20px;
}
.chart-wrap { width: 100%; height: 300px; }
.card-header { display: flex; align-items: center; justify-content: space-between; }
</style>
