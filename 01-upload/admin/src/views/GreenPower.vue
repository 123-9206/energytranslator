<template>
  <div class="green-power-page">
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
        <el-input v-model="query.keyword" placeholder="企业名称 / 合同编号" clearable style="width: 220px" @keyup.enter="loadList(1)" @clear="loadList(1)">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-select v-model="query.status" placeholder="合同状态" clearable style="width: 140px" @change="loadList(1)">
          <el-option label="草稿" value="draft" />
          <el-option label="生效中" value="active" />
          <el-option label="已完成" value="completed" />
          <el-option label="已终止" value="terminated" />
        </el-select>
        <el-button type="primary" @click="loadList(1)"><el-icon><Search /></el-icon>查询</el-button>
        <div class="spacer"></div>
        <el-button type="primary" @click="openDialog()"><el-icon><Plus /></el-icon>新增绿电合同</el-button>
      </div>
    </el-card>

    <!-- 合同列表 -->
    <el-card shadow="never">
      <template #header><span>绿电交易台账</span></template>
      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column prop="contractNo" label="合同编号" width="150" />
        <el-table-column prop="enterpriseName" label="企业名称" min-width="160" show-overflow-tooltip />
        <el-table-column prop="supplierName" label="供应商" min-width="140" show-overflow-tooltip />
        <el-table-column label="合同电量" width="120" align="right">
          <template #default="{ row }">{{ fmt(row.energyVolume) }} <span class="dim">kWh</span></template>
        </el-table-column>
        <el-table-column label="溢价" width="90" align="right">
          <template #default="{ row }">{{ (row.pricePremium || 0).toFixed(3) }} <span class="dim">元/kWh</span></template>
        </el-table-column>
        <el-table-column label="合同总额" width="120" align="right">
          <template #default="{ row }">¥{{ fmt(row.totalAmount) }}</template>
        </el-table-column>
        <el-table-column label="实际消纳" width="120" align="right">
          <template #default="{ row }">{{ fmt(row.actualVolume) }} <span class="dim">kWh</span></template>
        </el-table-column>
        <el-table-column label="碳减排" width="110" align="right">
          <template #default="{ row }">{{ fmt(row.carbonOffset, 1) }} <span class="dim">kg</span></template>
        </el-table-column>
        <el-table-column label="绿证" width="110" align="center">
          <template #default="{ row }">
            <span>{{ row.certificateCount || 0 }}张</span>
            <el-tag :type="row.certificateStatus === 'claimed' ? 'success' : 'info'" size="small" style="margin-left:4px">
              {{ row.certificateStatus === 'claimed' ? '已申领' : '未申领' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="170" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="openDialog(row)">编辑</el-button>
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

    <!-- 新增/编辑对话框 -->
    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑绿电合同' : '新增绿电合同'" width="640px" destroy-on-close>
      <el-form :model="form" :rules="rules" ref="formRef" label-width="110px">
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="企业" prop="enterpriseId">
              <el-select v-model="form.enterpriseId" filterable placeholder="选择企业" style="width: 100%" @change="onEnterpriseChange">
                <el-option v-for="e in enterprises" :key="e.id" :label="e.name" :value="e.id" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="供应商" prop="supplierName">
              <el-input v-model="form.supplierName" placeholder="风电场 / 售电公司名称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="合同电量(kWh)" prop="energyVolume">
              <el-input-number v-model="form.energyVolume" :min="0" :step="10000" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="绿电溢价" prop="pricePremium">
              <el-input-number v-model="form.pricePremium" :min="0" :max="1" :step="0.01" :precision="3" style="width: 100%" />
              <div class="dim">元/kWh（默认0.03）</div>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="开始日期" prop="startDate">
              <el-date-picker v-model="form.startDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="结束日期" prop="endDate">
              <el-date-picker v-model="form.endDate" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态" prop="status">
              <el-select v-model="form.status" style="width: 100%">
                <el-option label="草稿" value="draft" />
                <el-option label="生效中" value="active" />
                <el-option label="已完成" value="completed" />
                <el-option label="已终止" value="terminated" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="合同总额(元)">
              <el-input :model-value="computedTotal" disabled />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注">
              <el-input v-model="form.notes" type="textarea" :rows="2" placeholder="可选" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { greenPowerAPI } from '../api/greenPower'
import { adminAPI } from '../api/admin'

const list = ref([])
const total = ref(0)
const loading = ref(false)
const saving = ref(false)
const enterprises = ref([])
const dialogVisible = ref(false)
const formRef = ref(null)

const query = reactive({ keyword: '', status: '', page: 1, pageSize: 10 })

const kpis = reactive([
  { label: '绿电合同总数', value: 0, unit: '份', color: '#34d399' },
  { label: '生效中合同', value: 0, unit: '份', color: '#38bdf8' },
  { label: '总绿电电量', value: 0, unit: 'MWh', color: '#a78bfa' },
  { label: '碳减排量', value: 0, unit: '吨', color: '#4ade80' },
])

const emptyForm = () => ({
  id: '', enterpriseId: '', enterpriseName: '', supplierName: '',
  energyVolume: 100000, pricePremium: 0.03, startDate: '', endDate: '',
  status: 'active', notes: '',
})
const form = reactive(emptyForm())

const rules = {
  enterpriseId: [{ required: true, message: '请选择企业', trigger: 'change' }],
  supplierName: [{ required: true, message: '请输入供应商名称', trigger: 'blur' }],
  energyVolume: [{ required: true, message: '请输入合同电量', trigger: 'change' }],
  startDate: [{ required: true, message: '请选择开始日期', trigger: 'change' }],
  endDate: [{ required: true, message: '请选择结束日期', trigger: 'change' }],
}

const computedTotal = computed(() => {
  const v = Number(form.energyVolume || 0)
  const p = Number(form.pricePremium || 0)
  return '¥' + (v * p).toLocaleString('zh-CN', { maximumFractionDigits: 0 })
})

const fmt = (n, d = 0) => {
  if (n == null) return '--'
  return Number(n).toLocaleString('zh-CN', { maximumFractionDigits: d })
}

const statusLabel = (s) => ({ draft: '草稿', active: '生效中', completed: '已完成', terminated: '已终止' }[s] || s)
const statusType = (s) => ({ draft: 'info', active: 'success', completed: 'primary', terminated: 'danger' }[s] || 'info')

const loadKpi = async () => {
  try {
    const res = await greenPowerAPI.getPlatformSummary()
    const d = res?.data || res || {}
    kpis[0].value = d.totalContracts ?? 0
    kpis[1].value = d.activeContracts ?? 0
    kpis[2].value = fmt((d.totalVolume || 0) / 1000, 1)
    kpis[3].value = fmt((d.totalCarbonOffset || 0) / 1000, 1)
  } catch (e) { /* interceptor handled */ }
}

const loadList = async (page) => {
  if (page) query.page = page
  loading.value = true
  try {
    const res = await greenPowerAPI.getAllContracts({
      keyword: query.keyword || undefined,
      status: query.status || undefined,
      page: query.page,
      pageSize: query.pageSize,
    })
    const d = res?.data || res || {}
    list.value = d.list || []
    total.value = d.total || 0
  } catch (e) { /* interceptor handled */ } finally {
    loading.value = false
  }
}

const loadEnterprises = async () => {
  try {
    const res = await adminAPI.getEnterprises()
    const d = res?.data || res || []
    enterprises.value = (Array.isArray(d) ? d : []).map(e => ({ id: e.id, name: e.name || e.enterpriseName || e.username }))
  } catch (e) {
    enterprises.value = []
  }
}

const onEnterpriseChange = (id) => {
  const e = enterprises.value.find(x => x.id === id)
  if (e) form.enterpriseName = e.name
}

const openDialog = (row) => {
  Object.assign(form, row ? { ...row } : emptyForm())
  if (!row) {
    form.startDate = new Date().toISOString().slice(0, 10)
    form.endDate = `${new Date().getFullYear()}-12-31`
  }
  dialogVisible.value = true
}

const handleSave = async () => {
  await formRef.value.validate()
  saving.value = true
  try {
    if (form.id) {
      await greenPowerAPI.updateContract(form.id, { ...form })
      ElMessage.success('合同更新成功')
    } else {
      await greenPowerAPI.createContract({ ...form })
      ElMessage.success('绿电合同创建成功')
    }
    dialogVisible.value = false
    loadKpi()
    loadList()
  } catch (e) { /* interceptor handled */ } finally {
    saving.value = false
  }
}

const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm(`确认删除合同 ${row.contractNo}？删除后不可恢复。`, '删除确认', { type: 'warning' })
    await greenPowerAPI.deleteContract(row.id)
    ElMessage.success('合同已删除')
    loadKpi()
    loadList()
  } catch (e) { /* cancel or error */ }
}

onMounted(() => {
  loadKpi()
  loadList()
  loadEnterprises()
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
.toolbar { display: flex; align-items: center; gap: 12px; }
.spacer { flex: 1; }
.pager { display: flex; justify-content: flex-end; margin-top: 16px; }
.dim { font-size: 11px; color: var(--text-secondary); }
</style>
