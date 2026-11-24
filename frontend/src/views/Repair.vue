<template>
  <div class="repair-page">
    <div class="page-header">
      <div>
        <h2 class="page-title">智能报修</h2>
        <p class="page-subtitle">填写故障信息，AI 可辅助识别，提交后师傅/管理员可快速联系处理</p>
      </div>
    </div>

    <el-card v-if="!submitted" shadow="never" class="form-card">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="96px" class="repair-form">
        <el-divider content-position="left">故障信息</el-divider>

        <el-form-item label="故障描述" prop="description">
          <el-input v-model="form.description" type="textarea" :rows="4" maxlength="500" show-word-limit
                    placeholder="请描述故障现象、发生时间、影响范围，例如：3号宿舍楼302空调不制冷，昨晚开始" />
        </el-form-item>

        <el-form-item label="故障类型" prop="categoryId">
          <el-select v-model="form.categoryId" placeholder="请选择故障类型" clearable style="width: 260px">
            <el-option v-for="c in categories" :key="c.id" :label="c.name" :value="c.id" />
          </el-select>
          <span class="field-tip">AI 识别后可手动修改</span>
        </el-form-item>

        <el-form-item label="现场照片">
          <div class="upload-block">
            <input ref="fileInput" type="file" multiple accept="image/*" @change="onFileChange" />
            <div class="field-tip">可选，支持多张图片，便于快速判断故障</div>
            <div v-if="files.length" class="file-list">
              <div v-for="(f, idx) in files" :key="idx + f.name" class="file-item">
                <span class="file-name">{{ f.name }} ({{ formatSize(f.size) }})</span>
                <el-button link type="danger" @click="removeFile(idx)">移除</el-button>
              </div>
              <el-button link type="info" @click="clearFiles">清空已选</el-button>
            </div>
          </div>
        </el-form-item>

        <el-form-item label="AI 辅助">
          <el-button type="success" plain :loading="parsing" @click="handleAiParse">AI 智能解析</el-button>
          <span class="field-tip">自动识别类型、紧急度、地点，请确认后提交</span>
        </el-form-item>

        <el-alert v-if="parseResult" :title="aiAlertTitle" :type="parseResult.mock ? 'warning' : 'success'"
                  :closable="false" show-icon class="parse-alert">
          <template #default>
            <div>{{ aiAlertDetail }}</div>
          </template>
        </el-alert>

        <el-divider content-position="left">地点与时间</el-divider>

        <el-form-item label="楼栋" prop="building">
          <el-input v-model="form.building" placeholder="例如：3号宿舍楼 / 教学楼A座" maxlength="50" />
        </el-form-item>

        <el-form-item label="房间号" prop="room">
          <el-input v-model="form.room" placeholder="例如：302 / 机房201" maxlength="50" />
        </el-form-item>

        <el-form-item label="预约时间" prop="appointmentTime">
          <el-date-picker v-model="form.appointmentTime" type="datetime" placeholder="选择期望上门时间"
                          value-format="YYYY-MM-DDTHH:mm:ss" :disabled-date="disabledPastDate" style="width: 260px" />
        </el-form-item>

        <el-form-item label="紧急程度" prop="emergencyLevel">
          <el-radio-group v-model="form.emergencyLevel">
            <el-radio value="low">一般</el-radio>
            <el-radio value="medium">较急</el-radio>
            <el-radio value="high">紧急</el-radio>
          </el-radio-group>
        </el-form-item>

        <el-divider content-position="left">联系方式</el-divider>

        <el-form-item label="联系电话" prop="contactPhone">
          <el-input v-model="form.contactPhone" placeholder="用于师傅/管理员联系" maxlength="20" style="width: 260px" />
          <span class="field-tip">已登录账号自动带入，可修改</span>
        </el-form-item>

        <el-form-item label="备用电话">
          <el-input v-model="form.backupPhone" placeholder="可选，第二联系人电话" maxlength="20" style="width: 260px" />
        </el-form-item>

        <el-form-item>
          <el-button v-permission="'order:create'" type="primary" :loading="submitting" @click="handleSubmit">提交报修</el-button>
          <el-button @click="resetForm">重置</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card v-if="submitted" shadow="never" class="success-card">
      <el-result icon="success" title="报修提交成功" sub-title="工单已生成，维修人员将按预约时间处理">
        <template #extra>
          <div class="success-info">
            <p>工单号：{{ orderId }}</p>
            <p>故障类型：{{ categoryName(form.categoryId) }}</p>
            <p>联系电话：{{ form.contactPhone }}<span v-if="form.backupPhone"> / 备用 {{ form.backupPhone }}</span></p>
          </div>
          <div v-if="attachments.length" class="attachment-box">
            <h4>附件</h4>
            <ul>
              <li v-for="att in attachments" :key="att.id">
                <el-link :href="att.url" target="_blank" type="primary">{{ att.fileName }}</el-link>
              </li>
            </ul>
          </div>
          <div class="success-actions">
            <el-button type="primary" @click="goToOrders">查看工单</el-button>
            <el-button @click="reset">再报修一单</el-button>
          </div>
        </template>
      </el-result>
    </el-card>
  </div>
