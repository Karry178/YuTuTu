<template>
  <!-- GlobalHeader是全局顶部栏信息 -->
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

      <!-- 用户信息展示栏 -->
      <a-col flex="120px">
        <div class="user-login-status">
          <div>
            <div v-if="loginUserStore.loginUser.id">
              <!-- 用户头像组件：dropdown组件,只能放插槽 -->
              <a-dropdown>
                <!-- 把头像、用户名放入a-space组件，避免被dropdown组件吞掉 -->
                <a-space class="user-info">
                  <a-avatar :src="loginUserStore.loginUser.userAvatar" />
                  <span class="user-name">
                    {{ loginUserStore.loginUser.userName ?? '无名之辈' }}
                  </span>
                </a-space>
                <!-- 头像的下拉菜单 -->
                <template #overlay>
                  <a-menu>
                    <a-menu-item >
                      <router-link to="/my_space">
                        <UserOutlined />
                        我的空间
                      </router-link>
                    </a-menu-item>
                    <a-menu-item @click="doLogout">
                      <LogoutOutlined />
                      退出登录
                    </a-menu-item>
                  </a-menu>
                </template>
              </a-dropdown>
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
import { computed, h, ref } from 'vue'
import { HomeOutlined, LogoutOutlined, UserOutlined } from '@ant-design/icons-vue' // 引入各种图标
import { MenuProps, message } from 'ant-design-vue'
import { useRouter } from 'vue-router'
import { useLoginUserStore } from '@/stores/useLoginUserStore.ts'
import { userLogoutUsingPost } from '@/api/userController.ts'

const loginUserStore = useLoginUserStore()

// 未经过滤的菜单项
const originItems = [
  {
    key: '/',
    icon: () => h(HomeOutlined),
    label: '主页',
    title: '主页',
  },
  {
    key: '/add_picture',
    label: '创建图片',
    title: '创建图片',
  },
  {
    key: '/admin/userManage',
    label: '用户管理',
    title: '用户管理',
  },
  {
    key: '/admin/pictureManage',
    label: '图片管理',
    title: '图片管理',
  },
  {
    key: '/admin/spaceManage',
    label: '空间管理',
    title: '空间管理',
  },
  {
    key: 'others',
    label: h('a', { href: '', target: '_blank' }, ''),
    title: '',
  },
]

// 展示在菜单的路由数组
const items = computed<MenuProps['items']>(() => filterMenus(originItems))

// 根据权限过滤菜单项
// 1.先定义一个空数组filterMenus,定义默认值要用as
const filterMenus = (menus = [] as MenuProps['items']) => {
  return menus?.filter((menu) => {
    // 2.如果默认值开头以admin开头
    if (menu?.key?.startsWith('/admin')) {
      const loginUser = loginUserStore.loginUser
      // 3.无权限
      if (!loginUser || loginUser.userRole !== 'admin') {
        return false
      }
    }
    return true
  })
}

const router = useRouter()
// 当前要高亮的菜单项
const current = ref<string[]>([]) // 进入页面时，current默认为空值，但是钩子afterEach会改变current的值,把当前要前往的路径传到current，而current可以保证高亮哪些路径
// router路由支持钩子语法 -> 监听路由变化，更新高亮菜单项
router.afterEach((to, from, next) => {
  current.value = [to.path]
})

// 路由跳转事件
const doMenuClick = ({ key }) => {
  router.push({
    path: key,
  })
}

// 注销操作
const doLogout = async () => {
  const res = await userLogoutUsingPost()
  if (res.data.code === 0) {
    // 退出用户就是把登录用户重置为 未登录
    loginUserStore.setLoginUser({
      userName: '未登录',
    })
    message.success('退出登录成功')
    // 跳转到登录页面
    router.push({
      path: '/user/login',
    })
  } else {
    message.error('退出登录失败' + res.data.message)
  }
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

.user-info {
  display: inline-flex;
  align-items: center;
  gap: 8px; /*头像和用户名之间的间隔*/
}

.user-name {
  white-space: nowrap; /*不自动换行*/
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
}
</style>
