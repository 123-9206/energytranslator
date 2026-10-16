<template>
  <div class="control-page">
    <!-- KPI row -->
    <div class="kpi-grid">
      <div class="kpi-card">
        <div class="kpi-num" style="color:#38bdf8">{{ stats.totalRules }}</div>
        <div class="kpi-lbl">控制规则</div>
      </div>
      <div class="kpi-card">
        <div class="kpi-num" style="color:#4ade80">{{ stats.enabledRules }}</div>
        <div class="kpi-lbl">已启用</div>
      </div>
      <div class="kpi-card">
        <div class="kpi-num" style="color:#fbbf24">{{ stats.totalLogs }}</div>
        <div class="kpi-lbl">控制日志</div>
      </div>
    </div>

    <el-card shadow="never">
      <el-tabs v-model="activeTab" @tab-change="onTabChange">
        <!-- Tab 1: 设备控制 -->
        <el-tab-pane label="设备控制" name="control">
          <div class="control-layout">
            <!-- 左侧：设备列表 -->
            <div class="device-panel">
              <div class="panel-title">选择设备</div>
              <el-input v-model="deviceSearch" placeholder="搜索设备..." clearable size="small" style="margin-bottom:10px" />
              <div class="device-list" v-loading="devicesLoading">
                <div
                  v-for="d in filteredDevices"
                  :key="d.id"
                  :class="['device-item', { active: selectedDevice?.id === d.id }]"
                  @click="selectDevice(d)"
                >
                  <div class="device-name">{{ d.name }}</div>
                  <div class="device-meta">
                    <el-tag :type="protocolType(d.protocol)" size="small">{{ (d.protocol || '').toUpperCase() }}</el-tag>
                    <el-tag :type="statusType(d.status)" size="small" style="margin-left:4px">{{ statusText(d.status) }}</el-tag>
                  </div>
                </div>
                <el-empty v-if="!devicesLoading && filteredDevices.length === 0" description="无设备" :image-size="60" />
              </div>
            </div>
            <!-- 右侧：控制面板 -->
            <div class="action-panel">
              <template v-if="selectedDevice">
                <div class="panel-title">{{ selectedDevice.name }}</div>
                <el-descriptions :column="1" border size="small" style="margin-bottom:16px">
                  <el-descriptions-item label="协议">{{ (selectedDevice.protocol || '').toUpperCase() }}</el-descriptions-item>
                  <el-descriptions-item label="地址">{{ selectedDevice.ipAddress }}:{{ selectedDevice.port }}</el-descriptions-item>
                  <el-descriptions-item label="状态">
                    <el-tag :type="statusType(selectedDevice.status)" size="small">{{ statusText(selectedDevice.status) }}</el-tag>
                  </el-descriptions-item>
                </el-descriptions>

                <div class="panel-title">控制指令</div>
                <div class="command-grid">
                  <el-button type="success" :loading="sending === 'on'" @click="sendCommand('turn_on')">
                    开机
                  </el-button>
                  <el-button type="danger" :loading="sending === 'off'" @click="sendCommand('turn_off')">
                    关机
                  </el-button>
                  <el-button type="warning" :loading="sending === 'restart'" @click="sendCommand('restart')">
                    重启
                  </el-button>
                </div>

                <div style="margin-top:16px">
                  <div class="panel-title">设定值</div>
                  <div style="display:flex;gap:8px;align-items:center">
                    <el-select v-model="setValueMode" style="width:130px" size="small">
                      <el-option label="温度 (°C)" value="set_temp" />
                      <el-option label="模式" value="set_mode" />
                      <el-option label="功率 (kW)" value="set_power" />
                    </el-select>
                    <el-input-number v-model="setValue" size="small" :min="0" :max="9999" style="width:140px" />
                    <el-button type="primary" size="small" :loading="sending === 'set'" @click="sendSetValue">
                      设定
                    </el-button>
                  </div>
                </div>

                <!-- 最近控制日志 -->
                <div style="margin-top:20px">
                  <div class="panel-title">最近控制记录</div>
                  <el-table :data="recentLogs" size="small" style="width:100%" max-height="200">
                    <el-table-column prop="action" label="动作" width="100">
                      <template #default="{row}">
                        <el-tag size="small">{{ actionText(row.action) }}</el-tag>
                      </template>
                    </el-table-column>
                    <el-table-column prop="value" label="值" width="80" />
                    <el-table-column prop="status" label="结果" width="80">
                      <template #default="{row}">
                        <el-tag :type="row.status === 'success' ? 'success' : 'danger'" size="small">{{ row.status }}</el-tag>
                      </template>
                    </el-table-column>
                    <el-table-column prop="timestamp" label="时间" width="160">
                      <template #default="{row}">{{ row.timestamp ? dayjs(row.timestamp).format('MM-DD HH:mm:ss') : '-' }}</template>
                    </el-table-column>
                  </el-table>
                </div>
              </template>
              <el-empty v-else description="请选择设备" :image-size="100" />
            </div>
          </div>
        </el-tab-pane>

        <!-- Tab 2: 控制规则 -->
        <el-tab-pane label="控制规则" name="rules">
          <div style="margin-bottom:12px;display:flex;justify-content:flex-end">
            <el-button type="primary" @click="showRuleDialog = true"><el-icon><Plus /></el-icon> 新增规则</el-button>
          </div>
          <el-table :data="rules" style="width:100%" v-loading="rulesLoading" size="small" stripe>
            <el-table-column prop="name" label="规则名称" min-width="160" show-overflow-tooltip />
            <el-table-column prop="type" label="类型" width="100">
              <template #default="{row}">
                <el-tag :type="row.type === 'timer' ? 'primary' : 'warning'" size="small">
                  {{ row.type === 'timer' ? '定时' : row.type === 'auto' ? '自动' : row.type || '-' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="action" label="动作" width="110">
              <template #default="{row}">{{ actionText(row.action) }}</template>
            </el-table-column>
            <el-table-column prop="cronExpression" label="Cron 表达式" width="150" show-overflow-tooltip />
            <el-table-column label="目标设备" min-width="140">
              <template #default="{row}">
                <el-tag v-for="tid in (row.targetIds || [])" :key="tid" size="small" style="margin:2px">
                  {{ getDeviceName(tid) }}
                </el-tag>
                <span v-if="!row.targetIds?.length" class="text-muted">-</span>
              </template>
            </el-table-column>
            <el-table-column prop="enabled" label="状态" width="80">
              <template #default="{row}">
                <el-switch :model-value="row.enabled" @change="toggleRule(row)" size="small" />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="220" fixed="right">
              <template #default="{row}">
                <el-button text type="success" size="small" @click="handleTriggerRule(row)">执行</el-button>
                <el-button text type="primary" size="small" @click="editRule(row)">编辑</el-button>
                <el-button text type="danger" size="small" @click="deleteRule(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- Tab 3: 控制日志 -->
        <el-tab-pane label="控制日志" name="logs">
          <div class="filters">
            <el-select v-model="logStatusFilter" placeholder="状态" clearable style="width:120px" @change="loadLogs">
              <el-option label="全部" value="" />
              <el-option label="成功" value="success" />
              <el-option label="失败" value="failed" />
            </el-select>
            <el-input v-model="logDeviceFilter" placeholder="设备ID" clearable style="width:200px" @keyup.enter="loadLogs" />
            <el-button @click="loadLogs"><el-icon><Refresh /></el-icon></el-button>
          </div>
          <el-table :data="logs" style="width:100%" v-loading="logsLoading" size="small" stripe>
            <el-table-column prop="deviceName" label="设备" width="140" show-overflow-tooltip />
            <el-table-column prop="action" label="动作" width="110">
              <template #default="{row}">{{ actionText(row.action) }}</template>
            </el-table-column>
            <el-table-column prop="value" label="值" width="80" />
            <el-table-column prop="protocol" label="协议" width="90">
              <template #default="{row}">
                <el-tag size="small">{{ (row.protocol || '-').toUpperCase() }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="status" label="结果" width="90">
              <template #default="{row}">
                <el-tag :type="row.status === 'success' ? 'success' : row.status === 'failed' ? 'danger' : 'info'" size="small">
                  {{ row.status === 'success' ? '成功' : row.status === 'failed' ? '失败' : row.status }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="operator" label="操作人" width="100" />
            <el-table-column prop="timestamp" label="时间" min-width="160">
              <template #default="{row}">{{ row.timestamp ? dayjs(row.timestamp).format('YYYY-MM-DD HH:mm:ss') : '-' }}</template>
            </el-table-column>
          </el-table>
          <div class="pagination-wrap" v-if="logTotal > 0">
            <el-pagination v-model:current-page="logPage" v-model:page-size="logPageSize" :total="logTotal"
              :page-sizes="[10, 20, 50]" layout="total, sizes, prev, pager, next" @change="loadLogs" />
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-card>

    <!-- Rule Dialog -->
    <el-dialog v-model="showRuleDialog" :title="editingRule ? '编辑控制规则' : '新增控制规则'" width="600px">
      <el-form ref="ruleFormRef" :model="ruleForm" :rules="ruleRules" label-width="110px">
        <el-form-item label="规则名称" prop="name">
          <el-input v-model="ruleForm.name" placeholder="如 每日关机" />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="规则类型" prop="type">
              <el-select v-model="ruleForm.type" style="width:100%">
                <el-option label="定时控制" value="timer" />
                <el-option label="自动控制" value="auto" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="控制动作" prop="action">
              <el-select v-model="ruleForm.action" style="width:100%">
                <el-option label="开机" value="turn_on" />
                <el-option label="关机" value="turn_off" />
                <el-option label="重启" value="restart" />
                <el-option label="设定温度" value="set_temp" />
                <el-option label="设定模式" value="set_mode" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item v-if="ruleForm.type === 'timer'" label="Cron 表达式" prop="cronExpression">
          <el-input v-model="ruleForm.cronExpression" placeholder="0 0 22 * * ? (每晚22点)" />
          <div class="form-tip">秒 分 时 日 月 周。如"0 0 8 * * ?" 每天早8点</div>
        </el-form-item>
        <el-form-item label="目标设备" prop="targetIds">
          <el-select v-model="ruleForm.targetIds" multiple filterable placeholder="选择设备" style="width:100%">
            <el-option v-for="d in deviceList" :key="d.id" :label="d.name" :value="d.id" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="['set_temp','set_mode','set_power'].includes(ruleForm.action)" label="动作参数">
          <el-input v-model="ruleForm.actionParamsStr" :placeholder="ruleForm.action === 'set_temp' ? '温度值，如 26' : '参数值'" />
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="优先级">
              <el-input-number v-model="ruleForm.priority" :min="0" :max="100" style="width:100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="启用">
              <el-switch v-model="ruleForm.enabled" />
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
import { ref, reactive, computed, nextTick, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import dayjs from 'dayjs'
import { deviceAPI } from '../api/devices'
import { controlAPI } from '../api/control'
import { adminAPI } from '../api/admin'

// ========== State ==========
const activeTab = ref('control')
const sending = ref(null)

// Stats
const stats = reactive({ totalRules: 0, enabledRules: 0, totalLogs: 0 })

// Device panel
const devicesLoading = ref(false)
const deviceList = ref([])
const deviceSearch = ref('')
const selectedDevice = ref(null)

// Control panel
const setValueMode = ref('set_temp')
const setValue = ref(25)
const recentLogs = ref([])

// Rules
const rulesLoading = ref(false)
const ruleSaving = ref(false)
const rules = ref([])
const showRuleDialog = ref(false)
const editingRule = ref(null)
const ruleFormRef = ref(null)
const ruleForm = reactive({
  name: '', type: 'timer', action: 'turn_off',
  cronExpression: '', targetIds: [], priority: 0, enabled: true,
  actionParamsStr: '',
})

const ruleRules = {
  name: [{ required: true, message: '请输入规则名称', trigger: 'blur' }],
  type: [{ required: true, message: '请选择类型', trigger: 'change' }],
  action: [{ required: true, message: '请选择动作', trigger: 'change' }],
  targetIds: [{ type: 'array', required: true, message: '请选择目标设备', trigger: 'change' }],
}

// Logs
const logsLoading = ref(false)
const logs = ref([])
const logTotal = ref(0)
const logPage = ref(1)
const logPageSize = ref(20)
const logStatusFilter = ref('')
const logDeviceFilter = ref('')

// ========== Computed ==========
const filteredDevices = computed(() => {
  if (!deviceSearch.value) return deviceList.value
  const kw = deviceSearch.value.toLowerCase()
  return deviceList.value.filter(d => (d.name || '').toLowerCase().includes(kw))
})

// ========== Helpers ==========
const protocolType = (p) => ({ modbus: 'primary', mqtt: '', http: 'warning' }[p] || 'info')
const statusType = (s) => ({ online: 'success', offline: 'danger', maintenance: 'warning' }[s] || 'info')
const statusText = (s) => ({ online: '在线', offline: '离线', maintenance: '维护中' }[s] || s)
const actionText = (a) => ({
  turn_on: '开机', turn_off: '关机', restart: '重启',
  set_temp: '设定温度', set_mode: '设定模式', set_power: '设定功率',
}[a] || a || '-')

const getDeviceName = (id) => {
  const d = deviceList.value.find(d => d.id === id)
  return d ? d.name : id?.substring(0, 8) || id
}

// ========== Loaders ==========
const loadStats = async () => {
  try {
    const res = await adminAPI.getControlStats()
    const data = res.data || res
    stats.totalRules = data.totalRules || 0
    stats.enabledRules = data.enabledRules || 0
    stats.totalLogs = data.totalLogs || 0
  } catch {}
}

const loadDevices = async () => {
  devicesLoading.value = true
  try {
    const res = await deviceAPI.list({ size: 500 })
    const data = res.data || res
    deviceList.value = data.content || data.list || []
  } catch {} finally {
    devicesLoading.value = false
  }
}

const loadRules = async () => {
  rulesLoading.value = true
  try {
    const res = await adminAPI.getAllControlRules()
    const data = res.data || res
    rules.value = data || []
    stats.totalRules = rules.value.length
    stats.enabledRules = rules.value.filter(r => r.enabled).length
  } catch {} finally {
    rulesLoading.value = false
  }
}

const loadLogs = async () => {
  logsLoading.value = true
  try {
    const params = { page: logPage.value, size: logPageSize.value }
    if (logDeviceFilter.value) params.deviceId = logDeviceFilter.value
    const res = await controlAPI.getLogs(params)
    const data = res.data || res
    logs.value = data.list || []
    logTotal.value = data.total || 0
  } catch {} finally {
    logsLoading.value = false
  }
}

const loadRecentLogs = async (deviceId) => {
  try {
    const res = await controlAPI.getLogs({ page: 1, size: 10, deviceId })
    const data = res.data || res
    recentLogs.value = data.list || []
  } catch {
    recentLogs.value = []
  }
}

// ========== Actions ==========
const selectDevice = (device) => {
  selectedDevice.value = device
  loadRecentLogs(device.id)
}

const sendCommand = async (action) => {
  if (!selectedDevice.value) { ElMessage.warning('请先选择设备'); return }
  sending.value = action
  try {
    await controlAPI.controlDevice(selectedDevice.value.id, { action })
    ElMessage.success(`${actionText(action)} 指令已发送`)
    loadRecentLogs(selectedDevice.value.id)
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '指令发送失败')
  } finally { sending.value = null }
}

const sendSetValue = async () => {
  if (!selectedDevice.value) { ElMessage.warning('请先选择设备'); return }
  sending.value = 'set'
  try {
    await controlAPI.controlDevice(selectedDevice.value.id, {
      action: setValueMode.value,
      value: setValue.value,
    })
    ElMessage.success('设定值已发送')
    loadRecentLogs(selectedDevice.value.id)
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '发送失败')
  } finally { sending.value = null }
}

const onTabChange = (tab) => {
  if (tab === 'rules') loadRules()
  else if (tab === 'logs') loadLogs()
}

// ========== Rule CRUD ==========
const editRule = (rule) => {
  editingRule.value = rule
  ruleForm.name = rule.name || ''
  ruleForm.type = rule.type || 'timer'
  ruleForm.action = rule.action || 'turn_off'
  ruleForm.cronExpression = rule.cronExpression || ''
  ruleForm.targetIds = [...(rule.targetIds || [])]
  ruleForm.priority = rule.priority || 0
  ruleForm.enabled = rule.enabled !== false
  ruleForm.actionParamsStr = rule.actionParams != null ? String(rule.actionParams) : ''
  showRuleDialog.value = true
}

const resetRuleForm = () => {
  editingRule.value = null
  ruleForm.name = ''
  ruleForm.type = 'timer'
  ruleForm.action = 'turn_off'
  ruleForm.cronExpression = ''
  ruleForm.targetIds = []
  ruleForm.priority = 0
  ruleForm.enabled = true
  ruleForm.actionParamsStr = ''
}

const handleSaveRule = async () => {
  if (!ruleFormRef.value) return
  await ruleFormRef.value.validate(async (valid) => {
    if (!valid) return
    ruleSaving.value = true
    try {
      const payload = {
        name: ruleForm.name,
        type: ruleForm.type,
        action: ruleForm.action,
        cronExpression: ruleForm.cronExpression || null,
        targetIds: ruleForm.targetIds,
        targetType: 'device',
        priority: ruleForm.priority,
        enabled: ruleForm.enabled,
      }
      // Parse actionParams if set
      if (ruleForm.actionParamsStr && ['set_temp', 'set_mode', 'set_power'].includes(ruleForm.action)) {
        const num = Number(ruleForm.actionParamsStr)
        payload.actionParams = isNaN(num) ? ruleForm.actionParamsStr : num
      }

      if (editingRule.value) {
        await controlAPI.updateRule(editingRule.value.id, payload)
        ElMessage.success('规则已更新')
      } else {
        await controlAPI.createRule(payload)
        ElMessage.success('规则已创建')
      }
      showRuleDialog.value = false
      resetRuleForm()
      loadRules()
      loadStats()
    } catch (e) {
      ElMessage.error(e.response?.data?.message || '保存失败')
    } finally { ruleSaving.value = false }
  })
}

const toggleRule = async (rule) => {
  try {
    await controlAPI.toggleRule(rule.id)
    rule.enabled = !rule.enabled
    stats.enabledRules = rules.value.filter(r => r.enabled).length
  } catch (e) {
    ElMessage.error('操作失败')
  }
}

const handleTriggerRule = async (rule) => {
  try {
    await ElMessageBox.confirm(`确定立即执行规则「${rule.name}」吗？`, '手动触发', {
      confirmButtonText: '执行', type: 'info',
    })
    const res = await adminAPI.triggerControlRule(rule.id)
    const data = res.data || res
    ElMessage.success(`执行完成: 成功 ${data.success} / 失败 ${data.failed}`)
  } catch {}
}

const deleteRule = async (rule) => {
  try {
    await ElMessageBox.confirm(`确定删除规则「${rule.name}」吗？`, '删除确认', { type: 'warning' })
    await controlAPI.deleteRule(rule.id)
    ElMessage.success('规则已删除')
    loadRules()
    loadStats()
  } catch {}
}

// ========== Init ==========
onMounted(() => {
  loadDevices()
  loadStats()
})
</script>

<style scoped>
.kpi-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
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

.control-layout {
  display: flex;
  gap: 20px;
  min-height: 400px;
}
.device-panel {
  width: 280px;
  flex-shrink: 0;
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 12px;
  background: var(--bg-secondary);
}
.action-panel {
  flex: 1;
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 16px;
  background: var(--bg-secondary);
}
.panel-title {
  font-size: 14px;
  font-weight: 600;
  margin-bottom: 10px;
  color: var(--text-primary);
}
.device-list {
  max-height: 350px;
  overflow-y: auto;
}
.device-item {
  padding: 8px 10px;
  border-radius: 6px;
  cursor: pointer;
  margin-bottom: 4px;
  transition: background 0.15s;
}
.device-item:hover { background: var(--bg-hover); }
.device-item.active { background: var(--color-primary-light); border: 1px solid var(--color-primary); }
.device-name { font-size: 13px; font-weight: 500; }
.device-meta { margin-top: 4px; display: flex; }

.command-grid {
  display: flex;
  gap: 10px;
}

.filters { display: flex; gap: 12px; margin-bottom: 16px; align-items: center; }
.pagination-wrap { margin-top: 16px; display: flex; justify-content: flex-end; }
.text-muted { color: var(--text-secondary); font-size: 12px; }
.form-tip { font-size: 11px; color: var(--text-secondary); margin-top: 4px; }
</style>
