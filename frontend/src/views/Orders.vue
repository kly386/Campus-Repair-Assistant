<template>
  <div class="orders-page">
    <div class="page-header">
      <div>
        <h2 class="page-title">工单管理</h2>
        <p class="page-subtitle">查看报修进度、联系方式与处理结果</p>
      </div>
      <div class="header-actions">
        <el-button :loading="loading" @click="load">刷新</el-button>
        <el-button v-if="canCreate" type="primary" @click="$router.push('/repair')">新建报修</el-button>
      </div>
    </div>

    <el-card shadow="never" class="content-card">
      <div class="toolbar">
        <el-tabs v-model="activeStatus" class="status-tabs" @tab-change="load">
          <el-tab-pane label="全部" name="ALL" />
          <el-tab-pane label="待派单" name="SUBMITTED" />
          <el-tab-pane label="已派单" name="ASSIGNED" />
          <el-tab-pane label="待接单" name="PENDING_ACCEPT" />
          <el-tab-pane label="上门中" name="ON_THE_WAY" />
          <el-tab-pane label="维修中" name="PROCESSING" />
          <el-tab-pane label="待验收" name="PENDING_REVIEW" />
          <el-tab-pane label="已完成" name="COMPLETED" />
          <el-tab-pane label="已取消" name="CANCELLED" />
        </el-tabs>
        <el-input v-model="keyword" class="search-input" placeholder="搜索工单号/标题/楼栋/房间/电话" clearable>
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
      </div>

      <el-table :data="filteredOrders" v-loading="loading" size="small" class="orders-table">
        <el-table-column prop="orderNo" label="工单号" min-width="160" show-overflow-tooltip />
        <el-table-column prop="title" label="故障标题" min-width="140" show-overflow-tooltip />
        <el-table-column label="故障类型" width="110">
          <template #default="{ row }">{{ categoryName(row.categoryId) }}</template>
        </el-table-column>
        <el-table-column label="位置" min-width="120">
          <template #default="{ row }">{{ locationText(row) }}</template>
        </el-table-column>
        <el-table-column label="紧急度" width="88" align="center">
          <template #default="{ row }">
            <el-tag :type="emergencyTag(row.emergencyLevel)" size="small">{{ emergencyText(row.emergencyLevel) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.status)" size="small">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="contactPhone" label="联系电话" width="120" show-overflow-tooltip />
        <el-table-column label="预约时间" width="160">
          <template #default="{ row }">{{ fmtTime(row.appointmentTime) }}</template>
        </el-table-column>
        <el-table-column label="提交时间" width="160">
          <template #default="{ row }">{{ fmtTime(row.gmtCreate) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right" align="center">
          <template #default="{ row }">
            <el-button type="primary" link @click="openDetail(row)">详情</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无工单" :image-size="72" />
        </template>
      </el-table>
    </el-card>

    <el-drawer v-model="detailVisible" title="工单详情" size="520px">
      <el-descriptions v-if="current" :column="1" border size="small">
        <el-descriptions-item label="工单号">{{ current.orderNo || '-' }}</el-descriptions-item>
        <el-descriptions-item label="标题">{{ current.title || '-' }}</el-descriptions-item>
        <el-descriptions-item label="故障类型">{{ categoryName(current.categoryId) }}</el-descriptions-item>
        <el-descriptions-item label="故障描述">{{ current.description || '-' }}</el-descriptions-item>
        <el-descriptions-item label="位置">{{ locationText(current) }}</el-descriptions-item>
        <el-descriptions-item label="联系电话">{{ current.contactPhone || '-' }}</el-descriptions-item>
        <el-descriptions-item label="备用电话">{{ current.backupPhone || '-' }}</el-descriptions-item>
        <el-descriptions-item label="紧急度">{{ emergencyText(current.emergencyLevel) }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ statusText(current.status) }}</el-descriptions-item>
        <el-descriptions-item label="预约时间">{{ fmtTime(current.appointmentTime) }}</el-descriptions-item>
        <el-descriptions-item label="提交时间">{{ fmtTime(current.gmtCreate) }}</el-descriptions-item>
        <el-descriptions-item label="完成时间">{{ fmtTime(current.finishTime) }}</el-descriptions-item>
        <el-descriptions-item label="师傅ID">{{ current.workerId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="维修结果">{{ current.result || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-drawer>
  </div>
</template>
<script setup>
import { computed, onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { Search } from '@element-plus/icons-vue';
import { listOrdersByStatus, listOrdersByStudent } from '../api/orders';
import { authState, hasPermission } from '../auth/session';
import { formatApiError } from '../api/http';

const STATUS_TEXT = {
  SUBMITTED: '待派单', ASSIGNED: '已派单', PENDING_ACCEPT: '待接单', ON_THE_WAY: '上门中',
  PROCESSING: '维修中', PENDING_REVIEW: '待验收', COMPLETED: '已完成', CANCELLED: '已取消', RESCHEDULED: '已改约'
};
const STATUS_TAG = {
  SUBMITTED: 'warning', ASSIGNED: 'primary', PENDING_ACCEPT: 'warning', ON_THE_WAY: 'primary',
  PROCESSING: 'primary', PENDING_REVIEW: 'warning', COMPLETED: 'success', CANCELLED: 'info', RESCHEDULED: 'info'
};
const EMERGENCY_TEXT = { LOW: '一般', MEDIUM: '较急', HIGH: '紧急' };
const EMERGENCY_TAG = { LOW: 'info', MEDIUM: 'warning', HIGH: 'danger' };
const CATEGORIES = { 1: '水电维修', 2: '网络故障', 3: '门窗家具', 4: '报修咨询' };

const loading = ref(false);
const orders = ref([]);
const keyword = ref('');
const activeStatus = ref('ALL');
const detailVisible = ref(false);
const current = ref(null);

const role = computed(() => String(authState.user?.role || '').toUpperCase());
const canCreate = computed(() => hasPermission('order:create'));
const isStudent = computed(() => role.value === 'STUDENT');

const filteredOrders = computed(() => {
  const kw = keyword.value.trim().toLowerCase();
  let list = orders.value;
  if (activeStatus.value !== 'ALL') list = list.filter((o) => o.status === activeStatus.value);
  if (!kw) return list;
  return list.filter((o) => [o.orderNo, o.title, o.building, o.room, o.contactPhone, o.backupPhone, o.description]
    .some((v) => String(v || '').toLowerCase().includes(kw)));
});

function categoryName(id) { return CATEGORIES[id] || (id ? `类别#${id}` : '-'); }
function emergencyText(v) { return EMERGENCY_TEXT[String(v || '').toUpperCase()] || v || '-'; }
function emergencyTag(v) { return EMERGENCY_TAG[String(v || '').toUpperCase()] || 'info'; }
function statusText(v) { return STATUS_TEXT[v] || v || '-'; }
function statusTag(v) { return STATUS_TAG[v] || ''; }
function locationText(row) { return [row.building, row.room].filter(Boolean).join(' ') || '-'; }
function fmtTime(t) { return t ? String(t).replace('T', ' ').slice(0, 16) : '-'; }
function openDetail(row) { current.value = row; detailVisible.value = true; }

async function load() {
  loading.value = true;
  try {
    let data = [];
    if (isStudent.value && authState.user?.userId) {
      const resp = await listOrdersByStudent(authState.user.userId);
      data = Array.isArray(resp) ? resp : [];
    } else {
      const statuses = activeStatus.value === 'ALL'
        ? ['SUBMITTED', 'ASSIGNED', 'PENDING_ACCEPT', 'ON_THE_WAY', 'PROCESSING', 'PENDING_REVIEW', 'COMPLETED', 'CANCELLED']
        : [activeStatus.value];
      const results = await Promise.all(statuses.map((s) => listOrdersByStatus(s).catch(() => [])));
      data = results.flatMap((r) => Array.isArray(r) ? r : []);
    }
    const map = new Map();
    for (const o of data) { if (o && o.id != null) map.set(o.id, o); }
    orders.value = Array.from(map.values()).sort((a, b) => {
      const ta = new Date(a.gmtCreate || 0).getTime();
      const tb = new Date(b.gmtCreate || 0).getTime();
      return tb - ta || (Number(b.id) || 0) - (Number(a.id) || 0);
    });
  } catch (e) {
    orders.value = [];
    ElMessage.error('工单加载失败：' + formatApiError(e));
  } finally {
    loading.value = false;
  }
}

onMounted(load);
</script>
<style scoped>
.orders-page { max-width: 1400px; }
.page-header { display: flex; justify-content: space-between; align-items: flex-end; gap: 16px; margin-bottom: 16px; }
.page-title { margin: 0; font-size: 20px; font-weight: 600; color: #1f2d3d; }
.page-subtitle { margin: 4px 0 0; font-size: 13px; color: #8a97a6; }
.header-actions { display: flex; gap: 8px; }
.content-card { border: 1px solid #e9edf3; border-radius: 8px; }
.content-card :deep(.el-card__body) { padding: 16px 20px 20px; }
.toolbar { display: flex; justify-content: space-between; align-items: flex-end; gap: 16px; margin-bottom: 14px; }
.status-tabs { flex: 1; min-width: 0; }
.status-tabs :deep(.el-tabs__header) { margin: 0; }
.status-tabs :deep(.el-tabs__nav-wrap::after) { height: 1px; background: #e9edf3; }
.search-input { width: 280px; flex-shrink: 0; margin-bottom: 6px; }
.orders-table { width: 100%; --el-table-header-bg-color: #fafbfc; }
.orders-table :deep(th.el-table__cell) { color: #5f6b7a; font-weight: 600; background: #fafbfc; }
@media (max-width: 900px) {
  .page-header, .toolbar { flex-direction: column; align-items: stretch; }
  .search-input { width: 100%; margin-bottom: 0; }
}
</style>
