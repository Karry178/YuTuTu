<template>
  <div id="basicLayout">
    <!-- 基本上中下布局，vh:可视高度 -->
    <a-layout style="min-height: 100vh">
      <a-layout-header class="header">
        <GlobalHeader/>
      </a-layout-header>
      <a-layout-content>
        <!-- router-view会根据URL自动导航到相应页面 -->
        <router-view />
      </a-layout-content>
      <a-layout-footer class="footer">
        鱼图云图库 by 程序员Karry
      </a-layout-footer>
    </a-layout>
  </div>
</template>


<script setup lang="ts">

import GlobalHeader from '@/components/GlobalHeader.vue'
</script>


<style scoped>
#basicLayout .header {
  padding-inline: 20px;
  background: white;
  color: unset;
  margin-bottom: 16px;
}

#basicLayout .content {
  padding: 20px;
  background: linear-gradient(to right, #fefefe, #fff);
  margin-bottom: 10px;
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
