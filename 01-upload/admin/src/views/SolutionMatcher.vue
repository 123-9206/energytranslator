<template>
  <div class="solution-matcher">
    <div class="page-header">
      <h2>设备方案匹配器</h2>
      <p class="subtitle">根据客户配电情况，自动生成硬件方案和成本估算</p>
      <div class="mode-toggle">
        <el-radio-group v-model="mode" size="small" @change="onModeChange">
          <el-radio-button value="manual">
            <el-icon><Setting /></el-icon> 手动配置
          </el-radio-button>
          <el-radio-button value="ai">
            <el-icon><MagicStick /></el-icon> AI 智能分析
          </el-radio-button>
        </el-radio-group>
      </div>
    </div>

    <!-- AI 智能分析模式 -->
    <div v-if="mode === 'ai' && !aiResult" class="ai-input-panel">
      <el-card shadow="never">
        <template #header>
          <div class="ai-card-header">
            <span class="card-title">🤖 用自然语言描述客户的配电情况</span>
            <el-tag type="info" size="small">AI 助手</el-tag>
          </div>
        </template>

        <div class="ai-hints">
          <p>告诉我以下信息，我来帮你匹配设备方案：</p>
          <ul>
            <li>有几个配电柜、几个回路（进线 + 分路）</li>
            <li>电压等级（380V 三相 / 220V 单相 / 10kV 高压）</li>
            <li>配电柜有没有数字电表，知道型号更好</li>
            <li>现场网络条件（WiFi / 网线 / 无网）</li>
            <li>有没有光伏、储能等新能源设备</li>
          </ul>
        </div>

        <el-input
          v-model="aiText"
          type="textarea"
          :rows="6"
          placeholder="例如：XX纺织厂有1个配电柜，4个回路，380V三相电，柜门上有安科瑞电表ACR10R，现场有WiFi覆盖。屋顶装了华为SUN2000逆变器的光伏。没有储能。"
          maxlength="2000"
          show-word-limit
        />

        <div class="ai-actions">
          <el-button
            type="primary"
            size="large"
            :loading="aiLoading"
            :disabled="!aiText.trim()"
            @click="doAiAnalyze"
          >
            <el-icon v-if="!aiLoading"><MagicStick /></el-icon>
            {{ aiLoading ? 'AI 正在分析中...' : '开始 AI 分析' }}
          </el-button>
          <el-button size="large" @click="loadAiExample">填入示例</el-button>
        </div>

        <div v-if="aiText.trim()" class="ai-char-count">
          已输入 {{ aiText.length }} 字（描述越详细，分析越准确）
        </div>
      </el-card>
    </div>

    <!-- AI 分析结果 -->
    <div v-if="mode === 'ai' && aiResult" class="step-content">
      <div class="ai-result-banner">
        <el-icon :size="20" color="#22c55e"><CircleCheck /></el-icon>
        <span>AI 已完成分析，匹配出以下方案</span>
        <el-button size="small" plain @click="resetAi">重新分析</el-button>
      </div>

      <!-- 提取参数概览 -->
      <el-card shadow="never" class="params-card">
        <template #header><span class="card-title">AI 提取的参数</span></template>
        <div class="params-grid">
          <div class="param-item">
            <span class="param-label">客户</span>
            <span class="param-value">{{ aiParams.customerName || '未提及' }}</span>
          </div>
          <div class="param-item">
            <span class="param-label">回路数</span>
            <span class="param-value">{{ aiParams.circuitCount }} 路</span>
          </div>
          <div class="param-item">
            <span class="param-label">电压</span>
            <span class="param-value">{{ aiParams.voltage }}</span>
          </div>
          <div class="param-item">
            <span class="param-label">电表</span>
            <span class="param-value">{{ aiParams.hasMeter === 'yes' ? '有' : aiParams.hasMeter === 'unknown' ? '不确定' : '无' }}{{ aiParams.meterModel ? ' · ' + aiParams.meterModel : '' }}</span>
          </div>
          <div class="param-item">
            <span class="param-label">网络</span>
            <span class="param-value">{{ aiParams.network === 'wifi' ? 'WiFi' : aiParams.network === 'ethernet' ? '以太网' : aiParams.network === '4g' ? '4G' : '-' }}</span>
          </div>
          <div class="param-item">
            <span class="param-label">光伏</span>
            <span class="param-value">{{ aiParams.hasSolar === 'yes' ? '有' + (aiParams.inverterModel ? ' · ' + aiParams.inverterModel : '') : '无' }}</span>
          </div>
          <div class="param-item">
            <span class="param-label">储能</span>
            <span class="param-value">{{ aiParams.hasStorage === 'yes' ? '有' : '无' }}</span>
          </div>
          <div class="param-item">
            <span class="param-label">VPP</span>
            <span class="param-value">{{ aiParams.vppInterest === 'yes' ? '有意向' : '暂无' }}</span>
          </div>
        </div>
      </el-card>

      <!-- 设备清单（复用现有 Step 3 布局） -->
      <el-card shadow="never" class="result-card">
        <template #header>
          <div class="result-header">
            <span class="card-title">设备配置方案</span>
            <div class="result-actions">
              <el-tag :type="aiResultData.tier === '高级版' ? 'warning' : aiResultData.tier === '标准版' ? 'primary' : 'success'" size="large">
                {{ aiResultData.tier }}
              </el-tag>
              <el-button size="small" @click="copyAiSolution">
                <el-icon><CopyDocument /></el-icon> 复制方案
              </el-button>
            </div>
          </div>
        </template>

        <el-table :data="aiResultData.items" stripe>
          <el-table-column prop="name" label="设备名称" min-width="160" />
          <el-table-column prop="spec" label="规格" min-width="140" />
          <el-table-column prop="qty" label="数量" width="70" align="center" />
          <el-table-column prop="unitPrice" label="单价 (¥)" width="100" align="right">
            <template #default="{ row }">
              {{ row.unitPrice > 0 ? row.unitPrice.toLocaleString() : '-' }}
            </template>
          </el-table-column>
          <el-table-column prop="subtotal" label="小计 (¥)" width="110" align="right">
            <template #default="{ row }">
              <span :style="{ fontWeight: row.subtotal > 0 ? '600' : '400', color: row.subtotal > 0 ? 'var(--accent)' : 'var(--text-secondary)' }">
                {{ row.subtotal > 0 ? row.subtotal.toLocaleString() : '已利用' }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="说明" min-width="180">
            <template #default="{ row }">
              <span class="item-note">{{ row.note }}</span>
            </template>
          </el-table-column>
        </el-table>

        <div class="cost-summary">
          <div class="cost-row"><span>硬件合计</span><span class="cost-value">¥ {{ aiResultData.hardwareTotal.toLocaleString() }}</span></div>
          <div class="cost-row"><span>预估安装费</span><span class="cost-value">¥ {{ aiResultData.installCost.toLocaleString() }}</span></div>
          <div class="cost-row total"><span>总计估算</span><span class="cost-value">¥ {{ aiResultData.totalCost.toLocaleString() }}</span></div>
          <div class="cost-note">* 价格参考淘宝/1688 零售价，批量采购另有折扣。安装费含电工半天人工。</div>
        </div>
      </el-card>

      <!-- 建议 -->
      <el-card shadow="never" class="advice-card">
        <template #header><span class="card-title">实施建议</span></template>
        <div class="advice-list">
          <div class="advice-item" v-for="(a, idx) in aiResultData.advice" :key="idx">
            <el-icon :size="18" :color="a.icon === 'check' ? '#22c55e' : a.icon === 'warn' ? '#f59e0b' : '#3b82f6'">
              <component :is="a.icon === 'check' ? 'CircleCheck' : a.icon === 'warn' ? 'WarningFilled' : 'InfoFilled'" />
            </el-icon>
            <span>{{ a.text }}</span>
          </div>
        </div>
      </el-card>
    </div>

    <!-- ═══════════════ 手动配置模式 ═══════════════ -->
    <template v-if="mode === 'manual'">

    <div class="steps">
      <div class="step" :class="{ active: currentStep === 1, done: currentStep > 1 }">
        <div class="step-num">1</div>
        <span>配电信息</span>
      </div>
      <div class="step-line" :class="{ done: currentStep > 1 }"></div>
      <div class="step" :class="{ active: currentStep === 2, done: currentStep > 2 }">
        <div class="step-num">2</div>
        <span>新能源</span>
      </div>
      <div class="step-line" :class="{ done: currentStep > 2 }"></div>
      <div class="step" :class="{ active: currentStep === 3 }">
        <div class="step-num">3</div>
        <span>生成方案</span>
      </div>
    </div>

    <!-- Step 1: 配电信息 -->
    <div v-if="currentStep === 1" class="step-content">
      <el-card shadow="never">
        <template #header>
          <span class="card-title">配电柜基本情况</span>
        </template>

        <el-form :model="form" label-width="160px" label-position="left">
          <!-- 客户名称 -->
          <el-form-item label="客户名称">
            <el-input v-model="form.customerName" placeholder="例如：XX纺织有限公司" maxlength="50" />
          </el-form-item>

          <!-- 现有电表 -->
          <el-form-item label="配电柜有无数字电表">
            <el-radio-group v-model="form.hasMeter">
              <el-radio value="yes">
                <span class="radio-label">有</span>
                <span class="radio-hint">柜门上有数字屏幕显示电压电流</span>
              </el-radio>
              <el-radio value="no">
                <span class="radio-label">没有</span>
                <span class="radio-hint">只有机械表或完全无表</span>
              </el-radio>
              <el-radio value="unknown">
                <span class="radio-label">不确定</span>
                <span class="radio-hint">让客户拍张照片</span>
              </el-radio>
            </el-radio-group>
          </el-form-item>

          <!-- 电表品牌型号 -->
          <el-form-item v-if="form.hasMeter === 'yes'" label="电表品牌/型号">
            <el-input v-model="form.meterModel" placeholder="例如：安科瑞 ACR10R、威胜 DTSD341" maxlength="50" />
            <div class="form-hint">查看柜门屏幕下方的铭牌标签</div>
          </el-form-item>

          <!-- 回路数量 -->
          <el-form-item label="监测回路数量">
            <el-radio-group v-model="form.circuitCount">
              <el-radio value="1-2">1-2 路（入门）</el-radio>
              <el-radio value="3-5">3-5 路（标准）</el-radio>
              <el-radio value="6+">6 路以上（高级）</el-radio>
            </el-radio-group>
            <div class="form-hint">总进线 + 各分路线路数量。打开柜门数一下粗电缆根数</div>
          </el-form-item>

          <!-- 电压等级 -->
          <el-form-item label="电压等级">
            <el-radio-group v-model="form.voltage">
              <el-radio value="380">380V（三相，常见）</el-radio>
              <el-radio value="220">220V（单相）</el-radio>
              <el-radio value="10kv">10kV（高压进线）</el-radio>
            </el-radio-group>
          </el-form-item>

          <!-- 现场网络 -->
          <el-form-item label="现场网络条件">
            <el-radio-group v-model="form.network">
              <el-radio value="wifi">有 WiFi</el-radio>
              <el-radio value="ethernet">有网线接口</el-radio>
              <el-radio value="4g">无网络，需 4G 网关</el-radio>
            </el-radio-group>
          </el-form-item>
        </el-form>
      </el-card>

      <div class="step-actions">
        <el-button type="primary" size="large" @click="nextStep(2)">
          下一步：新能源接入
          <el-icon style="margin-left: 4px;"><ArrowRight /></el-icon>
        </el-button>
      </div>
    </div>

    <!-- Step 2: 新能源 -->
    <div v-if="currentStep === 2" class="step-content">
      <el-card shadow="never">
        <template #header>
          <span class="card-title">新能源与储能情况</span>
        </template>

        <el-form :model="form" label-width="160px" label-position="left">
          <el-form-item label="屋顶有光伏板吗">
            <el-radio-group v-model="form.hasSolar">
              <el-radio value="yes">有</el-radio>
              <el-radio value="no">没有</el-radio>
            </el-radio-group>
          </el-form-item>

          <template v-if="form.hasSolar === 'yes'">
            <el-form-item label="逆变器品牌/型号">
              <el-input v-model="form.inverterModel" placeholder="例如：华为 SUN2000、阳光电源 SG" maxlength="50" />
              <div class="form-hint">逆变器通常支持 Modbus TCP，直接读取数据，无需额外硬件</div>
            </el-form-item>
          </template>

          <el-form-item label="有储能柜吗">
            <el-radio-group v-model="form.hasStorage">
              <el-radio value="yes">有</el-radio>
              <el-radio value="no">没有</el-radio>
            </el-radio-group>
          </el-form-item>

          <template v-if="form.hasStorage === 'yes'">
            <el-form-item label="BMS 通讯协议">
              <el-select v-model="form.bmsProtocol" placeholder="选择协议">
                <el-option label="Modbus TCP" value="modbus_tcp" />
                <el-option label="Modbus RTU" value="modbus_rtu" />
                <el-option label="CAN" value="can" />
                <el-option label="不确定" value="unknown" />
              </el-select>
            </el-form-item>
          </template>

          <el-form-item label="参与 VPP 需求响应">
            <el-radio-group v-model="form.vppInterest">
              <el-radio value="yes">有意向</el-radio>
              <el-radio value="no">暂无</el-radio>
            </el-radio-group>
            <div class="form-hint">参与电网调度可获容量补偿，不影响正常生产</div>
          </el-form-item>
        </el-form>
      </el-card>

      <div class="step-actions">
        <el-button size="large" @click="currentStep = 1">
          <el-icon style="margin-right: 4px;"><ArrowLeft /></el-icon>
          上一步
        </el-button>
        <el-button type="primary" size="large" @click="generateSolution">
          生成方案
          <el-icon style="margin-left: 4px;"><Check /></el-icon>
        </el-button>
      </div>
    </div>

    <!-- Step 3: 方案结果 -->
    <div v-if="currentStep === 3" class="step-content">
      <!-- 方案摘要 -->
      <el-card shadow="never" class="result-card">
        <template #header>
          <div class="result-header">
            <span class="card-title">设备配置方案</span>
            <div class="result-actions">
              <el-tag :type="solution.tier === '高级版' ? 'warning' : solution.tier === '标准版' ? 'primary' : 'success'" size="large">
                {{ solution.tier }}
              </el-tag>
              <el-button size="small" @click="copySolution">
                <el-icon><CopyDocument /></el-icon> 复制方案
              </el-button>
              <el-button size="small" plain @click="resetForm">
                <el-icon><Refresh /></el-icon> 重新匹配
              </el-button>
            </div>
          </div>
        </template>

        <div v-if="form.customerName" class="customer-badge">
          <el-icon><User /></el-icon>
          {{ form.customerName }}
        </div>

        <!-- 设备清单 -->
        <el-table :data="solution.items" style="width: 100%; margin-top: 12px;" :show-header="true" stripe>
          <el-table-column prop="name" label="设备名称" min-width="160" />
          <el-table-column prop="spec" label="规格" min-width="140" />
          <el-table-column prop="qty" label="数量" width="70" align="center" />
          <el-table-column prop="unitPrice" label="单价 (¥)" width="100" align="right">
            <template #default="{ row }">
              {{ row.unitPrice > 0 ? row.unitPrice.toLocaleString() : '-' }}
            </template>
          </el-table-column>
          <el-table-column prop="subtotal" label="小计 (¥)" width="110" align="right">
            <template #default="{ row }">
              <span :style="{ fontWeight: row.subtotal > 0 ? '600' : '400', color: row.subtotal > 0 ? 'var(--accent)' : 'var(--text-secondary)' }">
                {{ row.subtotal > 0 ? row.subtotal.toLocaleString() : '已利用' }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="说明" min-width="180">
            <template #default="{ row }">
              <span class="item-note">{{ row.note }}</span>
            </template>
          </el-table-column>
        </el-table>

        <!-- 费用汇总 -->
        <div class="cost-summary">
          <div class="cost-row">
            <span>硬件合计</span>
            <span class="cost-value">¥ {{ solution.hardwareTotal.toLocaleString() }}</span>
          </div>
          <div class="cost-row">
            <span>预估安装费</span>
            <span class="cost-value">¥ {{ solution.installCost.toLocaleString() }}</span>
          </div>
          <div class="cost-row total">
            <span>总计估算</span>
            <span class="cost-value">¥ {{ (solution.hardwareTotal + solution.installCost).toLocaleString() }}</span>
          </div>
          <div class="cost-note">* 价格参考淘宝/1688 零售价，批量采购另有折扣。安装费含电工半天人工。</div>
        </div>
      </el-card>

      <!-- 实施建议 -->
      <el-card shadow="never" class="advice-card">
        <template #header>
          <span class="card-title">实施建议</span>
        </template>
        <div class="advice-list">
          <div class="advice-item" v-for="(advice, idx) in solution.advice" :key="idx">
            <el-icon :size="18" :color="advice.icon === 'check' ? '#22c55e' : advice.icon === 'warn' ? '#f59e0b' : '#3b82f6'">
              <component :is="advice.icon === 'check' ? 'CircleCheck' : advice.icon === 'warn' ? 'WarningFilled' : 'InfoFilled'" />
            </el-icon>
            <span>{{ advice.text }}</span>
          </div>
        </div>
      </el-card>

      <div class="step-actions">
        <el-button size="large" @click="currentStep = 2">
          <el-icon style="margin-right: 4px;"><ArrowLeft /></el-icon>
          调整参数
        </el-button>
      </div>
    </div>

    </template>
    <!-- ═══════════════ 手动配置模式结束 ═══════════════ -->
  </div>
</template>

<script setup>
import { ref, reactive, computed } from 'vue'
import { ElMessage } from 'element-plus'
import { adminAPI } from '../api/admin'

// --- Mode ---
const mode = ref('manual')
const aiText = ref('')
const aiLoading = ref(false)
const aiResult = ref(false)
const aiParams = reactive({})
const aiResultData = reactive({
  tier: '',
  items: [],
  hardwareTotal: 0,
  installCost: 0,
  totalCost: 0,
  advice: [],
})

function onModeChange(val) {
  if (val === 'ai') {
    aiResult.value = false
    aiText.value = ''
  }
}

// --- AI Analysis ---
async function doAiAnalyze() {
  if (!aiText.value.trim()) return
  aiLoading.value = true
  try {
    const res = await adminAPI.aiAnalyze(aiText.value.trim())
    const data = res.data
    if (data.error) {
      ElMessage.error(data.message || 'AI 分析失败')
      return
    }
    // Populate params
    const ep = data.extractedParams || {}
    Object.assign(aiParams, {
      customerName: ep.customerName || '',
      circuitCount: ep.circuitCount || '3-5',
      voltage: ep.voltage || '380',
      hasMeter: ep.hasMeter || 'unknown',
      meterModel: ep.meterModel || '',
      network: ep.network || 'wifi',
      hasSolar: ep.hasSolar || 'no',
      inverterModel: ep.inverterModel || '',
      hasStorage: ep.hasStorage || 'no',
      bmsProtocol: ep.bmsProtocol || 'unknown',
      vppInterest: ep.vppInterest || 'no',
    })
    // Populate result
    Object.assign(aiResultData, {
      tier: data.tier || '',
      items: data.items || [],
      hardwareTotal: data.hardwareTotal || 0,
      installCost: data.installCost || 0,
      totalCost: data.totalCost || 0,
      advice: data.advice || [],
    })
    aiResult.value = true
    ElMessage.success('AI 分析完成！')
  } catch (e) {
    ElMessage.error(e.message || 'AI 分析失败，请稍后重试')
  } finally {
    aiLoading.value = false
  }
}

function loadAiExample() {
  aiText.value = 'XX针织厂有1个配电柜，总共4个回路，380V三相电。柜门上有数字电表，型号是安科瑞 ACR10R。厂里有WiFi无线网络覆盖。屋顶装了光伏，逆变器是华为 SUN2000。没有储能柜。'
}

function resetAi() {
  aiResult.value = false
  aiText.value = ''
}

function copyAiSolution() {
  let text = '【能耗翻译官 - AI 智能方案】\n'
  if (aiParams.customerName) text += `客户：${aiParams.customerName}\n`
  text += `方案等级：${aiResultData.tier}\n`
  text += `监测回路：${aiParams.circuitCount} 路 | 电压：${aiParams.voltage}\n`
  text += `网络：${aiParams.network === 'wifi' ? 'WiFi' : aiParams.network === 'ethernet' ? '以太网' : '4G'}\n`
  text += `光伏：${aiParams.hasSolar === 'yes' ? '有' : '无'} | 储能：${aiParams.hasStorage === 'yes' ? '有' : '无'}\n\n`
  text += '━━ 设备清单 ━━\n'
  aiResultData.items.forEach(item => {
    const cost = item.subtotal > 0 ? `¥${item.subtotal.toLocaleString()}` : '已利用'
    text += `${item.name} ×${item.qty}  ${item.spec}  ${cost}\n`
  })
  text += `\n硬件：¥${aiResultData.hardwareTotal.toLocaleString()}\n`
  text += `安装：¥${aiResultData.installCost.toLocaleString()}\n`
  text += `合计：¥${aiResultData.totalCost.toLocaleString()}\n`

  navigator.clipboard.writeText(text).then(() => {
    ElMessage.success('方案已复制到剪贴板')
  }).catch(() => {
    ElMessage.info('复制失败，请手动选择文本')
  })
}

// --- Form state ---
const currentStep = ref(1)

const form = reactive({
  customerName: '',
  hasMeter: 'unknown',
  meterModel: '',
  circuitCount: '3-5',
  voltage: '380',
  network: 'wifi',
  hasSolar: 'no',
  inverterModel: '',
  hasStorage: 'no',
  bmsProtocol: 'unknown',
  vppInterest: 'no',
})

const solution = reactive({
  tier: '',
  items: [],
  hardwareTotal: 0,
  installCost: 0,
  advice: [],
})

// --- Equipment pricing database ---
const PRICE = {
  meter: { name: '多功能电力仪表', spec: 'Modbus RS485, 0.5S级', unitPrice: 1200, note: '含电压/电流/功率/电能全参数' },
  meter_hi: { name: '高端电力仪表', spec: 'Modbus RS485, 0.2S级, 谐波', unitPrice: 2500, note: '谐波分析+电能质量，适合精密制造' },
  ct: { name: '开口式电流互感器', spec: '100A/5A, 内径24mm', unitPrice: 100, note: '卡扣安装，不需断电' },
  ct_large: { name: '开口式电流互感器', spec: '400A/5A, 内径36mm', unitPrice: 150, note: '大电流回路，卡扣安装' },
  gateway: { name: 'Modbus 数据网关', spec: 'RS485×4, WiFi/以太网', unitPrice: 800, note: '多路 RS485 汇聚上传' },
  gateway_4g: { name: '4G Modbus 网关', spec: 'RS485×4, 4G全网通', unitPrice: 1200, note: '无网络环境，含 1 年流量卡' },
  temp: { name: '温湿度传感器', spec: '壁挂式, RS485', unitPrice: 150, note: '环境监测，可选配' },
  power: { name: 'DIN导轨电源', spec: '24V/2A', unitPrice: 60, note: '网关和传感器供电' },
  cable: { name: 'RS485屏蔽双绞线', spec: '2×0.5mm²', unitPrice: 4, note: '通讯线缆，按50米预留' },
  box: { name: '弱电接线盒', spec: '8位, DIN导轨', unitPrice: 50, note: '集中安放网关和电源' },
  breaker: { name: '智能断路器', spec: '3P 63A, RS485遥控', unitPrice: 350, note: '远程分合闸，可选配' },
}

// --- Solution engine ---
function generateSolution() {
  currentStep.value = 3
  solution.items = []
  solution.advice = []

  const circuits = form.circuitCount === '1-2' ? 2 : form.circuitCount === '3-5' ? 4 : 8
  const isHv = form.voltage === '10kv'
  const ctType = isHv ? { ...PRICE.ct_large } : { ...PRICE.ct }

  // 1. 电表
  if (form.hasMeter === 'yes' && form.meterModel) {
    solution.items.push({
      name: '现有电表复用',
      spec: form.meterModel,
      qty: 1,
      unitPrice: 0,
      subtotal: 0,
      note: '已确认有 RS485 通讯口，直接接线采集',
    })
    solution.advice.push({ icon: 'check', text: `✅ 现有 ${form.meterModel} 可直接接入，省去电表采购费用` })
  } else {
    const meterItem = { ...(circuits >= 6 ? PRICE.meter_hi : PRICE.meter) }
    meterItem.qty = 1
    meterItem.subtotal = meterItem.unitPrice
    solution.items.push(meterItem)
    solution.advice.push({ icon: 'info', text: '🆕 需新装一台多功能电表，安装在总进线回路' })
    if (form.hasMeter === 'unknown') {
      solution.advice.push({ icon: 'warn', text: '⚠️ 建议客户确认柜门有无数字屏幕，有则拍下型号标签，可能直接复用省钱' })
    }
  }

  // 2. CT
  const ctQty = circuits
  const ctTotal = ctQty * ctType.unitPrice
  solution.items.push({
    name: ctType.name,
    spec: ctType.spec,
    qty: ctQty,
    unitPrice: ctType.unitPrice,
    subtotal: ctTotal,
    note: `${ctType.note}，${circuits}个回路各1只`,
  })

  // 3. 网关
  const gw = form.network === '4g' ? { ...PRICE.gateway_4g } : { ...PRICE.gateway }
  gw.qty = 1
  gw.subtotal = gw.unitPrice
  solution.items.push(gw)

  // 4. 电源和配件
  solution.items.push({ ...PRICE.power, qty: 1, subtotal: PRICE.power.unitPrice })
  solution.items.push({ ...PRICE.cable, qty: 1, subtotal: PRICE.cable.unitPrice * 50, note: '通讯线缆，约50米' })
  solution.items.push({ ...PRICE.box, qty: 1, subtotal: PRICE.box.unitPrice })

  // 5. 温湿度（可选）
  solution.items.push({ ...PRICE.temp, qty: 2, subtotal: PRICE.temp.unitPrice * 2, note: '配电房环境监测' })

  // 6. 光伏接入
  let hasAdvanced = false
  if (form.hasSolar === 'yes') {
    solution.items.push({
      name: '光伏数据接入',
      spec: form.inverterModel || '逆变器 Modbus TCP',
      qty: 1,
      unitPrice: 0,
      subtotal: 0,
      note: '逆变器已有通讯接口，拉一根网线即可读取数据，无额外硬件成本',
    })
    solution.advice.push({ icon: 'check', text: `✅ 光伏逆变器 Modbus TCP 直连，零硬件成本接入平台` })
    hasAdvanced = true
  }

  // 7. 储能接入
  if (form.hasStorage === 'yes') {
    solution.items.push({
      name: '储能BMS接入',
      spec: `协议: ${form.bmsProtocol === 'unknown' ? '待确认' : form.bmsProtocol.toUpperCase()}`,
      qty: 1,
      unitPrice: form.bmsProtocol === 'can' ? 500 : 0,
      subtotal: form.bmsProtocol === 'can' ? 500 : 0,
      note: form.bmsProtocol === 'can' ? 'CAN 协议需额外转换器' : 'Modbus TCP/RTU 直连，零硬件成本',
    })
    solution.advice.push({ icon: 'info', text: `🔋 储能 BMS 接入: ${form.bmsProtocol === 'unknown' ? '需确认通讯协议' : `${form.bmsProtocol.toUpperCase()} 协议直连`}` })
    hasAdvanced = true
  }

  // 8. VPP
  if (form.vppInterest === 'yes') {
    solution.advice.push({ icon: 'check', text: '⚡ 资源已注册 VPP 虚拟电厂，可参与需求响应获取容量补偿' })
  }

  // 9. 智能断路器（高级版）
  if (circuits >= 6) {
    const bk = { ...PRICE.breaker }
    bk.qty = 2
    bk.subtotal = bk.unitPrice * 2
    bk.note = '关键回路远程控制，可选配'
    solution.items.push(bk)
  }

  // 计算总价
  solution.hardwareTotal = solution.items.reduce((sum, item) => sum + item.subtotal, 0)
  solution.installCost = circuits <= 2 ? 800 : circuits <= 5 ? 1200 : 2000

  // 方案等级
  if (hasAdvanced || circuits >= 6) {
    solution.tier = '高级版'
  } else if (circuits >= 3) {
    solution.tier = '标准版'
  } else {
    solution.tier = '入门版'
  }

  // 补充建议
  solution.advice.push({ icon: 'info', text: `📋 共 ${solution.items.length} 项设备，建议联系当地电工采购安装，硬件可在淘宝/1688比价` })
  if (form.network === '4g') {
    solution.advice.push({ icon: 'warn', text: '⚠️ 4G 网关需办理物联网卡，年费约 50-100 元，次年续费' })
  }
  if (isHv) {
    solution.advice.push({ icon: 'warn', text: '⚠️ 10kV 高压侧需由持证高压电工操作，普通电工不可操作高压CT' })
  }
}

function copySolution() {
  let text = `【能耗翻译官 - 设备配置方案】\n`
  if (form.customerName) text += `客户：${form.customerName}\n`
  text += `方案等级：${solution.tier}\n`
  text += `监测回路：${form.circuitCount} 路 | 电压：${form.voltage}\n`
  text += `网络：${form.network === 'wifi' ? 'WiFi' : form.network === 'ethernet' ? '以太网' : '4G'}\n`
  text += `光伏：${form.hasSolar === 'yes' ? '有' : '无'} | 储能：${form.hasStorage === 'yes' ? '有' : '无'}\n\n`
  text += `━━ 设备清单 ━━\n`
  solution.items.forEach(item => {
    const cost = item.subtotal > 0 ? `¥${item.subtotal.toLocaleString()}` : '已利用'
    text += `${item.name} ×${item.qty}  ${item.spec}  ${cost}\n`
  })
  text += `\n硬件：¥${solution.hardwareTotal.toLocaleString()}\n`
  text += `安装：¥${solution.installCost.toLocaleString()}\n`
  text += `合计：¥${(solution.hardwareTotal + solution.installCost).toLocaleString()}\n`

  navigator.clipboard.writeText(text).then(() => {
    ElMessage.success('方案已复制到剪贴板，可发送给客户')
  }).catch(() => {
    ElMessage.info('复制失败，请手动选择文本')
  })
}

function nextStep(step) {
  currentStep.value = step
}

function resetForm() {
  Object.assign(form, {
    customerName: '',
    hasMeter: 'unknown',
    meterModel: '',
    circuitCount: '3-5',
    voltage: '380',
    network: 'wifi',
    hasSolar: 'no',
    inverterModel: '',
    hasStorage: 'no',
    bmsProtocol: 'unknown',
    vppInterest: 'no',
  })
  currentStep.value = 1
}
</script>

<style scoped>
.solution-matcher {
  max-width: 860px;
}

.page-header {
  margin-bottom: 24px;
}

.page-header h2 {
  margin: 0 0 6px;
  font-size: 22px;
  color: var(--text-primary);
}

.page-header .subtitle {
  margin: 0;
  font-size: 13px;
  color: var(--text-secondary);
}

/* Steps */
.steps {
  display: flex;
  align-items: center;
  margin-bottom: 28px;
  padding: 20px;
  background: var(--bg-secondary);
  border: 1px solid var(--border);
  border-radius: 10px;
}

.step {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--text-secondary);
  white-space: nowrap;
}

.step-num {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 12px;
  background: var(--bg-tertiary);
  color: var(--text-secondary);
  transition: all 0.3s;
}

.step.active {
  color: var(--text-primary);
}

.step.active .step-num {
  background: var(--accent);
  color: #fff;
}

.step.done {
  color: var(--accent);
}

.step.done .step-num {
  background: #22c55e;
  color: #fff;
}

.step-line {
  flex: 1;
  height: 2px;
  background: var(--border);
  margin: 0 12px;
  transition: background 0.3s;
}

.step-line.done {
  background: var(--accent);
}

/* Step content */
.step-content {
  animation: fadeSlideIn 0.3s ease;
}

@keyframes fadeSlideIn {
  from { opacity: 0; transform: translateY(8px); }
  to { opacity: 1; transform: translateY(0); }
}

/* Cards */
.el-card {
  background: var(--bg-secondary) !important;
  border: 1px solid var(--border) !important;
  border-radius: 10px !important;
}

.card-title {
  font-weight: 700;
  font-size: 15px;
  color: var(--text-primary);
}

/* Form */
.el-form-item {
  margin-bottom: 20px;
}

.radio-label {
  font-weight: 600;
  display: block;
  font-size: 14px;
  color: var(--text-primary);
}

.radio-hint {
  font-size: 12px;
  color: var(--text-secondary);
  margin-left: 4px;
}

.form-hint {
  font-size: 11px;
  color: var(--text-secondary);
  margin-top: 4px;
}

/* Step actions */
.step-actions {
  display: flex;
  gap: 12px;
  justify-content: flex-end;
  margin-top: 24px;
}

/* Result */
.result-card {
  margin-bottom: 20px;
}

.result-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 8px;
}

