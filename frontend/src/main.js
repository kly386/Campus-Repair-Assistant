import { createApp } from 'vue';
import ElementPlus from 'element-plus';
import 'element-plus/dist/index.css';
import App from './App.vue';
import router from './router';
import { bindRouter } from './api/http';

bindRouter(router); // 供 axios 401 时跳转登录页

const app = createApp(App);
app.use(ElementPlus);
app.use(router);
app.mount('#app');
