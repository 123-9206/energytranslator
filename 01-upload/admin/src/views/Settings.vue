<template>
  <div class="settings-page">
    <el-card shadow="never">
      <template #header><span>系统配置</span></template>
      <el-form label-width="140px" label-position="right">
        <el-divider content-position="left">采集配置</el-divider>
        <el-form-item label="Modbus 采集间隔">
          <el-input-number v-model="settings.collectInterval" :min="10" :max="3600" /> 秒
        </el-form-item>
        <el-form-item label="HTTP 轮询间隔">
          <el-input-number v-model="settings.pollInterval" :min="10" :max="3600" /> 秒
        </el-form-item>
        <el-form-item label="采集线程池大小">
          <el-input-number v-model="settings.threadPool" :min="1" :max="50" />
        </el-form-item>

        <el-divider content-position="left">MQTT 配置</el-divider>
        <el-form-item label="Broker URL">
          <el-input v-model="settings.mqttBroker" style="width:400px" />
        </el-form-item>
        <el-form-item label="Client ID">
          <el-input v-model="settings.mqttClientId" style="width:300px" />
        </el-form-item>
        <el-form-item label="订阅主题">
          <el-input v-model="settings.mqttTopic" style="width:400px" placeholder="energy/device/+/data" />
        </el-form-item>

        <el-divider content-position="left">告警配置</el-divider>
        <el-form-item label="告警冷却时间">
          <el-input-number v-model="settings.alarmCooldown" :min="60" :max="86400" /> 秒
        </el-form-item>
        <el-form-item label="通知方式">
          <el-checkbox-group v-model="settings.notifyMethods">
            <el-checkbox label="sms">短信通知</el-checkbox>
            <el-checkbox label="email">邮件通知</el-checkbox>
            <el-checkbox label="wechat">微信通知</el-checkbox>
          </el-checkbox-group>
        </el-form-item>

        <el-divider content-position="left">碳排放</el-divider>
        <el-form-item label="碳排放因子">
          <el-input-number v-model="settings.carbonFactor" :min="0.1" :max="2.0" :precision="3" :step="0.001" /> kg CO₂/kWh
        </el-form-item>
        <el-form-item label="基准电价">
          <el-input-number v-model="settings.electricityPrice" :min="0.1" :max="5.0" :precision="2" :step="0.01" /> 元/kWh
        </el-form-item>

        <el-divider content-position="left">安全</el-divider>
        <el-form-item label="JWT 过期时间">
          <el-input-number v-model="settings.jwtExpiry" :min="1" :max="168" /> 小时
        </el-form-item>
        <el-form-item label="最大登录失败次数">
          <el-input-number v-model="settings.maxLoginAttempts" :min="3" :max="20" />
        </el-form-item>

        <el-form-item>
          <el-button type="primary" @click="saveSettings" :loading="saving">保存配置</el-button>
          <el-button @click="loadSettings">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card shadow="never" style="margin-top:20px">
      <template #header><span>数据管理</span></template>
      <div class="data-actions">
        <div class="action-item">
          <div>
            <strong>导出所有能耗数据</strong>
            <p class="text-muted">将全部能耗记录导出为 Excel 文件</p>
          </div>
          <el-button type="primary" plain @click="exportAllEnergy">导出</el-button>
        </div>
        <div class="action-item">
          <div>
            <strong>导出告警记录</strong>
            <p class="text-muted">将告警历史导出为 CSV</p>
          </div>
          <el-button type="primary" plain @click="exportAlarms">导出</el-button>
        </div>
        <div class="action-item">
          <div>
            <strong>清理过期数据</strong>
            <p class="text-muted">删除 90 天前的原始采集数据</p>
          </div>
          <el-button type="danger" plain @click="cleanOldData">清理</el-button>
        </div>
      </div>
    </el-card>

    <el-card shadow="never" style="margin-top:20px">
      <template #header><span>系统信息</span></template>
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="系统版本">v1.1.0</el-descriptions-item>
        <el-descriptions-item label="后端框架">Spring Boot 3.2</el-descriptions-item>
        <el-descriptions-item label="数据库">MongoDB 7.0</el-descriptions-item>
        <el-descriptions-item label="MQTT Broker">EMQX 5.8.9</el-descriptions-item>
        <el-descriptions-item label="管理后台版本">v2.0.0</el-descriptions-item>
        <el-descriptions-item label="前端框架">Vue 3 + Element Plus</el-descriptions-item>
        <el-descriptions-item label="部署服务器">腾讯云 CVM</el-descriptions-item>
        <el-descriptions-item label="域名">example.com</el-descriptions-item>
      </el-descriptions>
    </el-card>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminAPI } from '../api/admin'

