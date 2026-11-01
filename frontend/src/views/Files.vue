<template>
  <div class="files">
    <h2>文件管理</h2>

    <el-row :gutter="20">
      <!-- 小文件批量上传 -->
      <el-col :span="12">
        <el-card header="小文件批量上传（一次请求提交 N 个）">
          <el-form label-width="80px">
            <el-form-item label="工单ID">
              <el-input-number v-model="orderId" :min="0" controls-position="right" />
            </el-form-item>
            <el-form-item label="选择文件">
              <input ref="smallInput" type="file" multiple @change="onSmallPick" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="smallUploading" :disabled="smallFiles.length === 0"
                         @click="doBatchUpload">
                批量上传 {{ smallFiles.length }} 个（一次请求）
              </el-button>
            </el-form-item>
          </el-form>
        </el-card>

        <el-card header="大文件分片上传（每片 5MB，逐片上传后台转发 OSS）" style="margin-top: 16px">
          <el-form label-width="80px">
            <el-form-item label="选择文件">
              <input ref="bigInput" type="file" @change="onBigPick" />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="bigUploading" :disabled="!bigFile"
                         @click="doChunkUpload">
                {{ bigUploadId ? `继续上传（从第 ${bigNextIndex} 片，共 ${chunkCount} 片）` : `分片上传（${chunkCount} 片）` }}
              </el-button>
              <el-button v-if="bigUploading" type="warning" plain @click="pauseBigUpload">暂停</el-button>
              <span v-if="bigUploading" style="margin-left: 12px">正在上传第 {{ bigChunkIndex }}/{{ chunkCount }} 片</span>
              <span v-else-if="bigUploadId" style="margin-left: 12px; color: #e6a23c">已暂停（断点：第 {{ bigNextIndex }} 片）</span>
            </el-form-item>
            <el-progress v-if="bigUploading || bigPercent > 0" :percentage="bigPercent" />
          </el-form>
        </el-card>
      </el-col>

      <!-- 附件列表与下载 -->
      <el-col :span="12">
        <el-card header="工单附件（列表查询走 @Slave 从库）">
          <el-form inline>
            <el-form-item label="工单ID">
              <el-input-number v-model="listOrderId" :min="0" controls-position="right" />
            </el-form-item>
            <el-form-item>
              <el-button @click="loadList">查询</el-button>
              <el-button type="success" :disabled="list.length === 0" @click="onZip">
                打包 ZIP 下载（{{ list.length }} 个）
              </el-button>
            </el-form-item>
          </el-form>
          <el-table :data="list" size="small">
            <el-table-column prop="id" label="ID" width="70" />
            <el-table-column prop="fileName" label="文件名" show-overflow-tooltip />
            <el-table-column label="大小" width="110">
              <template #default="{ row }">{{ formatSize(row.fileSize) }}</template>
            </el-table-column>
            <el-table-column prop="fileType" label="来源" width="90" />
            <el-table-column label="操作" width="100">
              <template #default="{ row }">
                <el-button type="primary" link @click="onDownload(row)">下载</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue';
import { ElMessage } from 'element-plus';
import {
  batchUpload, initMultipart, uploadChunk, completeMultipart,
  listByOrder, getSignedUrl, downloadZip
} from '../api/files';

const CHUNK_SIZE = 5 * 1024 * 1024; // 5MB

/* ---------- 小文件批量上传 ---------- */
const orderId = ref(null);
const smallFiles = ref([]);
const smallUploading = ref(false);
const smallInput = ref(null);

function onSmallPick(e) {
  smallFiles.value = Array.from(e.target.files);
}

async function doBatchUpload() {
  smallUploading.value = true;
  try {
    // 一次 HTTP 请求把 N 个文件全部带上，后端单条多行 INSERT 落库
    const saved = await batchUpload(smallFiles.value, orderId.value || null);
    ElMessage.success(`批量上传成功，共 ${saved.length} 个文件，元数据已批量落库`);
    smallFiles.value = [];
    if (smallInput.value) smallInput.value.value = '';
  } catch (e) {
    ElMessage.error('上传失败：' + errText(e));
  } finally {
    smallUploading.value = false;
  }
}

