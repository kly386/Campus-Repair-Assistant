<template>
  <div class="schedule">
    <h2>师傅排班</h2>
    <el-table :data="[]" style="width: 100%">
      <el-table-column prop="name" label="师傅姓名" />
      <el-table-column prop="specialty" label="擅长类型" />
      <el-table-column prop="date" label="排班日期" />
      <el-table-column prop="timeSlot" label="时间段" />
      <el-table-column prop="status" label="状态" />
      <el-table-column label="操作" width="150">
        <template #default>
          <el-button type="primary" link>编辑</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup>
</script>

<style scoped>
.schedule h2 {
  margin-bottom: 20px;
}
</style>
