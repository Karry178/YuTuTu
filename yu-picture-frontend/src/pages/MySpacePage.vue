<template>
  <div id="mySpacePage">
    <p>正在跳转，请稍后......</p>
  </div>
</template>

<script setup lang="ts">
/*
* 先梳理业务流程，跳转到该界面时：
*   用户未登录，则直接跳转到登录页面
*   用户若已登录，会获取该用户已创建的空间
*     如果有，则进入第一个空间
*     如果没有，则跳转到创建控件界面
* */

import { useRouter } from 'vue-router'
import { useLoginUserStore } from '@/stores/useLoginUserStore.ts'
import { listSpaceVoByPageUsingPost } from '@/api/spaceController.ts'
import { message } from 'ant-design-vue'
import { onMounted } from 'vue'
import { SPACE_TYPE_ENUM } from '@/constants/space.ts'

const router = useRouter();
const loginUserStore = useLoginUserStore();

const checkUserSpace = async() => {
  // 用户未登录，则直接跳转到登录界面
  const loginUser = loginUserStore.loginUser;
  if (!loginUser?.id) {
    router.replace("/user/login");
    return
  }
  // 如果用户已登录，会获取该用户已创建的空间
  const res = await listSpaceVoByPageUsingPost({
    userId: loginUser.id,
    current: 1,
    pageSize: 1,
    spaceType: SPACE_TYPE_ENUM.PRIVATE,
  })
  // 再判断有无拿到数据
  // 没有数据，报错
  if (res.data?.code !== 0) {
    message.error("加载我的空间失败", + res.data.message)
    return;
  }

  const records = res.data?.data?.records ?? [];
  // 如果有，则进入第一个空间
  // 因为是分页，还要再获取records，并判断其长度
  if (records.length > 0 && records[0]) {
    router.replace(`/space/${records[0].id}`)
  } else {
    // 如果没有，则跳转到创建控件页面
    router.replace("/add_space")
    message.warn("请先创建空间")
  }

  /*if (res.data.code === 0) {
    // 如果有，则进入第一个空间
    // 因为是分页，还要再获取records，并判断其长度
    if (res.data.data?.records?.length > 0) {
      const space = res.data.data.records[0]
      router.replace(`/space/${space.id}`)
    } else {
      // 如果没有，则跳转到创建控件页面
      router.replace("/add_space")
      message.warn("请先创建空间")
    }
  } else {
  }*/
}

// 在页面加载的时候，检查用户空间
onMounted(async() => {
  await checkUserSpace();
})
</script>
