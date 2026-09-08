<template>
  <el-container class="layout">
    <el-aside width="220px" class="aside">
      <div class="logo">AI实训评价</div>
      <el-menu :default-active="$route.path" router class="menu">
        <el-menu-item index="/dashboard">数据看板</el-menu-item>
        <el-menu-item index="/evaluation">实训评价</el-menu-item>
        <el-menu-item index="/report">报表中心</el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="header">大学生软件实训教学 AI 检查评价系统</el-header>
      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
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
  font-weight: bold;
}
.main {
  background: #f5f7fa;
  padding: 20px;
}
</style>
