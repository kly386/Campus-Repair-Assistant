<template>
  <div class="dispatch">
    <div class="head">
      <h2>调度工作台</h2>
      <el-button v-permission="'dispatch:auto'" type="primary" :loading="batching" @click="onBatchAssign">
        一键全部自动派单
      </el-button>
    </div>

    <el-alert v-if="lastBatchSummary" :type="lastBatchSummary.fail === 0 ? 'success' : 'warning'" :closable="false"
      :title="`本轮批量派单：成功 ${lastBatchSummary.ok} 单，失败 ${lastBatchSummary.fail} 单`" style="margin-bottom: 12px" />

    <!-- 待派单池 -->
    <el-card shadow="never">
      <template #header>
        <span>待派单池（SUBMITTED · {{ pending.length }} 单，紧急优先展示）</span>
      </template>
      <el-table :data="pending" v-loading="loading" stripe>
        <el-table-column prop="orderNo" label="工单号" width="150" />
        <el-table-column prop="title" label="标题" min-width="140" show-overflow-tooltip />
        <el-table-column label="紧急" width="80">
          <template #default="{ row }">
            <el-tag :type="row.emergencyLevel === 'HIGH' ? 'danger' : row.emergencyLevel === 'MEDIUM' ? 'warning' : 'info'" size="small">
              {{ emergencyLabel(row.emergencyLevel) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="building" label="楼栋" width="110" />
        <el-table-column prop="room" label="房间" width="80" />
        <el-table-column label="预约时间" width="160">
          <template #default="{ row }">{{ fmt(row.appointmentTime) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="'dispatch:auto'" type="primary" size="small" :loading="assigningId === row.id"
              @click="onAutoAssign(row)">自动派单</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 已派单（可改约/看审计） -->
    <el-card shadow="never" style="margin-top: 16px">
      <template #header><span>已派工单（ASSIGNED）</span></template>
      <el-table :data="assigned" v-loading="loading" stripe>
        <el-table-column prop="orderNo" label="工单号" width="150" />
        <el-table-column prop="title" label="标题" min-width="130" show-overflow-tooltip />
        <el-table-column label="师傅" width="90">
          <template #default="{ row }">#{{ row.workerId }}</template>
        </el-table-column>
        <el-table-column label="评分" width="90">
          <template #default="{ row }">{{ row.assignScore }}</template>
        </el-table-column>
        <el-table-column label="预约时间" width="160">
          <template #default="{ row }">{{ fmt(row.appointmentTime) }}</template>
        </el-table-column>
        <el-table-column label="派单理由" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">{{ row.assignReason }}</template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button v-permission="'dispatch:reschedule'" size="small" @click="openReschedule(row)">改约</el-button>
            <el-button v-permission="'dispatch:read'" size="small" @click="openAudit(row)">审计</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 改约弹窗 -->
    <el-dialog v-model="rescheduleVisible" title="改约（自动做师傅时段冲突检测）" width="420px">
      <el-form label-width="90px">
        <el-form-item label="工单号">
          <span>{{ current?.orderNo }}</span>
        </el-form-item>
        <el-form-item label="新预约时间">
          <el-date-picker v-model="newTime" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss"
            placeholder="选择新的预约时间" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rescheduleVisible = false">取消</el-button>
        <el-button type="primary" :loading="rescheduling" @click="doReschedule">确认改约</el-button>
      </template>
    </el-dialog>

    <!-- 派单审计抽屉 -->
    <el-drawer v-model="auditVisible" title="派单决策审计" size="480px">
      <el-timeline v-if="auditRecords.length">
        <el-timeline-item v-for="a in auditRecords" :key="a.id"
          :type="a.isSuccess ? 'success' : 'danger'" :timestamp="fmt(a.gmtCreate)">
          <p>
            <b>师傅 #{{ a.workerId }}</b>
            <el-tag size="small" :type="a.isSuccess ? 'success' : 'danger'" style="margin-left: 8px">
              {{ a.isSuccess ? '中选' : '竞争落败' }}
            </el-tag>
            <el-tag size="small" style="margin-left: 8px">得分 {{ a.assignScore }}</el-tag>
          </p>
          <p style="color: #666; font-size: 13px">{{ a.assignReason }}</p>
        </el-timeline-item>
      </el-timeline>
      <el-empty v-else description="无派单记录" />
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { ElMessage } from 'element-plus';
import { fetchOrdersByStatus, autoAssign, batchAutoAssign, reschedule, fetchAssignments } from '../api/dispatch';
import { formatApiError } from '../api/http';

const pending = ref([]);
const assigned = ref([]);
const loading = ref(false);
const batching = ref(false);
const assigningId = ref(null);
const lastBatchSummary = ref(null);
const rescheduleVisible = ref(false);
const rescheduling = ref(false);
const auditVisible = ref(false);
const auditRecords = ref([]);
const current = ref(null);
const newTime = ref('');

const EMERGENCY = { HIGH: '紧急', MEDIUM: '较急', LOW: '一般' };
const emergencyLabel = (v) => EMERGENCY[v] || v;
const fmt = (t) => (t ? String(t).replace('T', ' ').slice(0, 16) : '-');

async function load() {
  loading.value = true;
  try {
    const [p, a] = await Promise.all([
      fetchOrdersByStatus('SUBMITTED'),
      fetchOrdersByStatus('ASSIGNED')
    ]);
    pending.value = Array.isArray(p) ? p : [];
    assigned.value = Array.isArray(a) ? a : [];
  } catch (e) {
    ElMessage.error(formatApiError(e));
  } finally {
    loading.value = false;
  }
}

async function onAutoAssign(row) {
  assigningId.value = row.id;
  try {
    const a = await autoAssign(row.id);
    ElMessage.success(`已派给师傅 #${a.workerId}（得分 ${a.assignScore}）`);
    await load();
  } catch (e) {
    ElMessage.error(formatApiError(e));
  } finally {
    assigningId.value = null;
  }
}

async function onBatchAssign() {
  batching.value = true;
  try {
    const results = await batchAutoAssign();
    const ok = results.filter((r) => r.success).length;
    lastBatchSummary.value = { ok, fail: results.length - ok };
    ElMessage.success(`批量派单完成：成功 ${ok}，失败 ${results.length - ok}`);
    await load();
  } catch (e) {
    ElMessage.error(formatApiError(e));
  } finally {
    batching.value = false;
  }
}

function openReschedule(row) {
  current.value = row;
  newTime.value = row.appointmentTime || '';
  rescheduleVisible.value = true;
}

async function doReschedule() {
  if (!newTime.value) {
    ElMessage.warning('请选择新的预约时间');
    return;
  }
  rescheduling.value = true;
  try {
    await reschedule(current.value.id, newTime.value);
    ElMessage.success('改约成功（已通过冲突检测）');
    rescheduleVisible.value = false;
    await load();
  } catch (e) {
    // 冲突时后端返回 400 业务错误，展示具体冲突原因
    ElMessage.error(formatApiError(e));
  } finally {
    rescheduling.value = false;
  }
}

async function openAudit(row) {
  current.value = row;
  auditVisible.value = true;
  auditRecords.value = [];
  try {
    const records = await fetchAssignments(row.id);
    auditRecords.value = Array.isArray(records) ? records : [];
  } catch (e) {
    ElMessage.error(formatApiError(e));
  }
}

onMounted(load);
</script>

<style scoped>
.dispatch h2 {
  margin-bottom: 20px;
}
.head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.head h2 {
  margin-bottom: 0;
}
</style>
