<template>
  <div class="kb-page">
    <div class="page-header">
      <div class="title-block">
        <h2 class="page-title">知识库审核</h2>
        <p class="page-subtitle">维修知识条目审核与维护</p>
      </div>
      <div class="header-actions">
        <el-button class="hdr-btn" :loading="loading" @click="load">刷新</el-button>
        <el-button class="hdr-btn" type="primary" @click="openCreate">新增知识</el-button>
      </div>
    </div>

    <el-card class="content-card" shadow="never">
      <div class="toolbar">
        <el-tabs v-model="activeStatus" class="status-tabs" @tab-change="load">
          <el-tab-pane label="全部" name="" />
          <el-tab-pane label="待审核" name="PENDING" />
          <el-tab-pane label="已通过" name="APPROVED" />
          <el-tab-pane label="已驳回" name="REJECTED" />
          <el-tab-pane label="已下线" name="OFFLINE" />
        </el-tabs>
        <el-input
          v-model="keyword"
          class="search-input"
          placeholder="搜索标题 / 关键词 / 问题 / 答案"
          clearable
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
      </div>

      <div class="filter-tip">
        <button type="button" class="tip-toggle" @click="tipOpen = !tipOpen">
          <el-icon class="tip-icon"><InfoFilled /></el-icon>
          <span>本地过滤说明</span>
          <el-icon class="tip-arrow"><ArrowUp v-if="tipOpen" /><ArrowDown v-else /></el-icon>
        </button>
        <div v-show="tipOpen" class="tip-body">
          当前搜索仅对已加载列表按标题、关键词、问题、答案进行本地匹配，不发起服务端查询；切换状态或需获取最新数据时请点击“刷新”。
        </div>
      </div>

      <el-table
        :data="filteredList"
        v-loading="loading"
        size="small"
        class="kb-table"
        :row-class-name="rowClassName"
      >
        <el-table-column prop="id" label="ID" width="72" align="center" />
        <el-table-column prop="title" label="标题" min-width="120" show-overflow-tooltip />
        <el-table-column prop="keyword" label="关键词" min-width="120" show-overflow-tooltip />
        <el-table-column prop="question" label="问题" min-width="160" show-overflow-tooltip />
        <el-table-column prop="answer" label="答案" min-width="200" show-overflow-tooltip />
        <el-table-column label="类别" width="100" align="center">
          <template #default="{ row }">{{ categoryName(row.categoryId) }}</template>
        </el-table-column>
        <el-table-column prop="source" label="来源" width="90" align="center" />
        <el-table-column prop="helpful" label="有用" width="72" align="center" />
        <el-table-column label="操作" width="300" fixed="right" align="center">
          <template #default="{ row }">
            <div class="op-wrap">
              <div class="op-group op-status">
                <template v-if="row.reviewStatus === 'PENDING'">
                  <el-button class="op-btn" type="success" size="small" @click="doReview(row, 'APPROVED')">通过</el-button>
                  <el-button class="op-btn" type="danger" size="small" @click="doReview(row, 'REJECTED')">驳回</el-button>
                </template>
                <template v-else>
                  <el-button class="op-btn" type="success" size="small" @click="doReview(row, 'APPROVED')">上线</el-button>
                </template>
              </div>
              <div class="op-group op-neutral">
                <el-button class="op-btn" size="small" @click="openEdit(row)">编辑</el-button>
                <el-button class="op-btn" size="small" @click="openHistory(row)">历史</el-button>
              </div>
              <span class="op-sep" aria-hidden="true"></span>
              <div class="op-group op-danger">
                <el-button class="op-btn" type="danger" size="small" @click="doDelete(row)">删除</el-button>
              </div>
            </div>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无数据" :image-size="72" />
        </template>
      </el-table>
    </el-card>

    <el-dialog
      v-model="editVisible"
      :title="editForm.id ? '编辑知识' : '新增知识'"
      width="560px"
      destroy-on-close
    >
      <el-form :model="editForm" label-width="72px" class="kb-form">
        <el-form-item label="标题">
          <el-input v-model="editForm.title" placeholder="可选，简要概括" maxlength="100" show-word-limit />
        </el-form-item>
        <el-form-item label="关键词" required>
          <el-input v-model="editForm.keyword" placeholder="用于检索，如：热水器 漏水" maxlength="100" show-word-limit />
        </el-form-item>
        <el-form-item label="问题" required>
          <el-input v-model="editForm.question" placeholder="故障现象描述" maxlength="255" show-word-limit />
        </el-form-item>
        <el-form-item label="答案" required>
          <el-input v-model="editForm.answer" type="textarea" :rows="4" placeholder="处理步骤与建议" />
        </el-form-item>
        <el-form-item label="类别">
          <el-select v-model="editForm.categoryId" clearable placeholder="请选择类别" style="width: 220px">
            <el-option v-for="c in CATEGORIES" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="historyVisible"
      :title="`修订历史：${historyRow?.title || historyRow?.question || ''}`"
      width="720px"
      destroy-on-close
    >
      <el-table :data="historyList" size="small" v-loading="historyLoading" stripe max-height="420">
        <el-table-column prop="id" label="历史ID" width="80" />
        <el-table-column prop="reviewStatus" label="当时状态" width="100" />
        <el-table-column prop="question" label="问题" min-width="160" show-overflow-tooltip />
        <el-table-column prop="answer" label="答案" min-width="200" show-overflow-tooltip />
        <el-table-column prop="operatorId" label="操作人" width="90" />
        <el-table-column prop="gmtCreate" label="时间" min-width="168" />
        <el-table-column label="操作" width="110" fixed="right">
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
import { Search, InfoFilled, ArrowDown, ArrowUp } from '@element-plus/icons-vue';
import {
  createKnowledge, deleteKnowledge, listKnowledge, listKnowledgeHistory,
  reviewKnowledge, rollbackKnowledge, updateKnowledge
} from '../api/knowledge';
import { formatApiError } from '../api/http';

