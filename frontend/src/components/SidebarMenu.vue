<template>
  <template v-for="item in items" :key="item.id || item.code">
    <el-sub-menu v-if="item.children?.length" :index="item.code || item.path">
      <template #title>
        <el-icon><component :is="iconOf(item.icon)" /></el-icon>
        <span>{{ item.name }}</span>
      </template>
      <SidebarMenu :items="item.children" />
    </el-sub-menu>
    <el-menu-item v-else :index="item.path">
      <el-icon><component :is="iconOf(item.icon)" /></el-icon>
      <span>{{ item.name }}</span>
    </el-menu-item>
  </template>
</template>

<script setup>
import {
  Calendar,
  FirstAidKit,
  Folder,
  House,
  Key,
  Menu,
  Setting,
  Tickets,
  Tools,
  User
} from '@element-plus/icons-vue';

defineOptions({ name: 'SidebarMenu' });
defineProps({ items: { type: Array, default: () => [] } });

const iconMap = {
  House,
  Tickets,
  Calendar,
  FirstAidKit,
  Folder,
  Setting,
  User,
  Key,
  Tools
};

function iconOf(name) {
  return iconMap[name] || Menu;
}
</script>