.result-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.customer-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 12px;
  background: var(--accent);
  color: #fff;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 600;
  margin-top: 4px;
}

.item-note {
  font-size: 12px;
  color: var(--text-secondary);
}

/* Cost summary */
.cost-summary {
  margin-top: 20px;
  padding: 16px;
  background: var(--bg-tertiary);
  border-radius: 8px;
}

.cost-row {
  display: flex;
  justify-content: space-between;
  padding: 6px 0;
  font-size: 14px;
  color: var(--text-primary);
}

.cost-row.total {
  border-top: 1px solid var(--border);
  padding-top: 10px;
  margin-top: 4px;
  font-weight: 700;
  font-size: 16px;
  color: var(--accent);
}

.cost-value {
  font-family: monospace;
  font-weight: 600;
}

.cost-note {
  font-size: 11px;
  color: var(--text-secondary);
  margin-top: 8px;
}

/* Advice */
.advice-card {
  margin-bottom: 20px;
}

.advice-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.advice-item {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  font-size: 13px;
  color: var(--text-primary);
  line-height: 1.6;
}

/* Element overrides */
:deep(.el-radio) {
  margin-right: 24px;
  margin-bottom: 8px;
  height: auto;
  align-items: flex-start;
  padding: 8px 12px;
  border: 1px solid var(--border);
  border-radius: 8px;
  transition: border-color 0.2s;
}

