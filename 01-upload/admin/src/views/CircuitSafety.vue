<template>
  <div class="cs-page">
    <!-- KPI -->
    <div class="kpi-grid">
      <div class="kpi-card" v-for="c in kpis" :key="c.label">
        <div class="kpi-num" :style="{ color: c.color }">{{ c.value }} <span class="unit">{{ c.unit }}</span></div>
        <div class="kpi-lbl">{{ c.label }}</div>
      </div>
    </div>

    <!-- 设备安全列表 -->
    <el-card shadow="never">
      <template #header><span>设备电路安全评估</span></template>
      <el-table :data="list" v-loading="loading" stripe @row-click="openDetail" row-class-name="row-clickable">
        <el-table-column prop="deviceName" label="设备名称" min-width="160" show-overflow-tooltip />
        <el-table-column label="安全评分" width="100" align="center">
          <template #default="{ row }"><b :style="{ color: levelColor(row.safetyLevel) }">{{ row.safetyScore }}</b></template>
        </el-table-column>
        <el-table-column label="等级" width="100" align="center">
          <template #default="{ row }"><el-tag :type="levelType(row.safetyLevel)" size="small">{{ row.safetyLevel }}</el-tag></template>
        </el-table-column>
        <el-table-column label="最大电流" width="110" align="right">
          <template #default="{ row }">{{ fmt(row.maxCurrent) }} <span class="dim">A</span></template>
        </el-table-column>
        <el-table-column label="平均电压" width="110" align="right">
          <template #default="{ row }">{{ fmt(row.avgVoltage) }} <span class="dim">V</span></template>
        </el-table-column>
        <el-table-column label="最高温度" width="110" align="right">
          <template #default="{ row }">{{ fmt(row.maxTemperature) }} <span class="dim">°C</span></template>
        </el-table-column>
        <el-table-column label="最低功率因数" width="120" align="right">
          <template #default="{ row }">{{ fmt(row.minPowerFactor, 2) }}</template>
        </el-table-column>
        <el-table-column label="隐患数" width="90" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.faultCount > 0" type="danger" size="small" effect="dark">{{ row.faultCount }}</el-tag>
            <span v-else class="dim">0</span>
          </template>
        </el-table-column>
        <el-table-column label="评估时间" min-width="150">
          <template #default="{ row }">{{ row.assessedAt || '--' }}</template>
        </el-table-column>
      </el-table>
      <div class="tip">点击任意设备查看隐患明细</div>
    </el-card>

    <!-- 明细抽屉 -->
    <el-drawer v-model="detailVisible" :title="current?.deviceName || '设备安全明细'" size="520px">
      <template v-if="current">
        <el-descriptions :column="2" border size="small" style="margin-bottom:16px">
          <el-descriptions-item label="安全评分">
            <b :style="{ color: levelColor(current.safetyLevel) }">{{ current.safetyScore }}</b>
          </el-descriptions-item>
          <el-descriptions-item label="等级">
            <el-tag :type="levelType(current.safetyLevel)" size="small">{{ current.safetyLevel }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="最大电流">{{ fmt(current.maxCurrent) }} A / 额定 {{ fmt(current.ratedCurrent) }} A</el-descriptions-item>
          <el-descriptions-item label="平均电压">{{ fmt(current.avgVoltage) }} V / 额定 {{ fmt(current.ratedVoltage) }} V</el-descriptions-item>
          <el-descriptions-item label="最高温度">{{ fmt(current.maxTemperature) }} °C</el-descriptions-item>
          <el-descriptions-item label="最低功率因数">{{ fmt(current.minPowerFactor, 2) }}</el-descriptions-item>
        </el-descriptions>

        <div class="section-title">隐患清单</div>
        <div v-if="current.faults && current.faults.length" class="fault-list">
          <div class="fault-item" v-for="(f, i) in current.faults" :key="i">
            <el-tag :type="f.severity === 'critical' ? 'danger' : 'warning'" size="small">{{ f.type }}</el-tag>
            <span class="fault-detail">{{ f.detail }}</span>
          </div>
        </div>
        <el-empty v-else description="未检测到电路安全隐患" />
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { circuitSafetyAPI } from '../api/circuitSafety'

const list = ref([])
const loading = ref(false)
const detailVisible = ref(false)
const current = ref(null)

const kpis = reactive([
  { label: '评估设备', value: 0, unit: '台', color: '#38bdf8' },
  { label: '安全', value: 0, unit: '台', color: '#34d399' },
  { label: '注意', value: 0, unit: '台', color: '#fbbf24' },
  { label: '危险/警告', value: 0, unit: '台', color: '#f87171' },
])

const fmt = (n, d = 0) => {
  if (n == null) return '--'
  return Number(n).toLocaleString('zh-CN', { maximumFractionDigits: d })
}
const levelColor = (l) => ({ '安全': '#34d399', '注意': '#fbbf24', '警告': '#fb923c', '危险': '#f87171' }[l] || '#94a3b8')
const levelType = (l) => ({ '安全': 'success', '注意': 'info', '警告': 'warning', '危险': 'danger' }[l] || 'info')

const loadData = async () => {
  loading.value = true
  try {
    const res = await circuitSafetyAPI.getOverview()
    const d = res?.data || res || {}
    list.value = d.items || []
    kpis[0].value = d.deviceCount || 0
    kpis[1].value = d.safe || 0
    kpis[2].value = d.warning || 0
    kpis[3].value = d.danger || 0
  } catch (e) { /* interceptor handled */ } finally {
    loading.value = false
  }
}

const openDetail = (row) => {
  if (!row.available) return
  current.value = row
  detailVisible.value = true
}

onMounted(loadData)
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
.dim { font-size: 11px; color: var(--text-secondary); }
.tip { font-size: 12px; color: var(--text-secondary); margin-top: 12px; }
.row-clickable { cursor: pointer; }
.section-title { font-size: 13px; font-weight: 600; margin: 12px 0 8px; color: var(--text-primary); }
.fault-list { display: flex; flex-direction: column; gap: 12px; }
.fault-item { display: flex; align-items: flex-start; gap: 10px; padding: 12px; background: var(--bg-secondary); border-radius: 10px; }
.fault-detail { font-size: 13px; color: var(--text-secondary); line-height: 1.6; }
</style>
