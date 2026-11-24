<template>
  <div class="schedule-page">
    <div class="page-header">
      <div>
        <h2 class="page-title">调度排班</h2>
        <p class="page-subtitle">维护师傅按日期与时段的排班；自动派单只读取这里的空闲记录</p>
      </div>
      <div class="header-actions">
        <el-button :loading="loading" @click="load">刷新</el-button>
        <el-button v-if="canManage" type="primary" @click="openCreate">新增排班</el-button>
      </div>
    </div>

    <el-card shadow="never" class="content-card">
      <div class="toolbar">
        <el-select v-model="filters.workerId" placeholder="全部师傅" clearable style="width: 180px">
          <el-option v-for="w in workers" :key="w.id" :label="w.name" :value="w.id" />
        </el-select>
        <el-date-picker v-model="dateRange" type="daterange" value-format="YYYY-MM-DD"
                        start-placeholder="开始日期" end-placeholder="结束日期" style="width: 260px" />
        <el-select v-model="filters.status" placeholder="全部状态" clearable style="width: 150px">
          <el-option label="可接单 FREE" value="FREE" />
          <el-option label="忙碌 BUSY" value="BUSY" />
          <el-option label="休息 REST" value="REST" />
        </el-select>
        <el-button type="primary" @click="load">查询</el-button>
        <el-button @click="resetFilters">重置</el-button>
      </div>

      <el-table :data="rows" v-loading="loading" size="small" class="schedule-table">
        <el-table-column label="师傅姓名" min-width="110">
          <template #default="{ row }">{{ row.workerName || ('师傅#' + row.workerId) }}</template>
        </el-table-column>
        <el-table-column prop="specialties" label="擅长类型" min-width="140" show-overflow-tooltip />
        <el-table-column prop="workDate" label="排班日期" width="120" />
        <el-table-column label="时间段" width="100" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="slotTag(row.timeSlot)">{{ slotText(row.timeSlot) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="area" label="负责区域" min-width="130" show-overflow-tooltip />
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag size="small" :type="statusTag(row.status)">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="负载" width="100" align="center">
          <template #default="{ row }">{{ row.curOrders ?? 0 }} / {{ row.maxOrders }}</template>
        </el-table-column>
        <el-table-column v-if="canManage" label="操作" width="130" fixed="right" align="center">
          <template #default="{ row }">
            <el-button type="primary" link @click="openEdit(row)">编辑</el-button>
            <el-button type="danger" link @click="remove(row)">删除</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无排班，点右上角新增" :image-size="72" />
        </template>
      </el-table>
    </el-card>

    <el-dialog v-model="dialogVisible" :title="editing ? '编辑排班' : '新增排班'" width="540px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="师傅" prop="workerId">
          <el-select v-model="form.workerId" placeholder="请选择师傅" style="width: 100%">
            <el-option v-for="w in workers" :key="w.id" :label="w.name + (w.specialties ? '（' + w.specialties + '）' : '')"
                       :value="w.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="排班日期" prop="workDate">
          <el-date-picker v-model="form.workDate" type="date" value-format="YYYY-MM-DD"
                          placeholder="选择日期" style="width: 100%" />
        </el-form-item>
        <el-form-item label="时间段" prop="timeSlot">
          <el-select v-model="form.timeSlot" placeholder="请选择" style="width: 100%">
            <el-option label="上午 MORNING" value="MORNING" />
            <el-option label="下午 AFTERNOON" value="AFTERNOON" />
            <el-option label="晚上 EVENING" value="EVENING" />
          </el-select>
        </el-form-item>
        <el-form-item label="负责区域">
          <el-input v-model="form.area" placeholder="例如：紫荆1栋,紫荆2栋（留空表示全区）" maxlength="100" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio value="FREE">可接单</el-radio>
            <el-radio value="BUSY">忙碌</el-radio>
            <el-radio value="REST">休息</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="最大接单数" prop="maxOrders">
          <el-input-number v-model="form.maxOrders" :min="1" :max="50" />
        </el-form-item>
        <el-form-item label="当前接单数">
          <el-input-number v-model="form.curOrders" :min="0" :max="form.maxOrders || 50" />
          <span class="field-tip">已达到上限时派单会自动跳过该时段</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { hasPermission } from '../auth/session';
import { formatApiError } from '../api/http';
import {
  createSchedule,
  deleteSchedule,
  listScheduleWorkers,
  listSchedules,
  updateSchedule
} from '../api/schedule';

const canManage = computed(() => hasPermission('schedule:create'));

const loading = ref(false);
const saving = ref(false);
const rows = ref([]);
const workers = ref([]);
const filters = reactive({ workerId: null, status: null });
const dateRange = ref(null);

const dialogVisible = ref(false);
const editing = ref(false);
const editingId = ref(null);
const formRef = ref(null);
const form = reactive(emptyForm());

function emptyForm() {
  return {
    workerId: null,
    workDate: null,
    timeSlot: 'MORNING',
    area: '',
    status: 'FREE',
    maxOrders: 3,
    curOrders: 0
  };
}

const rules = {
  workerId: [{ required: true, message: '请选择师傅', trigger: 'change' }],
  workDate: [{ required: true, message: '请选择排班日期', trigger: 'change' }],
  timeSlot: [{ required: true, message: '请选择时间段', trigger: 'change' }],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }]
};

