<!-- 在每个页面都会引入的初始化页面 APP.vue 引入通用布局 -->
<template>
  <div id="app">
    <BasicLayout/>
  </div>
</template>

<script setup lang="ts">
import BasicLayout from '@/layouts/BasicLayout.vue'
/*import { healthUsingGet } from '@/api/mainController.ts'
import { useLoginUserStore } from '@/stores/useLoginUserStore.ts'

  已经改为在权限校验文件access.ts中获取
const loginUserStore = useLoginUserStore()
loginUserStore.fetchLoginUser()

healthUsingGet().then((res) => {
  console.log(res)
})*/
</script>

<style scoped>

</style>
