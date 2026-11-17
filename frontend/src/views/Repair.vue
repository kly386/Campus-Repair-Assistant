<template>
  <div class="repair">
    <h2>智能报修</h2>
    <el-card v-if="!submitted" class="form-card">
      <el-form :model="form" label-width="100px">
        <el-form-item label="故障描述">
          <el-input v-model="form.description" type="textarea" :rows="3" placeholder="请描述故障现象，例如：宿舍热水器不出热水" />
        </el-form-item>
        <el-form-item label="上传照片">
          <input ref="fileInput" type="file" multiple accept="image/*" @change="onFileChange" />
          <div v-if="files.length" style="margin-top: 10px">
            <div v-for="(f, idx) in files" :key="idx">{{ f.name }} ({{ formatSize(f.size) }})</div>
          </div>
        </el-form-item>
        <el-form-item label="AI 解析">
          <el-button type="success" plain :loading="parsing" @click="handleAiParse">
            AI 智能解析
          </el-button>
          <span class="ai-tip">填描述/传照片后点击，自动补全下方字段</span>
        </el-form-item>
        <el-alert v-if="parseResult" :title="aiAlertTitle" :type="parseResult.mock ? 'warning' : 'success'"
                  :closable="false" style="margin-bottom: 16px"
                  :description="aiAlertDetail" show-icon />
        <el-form-item label="楼栋">
          <el-input v-model="form.building" placeholder="例如：3号宿舍楼" />
        </el-form-item>
        <el-form-item label="房间号">
          <el-input v-model="form.room" placeholder="例如：302" />
        </el-form-item>
        <el-form-item label="预约时间">
          <el-date-picker v-model="form.appointmentTime" type="datetime" placeholder="选择预约时间" value-format="YYYY-MM-DDTHH:mm:ss" />
        </el-form-item>
        <el-form-item label="紧急程度">
          <el-radio-group v-model="form.emergencyLevel">
            <el-radio value="low">一般</el-radio>
            <el-radio value="medium">较急</el-radio>
            <el-radio value="high">紧急</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item>
          <el-button v-permission="'order:create'" type="primary" :loading="submitting" @click="handleSubmit">提交报修</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-card v-if="submitted" class="success-card">
      <el-result icon="success" title="提交成功" sub-title="报修单已提交">
        <template #extra>
          <p>工单号：{{ orderId }}</p>
          <div v-if="attachments.length" style="margin-top: 20px; text-align: left">
            <h4>附件</h4>
            <ul>
              <li v-for="att in attachments" :key="att.id">
                <el-link :href="att.url" target="_blank" type="primary">{{ att.fileName }}</el-link>
              </li>
            </ul>
          </div>
          <el-button type="primary" @click="goToOrders">查看工单</el-button>
          <el-button @click="reset">再报修一单</el-button>
        </template>
      </el-result>
    </el-card>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { createOrder, parseRepair } from '../api/orders'
import { batchUpload, listByOrder, getSignedUrl } from '../api/files'

const router = useRouter()
const form = ref({
  description: '',
  building: '',
  room: '',
  appointmentTime: '',
  emergencyLevel: 'low'
})
const files = ref([])
const fileInput = ref(null)
const submitting = ref(false)
const submitted = ref(false)
const orderId = ref(null)
const attachments = ref([])
const parsing = ref(false)
const parseResult = ref(null)

/** AI 解析结果提示条 */
const aiAlertTitle = computed(() => {
  const r = parseResult.value
  if (!r) return ''
  const mockTag = r.mock ? '【模拟数据】' : ''
  return `${mockTag}识别类别：${r.categoryName}｜紧急度：${r.emergencyLevel}｜置信度：${(r.confidence * 100).toFixed(0)}%`
})
const aiAlertDetail = computed(() => {
  const r = parseResult.value
  if (!r) return ''
  const filled = ['building', 'room'].filter((k) => r[k]).map((k) => (k === 'building' ? '楼栋' : '房间号'))
  const notes = (r.notes || []).join('；')
  return `已自动回填${filled.length ? filled.join('、') : '紧急度'}${notes ? '。' + notes : ''}`
})

/**
 * AI 智能解析：把描述 + 照片发给 /api/orders/parse，
 * 返回结果自动回填楼栋/房间/紧急程度，用户确认后再提交
 */
async function handleAiParse() {
  if (!form.value.description && files.value.length === 0) {
    ElMessage.warning('请先填写故障描述或上传照片')
    return
  }
  parsing.value = true
  try {
    const fd = new FormData()
    fd.append('text', form.value.description || '')
    for (const f of files.value) {
      fd.append('images', f)
    }
    const r = await parseRepair(fd)
    parseResult.value = r
    if (r.building) form.value.building = r.building
    if (r.room) form.value.room = r.room
    if (r.emergencyLevel) form.value.emergencyLevel = r.emergencyLevel.toLowerCase()
    ElMessage.success('AI 解析完成，请确认后提交')
  } catch (e) {
    ElMessage.error('AI 解析失败：' + errText(e))
  } finally {
    parsing.value = false
  }
}

function onFileChange(e) {
  files.value = Array.from(e.target.files || [])
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

async function handleSubmit() {
  submitting.value = true
  try {
    const pr = parseResult.value
    const order = await createOrder({
      description: form.value.description,
      title: (pr && pr.title) || form.value.description?.slice(0, 50) || '报修',
      building: form.value.building,
      room: form.value.room,
      appointmentTime: form.value.appointmentTime || null,
      emergencyLevel: (form.value.emergencyLevel || 'low').toUpperCase(),
      categoryId: pr ? pr.categoryId : null,
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
      const withUrls = await Promise.all(
        list.map(async (f) => {
          try {
            const { signedUrl } = await getSignedUrl(f.id)
            return { ...f, url: signedUrl }
          } catch {
            return { ...f, url: '#' }
          }
        })
      )
      attachments.value = withUrls
    } catch (e) {
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

function reset() {
  submitted.value = false
  form.value = {
    description: '',
    building: '',
    room: '',
    appointmentTime: '',
    emergencyLevel: 'low'
  }
  files.value = []
  if (fileInput.value) fileInput.value.value = ''
  attachments.value = []
  orderId.value = null
  parseResult.value = null
}
</script>

<style scoped>
.repair h2 {
  margin-bottom: 20px;
}
.form-card {
  max-width: 600px;
}
.success-card {
  max-width: 600px;
}
.ai-tip {
  margin-left: 12px;
  font-size: 12px;
  color: #909399;
}
</style>