const SLOT_TEXT = { MORNING: '上午', AFTERNOON: '下午', EVENING: '晚上' };
const STATUS_TEXT = { FREE: '可接单', BUSY: '忙碌', REST: '休息' };
const slotText = (v) => SLOT_TEXT[v] || v || '-';
const statusText = (v) => STATUS_TEXT[v] || v || '-';
const slotTag = (v) => ({ MORNING: 'primary', AFTERNOON: 'success', EVENING: 'warning' }[v] || 'info');
const statusTag = (v) => ({ FREE: 'success', BUSY: 'danger', REST: 'info' }[v] || 'info');

async function load() {
  loading.value = true;
  try {
    const params = {};
    if (filters.workerId) params.workerId = filters.workerId;
    if (filters.status) params.status = filters.status;
    if (dateRange.value?.length === 2) {
      params.start = dateRange.value[0];
      params.end = dateRange.value[1];
    }
    rows.value = await listSchedules(params);
  } catch (e) {
    ElMessage.error(formatApiError(e));
  } finally {
    loading.value = false;
  }
}

function resetFilters() {
  filters.workerId = null;
  filters.status = null;
  dateRange.value = null;
  load();
}

async function loadWorkers() {
  try {
    workers.value = await listScheduleWorkers();
  } catch {
    workers.value = [];
  }
}

function openCreate() {
  editing.value = false;
  editingId.value = null;
  Object.assign(form, emptyForm());
  formRef.value?.clearValidate();
  dialogVisible.value = true;
}

function openEdit(row) {
  editing.value = true;
  editingId.value = row.id;
  Object.assign(form, {
    workerId: row.workerId,
    workDate: row.workDate,
    timeSlot: row.timeSlot,
    area: row.area || '',
    status: row.status,
    maxOrders: row.maxOrders,
    curOrders: row.curOrders ?? 0
  });
  formRef.value?.clearValidate();
  dialogVisible.value = true;
}

async function save() {
  const valid = await formRef.value?.validate().catch(() => false);
  if (!valid) return;
  saving.value = true;
  try {
    const payload = { ...form };
    if (editing.value) {
      await updateSchedule(editingId.value, payload);
      ElMessage.success('排班已更新');
    } else {
      await createSchedule(payload);
      ElMessage.success('排班已新增');
    }
    dialogVisible.value = false;
    await load();
  } catch (e) {
    ElMessage.error(formatApiError(e));
  } finally {
    saving.value = false;
  }
}

async function remove(row) {
  try {
    await ElMessageBox.confirm(
      `确认删除 ${row.workerName} 在 ${row.workDate} 的排班吗？`,
      '删除排班',
      { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
    );
  } catch {
    return;
  }
  try {
    await deleteSchedule(row.id);
    ElMessage.success('排班已删除');
    await load();
  } catch (e) {
    ElMessage.error(formatApiError(e));
  }
}

onMounted(() => {
  loadWorkers();
  load();
});
</script>

<style scoped>
.schedule-page { max-width: 1200px; }
.page-header { display: flex; align-items: flex-start; justify-content: space-between; margin-bottom: 16px; }
.page-title { margin: 0; font-size: 20px; font-weight: 600; color: #1f2d3d; }
.page-subtitle { margin: 4px 0 0; font-size: 13px; color: #8a97a6; }
.header-actions { display: flex; gap: 8px; }
.content-card { border: 1px solid #e9edf3; border-radius: 8px; }
.toolbar { display: flex; flex-wrap: wrap; gap: 10px; align-items: center; margin-bottom: 16px; }
.field-tip { margin-left: 12px; font-size: 12px; color: #909399; }
</style>