/* ---------- 大文件分片上传 ---------- */
const bigFile = ref(null);
const bigUploading = ref(false);
const bigChunkIndex = ref(0);
const bigInput = ref(null);
let bigPaused = false;            // 暂停标志：在分片间生效（分片级暂停）
// 断点状态：uploadId 对应后端/OSS 的分片会话，nextIndex 是下一个待传分片下标。
// 二者只要在进程内存活，重启上传即可从断点继续，已传分片全部跳过。
const bigUploadId = ref(null);
const bigNextIndex = ref(1);

const chunkCount = computed(() =>
  bigFile.value ? Math.ceil(bigFile.value.size / CHUNK_SIZE) : 0);
const bigPercent = computed(() =>
  chunkCount.value ? Math.round(((bigChunkIndex.value - 1) / chunkCount.value) * 100) : 0);

function onBigPick(e) {
  bigFile.value = e.target.files[0] || null;
  bigChunkIndex.value = 0;
  bigUploadId.value = null; // 换了文件 → 旧断点作废，从头开始
  bigNextIndex.value = 1;
}

function pauseBigUpload() {
  bigPaused = true;
  ElMessage.info('已暂停：当前片传完后停止，可点「继续上传」从断点续传');
}

async function doChunkUpload() {
  const file = bigFile.value;
  if (!file) return;
  bigPaused = false;
  bigUploading.value = true;
  try {
    // 无 uploadId 说明是首次上传 → init；有 uploadId 说明是断点续传 → 跳过 init 直接传剩余分片
    if (!bigUploadId.value) {
      const { uploadId } = await initMultipart(file.name, orderId.value || null);
      bigUploadId.value = uploadId;
      bigNextIndex.value = 1;
    }
    const total = Math.ceil(file.size / CHUNK_SIZE);
    let i = bigNextIndex.value;
    for (; i <= total; i++) { // 分片从 1 开始编号；失败/暂停后可从该片重试（断点续传）
      if (bigPaused) {
        bigNextIndex.value = i; // 记录断点：下一片从 i 开始
        ElMessage.warning(`已在第 ${i} 片前暂停，进度 ${(Math.round((i - 1) / total * 100))}%，可继续上传`);
        return;
      }
      bigChunkIndex.value = i;
      const start = (i - 1) * CHUNK_SIZE;
      const blob = file.slice(start, Math.min(start + CHUNK_SIZE, file.size));
      await uploadChunk(bigUploadId.value, i, blob);
      bigNextIndex.value = i + 1;
    }
    await completeMultipart(bigUploadId.value, orderId.value || null);
    ElMessage.success(`分片上传完成，共 ${total} 片，已通知 OSS 合并`);
    bigFile.value = null;
    bigUploadId.value = null;
    bigNextIndex.value = 1;
    if (bigInput.value) bigInput.value.value = '';
  } catch (e) {
    ElMessage.error('分片上传失败：' + errText(e) + '（已传分片保留在 OSS，修复网络可点继续上传）');
    // 单片失败：不删除 uploadId 会话，保留断点，重试时只从失败片开始
  } finally {
    bigUploading.value = false;
  }
}

/* ---------- 附件列表与下载 ---------- */
const listOrderId = ref(1);
const list = ref([]);

async function loadList() {
  try {
    list.value = await listByOrder(listOrderId.value);
  } catch (e) {
    ElMessage.error('查询失败：' + errText(e));
  }
}

async function onDownload(row) {
  try {
    const { signedUrl } = await getSignedUrl(row.id); // 私有 Bucket → 先拿临时签名 URL
    window.open(signedUrl);
  } catch (e) {
    ElMessage.error('获取下载链接失败：' + errText(e));
  }
}

async function onZip() {
  try {
    await downloadZip(list.value.map((f) => f.id)); // 一次打包所有附件
  } catch (e) {
    ElMessage.error('打包下载失败：' + errText(e));
  }
}

/* ---------- 工具 ---------- */
function formatSize(bytes) {
  if (bytes == null) return '-';
  if (bytes > 1024 * 1024) return (bytes / 1024 / 1024).toFixed(1) + ' MB';
  if (bytes > 1024) return (bytes / 1024).toFixed(1) + ' KB';
  return bytes + ' B';
}

function errText(e) {
  const data = e?.response?.data;
  if (typeof data === 'string') return data.slice(0, 200);
  if (data?.message) return data.message;
  return e?.message || '未知错误';
}
</script>
