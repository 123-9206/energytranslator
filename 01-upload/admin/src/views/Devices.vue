<template>
  <div class="devices-page">
    <el-card shadow="never">
      <template #header>
        <div class="page-header">
          <span>设备管理</span>
          <div class="header-actions">
            <el-button @click="loadDevices">
              <el-icon><Refresh /></el-icon> 刷新
            </el-button>
            <el-button type="primary" @click="showCreateDialog = true">
              <el-icon><Plus /></el-icon> 新增设备
            </el-button>
          </div>
        </div>
      </template>

      <!-- KPI cards -->
      <div class="kpi-row">
        <div class="kpi-mini">
          <span class="kpi-num" style="color:#38bdf8">{{ stats.total }}</span>
          <span class="kpi-lbl">总数</span>
        </div>
        <div class="kpi-mini">
          <span class="kpi-num" style="color:#4ade80">{{ stats.online }}</span>
          <span class="kpi-lbl">在线</span>
        </div>
        <div class="kpi-mini">
          <span class="kpi-num" style="color:#f87171">{{ stats.offline }}</span>
          <span class="kpi-lbl">离线</span>
        </div>
        <div class="kpi-mini">
          <span class="kpi-num" style="color:#fbbf24">{{ stats.maintenance }}</span>
          <span class="kpi-lbl">维护中</span>
        </div>
      </div>

      <!-- Filters -->
      <div class="filters">
        <el-input v-model="search" placeholder="搜索设备名称" clearable style="width:220px" @keyup.enter="loadDevices">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-select v-model="filterStatus" placeholder="状态筛选" clearable style="width:130px" @change="loadDevices">
          <el-option label="全部" value="" />
          <el-option label="在线" value="online" />
          <el-option label="离线" value="offline" />
          <el-option label="维护中" value="maintenance" />
        </el-select>
        <el-select v-model="filterProtocol" placeholder="协议筛选" clearable style="width:130px" @change="loadDevices">
          <el-option label="全部协议" value="" />
          <el-option label="Modbus" value="modbus" />
          <el-option label="MQTT" value="mqtt" />
          <el-option label="HTTP" value="http" />
        </el-select>
      </div>

      <el-table :data="filteredDevices" style="width:100%" v-loading="loading" size="small" stripe>
        <el-table-column prop="name" label="设备名称" min-width="150" show-overflow-tooltip />
        <el-table-column prop="type" label="类型" width="130" show-overflow-tooltip />
        <el-table-column prop="protocol" label="协议" width="100">
          <template #default="{row}">
            <el-tag :type="protocolType(row.protocol)" size="small">{{ (row.protocol || '').toUpperCase() }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="连接地址" width="170">
          <template #default="{row}">
            {{ row.ipAddress || '-' }}:{{ row.port || '-' }}
          </template>
        </el-table-column>
        <el-table-column prop="manufacturer" label="厂商" width="120" show-overflow-tooltip />
        <el-table-column prop="model" label="型号" width="120" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{row}">
            <el-tag :type="statusType(row.status)" size="small">
              {{ statusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="最后数据" width="170">
          <template #default="{row}">
            <span v-if="row.statistics?.lastDataTime" class="text-muted">
              {{ dayjs(row.statistics.lastDataTime).format('MM-DD HH:mm:ss') }}
            </span>
            <span v-else class="text-muted">从未上报</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{row}">
            <el-button text type="primary" size="small" @click="viewDevice(row)">详情</el-button>
            <el-button text type="warning" size="small" @click="editDevice(row)">编辑</el-button>
            <el-button text type="danger" size="small" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap" v-if="total > 0">
        <el-pagination
          v-model:current-page="page"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50, 100]"
          layout="total, sizes, prev, pager, next"
          @change="loadDevices"
        />
      </div>
    </el-card>

    <!-- Create/Edit Dialog -->
    <el-dialog v-model="showCreateDialog" :title="editingDevice ? '编辑设备' : '新增设备'" width="600px">
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="100px">
        <el-form-item label="设备名称" prop="name">
          <el-input v-model="form.name" placeholder="如 1号变压器" />
        </el-form-item>
        <el-form-item label="设备类型" prop="type">
          <el-select v-model="form.type" style="width:100%" placeholder="选择类型">
            <el-option label="变压器" value="transformer" />
            <el-option label="电表" value="meter" />
            <el-option label="逆变器" value="inverter" />
            <el-option label="储能系统" value="storage" />
            <el-option label="充电桩" value="charger" />
            <el-option label="空调" value="hvac" />
            <el-option label="电机" value="motor" />
            <el-option label="其他" value="other" />
          </el-select>
        </el-form-item>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="协议" prop="protocol">
              <el-select v-model="form.protocol" style="width:100%">
                <el-option label="Modbus TCP" value="modbus" />
                <el-option label="MQTT" value="mqtt" />
                <el-option label="HTTP" value="http" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态">
              <el-select v-model="form.status" style="width:100%">
                <el-option label="在线" value="online" />
                <el-option label="离线" value="offline" />
                <el-option label="维护中" value="maintenance" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="16">
            <el-form-item label="IP地址" prop="ipAddress">
              <el-input v-model="form.ipAddress" placeholder="如 192.168.1.100" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="端口" prop="port">
              <el-input-number v-model="form.port" :min="1" :max="65535" style="width:100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="厂商">
              <el-input v-model="form.manufacturer" placeholder="如 施耐德" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="型号">
              <el-input v-model="form.model" placeholder="如 PM5350" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="序列号">
          <el-input v-model="form.serialNumber" placeholder="设备序列号" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateDialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">
          {{ editingDevice ? '保存' : '创建' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- Detail Dialog -->
    <el-dialog v-model="showDetailDialog" title="设备详情" width="700px">
      <el-descriptions :column="2" border size="small" v-if="currentDevice">
        <el-descriptions-item label="设备名称">{{ currentDevice.name }}</el-descriptions-item>
        <el-descriptions-item label="类型">{{ currentDevice.type }}</el-descriptions-item>
        <el-descriptions-item label="协议">
          <el-tag :type="protocolType(currentDevice.protocol)" size="small"> {{ (currentDevice.protocol || '').toUpperCase() }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="statusType(currentDevice.status)" size="small">{{ statusText(currentDevice.status) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="IP地址">{{ currentDevice.ipAddress || '-' }}</el-descriptions-item>
        <el-descriptions-item label="端口">{{ currentDevice.port || '-' }}</el-descriptions-item>
        <el-descriptions-item label="厂商">{{ currentDevice.manufacturer || '-' }}</el-descriptions-item>
        <el-descriptions-item label="型号">{{ currentDevice.model || '-' }}</el-descriptions-item>
        <el-descriptions-item label="序列号">{{ currentDevice.serialNumber || '-' }}</el-descriptions-item>
        <el-descriptions-item label="数据点数">{{ currentDevice.statistics?.totalDataPoints || 0 }}</el-descriptions-item>
        <el-descriptions-item label="总能耗">{{ currentDevice.statistics?.totalEnergy || 0 }} kWh</el-descriptions-item>
        <el-descriptions-item label="最后数据时间">{{ currentDevice.statistics?.lastDataTime ? dayjs(currentDevice.statistics.lastDataTime).format('YYYY-MM-DD HH:mm:ss') : '无' }}</el-descriptions-item>
        <el-descriptions-item label="创建时间">{{ currentDevice.createdAt ? dayjs(currentDevice.createdAt).format('YYYY-MM-DD HH:mm:ss') : '-' }}</el-descriptions-item>
      </el-descriptions>
      <div style="margin-top:16px;display:flex;gap:12px">
        <el-button type="primary" @click="handleCollect(currentDevice)">
          <el-icon><Refresh /></el-icon> 手动采集
        </el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import dayjs from 'dayjs'
import { deviceAPI } from '../api/devices'

const loading = ref(false)
const saving = ref(false)
const devices = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const search = ref('')
const filterStatus = ref('')
const filterProtocol = ref('')

const showCreateDialog = ref(false)
const showDetailDialog = ref(false)
const editingDevice = ref(null)
const currentDevice = ref(null)
const formRef = ref(null)

const stats = reactive({ total: 0, online: 0, offline: 0, maintenance: 0 })

const form = reactive({
  name: '', type: '', protocol: 'modbus', status: 'online',
  ipAddress: '', port: 502, manufacturer: '', model: '', serialNumber: '',
})

const formRules = {
  name: [{ required: true, message: '请输入设备名称', trigger: 'blur' }],
  type: [{ required: true, message: '请选择设备类型', trigger: 'change' }],
  protocol: [{ required: true, message: '请选择协议', trigger: 'change' }],
  ipAddress: [{ required: true, message: '请输入IP地址', trigger: 'blur' }],
  port: [{ required: true, message: '请输入端口', trigger: 'blur' }],
}

const filteredDevices = computed(() => {
  let list = devices.value
  if (search.value) {
    const kw = search.value.toLowerCase()
    list = list.filter(d => (d.name || '').toLowerCase().includes(kw))
  }
  if (filterStatus.value) list = list.filter(d => d.status === filterStatus.value)
  if (filterProtocol.value) list = list.filter(d => d.protocol === filterProtocol.value)
  return list
})

const protocolType = (p) => ({ modbus: 'primary', mqtt: '', http: 'warning', bacnet: 'info' }[p] || 'info')
const statusType = (s) => ({ online: 'success', offline: 'danger', maintenance: 'warning' }[s] || 'info')
const statusText = (s) => ({ online: '在线', offline: '离线', maintenance: '维护中' }[s] || s)

const loadDevices = async () => {
  loading.value = true
  try {
    const res = await deviceAPI.list({ page: page.value, size: pageSize.value })
    const data = res.data || res
    devices.value = data.content || data.list || []
    total.value = data.totalElements || data.total || devices.value.length

    // Stats
    stats.total = devices.value.length
    stats.online = devices.value.filter(d => d.status === 'online').length
    stats.offline = devices.value.filter(d => d.status === 'offline').length
    stats.maintenance = devices.value.filter(d => d.status === 'maintenance').length
  } catch (e) {
    ElMessage.error('加载设备列表失败')
  } finally {
    loading.value = false
  }
}

const viewDevice = (device) => {
  currentDevice.value = device
  showDetailDialog.value = true
}

const editDevice = (device) => {
  editingDevice.value = device
  form.name = device.name || ''
  form.type = device.type || ''
  form.protocol = device.protocol || 'modbus'
  form.status = device.status || 'online'
  form.ipAddress = device.ipAddress || ''
  form.port = device.port || 502
  form.manufacturer = device.manufacturer || ''
  form.model = device.model || ''
  form.serialNumber = device.serialNumber || ''
  showCreateDialog.value = true
}

const resetForm = () => {
  editingDevice.value = null
  form.name = ''
  form.type = ''
  form.protocol = 'modbus'
  form.status = 'online'
  form.ipAddress = ''
  form.port = 502
  form.manufacturer = ''
  form.model = ''
  form.serialNumber = ''
}

const handleSave = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    saving.value = true
    try {
      const payload = { ...form }
      if (editingDevice.value) {
        await deviceAPI.update(editingDevice.value.id, payload)
        ElMessage.success('设备更新成功')
      } else {
        await deviceAPI.create(payload)
        ElMessage.success('设备创建成功')
      }
      showCreateDialog.value = false
      resetForm()
      loadDevices()
    } catch (e) {
      ElMessage.error(e.response?.data?.message || '保存失败')
    } finally {
      saving.value = false
    }
  })
}

const handleDelete = async (device) => {
  try {
    await ElMessageBox.confirm(`确定删除设备「${device.name}」吗？`, '删除确认', {
      type: 'warning', confirmButtonText: '确定删除', cancelButtonText: '取消',
    })
    await deviceAPI.delete(device.id)
    ElMessage.success('设备已删除')
    loadDevices()
  } catch {}
}

const handleCollect = async (device) => {
  try {
    await deviceAPI.collect(device.id)
    ElMessage.success('采集指令已发送')
  } catch (e) {
    ElMessage.error('采集失败')
  }
}

loadDevices()
</script>

<style scoped>
.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.header-actions {
  display: flex;
  gap: 8px;
}
.kpi-row {
  display: flex;
  gap: 24px;
  margin-bottom: 16px;
  padding: 12px 16px;
  background: var(--bg-tertiary);
  border-radius: 8px;
}
.kpi-mini {
  display: flex;
  align-items: baseline;
  gap: 6px;
}
.kpi-num {
  font-size: 20px;
  font-weight: 700;
}
.kpi-lbl {
  font-size: 12px;
  color: var(--text-secondary);
}
.filters {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
}
.pagination-wrap {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
.text-muted {
  color: var(--text-secondary);
  font-size: 12px;
}
</style>