:deep(.el-radio.is-checked) {
  border-color: var(--accent);
  background: rgba(59, 130, 246, 0.08);
}

:deep(.el-radio__label) {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

:deep(.el-table) {
  --el-table-bg-color: transparent;
  --el-table-tr-bg-color: transparent;
  --el-table-header-bg-color: var(--bg-tertiary);
  --el-table-border-color: var(--border);
  --el-table-text-color: var(--text-primary);
  --el-table-header-text-color: var(--text-secondary);
  font-size: 13px;
}

:deep(.el-table th) {
  font-weight: 600;
  font-size: 11px;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

:deep(.el-table .el-table__row--striped) {
  background: var(--bg-tertiary);
}

:deep(.el-input__wrapper) {
  background: var(--bg-tertiary);
  box-shadow: none;
  border: 1px solid var(--border);
}

:deep(.el-select .el-input__wrapper) {
  background: var(--bg-tertiary);
}

:deep(.el-card__header) {
  border-bottom: 1px solid var(--border);
  padding: 14px 20px;
}

:deep(.el-card__body) {
  padding: 20px;
}

/* ── AI Mode Styles ── */

.mode-toggle {
  margin-top: 12px;
}

:deep(.mode-toggle .el-radio-button__inner) {
  display: flex;
  align-items: center;
  gap: 4px;
}

.ai-input-panel {
  animation: fadeSlideIn 0.3s ease;
}

.ai-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.ai-hints {
  font-size: 13px;
  color: var(--text-secondary);
  line-height: 1.8;
  margin-bottom: 16px;
}

.ai-hints p {
  margin: 0 0 6px;
  color: var(--text-primary);
  font-weight: 600;
}

.ai-hints ul {
  margin: 0;
  padding-left: 20px;
}

.ai-hints li {
  margin-bottom: 2px;
}

.ai-actions {
  display: flex;
  gap: 12px;
  margin-top: 16px;
}

.ai-char-count {
  font-size: 11px;
  color: var(--text-secondary);
  margin-top: 8px;
}

.ai-result-banner {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;
  background: rgba(34, 197, 94, 0.1);
  border: 1px solid rgba(34, 197, 94, 0.2);
  border-radius: 8px;
  margin-bottom: 20px;
  font-size: 14px;
  color: var(--text-primary);
}

.ai-result-banner .el-button {
  margin-left: auto;
}

.params-card {
  margin-bottom: 20px;
}

.params-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
}

.param-item {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.param-label {
  font-size: 11px;
  color: var(--text-secondary);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.param-value {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary);
}

@media (max-width: 768px) {
  .params-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
</style>
