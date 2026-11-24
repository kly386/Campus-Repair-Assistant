<template>
  <div class="users-page">
    <div class="page-header">
      <div>
        <h2 class="page-title">用户管理</h2>
        <p class="page-subtitle">维护用户基本信息、账号状态与角色归属</p>
      </div>
      <div class="header-actions">
        <el-button :loading="loading" @click="load">刷新</el-button>
      </div>
    </div>

    <el-card shadow="never" class="content-card">
      <div class="toolbar">
        <el-input v-model="filters.keyword" class="search-input" placeholder="搜索用户名/姓名/电话" clearable
                  @keyup.enter="load">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-select v-model="filters.role" placeholder="全部角色" clearable style="width: 150px">
          <el-option label="管理员 ADMIN" value="ADMIN" />
          <el-option label="学生 STUDENT" value="STUDENT" />
          <el-option label="师傅 WORKER" value="WORKER" />
        </el-select>
        <el-select v-model="filters.status" placeholder="全部状态" clearable style="width: 150px">
          <el-option label="正常 ACTIVE" value="ACTIVE" />
          <el-option label="锁定 LOCKED" value="LOCKED" />
          <el-option label="禁用 DISABLED" value="DISABLED" />
        </el-select>
        <el-button type="primary" @click="load">查询</el-button>
        <el-button @click="resetFilters">重置</el-button>
      </div>

      <el-table :data="rows" v-loading="loading" size="small">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="username" label="用户名" width="120" show-overflow-tooltip />
        <el-table-column prop="realName" label="姓名" width="110" show-overflow-tooltip />
        <el-table-column label="角色" min-width="140">
          <template #default="{ row }">
            <template v-if="row.roleNames?.length">
              <el-tag v-for="name in row.roleNames" :key="name" size="small" style="margin-right: 4px">{{ name }}</el-tag>
            </template>
            <span v-else>{{ row.role || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="phone" label="电话" width="130" show-overflow-tooltip />
        <el-table-column prop="email" label="邮箱" min-width="160" show-overflow-tooltip />
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="statusTag(row.status)">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="最近登录" width="160">
          <template #default="{ row }">{{ fmtTime(row.lastLoginAt) }}</template>
        </el-table-column>
        <el-table-column v-if="canEdit" label="操作" width="210" fixed="right" align="center">
          <template #default="{ row }">
            <el-button type="primary" link @click="openEdit(row)">编辑</el-button>
            <el-button type="primary" link @click="openRoles(row)">角色</el-button>
            <el-button v-if="row.status === 'LOCKED'" type="success" link @click="doUnlock(row)">解锁</el-button>
            <el-button v-else type="warning" link @click="doLock(row)">锁定</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无用户" :image-size="72" />
        </template>
      </el-table>
      <div class="table-footer">共 {{ rows.length }} 位用户</div>
    </el-card>

    <el-dialog v-model="editVisible" title="编辑用户" width="520px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="用户名">
          <el-input :model-value="current?.username || ''" disabled />
        </el-form-item>
        <el-form-item label="姓名">
          <el-input v-model="form.realName" maxlength="50" />
        </el-form-item>
        <el-form-item label="电话">
          <el-input v-model="form.phone" maxlength="20" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="form.email" maxlength="100" />
        </el-form-item>
        <el-form-item label="楼栋">
          <el-input v-model="form.building" maxlength="50" />
        </el-form-item>
        <el-form-item label="房间">
          <el-input v-model="form.room" maxlength="50" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status" style="width: 100%">
            <el-option label="正常 ACTIVE" value="ACTIVE" />
            <el-option label="锁定 LOCKED" value="LOCKED" />
            <el-option label="禁用 DISABLED" value="DISABLED" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveUser">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="rolesVisible" title="分配角色" width="420px">
      <div class="roles-hint">保存后会吊销该用户全部会话，需重新登录才能生效</div>
      <el-checkbox-group v-model="selectedRoleIds" class="roles-list">
        <el-checkbox v-for="r in roles" :key="r.id" :value="r.id">
          {{ r.name }} <span class="role-code">{{ r.code }}</span>
        </el-checkbox>
      </el-checkbox-group>
      <template #footer>
        <el-button @click="rolesVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveRoles">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { Search } from '@element-plus/icons-vue';
import { hasPermission } from '../auth/session';
import { formatApiError } from '../api/http';
import {
  assignUserRoles,
  listRoles,
  listUsers,
  lockUser,
  unlockUser,
  updateUser
} from '../api/admin';

const canEdit = computed(() => hasPermission('system:user:update'));

const loading = ref(false);
const saving = ref(false);
const rows = ref([]);
const roles = ref([]);
const filters = reactive({ keyword: '', role: null, status: null });

const editVisible = ref(false);
const rolesVisible = ref(false);
const current = ref(null);
const form = reactive({ realName: '', phone: '', email: '', building: '', room: '', status: 'ACTIVE' });
const selectedRoleIds = ref([]);

const STATUS_TEXT = { ACTIVE: '正常', LOCKED: '锁定', DISABLED: '禁用' };
const statusText = (v) => STATUS_TEXT[v] || v || '-';
const statusTag = (v) => ({ ACTIVE: 'success', LOCKED: 'warning', DISABLED: 'info' }[v] || 'info');
const fmtTime = (v) => (v ? String(v).replace('T', ' ').slice(0, 19) : '-');

async function load() {
  loading.value = true;
  try {
    rows.value = await listUsers({ ...filters });
  } catch (e) {
    ElMessage.error(formatApiError(e));
  } finally {
    loading.value = false;
  }
}

function resetFilters() {
  filters.keyword = '';
  filters.role = null;
  filters.status = null;
  load();
}

function openEdit(row) {
  current.value = row;
  Object.assign(form, {
    realName: row.realName || '',
    phone: row.phone || '',
    email: row.email || '',
    building: row.building || '',
    room: row.room || '',
    status: row.status || 'ACTIVE'
  });
  editVisible.value = true;
}

async function saveUser() {
  saving.value = true;
  try {
    await updateUser(current.value.id, { ...form });
    ElMessage.success('用户信息已更新');
    editVisible.value = false;
    await load();
  } catch (e) {
    ElMessage.error(formatApiError(e));
  } finally {
    saving.value = false;
  }
}

async function openRoles(row) {
  current.value = row;
  selectedRoleIds.value = [...(row.roleIds || [])];
  rolesVisible.value = true;
}

async function saveRoles() {
  saving.value = true;
  try {
    await assignUserRoles(current.value.id, selectedRoleIds.value);
    ElMessage.success('角色已更新，该用户需重新登录');
    rolesVisible.value = false;
    await load();
  } catch (e) {
    ElMessage.error(formatApiError(e));
  } finally {
    saving.value = false;
  }
}

async function doLock(row) {
  try {
    await ElMessageBox.confirm(`确认锁定用户「${row.realName || row.username}」10 分钟吗？`, '锁定账号', {
      type: 'warning', confirmButtonText: '锁定', cancelButtonText: '取消'
    });
  } catch {
    return;
  }
  try {
    await lockUser(row.id, 10);
    ElMessage.success('账号已锁定');
    await load();
  } catch (e) {
    ElMessage.error(formatApiError(e));
  }
}

async function doUnlock(row) {
  try {
    await unlockUser(row.id);
    ElMessage.success('账号已解锁');
    await load();
  } catch (e) {
    ElMessage.error(formatApiError(e));
  }
}

onMounted(async () => {
  load();
  try {
    roles.value = await listRoles();
  } catch {
    roles.value = [];
  }
});
</script>

<style scoped>
.users-page { max-width: 1300px; }
.page-header { display: flex; align-items: flex-start; justify-content: space-between; margin-bottom: 16px; }
.page-title { margin: 0; font-size: 20px; font-weight: 600; color: #1f2d3d; }
.page-subtitle { margin: 4px 0 0; font-size: 13px; color: #8a97a6; }
.header-actions { display: flex; gap: 8px; }
.content-card { border: 1px solid #e9edf3; border-radius: 8px; }
.toolbar { display: flex; flex-wrap: wrap; gap: 10px; align-items: center; margin-bottom: 16px; }
.search-input { width: 260px; }
.table-footer { margin-top: 12px; text-align: right; font-size: 12px; color: #909399; }
.roles-hint { margin-bottom: 12px; font-size: 12px; color: #e6a23c; }
.roles-list { display: flex; flex-direction: column; gap: 10px; }
.role-code { margin-left: 6px; font-size: 12px; color: #909399; }
</style>
