<template>
  <div class="workbench">
    <h2>接单工作台</h2>

    <!-- 概览卡片 -->
    <el-row :gutter="12" class="stats">
      <el-col :span="6"><el-card shadow="never"><el-statistic title="待接单" :value="countBy('ASSIGNED')" /></el-card></el-col>
      <el-col :span="6"><el-card shadow="never"><el-statistic title="待上门" :value="countBy('PENDING_ACCEPT')" /></el-card></el-col>
      <el-col :span="6"><el-card shadow="never"><el-statistic title="维修中" :value="countBy('ON_THE_WAY') + countBy('PROCESSING')" /></el-card></el-col>
      <el-col :span="6"><el-card shadow="never"><el-statistic title="累计完成" :value="countBy('COMPLETED')" /></el-card></el-col>
    </el-row>

    <!-- 我的工单 -->
    <el-card shadow="never" style="margin-top: 16px">
      <template #header><span>我的工单</span></template>
      <el-table :data="orders" v-loading="loading" stripe>
        <el-table-column prop="orderNo" label="工单号" width="150" />
        <el-table-column prop="title" label="标题" min-width="130" show-overflow-tooltip />
        <el-table-column prop="building" label="楼栋" width="110" />
        <el-table-column prop="room" label="房间" width="80" />
        <el-table-column label="紧急" width="80">
          <template #default="{ row }">
            <el-tag :type="row.emergencyLevel === 'HIGH' ? 'danger' : row.emergencyLevel === 'MEDIUM' ? 'warning' : 'info'" size="small">
              {{ emergencyLabel(row.emergencyLevel) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="预约时间" width="160">
          <template #default="{ row }">{{ fmt(row.appointmentTime) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag size="small">{{ statusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button v-if="row.status === 'ASSIGNED'" type="primary" size="small"
              :loading="actingId === row.id" @click="onAccept(row)">接单</el-button>
            <el-button v-if="row.status === 'PENDING_ACCEPT'" type="warning" size="small"
              :loading="actingId === row.id" @click="onDepart(row)">出发上门</el-button>
            <el-button v-if="row.status === 'ON_THE_WAY'" type="success" size="small"
              :loading="actingId === row.id" @click="onStart(row)">开始维修</el-button>
            <el-button v-if="row.status === 'PROCESSING'" type="success" size="small"
              @click="openComplete(row)">完成维修</el-button>
            <span v-if="!hasAction(row.status)" style="color:#999;font-size:13px">—</span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 我的排班（本周） -->
    <el-card shadow="never" style="margin-top: 16px">
      <template #header><span>我的排班（未来 7 天）</span></template>
      <el-table :data="schedule" v-loading="loading" stripe>
        <el-table-column label="日期" width="130">
          <template #default="{ row }">{{ row.workDate }}</template>
        </el-table-column>
        <el-table-column label="时段" width="120">
          <template #default="{ row }">{{ slotLabel(row.timeSlot) }}</template>
        </el-table-column>
        <el-table-column prop="area" label="负责区域" />
        <el-table-column label="负载" width="160">
          <template #default="{ row }">{{ row.curOrders }} / {{ row.maxOrders }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag size="small" :type="row.status === 'FREE' ? 'success' : row.status === 'BUSY' ? 'warning' : 'info'">
              {{ row.status === 'FREE' ? '空闲' : row.status === 'BUSY' ? '繁忙' : '休息' }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 完成维修弹窗 -->
    <el-dialog v-model="completeVisible" title="完成维修" width="420px">
      <el-input v-model="completeResult" type="textarea" :rows="3" placeholder="填写维修结果说明（如：更换热水器温控器，已恢复正常）" />
      <template #footer>
        <el-button @click="completeVisible = false">取消</el-button>
        <el-button type="primary" :loading="acting" @click="doComplete">确认完成</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { ElMessage } from 'element-plus';
import { fetchMyOrders, acceptOrder, departOrder, startRepair, completeOrder, fetchMySchedule } from '../api/worker';
import { formatApiError } from '../api/http';

const orders = ref([]);
const schedule = ref([]);
const loading = ref(false);
const actingId = ref(null);
const acting = ref(false);
const completeVisible = ref(false);
const completeResult = ref('');
const completeTarget = ref(null);

const EMERGENCY = { HIGH: '紧急', MEDIUM: '较急', LOW: '一般' };
const STATUS = {
  ASSIGNED: '已派单', PENDING_ACCEPT: '待接单', ON_THE_WAY: '上门中',
  PROCESSING: '维修中', COMPLETED: '已完成', CANCELLED: '已取消'
};
const SLOTS = { MORNING: '上午', AFTERNOON: '下午', EVENING: '晚间' };
const emergencyLabel = (v) => EMERGENCY[v] || v;
const statusLabel = (v) => STATUS[v] || v;
const slotLabel = (v) => SLOTS[v] || v;
const fmt = (t) => (t ? String(t).replace('T', ' ').slice(0, 16) : '-');
const countBy = (s) => orders.value.filter((o) => o.status === s).length;
const hasAction = (s) => ['ASSIGNED', 'PENDING_ACCEPT', 'ON_THE_WAY', 'PROCESSING'].includes(s);

async function load() {
  loading.value = true;
  try {
    const [o, s] = await Promise.all([fetchMyOrders(), fetchMySchedule()]);
    orders.value = Array.isArray(o) ? o : [];
    schedule.value = Array.isArray(s) ? s : [];
  } catch (e) {
    ElMessage.error(formatApiError(e));
  } finally {
    loading.value = false;
  }
}

async function withLoading(row, fn, successMsg) {
  actingId.value = row.id;
  try {
    await fn(row.id);
    ElMessage.success(successMsg);
    await load();
  } catch (e) {
    ElMessage.error(formatApiError(e));
  } finally {
    actingId.value = null;
  }
}

const onAccept = (row) => withLoading(row, acceptOrder, '接单成功');
const onDepart = (row) => withLoading(row, departOrder, '已标记出发上门');
const onStart = (row) => withLoading(row, startRepair, '已开始维修');

function openComplete(row) {
  completeTarget.value = row;
  completeResult.value = '';
  completeVisible.value = true;
}

async function doComplete() {
  if (!completeResult.value.trim()) {
    ElMessage.warning('请填写维修结果说明');
    return;
  }
  acting.value = true;
  try {
    await completeOrder(completeTarget.value.id, completeResult.value.trim());
    ElMessage.success('维修完成，等待学生评价');
    completeVisible.value = false;
    await load();
  } catch (e) {
    ElMessage.error(formatApiError(e));
  } finally {
    acting.value = false;
  }
}

onMounted(load);
</script>

<style scoped>
.workbench h2 {
  margin-bottom: 20px;
}
</style>
