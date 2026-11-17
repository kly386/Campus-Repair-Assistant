<template>
  <div class="knowledge-review">
    <h2>知识库审核</h2>

    <el-card>
      <div class="toolbar">
        <el-tabs v-model="activeStatus" @tab-change="load">
          <el-tab-pane label="待审核" name="PENDING" />
          <el-tab-pane label="已通过" name="APPROVED" />
          <el-tab-pane label="已驳回" name="REJECTED" />
          <el-tab-pane label="已下线" name="OFFLINE" />
          <el-tab-pane label="全部" name="" />
        </el-tabs>
        <div class="toolbar-right">
          <el-input v-model="keyword" placeholder="本地过滤：标题/关键词/问题" clearable style="width: 240px" />
          <el-button type="primary" @click="openCreate">新增知识</el-button>
          <el-button :loading="loading" @click="load">刷新</el-button>
        </div>
      </div>

      <el-table :data="filteredList" size="small" v-loading="loading">
        <el-table-column prop="id" label="ID" width="60" />
        <el-table-column prop="title" label="标题" width="150" show-overflow-tooltip />
        <el-table-column prop="keyword" label="关键词" width="120" show-overflow-tooltip />
        <el-table-column prop="question" label="问题" min-width="160" show-overflow-tooltip />
        <el-table-column prop="answer" label="答案" min-width="180" show-overflow-tooltip />
        <el-table-column label="类别" width="100">
          <template #default="{ row }">{{ categoryName(row.categoryId) }}</template>
        </el-table-column>
        <el-table-column prop="source" label="来源" width="80" />
        <el-table-column prop="helpful" label="有用" width="60" />
        <el-table-column label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="statusTag(row.reviewStatus)" size="small">{{ STATUS_TEXT[row.reviewStatus] || row.reviewStatus }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="gmtModified" label="更新时间" width="160" />
        <el-table-column label="操作" width="250" fixed="right">
          <template #default="{ row }">
            <template v-if="row.reviewStatus === 'PENDING'">
              <el-button type="success" size="small" @click="doReview(row, 'APPROVED')">通过</el-button>
              <el-button type="danger" size="small" plain @click="doReview(row, 'REJECTED')">驳回</el-button>
            </template>
            <el-button v-if="row.reviewStatus === 'APPROVED'" type="warning" size="small" plain @click="doReview(row, 'OFFLINE')">下线</el-button>
            <el-button v-if="row.reviewStatus !== 'APPROVED'" type="success" size="small" plain @click="doReview(row, 'APPROVED')">上线</el-button>
            <el-button size="small" @click="openEdit(row)">编辑</el-button>
            <el-button size="small" type="info" plain @click="openHistory(row)">历史</el-button>
            <el-button size="small" type="danger" link @click="doDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 新增/编辑 -->
    <el-dialog v-model="editVisible" :title="editForm.id ? '编辑知识（自动写历史）' : '新增知识（默认待审核）'" width="560px">
      <el-form :model="editForm" label-width="70px">
        <el-form-item label="标题"><el-input v-model="editForm.title" /></el-form-item>
        <el-form-item label="关键词"><el-input v-model="editForm.keyword" placeholder="用于检索去重" /></el-form-item>
        <el-form-item label="问题"><el-input v-model="editForm.question" /></el-form-item>
        <el-form-item label="答案"><el-input v-model="editForm.answer" type="textarea" :rows="4" /></el-form-item>
        <el-form-item label="类别">
          <el-select v-model="editForm.categoryId" clearable style="width: 200px">
            <el-option v-for="c in CATEGORIES" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <!-- 历史与回滚 -->
    <el-dialog v-model="historyVisible" :title="`修订历史：${historyRow?.title || ''}`" width="640px">
      <el-table :data="historyList" size="small" v-loading="historyLoading">
        <el-table-column prop="id" label="历史ID" width="70" />
        <el-table-column prop="reviewStatus" label="当时状态" width="90" />
        <el-table-column prop="question" label="问题" min-width="140" show-overflow-tooltip />
        <el-table-column prop="answer" label="答案" min-width="180" show-overflow-tooltip />
        <el-table-column prop="operatorId" label="操作人" width="80" />
        <el-table-column prop="gmtCreate" label="时间" width="160" />
        <el-table-column label="操作" width="90">
          <template #default="{ row }">
            <el-button type="primary" size="small" plain @click="doRollback(row)">回滚到此</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import {
  createKnowledge, deleteKnowledge, listKnowledge, listKnowledgeHistory,
  reviewKnowledge, rollbackKnowledge, updateKnowledge
} from '../api/knowledge';
import { formatApiError } from '../api/http';

const STATUS_TEXT = { PENDING: '待审核', APPROVED: '已通过', REJECTED: '已驳回', OFFLINE: '已下线' };
const STATUS_TAG = { PENDING: 'warning', APPROVED: 'success', REJECTED: 'danger', OFFLINE: 'info' };
const CATEGORIES = [
  { id: 1, name: '水电维修' },
  { id: 2, name: '网络故障' },
  { id: 3, name: '门窗家具' },
  { id: 4, name: '报修咨询' }
];

const activeStatus = ref('PENDING');
const keyword = ref('');
const loading = ref(false);
const list = ref([]);

const editVisible = ref(false);
const saving = ref(false);
const editForm = ref({});

const historyVisible = ref(false);
const historyLoading = ref(false);
const historyRow = ref(null);
const historyList = ref([]);

const filteredList = computed(() => {
  const kw = keyword.value.trim().toLowerCase();
  if (!kw) {return list.value;}
  return list.value.filter((it) =>
    [it.title, it.keyword, it.question, it.answer].some((f) => (f || '').toLowerCase().includes(kw)));
});

function categoryName(id) {
  const c = CATEGORIES.find((x) => x.id === id);
  return c ? c.name : id || '-';
}

function statusTag(s) {
  return STATUS_TAG[s] || 'info';
}

async function load() {
  loading.value = true;
  try {
    const resp = await listKnowledge({ status: activeStatus.value || undefined });
    const data = resp?.data || {};
    list.value = data.records || data.list || data || [];
    if (!Array.isArray(list.value)) {list.value = [];}
  } catch (e) {
    list.value = [];
    ElMessage.error('知识列表加载失败：' + formatApiError(e));
  } finally {
    loading.value = false;
  }
}

function openCreate() {
  editForm.value = { title: '', keyword: '', question: '', answer: '', categoryId: null };
  editVisible.value = true;
}

function openEdit(row) {
  editForm.value = {
    id: row.id, title: row.title || '', keyword: row.keyword || '',
    question: row.question || '', answer: row.answer || '', categoryId: row.categoryId ?? null
  };
  editVisible.value = true;
}

async function save() {
  const f = editForm.value;
  if (!f.keyword || !f.question || !f.answer) {
    ElMessage.warning('关键词、问题、答案为必填项');
    return;
  }
  saving.value = true;
  try {
    const payload = {
      title: f.title, keyword: f.keyword, question: f.question,
      answer: f.answer, categoryId: f.categoryId, source: 'MANUAL'
    };
    if (f.id) {
      await updateKnowledge(f.id, payload);
      ElMessage.success('已保存（修改历史已自动记录）');
    } else {
      await createKnowledge(payload);
      ElMessage.success('已新增，状态：待审核');
    }
    editVisible.value = false;
    await load();
  } catch (e) {
    ElMessage.error('保存失败：' + formatApiError(e));
  } finally {
    saving.value = false;
  }
}

async function doReview(row, status) {
  const text = STATUS_TEXT[status];
  try {
    await ElMessageBox.confirm(`确定将「${row.title || row.question}」置为 ${text} 吗？`, '审核确认', { type: 'warning' });
  } catch {
    return;
  }
  try {
    await reviewKnowledge(row.id, status);
    ElMessage.success(`已${text}`);
    await load();
  } catch (e) {
    ElMessage.error('审核失败：' + formatApiError(e));
  }
}

async function doDelete(row) {
  try {
    await ElMessageBox.confirm(`确定软删除「${row.title || row.question}」吗？`, '删除确认', { type: 'warning' });
  } catch {
    return;
  }
  try {
    await deleteKnowledge(row.id);
    ElMessage.success('已删除');
    await load();
  } catch (e) {
    ElMessage.error('删除失败：' + formatApiError(e));
  }
}

async function openHistory(row) {
  historyRow.value = row;
  historyVisible.value = true;
  historyLoading.value = true;
  try {
    const resp = await listKnowledgeHistory(row.id);
    const data = resp?.data || {};
    historyList.value = data.records || data.list || data || [];
    if (!Array.isArray(historyList.value)) {historyList.value = [];}
  } catch (e) {
    historyList.value = [];
    ElMessage.error('历史加载失败：' + formatApiError(e));
  } finally {
    historyLoading.value = false;
  }
}

async function doRollback(history) {
  try {
    await ElMessageBox.confirm(`确定回滚到历史 #${history.id} 吗？`, '回滚确认', { type: 'warning' });
  } catch {
    return;
  }
  try {
    await rollbackKnowledge(history.id);
    ElMessage.success('已回滚');
    historyVisible.value = false;
    await load();
  } catch (e) {
    ElMessage.error('回滚失败：' + formatApiError(e));
  }
}

onMounted(load);
</script>

<style scoped>
.knowledge-review h2 {
  margin: 0 0 16px;
}
.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
}
.toolbar-right {
  display: flex;
  gap: 8px;
  padding-top: 4px;
}
</style>
