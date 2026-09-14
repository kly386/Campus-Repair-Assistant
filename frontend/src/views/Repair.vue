<template>
  <div class="repair">
    <h2>智能报修</h2>
    <el-card class="form-card">
      <el-form label-width="100px">
        <el-form-item label="故障描述">
          <el-input type="textarea" :rows="3" placeholder="请描述故障现象，例如：宿舍热水器不出热水" />
        </el-form-item>
        <el-form-item label="上传照片">
          <el-upload action="#" list-type="picture-card" :auto-upload="false">
            <el-icon><Plus /></el-icon>
          </el-upload>
        </el-form-item>
        <el-form-item label="楼栋">
          <el-input placeholder="例如：3号宿舍楼" />
        </el-form-item>
        <el-form-item label="房间号">
          <el-input placeholder="例如：302" />
        </el-form-item>
        <el-form-item label="预约时间">
          <el-date-picker type="datetime" placeholder="选择预约时间" />
        </el-form-item>
        <el-form-item label="紧急程度">
          <el-radio-group>
            <el-radio value="low">一般</el-radio>
            <el-radio value="medium">较急</el-radio>
            <el-radio value="high">紧急</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item>
          <el-button type="primary">提交报修</el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { Plus } from '@element-plus/icons-vue'
</script>

<style scoped>
.repair h2 {
  margin-bottom: 20px;
}
.form-card {
  max-width: 600px;
}
</style>
