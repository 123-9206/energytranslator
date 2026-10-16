<template>
  <div class="wallet-page">
    <el-alert type="success" :closable="false" show-icon style="margin-bottom:20px"
      title="老板钱包账 · 把省下来的钱讲清楚"
      description="聚合 VPP 结算净额 + 绿电年省 + AI 诊断年省，形成企业可感知的节能收益总账。" />

    <div class="kpi-grid">
      <div class="kpi-card" v-for="c in kpis" :key="c.label">
        <div class="kpi-num" :style="{ color: c.color }">{{ c.value }} <span class="unit">{{ c.unit }}</span></div>
        <div class="kpi-lbl">{{ c.label }}</div>
      </div>
    </div>

    <el-card shadow="never">
      <template #header><span>收益构成</span></template>
      <div ref="pieRef" class="chart-wrap"></div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onUnmounted, nextTick } from 'vue'
import * as echarts from 'echarts'
import { walletAPI } from '../api/wallet'

const pieRef = ref(null)
let chart = null
const kpis = reactive([
  { label: 'VPP 已结算净额', value: 0, unit: '元', color: '#38bdf8' },
  { label: 'VPP 可提现', value: 0, unit: '元', color: '#a78bfa' },
  { label: '绿电年省', value: 0, unit: '元', color: '#34d399' },
  { label: 'AI 诊断年省', value: 0, unit: '元', color: '#fbbf24' },
  { label: '年度总节省', value: 0, unit: '元', color: '#f87171' },
])

const fmt = (n) => {
  if (n == null) return '0'
  return Number(n).toLocaleString('zh-CN', { maximumFractionDigits: 0 })
}

const loadData = async () => {
  try {
    const res = await walletAPI.getSummary()
    const d = res?.data || res || {}
    kpis[0].value = fmt(d.vppSettled)
    kpis[1].value = fmt(d.vppWithdrawable)
    kpis[2].value = fmt(d.greenSavingAnnual)
    kpis[3].value = fmt(d.diagnosisSavingAnnual)
    kpis[4].value = fmt(d.totalSavedAnnual)
    await nextTick()
    renderPie(d)
  } catch (e) { /* interceptor handled */ }
}

const renderPie = (d) => {
  if (chart) { chart.dispose(); chart = null }
  if (!pieRef.value) return
  const data = [
    { name: 'VPP 结算净额', value: Number(d.vppSettled) || 0 },
    { name: '绿电年省', value: Number(d.greenSavingAnnual) || 0 },
    { name: 'AI 诊断年省', value: Number(d.diagnosisSavingAnnual) || 0 },
  ]
  chart = echarts.init(pieRef.value, null, { width: pieRef.value.clientWidth, height: 320 })
  chart.setOption({
    backgroundColor: 'transparent',
    tooltip: { trigger: 'item', formatter: '{b}: {c} 元 ({d}%)' },
    legend: { bottom: 0, textStyle: { color: '#94a3b8' } },
    series: [{
      type: 'pie', radius: ['45%', '70%'], center: ['50%', '45%'],
      data, label: { color: '#94a3b8' },
      itemStyle: { borderColor: '#0f172a', borderWidth: 2 },
    }],
  })
}

const handleResize = () => chart?.resize()
onMounted(() => { loadData(); window.addEventListener('resize', handleResize) })
onUnmounted(() => { window.removeEventListener('resize', handleResize); if (chart) chart.dispose() })
</script>

<style scoped>
.kpi-grid { display: grid; grid-template-columns: repeat(5, 1fr); gap: 16px; margin-bottom: 20px; }
.kpi-card { text-align: center; padding: 22px 16px; background: var(--bg-secondary); border: 1px solid var(--border); border-radius: 12px; }
.kpi-num { font-size: 24px; font-weight: 700; }
.kpi-num .unit { font-size: 12px; font-weight: 400; color: var(--text-secondary); }
.kpi-lbl { font-size: 12px; color: var(--text-secondary); margin-top: 6px; }
.chart-wrap { width: 100%; height: 320px; }
</style>
