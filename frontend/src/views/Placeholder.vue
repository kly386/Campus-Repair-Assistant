<template>
  <div class="placeholder-page">
    <el-card shadow="never">
      <div class="placeholder-icon">⌘</div>
      <h2>{{ title }}</h2>
      <p>该动态菜单已由后端权限系统挂载，业务页面可在此位置继续开发。</p>
      <el-tag type="info" effect="plain">权限标识：{{ permission || '仅登录可访问' }}</el-tag>
    </el-card>
  </div>
</template>

<script setup>
import { computed } from 'vue';
import { useRoute } from 'vue-router';

const route = useRoute();
const title = computed(() => route.meta.title || '功能页面');
const permission = computed(() => route.meta.permission || '');
</script>

<style scoped>
.placeholder-page { display: grid; place-items: center; min-height: 65vh; }
.placeholder-page .el-card { width: min(560px, 100%); text-align: center; border-radius: 16px; }
.placeholder-icon { font-size: 42px; color: #5b9bd5; margin-bottom: 12px; }
.placeholder-page h2 { margin-bottom: 12px; color: #26435d; }
.placeholder-page p { color: #6b7c93; margin-bottom: 18px; }
</style>