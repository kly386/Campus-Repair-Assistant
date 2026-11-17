<template>
  <router-view />
</template>

<style>
:root {
  --el-color-primary: #4b9be6;
  --el-color-primary-light-3: #75b4ed;
  --el-color-primary-light-5: #9ccbef;
  --el-color-primary-light-7: #c5e1f6;
  --el-color-primary-light-9: #edf7ff;
  --el-border-radius-base: 8px;
}
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}
html, body, #app {
  width: 100%;
  min-height: 100%;
}
body {
  font-family: 'Microsoft YaHei', 'PingFang SC', sans-serif;
  color: #304d66;
  background: #f4f9fe;
  -webkit-font-smoothing: antialiased;
}
button, input, textarea { font-family: inherit; }
</style>