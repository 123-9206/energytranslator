<template>
  <div class="alerts-page">
    <!-- Stats -->
    <div class="kpi-grid">
      <div class="kpi-card" v-for="s in alertStats" :key="s.label">
        <div class="kpi-num" :style="{color: s.color}">{{ s.value }}</div>
        <div class="kpi-lbl">{{ s.label }}</div>
      </div>
    </div>

    <!-- Tabs: Alert List / Rules -->
    <el-card shadow="never">
      <el-tabs v-model="activeTab" @tab-change="onTabChange">
        <el-tab-pane label="告警列表" name="list">
          <div class="filters">
            <el-select v-model="filterStatus" placeholder="状态筛选" clearable style="width:130px" @change="loadAlarms">
              <el-option label="全部" value="" />
              <el-option label="活跃" value="active" />
              <el-option label="已确认" value="acknowledged" />
              <el-option label="已解决" value="resolved" />
              <el-option label="已忽略" value="ignored" />
            </el-select>
            <el-select v-model="filterLevel" placeholder="级别筛选" clearable style="width:130px" @change="loadAlarms">
              <el-option label="全部级别" value="" />
              <el-option label="严重" value="critical" />
              <el-option label="重要" value="warning" />
              <el-option label="一般" value="info" />
            </el-select>
            <el-button @click="loadAlarms"><el-icon><Refresh /></el-icon></el-button>
            <div style="flex:1"/>
            <el-button type="primary" size="small" :disabled="!selectedAlarms.length" @click="handleBatchAck">
              批量确认 ({{ selectedAlarms.length }})
            </el-button>
          </div>

          <el-table :data="alarms" style="width:100%" v-loading="loading" size="small" stripe
            @selection-change="onSelectionChange">
            <el-table-column type="selection" width="45" />
            <el-table-column prop="title" label="告警标题" min-width="180" show-overflow-tooltip />
            <el-table-column prop="level" label="级别" width="90">
              <template #default="{row}">
                <el-tag :type="levelType(row.level)" size="small">{{ row.level || '-' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="deviceName" label="关联设备" width="140" />
            <el-table-column prop="occurredAt" label="发生时间" width="170">
              <template #default="{row}">{{ row.occurredAt ? dayjs(row.occurredAt).format('YYYY-MM-DD HH:mm:ss') : '-' }}</template>
            </el-table-column>
            <el-table-column prop="status" label="状态" width="100">
              <template #default="{row}">
                <el-tag :type="alarmStatusType(row.status)" size="small">{{ alarmStatusText(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="220" fixed="right">
              <template #default="{row}">
                <el-button v-if="row.status === 'active'" text type="primary" size="small" @click="handleAck(row)">确认</el-button>
                <el-button v-if="row.status !== 'resolved'" text type="success" size="small" @click="handleResolve(row)">解决</el-button>
                <el-button v-if="row.status === 'active'" text type="warning" size="small" @click="handleIgnore(row)">忽略</el-button>
              </template>
            </el-table-column>
          </el-table>

          <div class="pagination-wrap" v-if="total > 0">
            <el-pagination v-model:current-page="page" v-model:page-size="pageSize" :total="total"
              :page-sizes="[10, 20, 50]" layout="total, sizes, prev, pager, next" @change="loadAlarms" />
          </div>
        </el-tab-pane>

        <el-tab-pane label="告警规则" name="rules">
          <div style="margin-bottom:12px;display:flex;justify-content:flex-end">
            <el-button type="primary" @click="showRuleDialog = true"><el-icon><Plus /></el-icon> 新增规则</el-button>
          </div>
          <el-table :data="rules" style="width:100%" v-loading="rulesLoading" size="small" stripe>
            <el-table-column prop="name" label="规则名称" min-width="150" />
            <el-table-column prop="deviceName" label="关联设备" width="130" />
            <el-table-column prop="metric" label="监控指标" width="120" />
            <el-table-column prop="condition" label="条件" width="90">
              <template #default="{row}">{{ row.condition === 'gt' ? '大于' : row.condition === 'lt' ? '小于' : row.condition === 'eq' ? '等于' : row.condition }}</template>
            </el-table-column>
            <el-table-column prop="threshold" label="阈值" width="90" />
            <el-table-column prop="severity" label="级别" width="90">
              <template #default="{row}">
                <el-tag :type="levelType(row.severity)" size="small">{{ row.severity || '-' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="enabled" label="状态" width="80">
              <template #default="{row}">
                <el-switch :model-value="row.enabled" @change="toggleRule(row)" size="small" />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="120">
              <template #default="{row}">
                <el-button text type="primary" size="small" @click="editRule(row)">编辑</el-button>
                <el-button text type="danger" size="small" @click="deleteRule(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- Rule Dialog -->
    <el-dialog v-model="showRuleDialog" :title="editingRule ? '编辑规则' : '新增规则'" width="550px">
      <el-form ref="ruleFormRef" :model="ruleForm" :rules="ruleRules" label-width="100px">
        <el-form-item label="规则名称" prop="name">
          <el-input v-model="ruleForm.name" placeholder="如 变压器温度过高" />
        </el-form-item>
        <el-form-item label="关联设备" prop="deviceName">
          <el-input v-model="ruleForm.deviceName" placeholder="设备名称" />
        </el-form-item>
        <el-form-item label="监控指标" prop="metric">
          <el-select v-model="ruleForm.metric" style="width:100%">
            <el-option label="功率" value="power" />
            <el-option label="电压" value="voltage" />
            <el-option label="电流" value="current" />
            <el-option label="温度" value="temperature" />
            <el-option label="能耗" value="energy" />
            <el-option label="功率因数" value="powerFactor" />
          </el-select>
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="条件" prop="condition">
              <el-select v-model="ruleForm.condition" style="width:100%">
                <el-option label="大于" value="gt" />
                <el-option label="小于" value="lt" />
                <el-option label="等于" value="eq" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="阈值" prop="threshold">
              <el-input-number v-model="ruleForm.threshold" style="width:100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="告警级别">
              <el-select v-model="ruleForm.severity" style="width:100%">
                <el-option label="严重" value="critical" />
                <el-option label="重要" value="warning" />
                <el-option label="一般" value="info" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="持续时间(s)">
              <el-input-number v-model="ruleForm.durationSeconds" :min="0" style="width:100%" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="showRuleDialog = false">取消</el-button>
        <el-button type="primary" :loading="ruleSaving" @click="handleSaveRule">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import dayjs from 'dayjs'
import { alarmAPI } from '../api/alarms'

const loading = ref(false)
const rulesLoading = ref(false)
const ruleSaving = ref(false)
const activeTab = ref('list')
const alarms = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const filterStatus = ref('')
const filterLevel = ref('')
const selectedAlarms = ref([])

const rules = ref([])
const showRuleDialog = ref(false)
const editingRule = ref(null)
const ruleFormRef = ref(null)

const alertStats = [
  { label: '活跃告警', value: 0, color: '#f87171' },
  { label: '已确认', value: 0, color: '#fbbf24' },
  { label: '已解决', value: 0, color: '#4ade80' },
  { label: '已忽略', value: 0, color: '#94a3b8' },
]

const ruleForm = reactive({
  name: '', deviceName: '', metric: 'power', condition: 'gt',
  threshold: 100, severity: 'warning', durationSeconds: 60,
})

const ruleRules = {
  name: [{ required: true, message: '请输入规则名称', trigger: 'blur' }],
  metric: [{ required: true, message: '请选择指标', trigger: 'change' }],
  condition: [{ required: true, message: '请选择条件', trigger: 'change' }],
  threshold: [{ required: true, message: '请输入阈值', trigger: 'blur' }],
}

const levelType = (l) => ({ critical: 'danger', warning: 'warning', info: 'info', '严重': 'danger', '重要': 'warning', '一般': 'info' }[l] || 'info')
const alarmStatusType = (s) => ({ active: 'danger', acknowledged: 'warning', resolved: 'success', ignored: 'info' }[s] || 'info')
const alarmStatusText = (s) => ({ active: '活跃', acknowledged: '已确认', resolved: '已解决', ignored: '已忽略' }[s] || s)

const onSelectionChange = (val) => { selectedAlarms.value = val }

const loadAlarms = async () => {
  loading.value = true
  try {
    const params = { page: page.value, size: pageSize.value }
    if (filterStatus.value) params.status = filterStatus.value
    if (filterLevel.value) params.level = filterLevel.value
    const res = await alarmAPI.list(params)
    const data = res.data || res
    alarms.value = data.content || data.list || []
    total.value = data.totalElements || data.total || alarms.value.length
  } catch (e) {
    ElMessage.error('加载告警列表失败：' + (e.response?.data?.message || e.message || '网络错误'))
  } finally { loading.value = false }
}

const loadRules = async () => {
  rulesLoading.value = true
  try {
    const res = await alarmAPI.getRules({ size: 100 })
    const data = res.data || res
    rules.value = data.content || data.list || data || []
  } catch (e) {
    ElMessage.error('加载规则列表失败：' + (e.response?.data?.message || e.message || '网络错误'))
  } finally { rulesLoading.value = false }
}

const loadStats = async () => {
  try {
    const res = await alarmAPI.statistics()
    const data = res.data || res
    alertStats[0].value = data.active || 0
    alertStats[1].value = data.acknowledged || 0
    alertStats[2].value = data.resolved || 0
    alertStats[3].value = data.ignored || 0
  } catch (e) {
    console.warn('加载告警统计失败:', e.message || e)
  }
}

const onTabChange = (tab) => {
  if (tab === 'rules') loadRules()
  else loadAlarms()
}

const handleAck = async (alarm) => {
  try {
    await alarmAPI.acknowledge(alarm.id)
    ElMessage.success('已确认')
    loadAlarms()
  } catch (e) {
    ElMessage.error('确认失败：' + (e.response?.data?.message || e.message || '未知错误'))
  }
}

const handleResolve = async (alarm) => {
  try {
    await alarmAPI.resolve(alarm.id, '管理员处理')
    ElMessage.success('已解决')
    loadAlarms()
  } catch (e) {
    ElMessage.error('解决失败：' + (e.response?.data?.message || e.message || '未知错误'))
  }
}

const handleIgnore = async (alarm) => {
  try {
    await alarmAPI.ignore(alarm.id)
    ElMessage.success('已忽略')
    loadAlarms()
  } catch (e) {
    ElMessage.error('忽略失败：' + (e.response?.data?.message || e.message || '未知错误'))
  }
}

const handleBatchAck = async () => {
  if (!selectedAlarms.value.length) return
  try {
    await alarmAPI.batchAcknowledge(selectedAlarms.value.map(a => a.id))
    ElMessage.success(`已确认 ${selectedAlarms.value.length} 条告警`)
    loadAlarms()
  } catch (e) {
    ElMessage.error('批量确认失败：' + (e.response?.data?.message || e.message || '未知错误'))
  }
}

const editRule = (rule) => {
  editingRule.value = rule
  ruleForm.name = rule.name || ''
  ruleForm.deviceName = rule.deviceName || ''
  ruleForm.metric = rule.metric || 'power'
  ruleForm.condition = rule.condition || 'gt'
  ruleForm.threshold = rule.threshold || 100
  ruleForm.severity = rule.severity || 'warning'
  ruleForm.durationSeconds = rule.durationSeconds || 60
  showRuleDialog.value = true
}

const toggleRule = async (rule) => {
  try {
    await alarmAPI.toggleRule(rule.id)
    rule.enabled = !rule.enabled
    ElMessage.success(rule.enabled ? '规则已启用' : '规则已禁用')
  } catch (e) {
    ElMessage.error('操作失败：' + (e.response?.data?.message || e.message || '未知错误'))
  }
}

const deleteRule = async (rule) => {
  try {
    await ElMessageBox.confirm(`确定删除规则「${rule.name}」吗？`, '删除确认', { type: 'warning' })
    await alarmAPI.deleteRule(rule.id)
    ElMessage.success('已删除')
    loadRules()
  } catch (e) {
    if (e !== 'cancel' && e !== 'close') {
      ElMessage.error('删除失败：' + (e.response?.data?.message || e.message || '未知错误'))
    }
  }
}

const handleSaveRule = async () => {
  if (!ruleFormRef.value) return
  await ruleFormRef.value.validate(async (valid) => {
    if (!valid) return
    ruleSaving.value = true
    try {
      if (editingRule.value) {
        await alarmAPI.updateRule(editingRule.value.id, { ...ruleForm })
        ElMessage.success('规则已更新')
      } else {
        await alarmAPI.createRule({ ...ruleForm })
        ElMessage.success('规则已创建')
      }
      showRuleDialog.value = false
      editingRule.value = null
      loadRules()
    } catch (e) {
      ElMessage.error(e.response?.data?.message || '保存失败')
    } finally { ruleSaving.value = false }
  })
}

loadAlarms()
loadStats()
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
.kpi-num { font-size: 28px; font-weight: 700; }
.kpi-lbl { font-size: 12px; color: var(--text-secondary); margin-top: 4px; }
.filters { display: flex; gap: 12px; margin-bottom: 16px; align-items: center; }
.pagination-wrap { margin-top: 16px; display: flex; justify-content: flex-end; }
</style>
