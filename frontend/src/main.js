import { createApp } from 'vue';
import ElementPlus from 'element-plus';
import 'element-plus/dist/index.css';
import App from './App.vue';
import router, { rebuildRoutes } from './router';
import { bindRouteRebuilder, bindRouter } from './api/http';
import permissionDirective from './directives/permission';

bindRouter(router);
bindRouteRebuilder(rebuildRoutes);

const app = createApp(App);
app.use(ElementPlus);
app.use(router);
app.directive('permission', permissionDirective);
app.mount('#app');