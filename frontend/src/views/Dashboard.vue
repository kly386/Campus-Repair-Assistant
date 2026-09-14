<template>
  <div class="dashboard">
    <h2>运营看板</h2>
    <el-row :gutter="20">
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="stat-item">
            <div class="stat-value">128</div>
            <div class="stat-label">本月工单数</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="stat-item">
            <div class="stat-value">2.5h</div>
            <div class="stat-label">平均响应时长</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="stat-item">
            <div class="stat-value">4.6</div>
            <div class="stat-label">满意度评分</div>
          </div>
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <div class="stat-item">
            <div class="stat-value">92%</div>
            <div class="stat-label">完成率</div>
          </div>
        </el-card>
      </el-col>
    </el-row>
    <el-row :gutter="20" style="margin-top: 20px;">
      <el-col :span="12">
        <el-card header="高频故障 Top5">
          <div style="height: 300px; background: #f0f0f0; display: flex; align-items: center; justify-content: center; color: #999;">
            图表区域
          </div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card header="工单趋势">
          <div style="height: 300px; background: #f0f0f0; display: flex; align-items: center; justify-content: center; color: #999;">
            图表区域
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
</script>

<style scoped>
.dashboard h2 {
  margin-bottom: 20px;
}
.stat-item {
  text-align: center;
}
.stat-value {
  font-size: 28px;
  font-weight: bold;
  color: #409eff;
}
.stat-label {
  margin-top: 8px;
  color: #666;
}
</style>
