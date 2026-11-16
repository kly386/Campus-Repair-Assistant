<template>
  <el-container class="layout">
    <el-aside width="220px" class="aside">
      <div class="logo">校园快修助手</div>
      <el-menu :default-active="$route.path" router class="menu">
        <el-menu-item index="/repair">智能报修</el-menu-item>
        <el-menu-item index="/diagnosis">自助诊断</el-menu-item>
        <el-menu-item index="/orders">工单管理</el-menu-item>
        <el-menu-item index="/schedule">师傅排班</el-menu-item>
        <el-menu-item index="/dashboard">运营看板</el-menu-item>
        <el-menu-item index="/files">文件管理</el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="header">
        <span class="header-title">校园快修服务助手</span>
        <!-- 当前登录用户 + 退出登录 -->
        <el-dropdown v-if="user" @command="handleCommand">
          <span class="user-entry">
            <el-icon><UserFilled /></el-icon>
            {{ user.realName }}（{{ roleLabel }}）
            <el-icon><ArrowDown /></el-icon>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="logout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </el-header>
      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { UserFilled, ArrowDown } from '@element-plus/icons-vue';

const route = useRoute();
const router = useRouter();

const user = computed(() => {
  try {
    return JSON.parse(localStorage.getItem('user') || 'null');
  } catch {
    return null;
  }
});

const roleLabel = computed(() => ({
  STUDENT: '学生', WORKER: '维修工', ADMIN: '管理员'
}[user.value?.role] || user.value?.role || ''));

/** 下拉命令：退出登录（丢弃 token 并跳转登录页） */
function handleCommand(cmd) {
  if (cmd !== 'logout') return;
  localStorage.removeItem('token');
  localStorage.removeItem('user');
  ElMessage.success('已退出登录');
  router.push('/login');
}
</script>

<style scoped>
.layout {
  height: 100vh;
}
.aside {
  background: #304156;
}
.logo {
  color: #fff;
  font-size: 18px;
  font-weight: bold;
  text-align: center;
  line-height: 60px;
}
.menu {
  border-right: none;
}
.header {
  background: #fff;
  border-bottom: 1px solid #e6e6e6;
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-weight: bold;
}
.header-title {
  flex: 1;
}
.user-entry {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-weight: normal;
  color: #303133;
  cursor: pointer;
  font-size: 14px;
}
.main {
  background: #f5f7fa;
  padding: 20px;
}
</style>
