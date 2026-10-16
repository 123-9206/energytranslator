<template>
  <div class="partner-page">
    <!-- KPI + 新增 -->
    <el-card shadow="never" style="margin-bottom:20px">
      <div class="toolbar">
        <div class="kpi-inline">
          <span class="ki-num">{{ partners.length }}</span><span class="ki-lbl">合作售电方</span>
        </div>
        <el-button type="primary" @click="openCreate">新增合作方</el-button>
      </div>
    </el-card>

    <!-- 合作方列表 -->
    <el-card shadow="never" style="margin-bottom:20px">
      <template #header><span>合作售电公司</span></template>
      <el-table :data="partners" v-loading="loading" stripe>
        <el-table-column prop="partnerCompanyName" label="公司名称" min-width="160" show-overflow-tooltip />
        <el-table-column prop="licenseRegion" label="牌照属地" width="110" />
        <el-table-column prop="licenseNo" label="牌照编号" min-width="150" show-overflow-tooltip />
        <el-table-column prop="contactName" label="联系人" width="100" />
        <el-table-column prop="contactPhone" label="联系电话" width="140" />
        <el-table-column label="平台分成" width="100" align="center">
          <template #default="{ row }">{{ (row.platformShareRatio * 100).toFixed(0) }}%</template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }"><el-tag :type="row.status === 'active' ? 'success' : 'info'" size="small">{{ row.status === 'active' ? '启用' : '停用' }}</el-tag></template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!loading && !partners.length" description="暂无合作售电公司，点击右上角新增" />
    </el-card>

    <!-- 分润计算 -->
    <el-card shadow="never" style="margin-bottom:20px">
      <template #header><span>售电分润试算</span></template>
      <div class="toolbar">
        <el-select v-model="calc.partnerId" placeholder="选择合作方(可选)" clearable style="width:240px">
          <el-option v-for="p in partners" :key="p.id" :label="p.partnerCompanyName" :value="p.id" />
        </el-select>
        <el-input v-model="calc.period" placeholder="结算周期 yyyy-MM" style="width:180px" />
        <el-button type="primary" :loading="calcLoading" @click="doCalc">计算分润</el-button>
      </div>
      <el-descriptions v-if="calcResult" :column="2" border size="small" style="margin-top:16px">
        <el-descriptions-item label="结算周期">{{ calcResult.period }}</el-descriptions-item>
        <el-descriptions-item label="分润基数(峰谷月省)">{{ fmt(calcResult.savingBase) }} 元</el-descriptions-item>
        <el-descriptions-item label="平台分成比例">{{ (calcResult.platformShareRatio * 100).toFixed(0) }}%</el-descriptions-item>
        <el-descriptions-item label="平台分成金额">
          <b style="color:#34d399">{{ fmt(calcResult.platformShareAmount) }} 元</b>
        </el-descriptions-item>
        <el-descriptions-item label="合作方分成金额" :span="2">
          <b style="color:#38bdf8">{{ fmt(calcResult.partnerShareAmount) }} 元</b>
        </el-descriptions-item>
      </el-descriptions>
      <el-empty v-else-if="!calcLoading" description="选择合作方与周期后试算平台/合作方分成" />
    </el-card>

    <!-- 分润结算单 -->
    <el-card shadow="never">
      <template #header><span>分润结算单</span></template>
      <el-table :data="shares" v-loading="shareLoading" stripe>
        <el-table-column prop="period" label="周期" width="110" />
        <el-table-column prop="enterpriseId" label="企业ID" min-width="180" show-overflow-tooltip />
        <el-table-column prop="partnerId" label="合作方ID" min-width="160" show-overflow-tooltip />
        <el-table-column label="分润基数" width="130" align="right">
          <template #default="{ row }">{{ fmt(row.savingBase) }} 元</template>
        </el-table-column>
        <el-table-column label="平台分成" width="130" align="right">
          <template #default="{ row }">{{ fmt(row.platformShareAmount) }} 元</template>
        </el-table-column>
        <el-table-column label="合作方分成" width="130" align="right">
          <template #default="{ row }">{{ fmt(row.partnerShareAmount) }} 元</template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }"><el-tag :type="row.status === 'settled' ? 'success' : 'warning'" size="small">{{ row.status === 'settled' ? '已结算' : '待结算' }}</el-tag></template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!shareLoading && !shares.length" description="暂无分润结算单" />
    </el-card>

    <!-- 新增合作方对话框 -->
    <el-dialog v-model="createVisible" title="新增合作售电公司" width="520px">
      <el-form :model="form" label-width="96px">
        <el-form-item label="公司名称" required>
          <el-input v-model="form.partnerCompanyName" placeholder="如：XX售电有限公司" />
        </el-form-item>
        <el-form-item label="牌照属地">
          <el-input v-model="form.licenseRegion" placeholder="如：江苏省" />
        </el-form-item>
        <el-form-item label="牌照编号">
          <el-input v-model="form.licenseNo" placeholder="售电牌照编号" />
        </el-form-item>
        <el-form-item label="联系人">
          <el-input v-model="form.contactName" />
        </el-form-item>
        <el-form-item label="联系电话">
          <el-input v-model="form.contactPhone" />
        </el-form-item>
        <el-form-item label="平台分成">
          <el-input-number v-model="form.platformShareRatio" :min="0" :max="1" :step="0.05" :precision="2" />
          <span class="dim">（0~1，如 0.3 = 平台抽30%）</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="createLoading" @click="submitCreate">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { partnersAPI } from '../api/partners'

