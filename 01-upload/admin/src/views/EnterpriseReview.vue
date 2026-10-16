<template>
  <div class="review-page">
    <el-card shadow="never">
      <template #header>
        <div class="page-header">
          <span>企业审核</span>
          <div class="header-right">
            <el-tag type="warning" size="small" effect="dark" v-if="pendingCount > 0">
              待审核 {{ pendingCount }} 家
            </el-tag>
            <el-button @click="loadPending">
              <el-icon><Refresh /></el-icon> 刷新
            </el-button>
          </div>
        </div>
      </template>

      <el-alert
        v-if="pendingCount === 0"
        title="暂无待审核企业"
        type="success"
        :closable="false"
        show-icon
      />

      <el-table
        v-else
        :data="enterprises"
        style="width:100%"
        v-loading="loading"
        size="small"
        stripe
      >
        <el-table-column prop="name" label="企业名称" min-width="180" show-overflow-tooltip />
        <el-table-column prop="type" label="类型" width="120">
          <template #default="{row}">
            <el-tag size="small">{{ typeText(row.type) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="creditCode" label="统一信用代码" min-width="160" show-overflow-tooltip />
        <el-table-column prop="contactName" label="联系人" width="120" />
        <el-table-column prop="contactPhone" label="联系电话" width="140" />
        <el-table-column prop="contactEmail" label="邮箱" min-width="180" show-overflow-tooltip />
        <el-table-column prop="createdAt" label="注册时间" width="170">
          <template #default="{row}">
            {{ row.createdAt ? dayjs(row.createdAt).format('YYYY-MM-DD HH:mm') : '-' }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{row}">
            <el-button text type="success" size="small" @click="handleApprove(row)">
              <el-icon><Select /></el-icon> 通过
            </el-button>
            <el-button text type="danger" size="small" @click="handleReject(row)">
              <el-icon><Close /></el-icon> 拒绝
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import dayjs from 'dayjs'
import { adminAPI } from '../api/admin'

const enterprises = ref([])
const loading = ref(false)
const pendingCount = ref(0)

const typeText = (t) => ({
  manufacturing: '制造业',
  commercial: '商业',
  residential: '居民',
  general: '综合',
}[t] || t || '综合')

const loadPending = async () => {
  loading.value = true
  try {
    const res = await adminAPI.getEnterprises({ status: 'pending' })
    const body = res.data || res
    enterprises.value = body || []
    pendingCount.value = enterprises.value.length
  } catch (e) {
    ElMessage.error('加载待审核企业失败')
    enterprises.value = []
    pendingCount.value = 0
  } finally {
    loading.value = false
  }
}

const handleApprove = async (enterprise) => {
  try {
    await ElMessageBox.confirm(
      `确定通过企业「${enterprise.name}」的接入申请吗？通过后该企业账号即可登录使用。`,
      '审核通过',
      { type: 'success', confirmButtonText: '通过', cancelButtonText: '取消' }
    )
    await adminAPI.approveEnterprise(enterprise.id)
    ElMessage.success(`企业「${enterprise.name}」已审核通过`)
    loadPending()
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error(e.response?.data?.message || '审核失败')
    }
  }
}

const handleReject = async (enterprise) => {
  try {
    await ElMessageBox.confirm(
      `确定拒绝企业「${enterprise.name}」的接入申请吗？拒绝后其账号将被禁用。`,
      '审核拒绝',
      { type: 'warning', confirmButtonText: '拒绝', cancelButtonText: '取消' }
    )
    await adminAPI.rejectEnterprise(enterprise.id)
    ElMessage.success(`企业「${enterprise.name}」已拒绝`)
    loadPending()
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error(e.response?.data?.message || '操作失败')
    }
  }
}

onMounted(loadPending)
</script>

<style scoped>
.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}
</style>
