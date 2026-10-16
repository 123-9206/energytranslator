<template>
  <div class="energy-page">
    <!-- KPI cards -->
    <div class="kpi-grid">
      <div class="kpi-card">
        <div class="kpi-icon" style="background:rgba(56,189,248,.15)"><el-icon :size="22" color="#38bdf8"><TrendCharts /></el-icon></div>
        <div class="kpi-info">
          <div class="kpi-value">{{ formatNumber(stats.todayEnergy) }} <span class="unit">kWh</span></div>
          <div class="kpi-label">今日能耗</div>
        </div>
      </div>
      <div class="kpi-card">
        <div class="kpi-icon" style="background:rgba(74,222,128,.15)"><el-icon :size="22" color="#4ade80"><DataAnalysis /></el-icon></div>
        <div class="kpi-info">
          <div class="kpi-value">{{ formatNumber(stats.monthEnergy) }} <span class="unit">kWh</span></div>
          <div class="kpi-label">本月能耗</div>
        </div>
      </div>
      <div class="kpi-card">
        <div class="kpi-icon" style="background:rgba(248,113,113,.15)"><el-icon :size="22" color="#f87171"><Coin /></el-icon></div>
        <div class="kpi-info">
          <div class="kpi-value">¥{{ formatNumber(stats.todayCost) }}</div>
          <div class="kpi-label">今日电费</div>
        </div>
      </div>
      <div class="kpi-card">
        <div class="kpi-icon" style="background:rgba(167,139,250,.15)"><el-icon :size="22" color="#a78bfa"><Top /></el-icon></div>
        <div class="kpi-info">
          <div class="kpi-value">{{ stats.peakPower || '--' }} <span class="unit">kW</span></div>
          <div class="kpi-label">今日峰值功率</div>
        </div>
      </div>
    </div>

    <!-- Charts -->
    <div class="chart-row">
      <el-card shadow="never">
        <template #header>
          <div class="card-header">
            <span>能耗趋势</span>
            <el-radio-group v-model="trendType" size="small" @change="loadTrend">
              <el-radio-button label="day">日</el-radio-button>
              <el-radio-button label="hour">时</el-radio-button>
              <el-radio-button label="week">周</el-radio-button>
              <el-radio-button label="month">月</el-radio-button>
            </el-radio-group>
          </div>
        </template>
        <div class="chart-wrap" ref="trendChartRef"></div>
      </el-card>
      <el-card shadow="never">
        <template #header>
          <div class="card-header">
            <span>能耗排名</span>
            <el-select v-model="rankDimension" size="small" style="width:100px" @change="loadRanking">
              <el-option label="按设备" value="device" />
              <el-option label="按站点" value="site" />
            </el-select>
          </div>
        </template>
        <div class="chart-wrap" ref="rankChartRef"></div>
      </el-card>
    </div>

    <!-- Detailed table -->
    <el-card shadow="never" style="margin-top:20px">
      <template #header>
        <div class="card-header">
          <span>能耗记录</span>
          <el-button text type="primary" size="small" @click="exportData">导出Excel</el-button>
        </div>
      </template>
      <el-table :data="records" style="width:100%" v-loading="loading" size="small" stripe>
        <el-table-column label="时间" width="170">
          <template #default="{row}">
            {{ row.timestamp ? dayjs(row.timestamp).format('YYYY-MM-DD HH:mm:ss') : '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="deviceName" label="设备" width="140" />
        <el-table-column label="能耗(kWh)" width="120">
          <template #default="{row}">{{ formatNumber(row.value) }}</template>
        </el-table-column>
        <el-table-column label="功率(kW)" width="120">
          <template #default="{row}">{{ formatNumber(row.power) }}</template>
        </el-table-column>
        <el-table-column prop="unit" label="单位" width="80" />
        <el-table-column prop="source" label="来源" width="120" />
        <el-table-column prop="enterpriseName" label="企业" min-width="140" show-overflow-tooltip />
      </el-table>
      <div class="pagination-wrap" v-if="total > 0">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next"
          @change="loadRecords"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onUnmounted, nextTick } from 'vue'
import * as echarts from 'echarts'
import dayjs from 'dayjs'
import { ElMessage } from 'element-plus'
import { energyAPI } from '../api/energy'

const loading = ref(false)
const records = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const trendType = ref('day')
const rankDimension = ref('device')

const trendChartRef = ref(null)
const rankChartRef = ref(null)

let charts = {}

const stats = reactive({
  todayEnergy: 0,
  monthEnergy: 0,
  todayCost: 0,
  peakPower: 0,
})

const formatNumber = (n) => {
  if (n == null) return '--'
  return Number(n).toLocaleString('zh-CN', { maximumFractionDigits: 1 })
}

const loadRecords = async () => {
  loading.value = true
  try {
    const res = await energyAPI.getRecords({ page: page.value, pageSize: pageSize.value })
    const data = res.data || res
    records.value = data.content || data.list || []
    total.value = data.totalElements || data.total || records.value.length
  } catch {
    ElMessage.error('加载能耗记录失败')
  } finally {
    loading.value = false
  }
}

const loadKPIs = async () => {
  try {
    const res = await energyAPI.getSummary({ period: 'today' })
    const data = res.data || res
    if (data) {
      stats.todayEnergy = data.todayEnergy ?? data.energy ?? 0
      stats.monthEnergy = data.monthEnergy ?? data.monthTotal ?? 0
      stats.todayCost = data.todayCost ?? data.cost ?? 0
      stats.peakPower = data.peakPower ?? data.maxPower ?? 0
    }
  } catch {
    // KPI 加载失败不影响主页面
  }
}

const loadTrend = async () => {
  try {
    const res = await energyAPI.getTrend({ type: trendType.value, days: 7 })
    const data = res.data || res
    const trendData = data.data || data.trend || data || []
    await nextTick()
    destroyChart('trend')
    if (trendChartRef.value) {
      const labels = trendData.map(d => dayjs(d.date || d.timestamp).format(trendType.value === 'hour' ? 'HH:mm' : 'MM-DD'))
      const values = trendData.map(d => d.value || 0)
      charts.trend = initChart(trendChartRef.value, {
        tooltip: { trigger: 'axis' },
        grid: { left: '3%', right: '4%', bottom: '3%', top: 10, containLabel: true },
        xAxis: { type: 'category', data: labels.length ? labels : ['暂无'], axisLine: { lineStyle: { color: '#334155' } }, axisLabel: { color: '#94a3b8' } },
        yAxis: { type: 'value', splitLine: { lineStyle: { color: '#334155', type: 'dashed' } }, axisLabel: { color: '#94a3b8' } },
        series: [{
          data: values.length ? values : [0], type: 'line', smooth: true,
          lineStyle: { color: '#38bdf8', width: 2 },
          itemStyle: { color: '#38bdf8' },
          areaStyle: { color: new echarts.graphic.LinearGradient(0,0,0,1,[
            { offset: 0, color: 'rgba(56,189,248,.2)' }, { offset: 1, color: 'rgba(56,189,248,.0)' }
          ]) },
        }],
      })
    }
  } catch {}
}

const loadRanking = async () => {
  try {
    const res = await energyAPI.getRanking({ dimension: rankDimension.value, period: 'month' })
    const data = res.data || res
    const ranks = data.ranking || data.data || data || []
    await nextTick()
    destroyChart('rank')
    if (rankChartRef.value) {
      const names = ranks.slice(0, 10).map(d => d.name || d.deviceName || d.siteName || '-')
      const values = ranks.slice(0, 10).map(d => d.value || d.energy || 0)
      charts.rank = initChart(rankChartRef.value, {
        tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
        grid: { left: '3%', right: '8%', bottom: '3%', top: 10, containLabel: true },
        xAxis: { type: 'value', splitLine: { lineStyle: { color: '#334155', type: 'dashed' } }, axisLabel: { color: '#94a3b8' } },
        yAxis: { type: 'category', data: names.reverse(), axisLine: { lineStyle: { color: '#334155' } }, axisLabel: { color: '#94a3b8' } },
        series: [{
          data: values.reverse(), type: 'bar', barWidth: 16,
          itemStyle: {
            borderRadius: [0, 3, 3, 0],
            color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [
              { offset: 0, color: '#38bdf8' }, { offset: 1, color: '#a78bfa' }
            ]),
          },
        }],
      })
    }
  } catch {}
}

