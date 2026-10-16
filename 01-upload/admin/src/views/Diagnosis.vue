<template>
  <div class="diag-page">
    <!-- 企业选择 -->
    <el-card shadow="never" style="margin-bottom:20px">
      <div class="toolbar">
        <span class="dim">诊断企业</span>
        <el-select v-model="enterpriseId" filterable placeholder="选择企业" style="width:280px" @change="loadData">
          <el-option v-for="e in enterprises" :key="e.id" :label="e.name || e.id" :value="e.id" />
        </el-select>
        <el-select v-model="days" style="width:120px" @change="loadData">
          <el-option label="近7天" :value="7" />
          <el-option label="近30天" :value="30" />
          <el-option label="近90天" :value="90" />
        </el-select>
        <el-button type="primary" :loading="loading" @click="loadData">重新诊断</el-button>
      </div>
    </el-card>

    <div v-loading="loading" element-loading-text="AI 诊断中...">
      <template v-if="result">
        <!-- KPI -->
        <div class="kpi-grid">
          <div class="kpi-card">
            <div class="kpi-num" :style="{ color: scoreColor }">{{ result.overallScore }}</div>
            <div class="kpi-lbl">{{ result.scoreLevel }} · 综合能效评分</div>
          </div>
          <div class="kpi-card">
            <div class="kpi-num" style="color:#34d399">{{ fmt(result.estimatedAnnualSaving) }} <span class="unit">元</span></div>
            <div class="kpi-lbl">预估年度可省</div>
          </div>
          <div class="kpi-card">
            <div class="kpi-num" style="color:#38bdf8">{{ result.estimatedSavingPercent }}<span class="unit">%</span></div>
            <div class="kpi-lbl">占电费比例</div>
          </div>
          <div class="kpi-card">
            <div class="kpi-num" style="color:#a78bfa">{{ result.deviceCount }}</div>
            <div class="kpi-lbl">评估设备数</div>
          </div>
        </div>

        <!-- 用电模式识别 -->
        <el-card shadow="never" style="margin-bottom:20px">
          <template #header>
            <div class="card-header"><span>用电模式识别</span><el-tag type="info" size="small">{{ lp.shape }}</el-tag></div>
          </template>
          <div class="lp-row">
            <div class="lp-chart"><div class="chart-wrap" ref="lpChartRef"></div></div>
            <div class="lp-stats">
              <div class="stat"><span class="sv">{{ fmt(lp.weekdayAvg) }}</span><span class="sl">工作日日均 kWh</span></div>
              <div class="stat"><span class="sv">{{ fmt(lp.weekendAvg) }}</span><span class="sl">周末日均 kWh</span></div>
              <div class="stat"><span class="sv">{{ fmt(lp.weekendRatio) }}%</span><span class="sl">周末/工作日比</span></div>
              <div class="stat"><span class="sv">{{ fmt(lp.peakToAvg) }}</span><span class="sl">峰均比</span></div>
              <div class="stat"><span class="sv">{{ lp.adjustabilityLevel }} · {{ lp.adjustabilityScore }}</span><span class="sl">负荷可调性(分)</span></div>
              <div class="stat"><span class="sv">{{ lp.responseDelay }}</span><span class="sl">响应延迟</span></div>
              <div class="interpret">{{ lp.interpretation }}</div>
              <div class="interpret" v-if="lp.distributedCoordination" style="margin-top:10px;border-left:3px solid #38bdf8">{{ lp.distributedCoordination }}</div>
            </div>
          </div>
        </el-card>

        <!-- 需求响应 DR 策略 -->
        <el-card shadow="never" style="margin-bottom:20px">
          <template #header><span>需求响应(DR)策略 · {{ drStrategies.length }} 条</span></template>
          <div class="strategy-list">
            <div class="strategy-item" v-for="(s, i) in drStrategies" :key="i">
              <div class="si-head">
                <el-tag :type="s.priority === 'high' ? 'danger' : s.priority === 'medium' ? 'warning' : 'info'" size="small">{{ s.priority }}</el-tag>
                <b class="si-type">{{ s.type }}</b>
                <span class="si-save">月省 {{ fmt(s.estimatedMonthlySaving) }} 元</span>
              </div>
              <div class="si-target">目标：{{ s.target }}</div>
              <div class="si-action">动作：{{ s.action }}</div>
            </div>
          </div>
        </el-card>

        <!-- 峰谷优化 -->
        <el-card shadow="never" style="margin-bottom:20px" v-if="pv">
          <template #header><span>峰谷用电优化</span></template>
          <el-descriptions :column="3" border size="small">
            <el-descriptions-item label="峰电占比">{{ pv.peakRatio }}%</el-descriptions-item>
            <el-descriptions-item label="谷电占比">{{ pv.valleyRatio }}%</el-descriptions-item>
            <el-descriptions-item label="峰谷转移月省">{{ fmt(pv.potentialMonthlySaving) }} 元</el-descriptions-item>
            <el-descriptions-item label="评估" :span="3">{{ pv.assessment }}</el-descriptions-item>
          </el-descriptions>
          <div class="shift-tips" v-if="pv.shiftSuggestions && pv.shiftSuggestions.length">
            <div v-for="(t, i) in pv.shiftSuggestions" :key="i" class="shift-tip">· {{ t }}</div>
          </div>
        </el-card>

        <!-- 优化建议 -->
        <el-card shadow="never">
          <template #header><span>优化建议 · {{ recommendations.length }} 条</span></template>
          <div class="rec-list">
            <div class="rec-item" v-for="(r, i) in recommendations" :key="i">
              <div class="ri-head">
                <span class="ri-prio">#{{ r.priority }}</span>
                <el-tag :type="sevType(r.severity)" size="small">{{ r.severity }}</el-tag>
                <b>{{ r.title }}</b>
                <span class="ri-cat">{{ r.category }}</span>
                <span class="ri-save" v-if="r.estimatedSaving">省 {{ r.estimatedSaving }}</span>
              </div>
              <div class="ri-desc">{{ r.description }}</div>
            </div>
          </div>
        </el-card>

        <!-- 分布式协同调度 · 多企业可调负荷聚合优化器（吸收 CN120767796B 分布式协同，独立实现） -->
        <el-card shadow="never" style="margin-top:20px">
          <template #header>
            <div class="card-header">
              <span>分布式协同调度 · 多企业可调负荷聚合</span>
              <div style="display:flex;gap:8px;align-items:center">
                <el-input-number v-model="coordTarget" :min="100" :step="100" size="small" style="width:150px" />
                <span class="dim">kW 目标削减</span>
                <el-button type="primary" size="small" :loading="coordLoading" @click="loadCoordination">调度</el-button>
              </div>
            </div>
          </template>
          <div v-if="coord">
            <div class="kpi-grid" style="grid-template-columns:repeat(4,1fr)">
              <div class="kpi-card"><div class="kpi-num" style="color:#38bdf8">{{ fmt(coord.regionalAdjustableCapacityKw) }}<span class="unit">kW</span></div><div class="kpi-lbl">区域可调容量</div></div>
              <div class="kpi-card"><div class="kpi-num" style="color:#34d399">{{ fmt(coord.totalAllocatedKw) }}<span class="unit">kW</span></div><div class="kpi-lbl">已分配削减</div></div>
              <div class="kpi-card"><div class="kpi-num" :style="{color: coord.shortfallKw>0?'#f87171':'#34d399'}">{{ fmt(coord.shortfallKw) }}<span class="unit">kW</span></div><div class="kpi-lbl">缺口</div></div>
              <div class="kpi-card"><div class="kpi-num" style="color:#a78bfa">{{ fmt(coord.fulfilledPct) }}<span class="unit">%</span></div><div class="kpi-lbl">目标达成率</div></div>
            </div>
            <el-table :data="coord.nodes" size="small" border style="margin-top:14px">
              <el-table-column prop="name" label="协同成员" min-width="190">
                <template #default="{row}">
                  <span>{{ row.name }}</span>
                  <el-tag v-if="row.isCurrentUser" size="small" type="success" style="margin-left:6px">我</el-tag>
                  <el-tag v-if="row.synthetic" size="small" type="info" style="margin-left:6px">示例</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="可调负荷(kW)" prop="adjustableLoadKw" width="120" align="right" />
              <el-table-column label="响应延迟(min)" prop="responseDelayMin" width="120" align="right" />
              <el-table-column label="可调性" width="92" align="center">
                <template #default="{row}">{{ row.adjustabilityLevel }}·{{ row.adjustabilityScore }}</template>
              </el-table-column>
              <el-table-column label="分配(kW)" prop="allocatedKw" width="110" align="right" />
              <el-table-column label="本地决策" min-width="230">
                <template #default="{row}"><span class="coord-action">{{ row.action }}</span></template>
              </el-table-column>
            </el-table>
            <div class="interpret" style="margin-top:12px">{{ coord.algorithmNote }}</div>
          </div>
          <el-empty v-else :image-size="60" description="点击「调度」生成区域协同方案" />
        </el-card>
      </template>
      <el-empty v-else-if="!loading" description="暂无诊断数据，请选择企业" />
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, onUnmounted, nextTick } from 'vue'
import * as echarts from 'echarts'
import { diagnosisAPI } from '../api/diagnosis'

