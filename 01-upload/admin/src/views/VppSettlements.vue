<template>
  <div class="vpp-settle-page">
    <!-- KPI -->
    <div class="kpi-grid">
      <div class="kpi-card" v-for="c in kpis" :key="c.label">
        <div class="kpi-num" :style="{ color: c.color }">{{ c.value }} <span class="unit">{{ c.unit }}</span></div>
        <div class="kpi-lbl">{{ c.label }}</div>
      </div>
    </div>

    <!-- 工具栏 -->
    <el-card shadow="never" style="margin-bottom: 20px">
      <div class="toolbar">
        <el-select v-model="query.status" placeholder="结算状态" clearable style="width: 150px" @change="loadList(1)">
          <el-option label="待审批" value="pending" />
          <el-option label="已审批" value="approved" />
          <el-option label="已付款" value="paid" />
        </el-select>
        <span class="dim">共 {{ total }} 张结算单</span>
      </div>
    </el-card>

    <!-- 结算单列表 -->
    <el-card shadow="never">
      <template #header><span>虚拟电厂结算单</span></template>
      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column prop="enterpriseName" label="企业名称" min-width="150" show-overflow-tooltip />
        <el-table-column prop="period" label="结算周期" width="100" align="center" />
        <el-table-column label="活动数" width="80" align="center">
          <template #default="{ row }">{{ row.summary?.totalActivities ?? 0 }}</template>
        </el-table-column>
        <el-table-column label="承诺容量" width="110" align="right">
          <template #default="{ row }">{{ fmt(row.summary?.totalCapacity) }} <span class="dim">kW</span></template>
        </el-table-column>
        <el-table-column label="响应电量" width="110" align="right">
          <template #default="{ row }">{{ fmt(row.summary?.totalEnergy) }} <span class="dim">kWh</span></template>
        </el-table-column>
        <el-table-column label="容量补偿" width="110" align="right">
          <template #default="{ row }">¥{{ fmt(row.summary?.capacityCompensation) }}</template>
        </el-table-column>
        <el-table-column label="电量补偿" width="110" align="right">
          <template #default="{ row }">¥{{ fmt(row.summary?.energyCompensation) }}</template>
        </el-table-column>
        <el-table-column label="奖励/扣罚" width="110" align="right">
          <template #default="{ row }">
            <span :style="{ color: ((row.summary?.bonusCompensation||0) - (row.summary?.penaltyAmount||0)) >= 0 ? '#34d399' : '#f87171' }">
              {{ ((row.summary?.bonusCompensation||0) - (row.summary?.penaltyAmount||0)) >= 0 ? '+' : '' }}{{ fmt((row.summary?.bonusCompensation||0) - (row.summary?.penaltyAmount||0)) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="合计" width="120" align="right">
          <template #default="{ row }">
            <b style="color:var(--accent)">¥{{ fmt(row.summary?.totalAmount) }}</b>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDetail(row)">详情</el-button>
            <el-button v-if="row.status === 'pending'" link type="success" size="small" @click="handleApprove(row)">审批</el-button>
            <el-button v-if="row.status === 'approved'" link type="warning" size="small" @click="handlePay(row)">确认付款</el-button>
            <el-button link type="danger" size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pager">
        <el-pagination
          v-model:current-page="query.page"
          v-model:page-size="query.pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @current-change="loadList()"
          @size-change="loadList(1)"
        />
      </div>
    </el-card>

    <!-- 详情抽屉 -->
    <el-drawer v-model="detailVisible" title="结算单详情" size="560px">
      <template v-if="current">
        <el-descriptions :column="2" border size="small" style="margin-bottom:16px">
          <el-descriptions-item label="企业">{{ current.enterpriseName }}</el-descriptions-item>
          <el-descriptions-item label="结算周期">{{ current.period }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="statusType(current.status)" size="small">{{ statusLabel(current.status) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="审批人">{{ current.approvedBy || '--' }}</el-descriptions-item>
        </el-descriptions>

        <div class="section-title">补偿汇总</div>
        <el-descriptions :column="2" border size="small" style="margin-bottom:16px">
          <el-descriptions-item label="活动数">{{ current.summary?.totalActivities ?? 0 }}</el-descriptions-item>
          <el-descriptions-item label="总承诺容量">{{ fmt(current.summary?.totalCapacity) }} kW</el-descriptions-item>
          <el-descriptions-item label="总响应电量">{{ fmt(current.summary?.totalEnergy) }} kWh</el-descriptions-item>
          <el-descriptions-item label="容量补偿">¥{{ fmt(current.summary?.capacityCompensation) }}</el-descriptions-item>
          <el-descriptions-item label="电量补偿">¥{{ fmt(current.summary?.energyCompensation) }}</el-descriptions-item>
          <el-descriptions-item label="完成奖励">¥{{ fmt(current.summary?.bonusCompensation) }}</el-descriptions-item>
          <el-descriptions-item label="未完成扣罚">-¥{{ fmt(current.summary?.penaltyAmount) }}</el-descriptions-item>
          <el-descriptions-item label="合计应付">
            <b style="color:var(--accent)">¥{{ fmt(current.summary?.totalAmount) }}</b>
          </el-descriptions-item>
        </el-descriptions>

        <div class="section-title">活动明细</div>
        <el-table :data="current.items || []" size="small" stripe>
          <el-table-column prop="activityTitle" label="活动" min-width="140" show-overflow-tooltip />
          <el-table-column label="承诺/实际(kW)" width="110" align="right">
            <template #default="{ row }">{{ fmt(row.committedCapacity) }} / {{ fmt(row.actualCapacity) }}</template>
          </el-table-column>
          <el-table-column label="完成率" width="80" align="right">
            <template #default="{ row }">{{ fmt(row.completionRate * 100, 0) }}%</template>
          </el-table-column>
          <el-table-column label="小计" width="100" align="right">
            <template #default="{ row }">¥{{ fmt(row.total) }}</template>
          </el-table-column>
        </el-table>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { vppSettlementAPI } from '../api/vppSettlements'

const list = ref([])
const total = ref(0)
const loading = ref(false)
const detailVisible = ref(false)
const current = ref(null)

const query = reactive({ status: '', page: 1, pageSize: 10 })

const kpis = reactive([
  { label: '结算单总数', value: 0, unit: '张', color: '#38bdf8' },
  { label: '待审批', value: 0, unit: '张', color: '#fbbf24' },
  { label: '已审批', value: 0, unit: '张', color: '#a78bfa' },
  { label: '已付款', value: 0, unit: '张', color: '#34d399' },
  { label: '累计应付', value: 0, unit: '万元', color: '#f87171' },
])

const fmt = (n, d = 0) => {
  if (n == null) return '--'
  return Number(n).toLocaleString('zh-CN', { maximumFractionDigits: d })
}
const statusLabel = (s) => ({ pending: '待审批', approved: '已审批', paid: '已付款' }[s] || s)
const statusType = (s) => ({ pending: 'warning', approved: 'primary', paid: 'success' }[s] || 'info')

const loadList = async (page) => {
  if (page) query.page = page
  loading.value = true
  try {
    const res = await vppSettlementAPI.getAllSettlements({
      status: query.status || undefined,
      page: query.page,
      pageSize: query.pageSize,
    })
    const d = res?.data || res || {}
    list.value = d.list || []
    total.value = d.total || 0
    computeKpi()
  } catch (e) { /* interceptor handled */ } finally {
    loading.value = false
  }
}

const computeKpi = async () => {
  // 全量统计需要遍历，这里用最近200条近似统计
  try {
    const res = await vppSettlementAPI.getAllSettlements({ page: 1, pageSize: 200 })
    const d = res?.data || res || {}
    const rows = d.list || []
    let pending = 0, approved = 0, paid = 0, amount = 0
    rows.forEach(r => {
      if (r.status === 'pending') pending++
      else if (r.status === 'approved') approved++
      else if (r.status === 'paid') paid++
      amount += r.summary?.totalAmount || 0
    })
    kpis[0].value = d.total || rows.length
    kpis[1].value = pending
    kpis[2].value = approved
    kpis[3].value = paid
    kpis[4].value = fmt(amount / 10000, 1)
  } catch (e) { /* ignore */ }
}

const openDetail = (row) => {
  current.value = row
  detailVisible.value = true
}

const handleApprove = async (row) => {
  try {
    await ElMessageBox.confirm(`确认审批通过「${row.enterpriseName}」${row.period} 的结算单（¥${fmt(row.summary?.totalAmount)}）？`, '审批确认', { type: 'warning' })
    await vppSettlementAPI.approveSettlement(row.id)
    ElMessage.success('结算单已审批')
    loadList()
  } catch (e) { /* cancel or error */ }
}

const handlePay = async (row) => {
  try {
    await ElMessageBox.confirm(`确认已向「${row.enterpriseName}」支付 ¥${fmt(row.summary?.totalAmount)}？`, '付款确认', { type: 'warning' })
    await vppSettlementAPI.paySettlement(row.id)
    ElMessage.success('结算单已标记付款')
    loadList()
  } catch (e) { /* cancel or error */ }
}

const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm(`确认删除 ${row.enterpriseName} ${row.period} 的结算单？`, '删除确认', { type: 'warning' })
    await vppSettlementAPI.deleteSettlement(row.id)
    ElMessage.success('结算单已删除')
    loadList()
  } catch (e) { /* cancel or error */ }
}

onMounted(() => {
  loadList()
  computeKpi()
})
</script>

<style scoped>
.kpi-grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
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
.toolbar { display: flex; align-items: center; gap: 12px; }
.pager { display: flex; justify-content: flex-end; margin-top: 16px; }
.dim { font-size: 11px; color: var(--text-secondary); }
.section-title { font-size: 13px; font-weight: 600; margin: 12px 0 8px; color: var(--text-primary); }
</style>
