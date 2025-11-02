import { createApp } from 'vue'
import { createPinia } from 'pinia'

import App from './App.vue'
import router from './router'

// 引入 Ant Design Vue
import Antd from 'ant-design-vue';
import 'ant-design-vue/dist/reset.css';


// main.ts 是全局注册组件的入口文件

// 1. 创建 Vue 应用实例
const app = createApp(App)

// 2.注册插件（插件是用于扩展 Vue 应用功能的工具）
// 2.1 注册 Pinia 状态管理
app.use(createPinia())
// 2.2 注册 Vue Router
app.use(router)
// 2.3 注册 Ant Design Vue
app.use(Antd)


// 3. 应用挂载：将 Vue 应用挂载到 HTML 中的某个 DOM 元素上。
app.mount('#app')