const enterprises = ref([])
const enterpriseId = ref('')
const days = ref(30)
const loading = ref(false)
const result = ref(null)
const lpChartRef = ref(null)
let chart = null

// 分布式协同调度
const coordTarget = ref(1500)
const coordLoading = ref(false)
const coord = ref(null)
const loadCoordination = async () => {
  coordLoading.value = true
  try {
    const res = await diagnosisAPI.aggregateCoordination({ targetReductionKw: coordTarget.value, windowMinutes: 60 })
    coord.value = res?.data || res || null
  } catch (e) { /* interceptor handled */ } finally {
    coordLoading.value = false
  }
}

const lp = computed(() => result.value?.loadPatterns || {})
const drStrategies = computed(() => result.value?.drStrategies || [])
const recommendations = computed(() => result.value?.recommendations || [])
const pv = computed(() => result.value?.peakValleyAnalysis || null)
const scoreColor = computed(() => {
  const s = result.value?.overallScore || 0
  return s >= 80 ? '#34d399' : s >= 60 ? '#fbbf24' : s >= 40 ? '#fb923c' : '#f87171'
})

const fmt = (n, d = 0) => {
  if (n == null) return '--'
  return Number(n).toLocaleString('zh-CN', { maximumFractionDigits: d })
}
const sevType = (s) => ({ critical: 'danger', warning: 'warning', info: 'info' }[s] || 'info')

