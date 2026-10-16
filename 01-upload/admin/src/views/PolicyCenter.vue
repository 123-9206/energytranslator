<template>
  <div class="policy-page">
    <!-- KPI（基于列表统计，无需企业ID） -->
    <div class="kpi-grid">
      <div class="kpi-card" v-for="c in kpis" :key="c.label">
        <div class="kpi-num" :style="{ color: c.color }">{{ c.value }} <span class="unit">{{ c.unit }}</span></div>
        <div class="kpi-lbl">{{ c.label }}</div>
      </div>
    </div>

    <!-- 工具栏 -->
    <el-card shadow="never" style="margin-bottom: 20px">
      <div class="toolbar">
        <el-input v-model="query.keyword" placeholder="政策标题 / 文号" clearable style="width: 220px" @keyup.enter="loadList(1)" @clear="loadList(1)">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-select v-model="query.policyType" placeholder="政策类型" clearable style="width: 150px" @change="loadList(1)">
          <el-option v-for="t in typeOptions" :key="t.value" :label="t.label" :value="t.value" />
        </el-select>
        <el-select v-model="query.status" placeholder="状态" clearable style="width: 120px" @change="loadList(1)">
          <el-option label="生效中" value="active" />
          <el-option label="已过期" value="expired" />
        </el-select>
        <el-button type="primary" @click="loadList(1)"><el-icon><Search /></el-icon>查询</el-button>
        <div class="spacer"></div>
        <el-button type="primary" @click="openDialog()"><el-icon><Plus /></el-icon>新增政策</el-button>
      </div>
    </el-card>

    <!-- 政策列表 -->
    <el-card shadow="never">
      <template #header><span>政策福利库</span></template>
      <el-table :data="list" v-loading="loading" stripe>
        <el-table-column prop="title" label="政策标题" min-width="220" show-overflow-tooltip />
        <el-table-column label="类型" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="typeTag(row.policyType)" size="small">{{ typeLabel(row.policyType) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="region" label="区域" width="100" align="center" />
        <el-table-column label="预估福利" width="130" align="right">
          <template #default="{ row }">¥{{ fmt(row.benefitAmount) }}<span class="dim">/年</span></template>
        </el-table-column>
        <el-table-column prop="source" label="政策文号" width="160" show-overflow-tooltip />
        <el-table-column label="申报截止" width="110" align="center">
          <template #default="{ row }">{{ row.applicationDeadline || '--' }}</template>
        </el-table-column>
        <el-table-column label="申报数" width="80" align="center">
          <template #default="{ row }">{{ row.applyCount || 0 }}</template>
        </el-table-column>
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 'active' ? 'success' : 'info'" size="small">
              {{ row.status === 'active' ? '生效中' : '已过期' }}
            </el-tag>
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
    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑政策' : '新增政策'" width="720px" destroy-on-close>
      <el-form :model="form" :rules="rules" ref="formRef" label-width="120px">
        <el-form-item label="政策标题" prop="title">
          <el-input v-model="form.title" placeholder="如：江苏省绿电消费激励政策" />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="政策类型" prop="policyType">
              <el-select v-model="form.policyType" style="width: 100%">
                <el-option v-for="t in typeOptions" :key="t.value" :label="t.label" :value="t.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="适用区域" prop="region">
              <el-input v-model="form.region" placeholder="江苏省 / 苏州市 / 全国" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="预估福利(元/年)" prop="benefitAmount">
              <el-input-number v-model="form.benefitAmount" :min="0" :step="10000" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="申报截止" prop="applicationDeadline">
              <el-date-picker v-model="form.applicationDeadline" type="date" value-format="YYYY-MM-DD" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态" prop="status">
              <el-select v-model="form.status" style="width: 100%">
                <el-option label="生效中" value="active" />
                <el-option label="已过期" value="expired" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="政策文号" prop="source">
              <el-input v-model="form.source" placeholder="如：苏发改能源〔2026〕XX号" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="最低年用电量" prop="minAnnualUsage">
              <el-input-number v-model="form.minAnnualUsage" :min="0" :step="100000" style="width: 100%" />
              <div class="dim">kWh（匹配门槛，可空）</div>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="申报入口链接">
              <el-input v-model="form.applyUrl" placeholder="https://...（可空）" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="适用对象">
          <el-input v-model="form.eligibility" placeholder="如：年用电量500万度以上工商业企业" />
        </el-form-item>
        <el-form-item label="政策要点">
          <el-select v-model="form.highlights" multiple filterable allow-create default-first-option placeholder="输入要点后回车" style="width: 100%" />
        </el-form-item>
        <el-form-item label="匹配行业">
          <el-select v-model="form.matchIndustries" multiple filterable allow-create default-first-option placeholder="如：纺织 / 电子 / 机械（可空）" style="width: 100%" />
        </el-form-item>
        <el-form-item label="政策描述">
          <el-input v-model="form.description" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="平台备注">
          <el-input v-model="form.notes" type="textarea" :rows="2" placeholder="可选" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { policyAPI } from '../api/policy'

const list = ref([])
const total = ref(0)
const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const formRef = ref(null)

const query = reactive({ keyword: '', policyType: '', status: '', page: 1, pageSize: 10 })

const typeOptions = [
  { value: 'green_power', label: '绿电' },
  { value: 'carbon', label: '碳交易' },
  { value: 'demand_response', label: '需求响应' },
  { value: 'energy_saving', label: '节能改造' },
  { value: 'green_factory', label: '绿色工厂' },
  { value: 'subsidy', label: '专项补贴' },
]
const typeLabel = (t) => typeOptions.find(x => x.value === t)?.label || t || '--'
const typeTag = (t) => ({
  green_power: 'success', carbon: 'warning', demand_response: 'primary',
  energy_saving: 'info', green_factory: 'danger', subsidy: 'success',
}[t] || 'info')

const kpis = reactive([
  { label: '政策总数', value: 0, unit: '条', color: '#38bdf8' },
  { label: '生效中', value: 0, unit: '条', color: '#34d399' },
  { label: '已过期', value: 0, unit: '条', color: '#f87171' },
  { label: '累计申报', value: 0, unit: '次', color: '#a78bfa' },
  { label: '福利总额(当前页)', value: 0, unit: '万元/年', color: '#fbbf24' },
])

const fmt = (n, d = 0) => {
  if (n == null) return '--'
  return Number(n).toLocaleString('zh-CN', { maximumFractionDigits: d })
}

const emptyForm = () => ({
  id: '', title: '', policyType: 'green_power', region: '江苏省',
  description: '', eligibility: '', benefitAmount: 100000,
  applicationDeadline: '', status: 'active', source: '',
  applyUrl: '', matchIndustries: [], minAnnualUsage: 0,
  highlights: [], notes: '',
})
const form = reactive(emptyForm())

const rules = {
  title: [{ required: true, message: '请输入政策标题', trigger: 'blur' }],
  policyType: [{ required: true, message: '请选择政策类型', trigger: 'change' }],
  region: [{ required: true, message: '请输入适用区域', trigger: 'blur' }],
}

const loadList = async (page) => {
  if (page) query.page = page
  loading.value = true
  try {
    const res = await policyAPI.getPolicies({
      keyword: query.keyword || undefined,
      policyType: query.policyType || undefined,
      status: query.status || undefined,
      page: query.page,
      pageSize: query.pageSize,
    })
    const d = res?.data || res || {}
    list.value = d.list || []
    total.value = d.total || 0
    computeKpi(d.list || [])
  } catch (e) { /* interceptor handled */ } finally {
    loading.value = false
  }
}

const computeKpi = (rows) => {
  let all = total.value
  let active = 0, expired = 0, applied = 0, benefit = 0
  rows.forEach(r => {
    if (r.status === 'active') active++
    else expired++
    applied += r.applyCount || 0
    benefit += r.benefitAmount || 0
  })
  kpis[0].value = all
  kpis[1].value = active
  kpis[2].value = expired
  kpis[3].value = applied
  kpis[4].value = fmt(benefit / 10000, 1)
}

const openDialog = (row) => {
  Object.assign(form, row ? JSON.parse(JSON.stringify(row)) : emptyForm())
  if (!row) {
    form.policyType = 'green_power'
    form.region = '江苏省'
    form.status = 'active'
    form.benefitAmount = 100000
    form.minAnnualUsage = 0
  }
  dialogVisible.value = true
}

const handleSave = async () => {
  await formRef.value.validate()
  saving.value = true
  try {
    if (form.id) {
      await policyAPI.updatePolicy(form.id, { ...form })
      ElMessage.success('政策更新成功')
    } else {
      await policyAPI.createPolicy({ ...form })
      ElMessage.success('政策创建成功')
    }
    dialogVisible.value = false
    loadList()
  } catch (e) { /* interceptor handled */ } finally {
    saving.value = false
  }
}

const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm(`确认删除政策「${row.title}」？`, '删除确认', { type: 'warning' })
    await policyAPI.deletePolicy(row.id)
    ElMessage.success('政策已删除')
    loadList()
  } catch (e) { /* cancel or error */ }
}

onMounted(() => loadList())
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
.spacer { flex: 1; }
.pager { display: flex; justify-content: flex-end; margin-top: 16px; }
.dim { font-size: 11px; color: var(--text-secondary); }
</style>
