<template>
  <div class="ca-page">
    <el-alert type="success" :closable="false" show-icon style="margin-bottom:20px"
      title="碳资产单位已统一为 吨CO₂ (tCO₂)"
      :description="asset?.unitNote || '单位：吨CO₂（tCO₂），与全国碳市场口径一致。'" />

    <div class="kpi-grid">
      <div class="kpi-card">
        <div class="kpi-num" style="color:#38bdf8">{{ fmt(asset.allocatedQuota) }}</div>
        <div class="kpi-lbl">核定配额 (tCO₂)</div>
      </div>
      <div class="kpi-card">
        <div class="kpi-num" style="color:#a78bfa">{{ fmt(asset.purchasedQuota) }}</div>
        <div class="kpi-lbl">已购碳汇 (tCO₂)</div>
      </div>
      <div class="kpi-card">
        <div class="kpi-num" style="color:#fb923c">{{ fmt(asset.actualEmission) }}</div>
        <div class="kpi-lbl">实际排放 (tCO₂)</div>
      </div>
      <div class="kpi-card">
        <div class="kpi-num" :style="{ color: asset.surplus >= 0 ? '#34d399' : '#f87171' }">{{ fmt(asset.surplus) }}</div>
        <div class="kpi-lbl">盈余 (tCO₂)</div>
      </div>
    </div>

    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span>碳资产状态</span>
          <el-tag :type="asset.surplus >= 0 ? 'success' : 'danger'" size="small">{{ asset.status }}</el-tag>
        </div>
      </template>
      <el-descriptions :column="2" border size="small">
        <el-descriptions-item label="核定配额 (tCO₂)">{{ fmt(asset.allocatedQuota) }}</el-descriptions-item>
        <el-descriptions-item label="已购碳汇 (tCO₂)">{{ fmt(asset.purchasedQuota) }}</el-descriptions-item>
        <el-descriptions-item label="实际排放 (tCO₂)">{{ fmt(asset.actualEmission) }}</el-descriptions-item>
        <el-descriptions-item label="预警阈值">{{ asset.alertThreshold }}%</el-descriptions-item>
        <el-descriptions-item label="盈余 (tCO₂)" :span="2">
          <b :style="{ color: asset.surplus >= 0 ? '#34d399' : '#f87171' }">{{ fmt(asset.surplus) }}</b>
          <span class="dim"> （配额 + 碳汇 − 实际排放）</span>
        </el-descriptions-item>
      </el-descriptions>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { carbonAPI } from '../api/carbon'

const asset = ref({ allocatedQuota: 0, purchasedQuota: 0, actualEmission: 0, surplus: 0, status: '', alertThreshold: 80, unitNote: '' })

const fmt = (n) => {
  if (n == null) return '0'
  return Number(n).toLocaleString('zh-CN', { maximumFractionDigits: 0 })
}

const loadData = async () => {
  try {
    const res = await carbonAPI.getAsset()
    asset.value = res?.data || res || asset.value
  } catch (e) { /* interceptor handled */ }
}

onMounted(loadData)
</script>

<style scoped>
.kpi-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 16px; margin-bottom: 20px; }
.kpi-card { text-align: center; padding: 22px 16px; background: var(--bg-secondary); border: 1px solid var(--border); border-radius: 12px; }
.kpi-num { font-size: 26px; font-weight: 700; }
.kpi-lbl { font-size: 12px; color: var(--text-secondary); margin-top: 6px; }
.card-header { display: flex; align-items: center; justify-content: space-between; }
.dim { font-size: 12px; color: var(--text-secondary); }
</style>