const loadEnterprises = async () => {
  try {
    const res = await diagnosisAPI.getEnterprises()
    const list = res?.data || res || []
    enterprises.value = Array.isArray(list) ? list : []
    if (enterprises.value.length && !enterpriseId.value) {
      enterpriseId.value = enterprises.value[0].id
    }
  } catch (e) { /* ignore */ }
}

const loadData = async () => {
  if (!enterpriseId.value) return
  loading.value = true
  try {
    const res = await diagnosisAPI.run({ enterpriseId: enterpriseId.value, days: days.value })
    result.value = res?.data || res || null
    await nextTick()
    renderLpChart()
    loadCoordination()
  } catch (e) { /* interceptor handled */ } finally {
    loading.value = false
  }
}

const renderLpChart = () => {
  if (chart) { chart.dispose(); chart = null }
  if (!lpChartRef.value || !lp.value?.loadCurve) return
  const curve = lp.value.loadCurve
  const hours = curve.map((_, i) => `${i}:00`)
  chart = echarts.init(lpChartRef.value, null, { width: lpChartRef.value.clientWidth, height: 260 })
  chart.setOption({
    backgroundColor: 'transparent',
    tooltip: { trigger: 'axis' },
    grid: { left: '3%', right: '4%', bottom: '3%', top: 16, containLabel: true },
    xAxis: { type: 'category', data: hours, axisLine: { lineStyle: { color: '#334155' } }, axisLabel: { color: '#94a3b8', interval: 2 } },
    yAxis: { type: 'value', splitLine: { lineStyle: { color: '#334155', type: 'dashed' } }, axisLabel: { color: '#94a3b8' } },
    series: [{
      type: 'bar', data: curve,
      itemStyle: { color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [{ offset: 0, color: '#38bdf8' }, { offset: 1, color: 'rgba(56,189,248,.2)' }]), borderRadius: [3, 3, 0, 0] },
    }],
  })
}

const handleResize = () => chart?.resize()
onMounted(() => { loadEnterprises().then(loadData); window.addEventListener('resize', handleResize) })
onUnmounted(() => { window.removeEventListener('resize', handleResize); if (chart) chart.dispose() })
</script>

<style scoped>
.toolbar { display: flex; align-items: center; gap: 12px; }
.dim { font-size: 12px; color: var(--text-secondary); }
.kpi-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px; margin-bottom: 20px; }
.kpi-card { text-align: center; padding: 20px; background: var(--bg-secondary); border: 1px solid var(--border); border-radius: 12px; }
.kpi-num { font-size: 28px; font-weight: 700; }
.kpi-num .unit { font-size: 12px; font-weight: 400; color: var(--text-secondary); }
.kpi-lbl { font-size: 12px; color: var(--text-secondary); margin-top: 4px; }
.card-header { display: flex; align-items: center; justify-content: space-between; }
.lp-row { display: flex; gap: 20px; }
.lp-chart { flex: 1.4; }
.chart-wrap { width: 100%; height: 260px; }
.lp-stats { flex: 1; display: flex; flex-direction: column; gap: 14px; }
.stat { display: flex; flex-direction: column; }
.sv { font-size: 22px; font-weight: 700; color: var(--accent); }
.sl { font-size: 12px; color: var(--text-secondary); }
.interpret { font-size: 13px; color: var(--text-secondary); line-height: 1.7; background: var(--bg-secondary); padding: 14px; border-radius: 10px; }
.strategy-list, .rec-list { display: flex; flex-direction: column; gap: 14px; }
.strategy-item, .rec-item { background: var(--bg-secondary); border: 1px solid var(--border); border-radius: 10px; padding: 14px 16px; }
.si-head, .ri-head { display: flex; align-items: center; gap: 10px; margin-bottom: 8px; flex-wrap: wrap; }
.si-type { font-size: 15px; }
.si-save { margin-left: auto; color: #34d399; font-weight: 600; }
.si-target, .si-action, .ri-desc { font-size: 13px; color: var(--text-secondary); line-height: 1.6; }
.ri-prio { color: var(--accent); font-weight: 700; }
.ri-cat { font-size: 11px; color: var(--text-secondary); background: var(--bg-tertiary); padding: 2px 8px; border-radius: 8px; }
.ri-save { margin-left: auto; color: #34d399; font-weight: 600; }
.shift-tips { margin-top: 12px; display: flex; flex-direction: column; gap: 6px; }
.shift-tip { font-size: 13px; color: var(--text-secondary); }
.coord-action { font-size: 13px; color: #fbbf24; }
</style>