</template>
<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { createOrder, parseRepair } from '../api/orders'
import { batchUpload, listByOrder, getSignedUrl } from '../api/files'
import { authState } from '../auth/session'
import { me } from '../api/auth'

const route = useRoute()
const router = useRouter()
const formRef = ref(null)
const fileInput = ref(null)

const categories = [
  { id: 1, name: '水电维修', code: 'WATER_ELECTRIC' },
  { id: 2, name: '网络故障', code: 'NETWORK' },
  { id: 3, name: '门窗家具', code: 'FURNITURE' },
  { id: 4, name: '报修咨询', code: 'OTHER' }
]

const defaultForm = () => ({
  description: '',
  categoryId: null,
  building: '',
  room: '',
  appointmentTime: '',
  emergencyLevel: 'low',
  contactPhone: authState.user?.phone || '',
  backupPhone: ''
})

const form = ref(defaultForm())
const files = ref([])
const submitting = ref(false)
const submitted = ref(false)
const orderId = ref(null)
const attachments = ref([])
const parsing = ref(false)
const parseResult = ref(null)

const phonePattern = /^1[3-9]\d{9}$|^0\d{2,3}-?\d{7,8}$/
const rules = {
  description: [{ required: true, message: '请填写故障描述', trigger: 'blur' }],
  categoryId: [{ required: true, message: '请选择故障类型', trigger: 'change' }],
  building: [{ required: true, message: '请填写楼栋', trigger: 'blur' }],
  room: [{ required: true, message: '请填写房间号', trigger: 'blur' }],
  emergencyLevel: [{ required: true, message: '请选择紧急程度', trigger: 'change' }],
  contactPhone: [
    { required: true, message: '请填写联系电话', trigger: 'blur' },
    { pattern: phonePattern, message: '请输入正确的手机号或座机号', trigger: 'blur' }
  ]
}

const aiAlertTitle = computed(() => {
  const r = parseResult.value
  if (!r) return ''
  const mockTag = r.mock ? '【模拟】' : ''
  return `${mockTag}识别：${r.categoryName || '未识别'} / 紧急度 ${emergencyText(r.emergencyLevel)} / 置信度 ${((r.confidence || 0) * 100).toFixed(0)}%`
})

const aiAlertDetail = computed(() => {
  const r = parseResult.value
  if (!r) return ''
  const filled = []
  if (r.categoryId) filled.push('故障类型')
  if (r.building) filled.push('楼栋')
  if (r.room) filled.push('房间号')
  if (r.emergencyLevel) filled.push('紧急度')
  const notes = (r.notes || []).filter(Boolean).join('；')
  return `已自动回填：${filled.length ? filled.join('、') : '无'}。请核对后提交${notes ? '。' + notes : ''}`
})

function emergencyText(level) {
  const map = { LOW: '一般', MEDIUM: '较急', HIGH: '紧急', low: '一般', medium: '较急', high: '紧急' }
  return map[level] || level || '-'
}

function categoryName(id) {
  return categories.find((c) => c.id === id)?.name || '未选择'
}

function disabledPastDate(time) {
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  return time.getTime() < today.getTime()
}

function onFileChange(e) {
  files.value = Array.from(e.target.files || [])
}

function removeFile(idx) {
  files.value = files.value.filter((_, i) => i !== idx)
  if (files.value.length === 0 && fileInput.value) fileInput.value.value = ''
}

function clearFiles() {
  files.value = []
  if (fileInput.value) fileInput.value.value = ''
}

