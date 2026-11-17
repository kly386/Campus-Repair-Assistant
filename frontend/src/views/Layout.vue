<template>
  <el-container class="layout">
    <el-aside width="230px" class="aside">
      <div class="logo">
        <div class="logo-mark"><el-icon><Tools /></el-icon></div>
        <div>
          <strong>校园快修</strong>
          <span>服务助手</span>
        </div>
      </div>
      <el-scrollbar class="menu-scroll">
        <el-menu
          :default-active="$route.path"
          :default-openeds="openedMenus"
          router
          class="menu"
        >
          <SidebarMenu :items="menus" />
        </el-menu>
      </el-scrollbar>
    </el-aside>

    <el-container class="workspace">
      <el-header class="header">
        <div class="header-left">
          <span class="page-title">{{ currentTitle }}</span>
          <span class="page-subtitle">校园维修服务统一工作台</span>
        </div>
        <el-dropdown v-if="user" trigger="click" @command="handleCommand">
          <span class="user-entry">
            <span class="avatar">{{ avatarText }}</span>
            <span class="user-info">
              <strong>{{ user.realName || user.username }}</strong>
              <small>{{ roleLabel }}</small>
            </span>
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
        <router-view v-slot="{ Component }">
          <transition name="page" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { ArrowDown, Tools } from '@element-plus/icons-vue';
import SidebarMenu from '../components/SidebarMenu.vue';
import { logout } from '../api/auth';
import { authState, clearSession } from '../auth/session';
import { resetDynamicRoutes } from '../router/dynamicRoutes';

const route = useRoute();
const router = useRouter();
const menus = computed(() => authState.menus || []);
const user = computed(() => authState.user);
const currentTitle = computed(() => route.meta.title || '工作台');
const openedMenus = computed(() => (authState.menus || []).filter((item) => item.children?.length).map((item) => item.code));
const roleLabel = computed(() => ({
  ADMIN: '系统管理员',
  STUDENT: '学生用户',
  WORKER: '维修师傅'
}[user.value?.role] || user.value?.role || '用户'));
const avatarText = computed(() => (user.value?.realName || user.value?.username || 'U').slice(0, 1));

async function handleCommand(command) {
  if (command !== 'logout') return;
  try {
    await logout();
  } catch {
    // 即使服务端会话已过期，也要清除本地登录态。
  } finally {
    clearSession();
    resetDynamicRoutes(router);
    ElMessage.success('已退出登录');
    router.replace('/login');
  }
}
</script>

<style scoped>
.layout { height: 100vh; background: #f4f9fe; }
.aside {
  display: flex;
  flex-direction: column;
  background: linear-gradient(180deg, #f8fcff 0%, #eef7ff 100%);
  border-right: 1px solid #dfeefa;
  box-shadow: 8px 0 24px rgba(88, 133, 170, .06);
}
.logo {
  height: 74px;
  display: flex;
  align-items: center;
  gap: 11px;
  padding: 0 22px;
  border-bottom: 1px solid #e6f1fa;
}
.logo-mark {
  width: 38px;
  height: 38px;
  display: grid;
  place-items: center;
  border-radius: 12px;
  color: #fff;
  font-size: 20px;
  background: linear-gradient(135deg, #4a9ce9, #5fc3bd);
  box-shadow: 0 7px 16px rgba(74, 156, 233, .25);
}
.logo strong { display: block; color: #24445f; font-size: 17px; line-height: 1.1; }
.logo span { display: block; margin-top: 3px; color: #8ca2b5; font-size: 11px; letter-spacing: 2px; }
.menu-scroll { flex: 1; }
.menu {
  border-right: none;
  background: transparent;
  padding: 10px 12px;
}
.menu :deep(.el-menu-item),
.menu :deep(.el-sub-menu__title) {
  height: 46px;
  margin-bottom: 5px;
  border-radius: 11px;
  color: #587089;
}
.menu :deep(.el-menu-item:hover),
.menu :deep(.el-sub-menu__title:hover) { background: #eaf4fd; color: #327fc6; }
.menu :deep(.el-menu-item.is-active) {
  color: #2f7fc8;
  font-weight: 600;
  background: linear-gradient(90deg, #e1f1ff, #edf9fa);
  box-shadow: inset 3px 0 0 #4b9be6;
}
.workspace { min-width: 0; }
.header {
  height: 74px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 26px;
  background: rgba(255, 255, 255, .9);
  border-bottom: 1px solid #e6f0f8;
  backdrop-filter: blur(8px);
}
.header-left { display: flex; flex-direction: column; gap: 4px; }
.page-title { color: #24445f; font-size: 19px; font-weight: 700; }
.page-subtitle { color: #94a5b5; font-size: 12px; }
.user-entry {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 10px;
  border-radius: 12px;
  cursor: pointer;
  transition: background .2s;
}
.user-entry:hover { background: #f0f7fd; }
.avatar {
  width: 36px;
  height: 36px;
  display: grid;
  place-items: center;
  border-radius: 50%;
  color: #fff;
  font-weight: 700;
  background: linear-gradient(135deg, #5b9fe0, #65c0b9);
}
.user-info { display: flex; flex-direction: column; line-height: 1.25; }
.user-info strong { color: #304d66; font-size: 13px; }
.user-info small { color: #8fa2b4; font-size: 11px; }
.main { padding: 22px 26px; overflow-y: auto; }
.page-enter-active, .page-leave-active { transition: opacity .18s ease, transform .18s ease; }
.page-enter-from { opacity: 0; transform: translateY(6px); }
.page-leave-to { opacity: 0; transform: translateY(-4px); }
</style>