const initChart = (ref, opts) => {
  const inst = echarts.init(ref, null, { width: ref.clientWidth, height: ref.clientHeight || 300 })
  inst.setOption({ ...opts, backgroundColor: 'transparent' })
  return inst
}

const destroyChart = (key) => {
  if (charts[key]) { charts[key].dispose(); delete charts[key] }
}

const exportData = () => {
  ElMessage.info('导出功能开发中，请稍候')
}

const handleResize = () => Object.values(charts).forEach(c => c?.resize())

onMounted(() => {
  loadKPIs()
  loadRecords()
  loadTrend()
  loadRanking()
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
@media (max-width: 900px) { .kpi-grid { grid-template-columns: repeat(2, 1fr); } }
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
  flex-shrink: 0;
}
.kpi-value { font-size: 24px; font-weight: 700; color: var(--text-primary); }
.kpi-value .unit { font-size: 12px; font-weight: 400; color: var(--text-secondary); }
.kpi-label { font-size: 12px; color: var(--text-secondary); margin-top: 2px; }
.chart-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
  margin-bottom: 20px;
}
@media (max-width: 900px) { .chart-row { grid-template-columns: 1fr; } }
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.chart-wrap { width: 100%; height: 300px; }
.pagination-wrap { margin-top: 16px; display: flex; justify-content: flex-end; }
</style>
