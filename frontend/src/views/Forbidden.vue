<template>
  <div class="forbidden">
    <el-result icon="warning" title="403" sub-title="当前账号没有访问该页面的权限">
      <template #extra>
        <el-button type="primary" @click="goHome">返回可访问页面</el-button>
      </template>
    </el-result>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router';
import { firstAccessiblePath } from '../auth/session';

const router = useRouter();
function goHome() {
  router.replace(firstAccessiblePath() || '/login');
}
</script>

<style scoped>
.forbidden { min-height: 70vh; display: grid; place-items: center; }
</style>