const STATUS_TEXT = { PENDING: '待审核', APPROVED: '已通过', REJECTED: '已驳回', OFFLINE: '已下线' };
const CATEGORIES = [
  { id: 1, name: '水电维修' },
  { id: 2, name: '网络故障' },
  { id: 3, name: '门窗家具' },
  { id: 4, name: '报修咨询' }
];

const activeStatus = ref('');
const keyword = ref('');
const loading = ref(false);
const list = ref([]);
const tipOpen = ref(false);

const editVisible = ref(false);
const saving = ref(false);
const editForm = ref({});

const historyVisible = ref(false);
const historyLoading = ref(false);
const historyRow = ref(null);
const historyList = ref([]);

const filteredList = computed(() => {
  const kw = keyword.value.trim().toLowerCase();
  if (!kw) { return list.value; }
  return list.value.filter((it) =>
    [it.title, it.keyword, it.question, it.answer].some((f) => (f || '').toLowerCase().includes(kw)));
});

function rowClassName({ row }) {
  return row.reviewStatus === 'PENDING' ? 'row-pending' : '';
}

function categoryName(id) {
  const c = CATEGORIES.find((x) => x.id === id);
  return c ? c.name : (id || '-');
}

async function load() {
  loading.value = true;
  try {
    const resp = await listKnowledge({ status: activeStatus.value || undefined });
    const data = resp?.data || {};
    const arr = data.records || data.list || data || [];
    list.value = Array.isArray(arr) ? [...arr].sort((a, b) => (Number(a.id) || 0) - (Number(b.id) || 0)) : [];
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
    ElMessage.warning('请填写关键词、问题、答案（必填）');
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
      ElMessage.success('已保存');
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
  const text = STATUS_TEXT[status] || status;
  try {
    await ElMessageBox.confirm(`确定将「${row.title || row.question || ('#' + row.id)}」置为${text}吗？`, '操作确认', { type: 'warning' });
  } catch {
    return;
  }
  try {
    await reviewKnowledge(row.id, status);
    ElMessage.success(`已${text}`);
    await load();
  } catch (e) {
    ElMessage.error('操作失败：' + formatApiError(e));
  }
}

async function doDelete(row) {
  try {
    await ElMessageBox.confirm(`确定删除「${row.title || row.question || ('#' + row.id)}」吗？删除后不可恢复。`, '删除确认', { type: 'warning' });
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
    if (!Array.isArray(historyList.value)) { historyList.value = []; }
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
.kb-page {
  max-width: 1400px;
}

.page-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.title-block {
  min-width: 0;
}

.page-title {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  color: #1f2d3d;
  line-height: 1.3;
}

.page-subtitle {
  margin: 4px 0 0;
  font-size: 13px;
  color: #8c96a3;
  line-height: 1.5;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.hdr-btn {
  min-width: 88px;
}

.content-card {
  border: 1px solid #e9edf3;
  border-radius: 8px;
}

.content-card :deep(.el-card__body) {
  padding: 16px 20px 32px;
}

.toolbar {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 10px;
}

.status-tabs {
  flex: 1;
  min-width: 0;
}

.status-tabs :deep(.el-tabs__header) {
  margin: 0;
}

.status-tabs :deep(.el-tabs__nav-wrap::after) {
  height: 1px;
  background-color: #e9edf3;
}

.search-input {
  width: 300px;
  flex-shrink: 0;
  margin-bottom: 6px;
}

.filter-tip {
  margin: 4px 0 16px;
}

.tip-toggle {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 6px;
  border: none;
  background: transparent;
  color: #909399;
  font-size: 12px;
  line-height: 1;
  cursor: pointer;
  border-radius: 4px;
}

.tip-toggle:hover {
  color: #606266;
  background: #f5f7fa;
}

.tip-icon,
.tip-arrow {
  font-size: 14px;
}

.tip-body {
  margin-top: 6px;
  padding: 8px 12px;
  font-size: 12px;
  line-height: 1.6;
  color: #606266;
  background: #f7f9fc;
  border: 1px solid #edf1f5;
  border-radius: 6px;
}

.kb-table {
  width: 100%;
  margin-bottom: 8px;
  --el-table-border-color: #ebeef5;
  --el-table-header-bg-color: #fafbfc;
}

.kb-table :deep(.el-table__cell) {
  padding-top: 10px;
  padding-bottom: 10px;
}

.kb-table :deep(th.el-table__cell) {
  color: #5f6b7a;
  font-weight: 600;
  background: #fafbfc;
}

.kb-table :deep(.el-table__row.row-pending > td.el-table__cell) {
  background-color: #fffaf2;
}

.kb-table :deep(.el-table__row.row-pending:hover > td.el-table__cell) {
  background-color: #fff5e6;
}

.op-wrap {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-wrap: wrap;
  gap: 6px;
}

.op-group {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.op-sep {
  width: 1px;
  height: 18px;
  background: #e0e6ed;
  margin: 0 2px;
}

.op-btn {
  min-width: 52px;
  padding: 4px 10px;
  margin: 0;
}

.kb-form :deep(.el-form-item) {
  margin-bottom: 18px;
}

@media (max-width: 900px) {
  .page-header {
    flex-direction: column;
    align-items: stretch;
  }

  .header-actions {
    justify-content: flex-end;
  }

  .toolbar {
    flex-direction: column;
    align-items: stretch;
  }

  .search-input {
    width: 100%;
    margin-bottom: 0;
  }
}
</style>
