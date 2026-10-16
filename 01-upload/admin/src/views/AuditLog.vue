<template>
  <div class="audit-page">
    <el-card shadow="never">
      <template #header>
        <div class="page-header">
          <span>系统活动日志</span>
          <div class="header-actions">
            <el-select v-model="filterType" placeholder="日志类型" clearable style="width:140px">
              <el-option label="全部" value="" />
              <el-option label="设备控制" value="CONTROL" />
              <el-option label="告警处理" value="ALARM" />
              <el-option label="设备管理" value="DEVICE" />
            </el-select>
            <el-date-picker
              v-model="dateRange"
              type="daterange"
              range-separator="至"
              start-placeholder="开始日期"
              end-placeholder="结束日期"
              style="width:260px"
              value-format="YYYY-MM-DD"
            />
            <el-button type="primary" @click="loadLogs">查询</el-button>
            <el-button @click="refreshLogs">刷新</el-button>
          </div>
        </div>
      </template>

      <el-table :data="logs" style="width:100%" v-loading="loading" size="small" stripe>
        <el-table-column prop="timestamp" label="时间" width="170">
          <template #default="{row}">{{ row.timestamp || '-' }}</template>
        </el-table-column>
        <el-table-column prop="type" label="类型" width="100">
          <template #default="{row}">
            <el-tag :type="logTypeColor(row.type)" size="small">{{ row.type || '-' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="operator" label="来源" width="120" />
        <el-table-column prop="target" label="操作对象" min-width="160" show-overflow-tooltip />
        <el-table-column prop="detail" label="详情" min-width="220" show-overflow-tooltip />
        <el-table-column prop="result" label="结果" width="80">
          <template #default="{row}">
            <el-tag :type="row.result === '成功' ? 'success' : (row.result === '失败' ? 'danger' : 'warning')" size="small">
              {{ row.result || '-' }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap" v-if="total > 0">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @change="loadLogs"
        />
      </div>
    </el-card>

    <div class="stats-row">
      <el-card shadow="never" style="flex:1">
        <template #header><span>日志类型分布</span></template>
        <div class="chart-wrap" ref="typeChartRef"></div>
      </el-card>
      <el-card shadow="never" style="flex:1">
        <template #header><span>设备操作 Top 10</span></template>
        <div class="chart-wrap" ref="deviceChartRef"></div>
      </el-card>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import * as echarts from 'echarts'
import { ElMessage } from 'element-plus'
import { adminAPI } from '../api/admin'

const loading = ref(false)
const logs = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const filterType = ref('')
const dateRange = ref([])

const typeChartRef = ref(null)
const deviceChartRef = ref(null)
let charts = {}

const logTypeColor = (type) => {
  const map = { CONTROL: '', ALARM: 'warning', DEVICE: 'primary' }
  return map[type] || 'info'
}

const loadLogs = async () => {
  loading.value = true
  try {
    const params = {
      page: page.value - 1,   // 服务端 0-based
      size: pageSize.value,
    }
    if (filterType.value) params.type = filterType.value
    if (dateRange.value && dateRange.value.length === 2) {
      params.startDate = dateRange.value[0]
      params.endDate = dateRange.value[1]
    }

    const res = await adminAPI.getAuditLogs(params)
    const data = res.data || res
    logs.value = data.content || []
    total.value = data.totalElements || 0

    await nextTick()
    renderCharts()
  } catch (e) {
    ElMessage.error('加载日志失败：' + (e.response?.data?.message || e.message || '未知错误'))
  } finally {
    loading.value = false
  }
}

const refreshLogs = () => loadLogs()

const renderCharts = () => {
  destroyChart('type')
  destroyChart('device')

  if (typeChartRef.value) {
    const typeCounts = {}
    logs.value.forEach(l => { typeCounts[l.type] = (typeCounts[l.type] || 0) + 1 })
    charts.type = echarts.init(typeChartRef.value)
    charts.type.setOption({
      tooltip: { trigger: 'item' },
      legend: { bottom: 0, textStyle: { color: '#94a3b8' } },
      series: [{
        type: 'pie', radius: ['45%', '72%'], center: ['50%', '45%'],
        data: [
          { value: typeCounts.CONTROL || 0, name: '设备控制' },
          { value: typeCounts.ALARM || 0, name: '告警处理' },
        ],
        label: { color: '#94a3b8' },
      }],
      backgroundColor: 'transparent',
    })
  }

  if (deviceChartRef.value) {
    const deviceMap = {}
    logs.value.forEach(l => {
      const key = l.target || '未知'
      deviceMap[key] = (deviceMap[key] || 0) + 1
    })
    const sorted = Object.entries(deviceMap)
      .sort((a, b) => b[1] - a[1])
      .slice(0, 10)

    charts.device = echarts.init(deviceChartRef.value)
    charts.device.setOption({
      tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
      grid: { left: '3%', right: '8%', bottom: '3%', top: 10, containLabel: true },
      xAxis: { type: 'value', splitLine: { lineStyle: { color: '#334155', type: 'dashed' } }, axisLabel: { color: '#94a3b8' } },
      yAxis: { type: 'category', data: sorted.map(s => s[0]).reverse(), axisLabel: { color: '#94a3b8' } },
      series: [{
        data: sorted.map(s => s[1]).reverse(), type: 'bar', barWidth: 16,
        itemStyle: {
          borderRadius: [0, 3, 3, 0],
          color: new echarts.graphic.LinearGradient(0, 0, 1, 0, [
            { offset: 0, color: '#38bdf8' }, { offset: 1, color: '#22d3ee' },
          ]),
        },
      }],
      backgroundColor: 'transparent',
    })
  }
}

const destroyChart = (key) => { if (charts[key]) { charts[key].dispose(); delete charts[key] } }
const handleResize = () => Object.values(charts).forEach(c => c?.resize())

onMounted(() => {
  loadLogs()
  window.addEventListener('resize', handleResize)
})
onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  Object.values(charts).forEach(c => c?.dispose())
})
</script>

<style scoped>
.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px;
}
.header-actions {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
}
.pagination-wrap { margin-top: 16px; display: flex; justify-content: flex-end; }
.stats-row {
  display: flex;
  gap: 20px;
  margin-top: 20px;
}
@media (max-width: 900px) { .stats-row { flex-direction: column; } }
.chart-wrap { width: 100%; height: 280px; }
</style>
