<template>
  <div class="roles-page">
    <div class="page-header">
      <div>
        <h2 class="page-title">角色管理</h2>
        <p class="page-subtitle">查看三类角色，并为角色配置菜单与接口权限</p>
      </div>
      <div class="header-actions">
        <el-button :loading="loading" @click="load">刷新</el-button>
      </div>
    </div>

    <el-card shadow="never" class="content-card">
      <el-table :data="rows" v-loading="loading" size="small">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column prop="code" label="角色编码" width="150" show-overflow-tooltip />
        <el-table-column prop="name" label="角色名称" width="160" show-overflow-tooltip />
        <el-table-column prop="description" label="说明" min-width="260" show-overflow-tooltip />
        <el-table-column label="操作" width="140" align="center">
          <template #default="{ row }">
            <el-button type="primary" link @click="openPermissions(row)">配置权限</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无角色" :image-size="72" />
        </template>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="`配置权限 - ${current?.name || ''}`" width="760px">
      <div class="dialog-hint">
        保存后会吊销该角色下全部用户的会话，需重新登录才能拿到新权限。
        已选 {{ selectedIds.length }} 项。
      </div>
      <el-tabs v-model="activeTab" class="permission-tabs">
        <el-tab-pane label="菜单权限" name="MENU">
          <el-checkbox-group v-model="selectedIds" class="permission-grid">
            <el-checkbox v-for="p in menuPermissions" :key="p.id" :value="p.id" class="permission-item">
              <span class="perm-name">{{ p.name }}</span>
              <span class="perm-code">{{ p.code }}</span>
              <span class="perm-path">{{ p.routePath }}</span>
            </el-checkbox>
          </el-checkbox-group>
        </el-tab-pane>
        <el-tab-pane label="接口权限" name="API">
          <el-checkbox-group v-model="selectedIds" class="permission-grid">
            <el-checkbox v-for="p in apiPermissions" :key="p.id" :value="p.id" class="permission-item">
              <span class="perm-name">{{ p.name }}</span>
              <span class="perm-code">{{ p.code }}</span>
              <span class="perm-path">{{ p.httpMethod }} {{ p.apiPath }}</span>
            </el-checkbox>
          </el-checkbox-group>
        </el-tab-pane>
      </el-tabs>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="savePermissions">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { formatApiError } from '../api/http';
import {
  assignRolePermissions,
  getRolePermissionIds,
  listPermissions,
  listRoles
} from '../api/admin';

const loading = ref(false);
const saving = ref(false);
const rows = ref([]);
const permissions = ref([]);
const current = ref(null);
const dialogVisible = ref(false);
const activeTab = ref('MENU');
const selectedIds = ref([]);

const menuPermissions = computed(() => permissions.value.filter((p) => p.type === 'MENU'));
const apiPermissions = computed(() => permissions.value.filter((p) => p.type === 'API'));

async function load() {
  loading.value = true;
  try {
    rows.value = await listRoles();
  } catch (e) {
    ElMessage.error(formatApiError(e));
  } finally {
    loading.value = false;
  }
}

async function openPermissions(role) {
  current.value = role;
  activeTab.value = 'MENU';
  try {
    selectedIds.value = await getRolePermissionIds(role.id);
    dialogVisible.value = true;
  } catch (e) {
    ElMessage.error(formatApiError(e));
  }
}

async function savePermissions() {
  saving.value = true;
  try {
    await assignRolePermissions(current.value.id, selectedIds.value);
    ElMessage.success('权限已更新，受影响用户需重新登录');
    dialogVisible.value = false;
  } catch (e) {
    ElMessage.error(formatApiError(e));
  } finally {
    saving.value = false;
  }
}

onMounted(async () => {
  load();
  try {
    permissions.value = await listPermissions();
  } catch (e) {
    ElMessage.error(formatApiError(e));
  }
});
</script>

<style scoped>
.roles-page { max-width: 1100px; }
.page-header { display: flex; align-items: flex-start; justify-content: space-between; margin-bottom: 16px; }
.page-title { margin: 0; font-size: 20px; font-weight: 600; color: #1f2d3d; }
.page-subtitle { margin: 4px 0 0; font-size: 13px; color: #8a97a6; }
.header-actions { display: flex; gap: 8px; }
.content-card { border: 1px solid #e9edf3; border-radius: 8px; }
.dialog-hint { margin-bottom: 12px; padding: 8px 12px; background: #fdf6ec; border-radius: 4px; font-size: 12px; color: #e6a23c; }
.permission-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px 16px; max-height: 440px; overflow-y: auto; }
.permission-item { display: flex !important; align-items: center; gap: 6px; margin-right: 0 !important; }
.perm-name { font-size: 13px; color: #303133; }
.perm-code { font-size: 11px; color: #909399; }
.perm-path { font-size: 11px; color: #c0c4cc; }
</style>
