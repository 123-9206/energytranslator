<template>
  <div class="users-page">
    <el-card shadow="never">
      <template #header>
        <div class="page-header">
          <span>用户列表</span>
          <el-button type="primary" @click="showCreateDialog = true">
            <el-icon><Plus /></el-icon> 新增用户
          </el-button>
        </div>
      </template>

      <!-- Filters -->
      <div class="filters">
        <el-input v-model="search" placeholder="搜索用户名/手机号" clearable style="width:220px" @clear="loadUsers" @keyup.enter="loadUsers">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-select v-model="filterRole" placeholder="角色筛选" clearable style="width:140px" @change="loadUsers">
          <el-option label="全部角色" value="" />
          <el-option label="管理员" value="admin" />
          <el-option label="企业管理员" value="enterprise_admin" />
          <el-option label="普通用户" value="user" />
        </el-select>
        <el-button @click="loadUsers">
          <el-icon><Refresh /></el-icon> 刷新
        </el-button>
      </div>

      <el-table :data="users" style="width:100%" v-loading="loading" size="small" stripe>
        <el-table-column prop="username" label="用户名" min-width="140" />
        <el-table-column prop="phone" label="手机号" width="140" />
        <el-table-column prop="email" label="邮箱" width="180" show-overflow-tooltip />
        <el-table-column prop="role" label="角色" width="130">
          <template #default="{row}">
            <el-tag :type="roleType(row.role)" size="small">{{ roleText(row.role) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="enterpriseName" label="所属企业" min-width="160" show-overflow-tooltip />
        <el-table-column prop="status" label="状态" width="90">
          <template #default="{row}">
            <el-tag :type="row.status === 'active' ? 'success' : 'danger'" size="small">
              {{ row.status === 'active' ? '正常' : row.status || '-' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="loginCount" label="登录次数" width="100" />
        <el-table-column prop="lastLoginAt" label="最近登录" width="170">
          <template #default="{row}">
            {{ row.lastLoginAt ? dayjs(row.lastLoginAt).format('YYYY-MM-DD HH:mm') : '从未登录' }}
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="注册时间" width="170">
          <template #default="{row}">
            {{ row.createdAt ? dayjs(row.createdAt).format('YYYY-MM-DD HH:mm') : '-' }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{row}">
            <el-button text type="primary" size="small" @click="viewUser(row)">详情</el-button>
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
          @current-change="loadUsers"
          @size-change="loadUsers"
        />
      </div>
    </el-card>

    <!-- Create User Dialog -->
    <el-dialog v-model="showCreateDialog" title="新增用户" width="500px" @closed="resetForm">
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="100px">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" placeholder="请输入用户名" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" type="password" placeholder="请输入密码" show-password />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="form.phone" placeholder="请输入手机号" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="form.email" placeholder="请输入邮箱" />
        </el-form-item>
        <el-form-item label="角色" prop="role">
          <el-select v-model="form.role" style="width:100%">
            <el-option label="普通用户" value="user" />
            <el-option label="企业管理员" value="enterprise_admin" />
            <el-option label="管理员" value="admin" />
          </el-select>
        </el-form-item>
        <el-form-item label="企业名称">
          <el-input v-model="form.enterpriseName" placeholder="请输入企业名称" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateDialog = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="handleCreate">创建</el-button>
      </template>
    </el-dialog>

    <!-- User Detail Dialog -->
    <el-dialog v-model="showDetailDialog" title="用户详情" width="560px">
      <el-descriptions :column="2" border size="small" v-if="currentUser">
        <el-descriptions-item label="用户名">{{ currentUser.username }}</el-descriptions-item>
        <el-descriptions-item label="角色">
          <el-tag :type="roleType(currentUser.role)" size="small">{{ roleText(currentUser.role) }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="手机号">{{ currentUser.phone || '-' }}</el-descriptions-item>
        <el-descriptions-item label="邮箱">{{ currentUser.email || '-' }}</el-descriptions-item>
        <el-descriptions-item label="企业ID">{{ currentUser.enterpriseId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="企业名称">{{ currentUser.enterpriseName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ currentUser.status || 'active' }}</el-descriptions-item>
        <el-descriptions-item label="登录次数">{{ currentUser.loginCount || 0 }}</el-descriptions-item>
        <el-descriptions-item label="最近登录">{{ currentUser.lastLoginAt ? dayjs(currentUser.lastLoginAt).format('YYYY-MM-DD HH:mm:ss') : '从未登录' }}</el-descriptions-item>
        <el-descriptions-item label="注册时间">{{ currentUser.createdAt ? dayjs(currentUser.createdAt).format('YYYY-MM-DD HH:mm:ss') : '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import dayjs from 'dayjs'
import { authAPI } from '../api/auth'
import { adminAPI } from '../api/admin'

const users = ref([])
const loading = ref(false)
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const search = ref('')
const filterRole = ref('')

const showCreateDialog = ref(false)
const showDetailDialog = ref(false)
const creating = ref(false)
const currentUser = ref(null)
const formRef = ref(null)

const form = reactive({
  username: '', password: '', phone: '', email: '', role: 'user', enterpriseName: '',
})

const formRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, min: 6, message: '密码至少6位', trigger: 'blur' }],
  phone: [{ required: true, message: '请输入手机号', trigger: 'blur' }],
  role: [{ required: true, message: '请选择角色', trigger: 'change' }],
}

const roleType = (r) => ({ admin: 'danger', enterprise_admin: 'warning', user: 'info' }[r] || 'info')
const roleText = (r) => ({ admin: '管理员', enterprise_admin: '企业管理员', user: '普通用户' }[r] || r)

const loadUsers = async () => {
  loading.value = true
  try {
    const params = {
      page: (page.value || 1) - 1,   // 后端 0-based
      size: pageSize.value,
    }
    if (filterRole.value) params.role = filterRole.value
    if (search.value) params.keyword = search.value

    const res = await adminAPI.getUsers(params)
    const body = res.data || res
    users.value = body.content || body.list || []
    total.value = body.totalElements || body.total || 0
  } catch (e) {
    ElMessage.error('加载用户列表失败')
    users.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

const viewUser = (user) => {
  currentUser.value = user
  showDetailDialog.value = true
}

const handleCreate = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    creating.value = true
    try {
      await authAPI.register({
        username: form.username,
        password: form.password,
        phone: form.phone,
        email: form.email,
        role: form.role,
        enterpriseName: form.enterpriseName,
      })
      ElMessage.success('用户创建成功')
      showCreateDialog.value = false
      resetForm()
      loadUsers()
    } catch (e) {
      ElMessage.error(e.response?.data?.message || '创建失败')
    } finally {
      creating.value = false
    }
  })
}

const handleDelete = async (user) => {
  try {
    await ElMessageBox.confirm(`确定要删除用户「${user.username}」吗？此操作不可撤销。`, '删除确认', {
      type: 'warning',
      confirmButtonText: '确定删除',
      cancelButtonText: '取消',
    })
    await adminAPI.deleteUser(user.id)
    ElMessage.success('用户已删除')
    loadUsers()
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error(e.response?.data?.message || '删除失败')
    }
  }
}

const resetForm = () => {
  form.username = ''
  form.password = ''
  form.phone = ''
  form.email = ''
  form.role = 'user'
  form.enterpriseName = ''
}

loadUsers()
</script>

<style scoped>
.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.filters {
  display: flex;
  gap: 12px;
  margin-bottom: 16px;
  align-items: center;
}

.pagination-wrap {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}
</style>