function formatSize(bytes) {
  if (bytes == null) return '-'
  if (bytes > 1024 * 1024) return (bytes / 1024 / 1024).toFixed(1) + ' MB'
  if (bytes > 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return bytes + ' B'
}

function errText(e) {
  const data = e?.response?.data
  if (typeof data === 'string') return data.slice(0, 200)
  if (data?.message) return data.message
  return e?.message || '未知错误'
}

function normalizeEmergencyLevel(level) {
  const v = String(level || '').toUpperCase()
  if (v === 'HIGH') return 'high'
  if (v === 'MEDIUM' || v === 'NORMAL') return 'medium'
  if (v === 'LOW') return 'low'
  return ''
}

function applyPrefill() {
  const handoff = route.query.handoff
  if (typeof handoff === 'string' && handoff.trim()) {
    try {
      const raw = sessionStorage.getItem('repairHandoff')
      if (raw) {
        const data = JSON.parse(raw)
        if (data && data.nonce === handoff) {
          if (!form.value.description && data.question) form.value.description = String(data.question).trim()
          if (!form.value.categoryId && data.categoryId) form.value.categoryId = Number(data.categoryId)
          const em = normalizeEmergencyLevel(data.emergencyLevel)
          if (em && form.value.emergencyLevel === 'low') form.value.emergencyLevel = em
          sessionStorage.removeItem('repairHandoff')
          return
        }
      }
    } catch {
      // ignore invalid handoff
    }
  }

  const q = route.query.question
  if (typeof q === 'string' && q.trim() && !form.value.description) {
    form.value.description = q.trim()
  }
}

async function loadProfilePhone() {
  if (form.value.contactPhone) return
  try {
    const profile = await me()
    const phone = profile?.phone || authState.user?.phone
    if (phone && !form.value.contactPhone) form.value.contactPhone = phone
  } catch {
    // ignore
  }
}

async function handleAiParse() {
  if (!form.value.description && files.value.length === 0) {
    ElMessage.warning('请先填写故障描述或上传照片')
    return
  }
  parsing.value = true
  try {
    const fd = new FormData()
    fd.append('text', form.value.description || '')
    for (const f of files.value) fd.append('images', f)
    const r = await parseRepair(fd)
    parseResult.value = r
    if (r.categoryId) form.value.categoryId = Number(r.categoryId)
    if (r.building) form.value.building = r.building
    if (r.room) form.value.room = r.room
    if (r.emergencyLevel) form.value.emergencyLevel = String(r.emergencyLevel).toLowerCase()
    ElMessage.success('AI 解析完成，请核对信息后提交')
  } catch (e) {
    ElMessage.error('AI 解析失败：' + errText(e))
  } finally {
    parsing.value = false
  }
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  if (form.value.backupPhone && !phonePattern.test(form.value.backupPhone)) {
    ElMessage.warning('备用电话格式不正确')
    return
  }
  submitting.value = true
  try {
    const pr = parseResult.value
    const order = await createOrder({
      title: (pr && pr.title) || form.value.description.slice(0, 50) || '报修',
      description: form.value.description,
      categoryId: form.value.categoryId,
      building: form.value.building,
      room: form.value.room,
      appointmentTime: form.value.appointmentTime || null,
      emergencyLevel: (form.value.emergencyLevel || 'low').toUpperCase(),
      contactPhone: form.value.contactPhone,
      backupPhone: form.value.backupPhone || null,
      aiParsedInfo: pr ? pr.aiParsedInfo : null,
      aiConfidence: pr ? pr.confidence : null
    })
    const newOrderId = order.id
    orderId.value = newOrderId

    if (files.value.length > 0) {
      await batchUpload(files.value, newOrderId)
    }

    try {
      const list = await listByOrder(newOrderId)
      attachments.value = await Promise.all(list.map(async (f) => {
        try {
          const { signedUrl } = await getSignedUrl(f.id)
          return { ...f, url: signedUrl }
        } catch {
          return { ...f, url: '#' }
        }
      }))
    } catch {
      // ignore
    }

    submitted.value = true
    ElMessage.success('提交成功')
  } catch (e) {
    ElMessage.error('提交失败：' + errText(e))
  } finally {
    submitting.value = false
  }
}

function goToOrders() {
  router.push('/orders')
}

function resetForm() {
  form.value = defaultForm()
  files.value = []
  parseResult.value = null
  if (fileInput.value) fileInput.value.value = ''
  formRef.value?.clearValidate()
}

function reset() {
  submitted.value = false
  orderId.value = null
  attachments.value = []
  resetForm()
  loadProfilePhone()
}

onMounted(() => {
  applyPrefill()
  loadProfilePhone()
})
</script>
<style scoped>
.repair-page { max-width: 920px; }
.page-header { margin-bottom: 16px; }
.page-title { margin: 0; font-size: 20px; font-weight: 600; color: #1f2d3d; }
.page-subtitle { margin: 4px 0 0; font-size: 13px; color: #8a97a6; }
.form-card, .success-card { border: 1px solid #e9edf3; border-radius: 8px; }
.repair-form :deep(.el-divider__text) { font-size: 13px; color: #606266; }
.field-tip { margin-left: 12px; font-size: 12px; color: #909399; line-height: 1.5; }
.upload-block { width: 100%; }
.file-list { margin-top: 10px; padding: 8px 12px; background: #fafbfc; border: 1px solid #ebeef5; border-radius: 6px; }
.file-item { display: flex; align-items: center; gap: 8px; margin-bottom: 4px; }
.file-name { flex: 1; font-size: 13px; color: #606266; word-break: break-all; }
.parse-alert { margin: 0 0 18px 96px; }
.success-info { margin-bottom: 12px; color: #606266; line-height: 1.8; }
.success-info p { margin: 0; }
.attachment-box { margin: 16px 0; text-align: left; padding: 12px 16px; background: #fafbfc; border-radius: 6px; }
.attachment-box h4 { margin: 0 0 8px; font-size: 14px; color: #303133; }
.attachment-box ul { margin: 0; padding-left: 18px; }
.success-actions { display: flex; justify-content: center; gap: 12px; margin-top: 8px; }
@media (max-width: 768px) {
  .parse-alert { margin-left: 0; }
  .field-tip { display: block; margin-left: 0; margin-top: 4px; }
}
</style>
