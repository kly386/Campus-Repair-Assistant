<template>
  <div class="diagnosis">
    <h2>自助诊断</h2>
    <el-card class="chat-card">
      <div class="chat-area">
        <div class="message system">您好！我是维修小助手，请描述您遇到的问题，我会帮您判断是否可以自行解决。</div>
      </div>
      <div class="input-area">
        <el-input placeholder="例如：路由器亮红灯怎么办" />
        <el-button type="primary">提问</el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup>
</script>

<style scoped>
.diagnosis h2 {
  margin-bottom: 20px;
}
.chat-card {
  max-width: 700px;
}
.chat-area {
  min-height: 300px;
  padding: 10px;
  background: #f9f9f9;
  border-radius: 4px;
  margin-bottom: 15px;
}
.message {
  padding: 10px 15px;
  border-radius: 8px;
  margin-bottom: 10px;
  max-width: 80%;
}
.message.system {
  background: #e3f2fd;
  color: #1976d2;
}
.input-area {
  display: flex;
  gap: 10px;
}
</style>
