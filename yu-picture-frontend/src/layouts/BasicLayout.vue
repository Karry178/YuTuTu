<template>
  <div id="basicLayout">
    <!-- 基本上中下布局，vh:可视高度 -->
    <a-layout style="min-height: 100vh">
      <a-layout-header class="header">
        <GlobalHeader/>
      </a-layout-header>
      <a-layout>
        <GlobalSider class="sider"/>
        <a-layout-content class="content">
          <!-- router-view会根据URL自动导航到相应页面 -->
          <router-view />
        </a-layout-content>
      </a-layout>
      <a-layout-footer class="footer">
        鱼图云图库 by 程序员Karry
      </a-layout-footer>
    </a-layout>
  </div>
</template>


<script setup lang="ts">

import GlobalHeader from '@/components/GlobalHeader.vue'
import GlobalSider from '@/components/GlobalSider.vue'
</script>


<style scoped>
#basicLayout .header {
  padding-inline: 30px;
  background: white;
  color: unset;
  margin-bottom: 1px;
}

#basicLayout .sider {
  background: white;
  border-right: 1px solid #eee;
  padding-top: 20px;
}

/*消除板块之间的细线*/
#basicLayout :deep(.ant-menu-root) {
  border-bottom: none !important;
  border-inline-end: none !important;
}

#basicLayout .content {
  padding: 28px;
  background: linear-gradient(to right, #fefefe, #fff);
  margin-bottom: 28px;
}

#basicLayout .footer {
  background: #efefef;
  padding: 16px;
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  text-align: center;
}
</style>
