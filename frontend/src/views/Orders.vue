<template>
  <div class="orders">
    <h2>工单管理</h2>
    <el-table :data="[]" style="width: 100%">
      <el-table-column prop="id" label="工单号" width="120" />
      <el-table-column prop="title" label="故障类型" />
      <el-table-column prop="location" label="位置" />
      <el-table-column prop="status" label="状态" />
      <el-table-column prop="createTime" label="提交时间" />
      <el-table-column label="操作" width="150">
        <template #default>
          <el-button type="primary" link>详情</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup>
</script>

<style scoped>
.orders h2 {
  margin-bottom: 20px;
}
</style>
