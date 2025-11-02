<template>
  <div id="globalHeader">
    <!-- Grid布局 -> Flex填充，作用：对页面菜单固定左右，自动适配中间大小 -->
    <a-row :wrap="false">
      <a-col flex="200px">
        <!-- router-link 表示跳转页面，本步是点击logo后跳转到主页 -->
        <router-link to="/">
          <div class="title-bar">
            <img class="logo" src="../assets/favicon.ico" alt="logo" />
            <div class="title">鱼图云图库</div>
          </div>
        </router-link>
      </a-col>

      <a-col flex="auto">
        <a-menu
          v-model:selectedKeys="current"
          mode="horizontal"
          :items="items"
          @click="doMenuClick"
        />
      </a-col>

      <a-col flex="120px">
        <div class="user-login-status">
          <div>
            <div v-if="loginUserStore.loginUser.id">
              {{ loginUserStore.loginUser.userName ?? '无名' }}
            </div>
            <div v-else>
              <a-button type="primary" href="/user/login">登录</a-button>
            </div>
          </div>
        </div>
      </a-col>
    </a-row>
  </div>
</template>


<script lang="ts" setup>
import { h, ref } from 'vue';
import { HomeOutlined } from '@ant-design/icons-vue';
import { MenuProps } from 'ant-design-vue';
import { useRouter } from 'vue-router'
import { useLoginUserStore } from '@/stores/useLoginUserStore.ts'

const loginUserStore = useLoginUserStore()

const items = ref<MenuProps['items']>([
  {
    key: '/',
    icon: () => h(HomeOutlined),
    label: '主页',
    title: '主页',
  },
  {
    key: 'about',
    label: '关于',
    title: '关于',
  },
  {
    key: 'others',
    label: h('a', { href: '', target: '_blank' }, ''),
    title: ''
  },
]);

const router = useRouter();
// 当前要高亮的菜单项
const current = ref<string[]>([]); // 进入页面时，current默认为空值，但是钩子afterEach会改变current的值,把当前要前往的路径传到current，而current可以保证高亮哪些路径
// router路由支持钩子语法 -> 监听路由变化，更新高亮菜单项
router.afterEach((to, from, next) => {
  current.value = [to.path]
})

// 路由跳转事件
const doMenuClick = ({ key }) => {
  router.push({
    path: key
  })
}

</script>

<style scoped>
#globalHeader .title-bar {
  display: flex;
  align-items: center;
}

.title {
  color: black;
  font-size: 20px;
  margin-left: 16px;
}

.logo {
  height: 48px;
}
</style>