const saving = ref(false)
const loading = ref(false)

const settings = reactive({
  collectInterval: 60,
  pollInterval: 60,
  threadPool: 10,
  mqttBroker: 'tcp://localhost:1883',
  mqttClientId: 'energy_translator_backend',
  mqttTopic: 'energy/device/+/data',
  alarmCooldown: 300,
  notifyMethods: ['email'],
  carbonFactor: 0.785,
  electricityPrice: 0.60,
  jwtExpiry: 2,
  maxLoginAttempts: 5,
})

const loadSettings = async () => {
  loading.value = true
  try {
    const res = await adminAPI.getSettings()
    const data = res.data || res
    if (data && data.id) {
      settings.collectInterval = data.collectInterval ?? 60
      settings.pollInterval = data.pollInterval ?? 60
      settings.threadPool = data.threadPool ?? 10
      settings.mqttBroker = data.mqttBroker || 'tcp://localhost:1883'
      settings.mqttClientId = data.mqttClientId || 'energy_translator_backend'
      settings.mqttTopic = data.mqttTopic || 'energy/device/+/data'
      settings.alarmCooldown = data.alarmCooldown ?? 300
      settings.notifyMethods = data.notifyMethods || ['email']
      settings.carbonFactor = data.carbonFactor ?? 0.785
      settings.electricityPrice = data.electricityPrice ?? 0.60
      settings.jwtExpiry = data.jwtExpiry ?? 2
      settings.maxLoginAttempts = data.maxLoginAttempts ?? 5
    }
  } catch (e) {
    ElMessage.warning('加载系统配置失败，使用默认值')
  } finally {
    loading.value = false
  }
}

const saveSettings = async () => {
  saving.value = true
  try {
    await adminAPI.saveSettings({
      collectInterval: settings.collectInterval,
      pollInterval: settings.pollInterval,
      threadPool: settings.threadPool,
      mqttBroker: settings.mqttBroker,
      mqttClientId: settings.mqttClientId,
      mqttTopic: settings.mqttTopic,
      alarmCooldown: settings.alarmCooldown,
      notifyMethods: settings.notifyMethods,
      carbonFactor: settings.carbonFactor,
      electricityPrice: settings.electricityPrice,
      jwtExpiry: settings.jwtExpiry,
      maxLoginAttempts: settings.maxLoginAttempts,
    })
    ElMessage.success('配置已保存')
  } catch (e) {
    ElMessage.error('保存失败')
  } finally {
    saving.value = false
  }
}

const exportAllEnergy = () => ElMessage.info('导出功能开发中')
const exportAlarms = () => ElMessage.info('导出功能开发中')

const cleanOldData = async () => {
  try {
    await ElMessageBox.confirm('此操作将删除 90 天前的原始采集数据，不可恢复。确定继续？', '清理确认', {
      type: 'warning', confirmButtonText: '确定清理', cancelButtonText: '取消',
    })
    const res = await adminAPI.cleanupOldData(90)
    const data = res.data || res
    ElMessage.success(`清理完成，共删除 ${data.deletedCount ?? 0} 条数据`)
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('清理失败: ' + (e?.message || '未知错误'))
  }
}

loadSettings()
</script>

<style scoped>
.data-actions {
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.action-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  background: var(--bg-tertiary);
  border-radius: 8px;
}
.action-item strong {
  color: var(--text-primary);
  font-size: 14px;
}
.text-muted {
  color: var(--text-secondary);
  font-size: 12px;
  margin-top: 2px;
}
</style>
