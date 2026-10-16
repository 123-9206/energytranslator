<template>
  <div class="bill-check-page">
    <div class="page-header">
      <h2>电费体检复核台</h2>
      <div class="filters">
        <el-select v-model="statusFilter" placeholder="全部状态" clearable style="width: 200px" @change="loadList">
          <el-option v-for="s in statusOptions" :key="s.value" :label="s.label" :value="s.value" />
        </el-select>
        <el-button type="primary" @click="loadList">刷新</el-button>
      </div>
    </div>

    <!-- 漏斗看板 -->
    <div class="funnel-cards" v-if="funnel">
      <div class="funnel-card">
        <div class="fc-num">{{ funnel.uploaded }}</div>
        <div class="fc-label">已上传</div>
      </div>
      <div class="funnel-arrow">→</div>
      <div class="funnel-card">
        <div class="fc-num">{{ funnel.readOk }}</div>
        <div class="fc-label">读单成功</div>
        <div class="fc-rate">{{ funnel.readOkRate }}%</div>
      </div>
      <div class="funnel-arrow">→</div>
      <div class="funnel-card">
        <div class="fc-num">{{ funnel.reviewPassed }}</div>
        <div class="fc-label">复核通过</div>
        <div class="fc-rate">{{ funnel.reviewPassRate }}%</div>
      </div>
      <div class="funnel-arrow">→</div>
      <div class="funnel-card">
        <div class="fc-num">{{ funnel.delivered }}</div>
        <div class="fc-label">已交付</div>
        <div class="fc-rate">{{ funnel.deliverRate }}%</div>
      </div>
      <div class="funnel-side">
        <el-tag v-if="funnel.pendingReview > 0" type="warning" size="small">待复核 {{ funnel.pendingReview }}</el-tag>
        <el-tag v-if="funnel.needsManualRead > 0" type="danger" size="small">需人工录入 {{ funnel.needsManualRead }}</el-tag>
        <span v-if="funnel.avgMonthlySaving > 0" class="fc-saving">客均月省 ¥{{ fmtNum(funnel.avgMonthlySaving) }}</span>
      </div>
    </div>

    <el-table :data="list" v-loading="loading" stripe>
      <el-table-column prop="createdAt" label="提交时间" width="170">
        <template #default="{ row }">{{ fmtTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column prop="factoryName" label="工厂" min-width="120">
        <template #default="{ row }">{{ row.factoryName || row.contactName || '-' }}</template>
      </el-table-column>
      <el-table-column prop="contactPhone" label="手机号" width="130" />
      <el-table-column label="状态" width="150">
        <template #default="{ row }">
          <el-tag :type="statusTag(row.status).type">{{ statusTag(row.status).label }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="勾稽校验" width="100">
        <template #default="{ row }">
          <el-tag v-if="row.validationPassed === true" type="success" size="small">通过</el-tag>
          <el-tag v-else-if="row.validationPassed === false" type="danger" size="small">未过</el-tag>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="月节省估算" width="120" align="right">
        <template #default="{ row }">
          <span v-if="row.totalMonthlySavingEstimate">¥{{ fmtNum(row.totalMonthlySavingEstimate) }}</span>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="100" fixed="right">
        <template #default="{ row }">
          <el-button size="small" type="primary" link @click="openDetail(row)">复核</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-model:current-page="page"
      :page-size="size"
      :total="total"
      layout="total, prev, pager, next"
      style="margin-top: 16px; justify-content: flex-end"
      @current-change="loadList"
    />

    <!-- 复核抽屉 -->
    <el-drawer v-model="drawerVisible" size="72%" :title="'复核：' + (current?.factoryName || current?.contactPhone || '')">
      <div v-if="current" class="review-body">
        <el-row :gutter="20">
          <!-- 左：原图 -->
          <el-col :span="9">
            <h4>电费单原图</h4>
            <img v-if="imgSrc" :src="imgSrc" class="bill-img" alt="电费单" />
            <el-empty v-else description="无图片" :image-size="80" />
            <el-descriptions :column="1" border size="small" style="margin-top: 12px">
              <el-descriptions-item label="单号">{{ current.id }}</el-descriptions-item>
              <el-descriptions-item label="手机号">{{ current.contactPhone }}</el-descriptions-item>
              <el-descriptions-item label="联系人">{{ current.contactName || '-' }}</el-descriptions-item>
              <el-descriptions-item label="AI模型">{{ current.aiModel || '未调用' }}</el-descriptions-item>
            </el-descriptions>

            <h4 style="margin-top: 16px">勾稽校验</h4>
            <div v-if="current.checkItems?.length">
              <div v-for="(c, i) in current.checkItems" :key="i" class="check-item">
                <el-tag :type="c.passed ? 'success' : 'danger'" size="small">{{ c.passed ? '过' : '挂' }}</el-tag>
                <span class="check-name">{{ c.name }}</span>
                <span class="check-vals">期望 {{ c.expected }} / 实际 {{ c.actual }}<template v-if="c.diffPct != null">（差 {{ c.diffPct }}%）</template></span>
              </div>
            </div>
            <el-text v-else type="info" size="small">无校验记录</el-text>
          </el-col>

          <!-- 右：账单字段 + 分析 + 报告 -->
          <el-col :span="15">
            <div class="section-title">
              <h4>账单字段（可人工修正后重跑）</h4>
              <el-button size="small" type="warning" @click="rerun" :loading="rerunning">保存字段并重跑分析</el-button>
            </div>
            <el-form label-width="110px" size="small" class="bill-form">
              <el-row :gutter="10">
                <el-col :span="12"><el-form-item label="账期"><el-input v-model="form.billMonth" placeholder="2026-08" /></el-form-item></el-col>
                <el-col :span="12"><el-form-item label="户名"><el-input v-model="form.customerName" /></el-form-item></el-col>
                <el-col :span="12">
                  <el-form-item label="电价类别">
                    <el-select v-model="form.meterType" clearable style="width: 100%">
                      <el-option label="两部制" value="TWO_PART" /><el-option label="单一制" value="SINGLE" />
                    </el-select>
                  </el-form-item>
                </el-col>
                <el-col :span="12">
                  <el-form-item label="计价方式">
                    <el-select v-model="form.pricingMode" clearable style="width: 100%">
                      <el-option label="按容量" value="CAPACITY" /><el-option label="按核定需量" value="DEMAND" /><el-option label="按实际需量" value="ACTUAL_DEMAND" />
                    </el-select>
                  </el-form-item>
                </el-col>
                <el-col :span="12"><el-form-item label="合同容量kVA"><el-input-number v-model="form.contractCapacityKva" :controls="false" style="width: 100%" /></el-form-item></el-col>
                <el-col :span="12"><el-form-item label="核定需量kW"><el-input-number v-model="form.demandValueKw" :controls="false" style="width: 100%" /></el-form-item></el-col>
                <el-col :span="12"><el-form-item label="实际需量kW"><el-input-number v-model="form.maxDemandKw" :controls="false" style="width: 100%" /></el-form-item></el-col>
                <el-col :span="12"><el-form-item label="功率因数"><el-input-number v-model="form.powerFactor" :controls="false" :step="0.01" style="width: 100%" /></el-form-item></el-col>
                <el-col :span="12"><el-form-item label="尖峰kWh"><el-input-number v-model="form.tipKwh" :controls="false" style="width: 100%" /></el-form-item></el-col>
                <el-col :span="12"><el-form-item label="高峰kWh"><el-input-number v-model="form.peakKwh" :controls="false" style="width: 100%" /></el-form-item></el-col>
                <el-col :span="12"><el-form-item label="平段kWh"><el-input-number v-model="form.flatKwh" :controls="false" style="width: 100%" /></el-form-item></el-col>
                <el-col :span="12"><el-form-item label="低谷kWh"><el-input-number v-model="form.valleyKwh" :controls="false" style="width: 100%" /></el-form-item></el-col>
                <el-col :span="12"><el-form-item label="总电量kWh"><el-input-number v-model="form.totalKwh" :controls="false" style="width: 100%" /></el-form-item></el-col>
                <el-col :span="12"><el-form-item label="基本电费元"><el-input-number v-model="form.basicFee" :controls="false" style="width: 100%" /></el-form-item></el-col>
                <el-col :span="12"><el-form-item label="电度电费元"><el-input-number v-model="form.energyFee" :controls="false" style="width: 100%" /></el-form-item></el-col>
                <el-col :span="12"><el-form-item label="力调电费元"><el-input-number v-model="form.powerFactorAdjustmentFee" :controls="false" style="width: 100%" /></el-form-item></el-col>
                <el-col :span="12"><el-form-item label="总电费元"><el-input-number v-model="form.totalAmount" :controls="false" style="width: 100%" /></el-form-item></el-col>
              </el-row>
            </el-form>

            <h4>分析发现</h4>
            <div v-if="current.findings?.length" class="findings">
              <div v-for="(f, i) in current.findings" :key="i" class="finding-item">
                <el-tag :type="sevTag(f.severity)" size="small">{{ f.severity }}</el-tag>
                <b>{{ f.title }}</b>
                <span v-if="f.monthlySavingEstimate > 0" class="saving">月省约 ¥{{ fmtNum(f.monthlySavingEstimate) }}</span>
                <div class="finding-detail">{{ f.detail }}</div>
                <div class="finding-basis">依据：{{ f.basis }}</div>
              </div>
            </div>
            <el-text v-else type="info" size="small">无分析结果</el-text>

            <div class="section-title" style="margin-top: 16px">
              <h4>报告正文（可微调措辞，数字以系统计算为准）</h4>
            </div>
            <el-input v-model="finalReportText" type="textarea" :rows="10" />

            <div class="actions">
              <el-input v-model="reviewNote" placeholder="复核备注（选填）" style="width: 280px" size="small" />
              <el-button type="success" @click="approve" :disabled="current.status === 'APPROVED' || current.status === 'DELIVERED'">复核通过</el-button>
              <el-button type="primary" @click="deliver" :disabled="current.status !== 'APPROVED'">交付客户</el-button>
              <el-button type="danger" @click="reject">作废</el-button>
            </div>
          </el-col>
        </el-row>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { billCheckAPI } from '../api/billCheck'
import request from '../api/request'

const list = ref([])
const loading = ref(false)
const funnel = ref(null)
const page = ref(1)
const size = 20
const total = ref(0)
const statusFilter = ref('')

const drawerVisible = ref(false)
const current = ref(null)
const form = ref({})
const finalReportText = ref('')
const reviewNote = ref('')
const rerunning = ref(false)
const imgSrc = ref('')

const statusOptions = [
  { value: 'READING', label: '读单中' },
  { value: 'PENDING_REVIEW', label: '待复核' },
  { value: 'NEEDS_MANUAL_READ', label: '需人工录入' },
  { value: 'APPROVED', label: '复核通过' },
  { value: 'DELIVERED', label: '已交付' },
  { value: 'REJECTED', label: '已作废' },
]

function statusTag(s) {
  const map = {
    READING: { label: '读单中', type: 'info' },
    PENDING_REVIEW: { label: '待复核', type: 'warning' },
    NEEDS_MANUAL_READ: { label: '需人工录入', type: 'danger' },
    APPROVED: { label: '复核通过', type: 'success' },
    DELIVERED: { label: '已交付', type: 'success' },
    REJECTED: { label: '已作废', type: 'info' },
  }
  return map[s] || { label: s, type: 'info' }
}

function sevTag(s) {
  return { HIGH: 'danger', MEDIUM: 'warning', LOW: 'info', INFO: 'success' }[s] || 'info'
}

function fmtTime(t) {
  return t ? new Date(t).toLocaleString('zh-CN', { hour12: false }) : '-'
}

function fmtNum(n) {
  return Number(n).toLocaleString('zh-CN', { maximumFractionDigits: 0 })
}

async function loadList() {
  loading.value = true
  try {
    const res = await billCheckAPI.list({
      status: statusFilter.value || undefined,
      page: page.value - 1,
      size,
    })
    list.value = res.data?.list || []
    total.value = res.data?.total || 0
  } finally {
    loading.value = false
  }
  loadFunnel()
}

async function loadFunnel() {
  try {
    const res = await billCheckAPI.funnel()
    funnel.value = res.data
  } catch (e) { /* 看板失败不影响列表 */ }
}

async function openDetail(row) {
  const res = await billCheckAPI.detail(row.id)
  current.value = res.data
  form.value = { ...(res.data.billData || {}) }
  finalReportText.value = res.data.reportText || ''
  reviewNote.value = res.data.reviewerNote || ''
  imgSrc.value = ''
  drawerVisible.value = true
  loadImage(row.id)
}

async function loadImage(id) {
  try {
    const token = localStorage.getItem('admin_token')
    const resp = await fetch(`/api/admin/bill-check/${id}/image`, {
      headers: { Authorization: `Bearer ${token}` },
    })
    if (resp.ok) {
      imgSrc.value = URL.createObjectURL(await resp.blob())
    }
  } catch (e) { /* 无图可忽略 */ }
}

async function rerun() {
  rerunning.value = true
  try {
    const res = await billCheckAPI.rerun(current.value.id, form.value)
    current.value = res.data
    finalReportText.value = res.data.reportText || ''
    ElMessage.success(res.data.validationPassed ? '校验通过，已重新生成分析与报告' : '校验未通过，请检查勾稽项')
    loadList()
  } finally {
    rerunning.value = false
  }
}

async function approve() {
  const res = await billCheckAPI.approve(current.value.id, reviewNote.value, finalReportText.value)
  current.value = res.data
  ElMessage.success('已复核通过')
  loadList()
}

async function deliver() {
  await ElMessageBox.confirm('交付后客户即可凭单号+手机号查看报告，确认交付？', '确认交付')
  const res = await billCheckAPI.deliver(current.value.id)
  current.value = res.data
  ElMessage.success('已交付')
  loadList()
}

async function reject() {
  await ElMessageBox.confirm('确认作废该体检单？', '确认作废', { type: 'warning' })
  const res = await billCheckAPI.reject(current.value.id, reviewNote.value)
  current.value = res.data
  ElMessage.success('已作废')
  loadList()
}

onMounted(loadList)
</script>

<style scoped>
.bill-check-page { padding: 20px; }
.funnel-cards { display: flex; align-items: center; gap: 10px; margin-bottom: 16px; flex-wrap: wrap; }
.funnel-card { background: #f6f8fa; border: 1px solid #e4e7ed; border-radius: 8px; padding: 10px 18px; text-align: center; min-width: 100px; }
.fc-num { font-size: 22px; font-weight: 700; color: #303133; }
.fc-label { font-size: 12px; color: #909399; margin-top: 2px; }
.fc-rate { font-size: 11px; color: #67c23a; margin-top: 2px; }
.funnel-arrow { color: #c0c4cc; font-size: 16px; }
.funnel-side { display: flex; align-items: center; gap: 8px; margin-left: 8px; }
.fc-saving { color: #c0392b; font-size: 13px; font-weight: 600; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.page-header h2 { margin: 0; font-size: 18px; }
.filters { display: flex; gap: 10px; }
.review-body { padding: 0 8px 20px; }
.bill-img { width: 100%; border-radius: 8px; border: 1px solid #eee; }
.section-title { display: flex; justify-content: space-between; align-items: center; margin: 12px 0 8px; }
.section-title h4, .review-body h4 { margin: 0 0 8px; font-size: 14px; }
.check-item { display: flex; align-items: center; gap: 6px; font-size: 12px; margin-bottom: 6px; flex-wrap: wrap; }
.check-vals { color: #888; }
.finding-item { border: 1px solid #eee; border-radius: 8px; padding: 10px; margin-bottom: 8px; font-size: 13px; }
.finding-item b { margin: 0 6px; }
.saving { color: #c0392b; font-weight: 600; }
.finding-detail { margin-top: 6px; color: #444; line-height: 1.6; }
.finding-basis { margin-top: 4px; color: #999; font-size: 12px; }
.actions { display: flex; gap: 10px; margin-top: 16px; align-items: center; }
.bill-form :deep(.el-form-item) { margin-bottom: 10px; }
</style>
