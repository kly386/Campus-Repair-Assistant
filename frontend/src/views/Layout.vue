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
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="header">校园快修服务助手</el-header>
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