const partners = ref([])
const shares = ref([])
const loading = ref(false)
const shareLoading = ref(false)
const createVisible = ref(false)
const createLoading = ref(false)

const calc = reactive({ partnerId: '', period: '' })
const calcLoading = ref(false)
const calcResult = ref(null)

const form = reactive({
  partnerCompanyName: '', licenseRegion: '', licenseNo: '',
  contactName: '', contactPhone: '', platformShareRatio: 0.3,
})

const fmt = (n) => {
  if (n == null) return '0'
  return Number(n).toLocaleString('zh-CN', { maximumFractionDigits: 0 })
}

const loadPartners = async () => {
  loading.value = true
  try {
    const res = await partnersAPI.list()
    partners.value = res?.data || res || []
  } catch (e) { /* ignore */ } finally { loading.value = false }
}
const loadShares = async () => {
  shareLoading.value = true
  try {
    const res = await partnersAPI.getRevenueShares()
    shares.value = res?.data || res || []
  } catch (e) { /* ignore */ } finally { shareLoading.value = false }
}

const openCreate = () => {
  Object.assign(form, { partnerCompanyName: '', licenseRegion: '', licenseNo: '', contactName: '', contactPhone: '', platformShareRatio: 0.3 })
  createVisible.value = true
}
const submitCreate = async () => {
  if (!form.partnerCompanyName) { ElMessage.warning('请填写公司名称'); return }
  createLoading.value = true
  try {
    await partnersAPI.create({ ...form })
    ElMessage.success('合作方已添加')
    createVisible.value = false
    loadPartners()
  } catch (e) { /* interceptor */ } finally { createLoading.value = false }
}

const doCalc = async () => {
  if (!calc.period) { ElMessage.warning('请填写结算周期 yyyy-MM'); return }
  calcLoading.value = true
  try {
    const res = await partnersAPI.calculateRevenueShare({ partnerId: calc.partnerId || undefined, period: calc.period })
    calcResult.value = res?.data || res || null
  } catch (e) { /* interceptor */ } finally { calcLoading.value = false }
}

onMounted(() => { loadPartners(); loadShares() })
</script>

<style scoped>
.toolbar { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.kpi-inline { display: flex; flex-direction: column; margin-right: auto; }
.ki-num { font-size: 24px; font-weight: 700; color: var(--accent); }
.ki-lbl { font-size: 12px; color: var(--text-secondary); }
.dim { font-size: 12px; color: var(--text-secondary); margin-left: 8px; }
</style>
