import router from '@/router'
import { useLoginUserStore } from '@/stores/useLoginUserStore.ts'
import { message } from 'ant-design-vue'

// 定义一个变量，判断是否为首次获取
let firstFetchLoginUser = true

/* 全局权限校验页面，每次切换页面都会执行 */
router.beforeEach(async (to, from, next) => {
  // 1.先获取当前登录用户管理器
  const loginUserStore = useLoginUserStore()
  let loginUser = loginUserStore.loginUser
  // 2.确保页面刷新时/首次加载时，能等待后端返回用户信息后再校验权限
  if (firstFetchLoginUser) {
    await loginUserStore.fetchLoginUser()
    loginUser = loginUserStore.loginUser
    firstFetchLoginUser = false
  }

  const toUrl = to.fullPath
  // 现在开始，可以自定义权限校验规则了，比如:只有管理员才可以访问/admin开头的页面
  if (toUrl.startsWith('/admin')) {
    if (!loginUser || loginUser.userRole !== 'admin') {
      message.error('没有权限')
      next(`/user/login?redirect=${to.fullPath}`)
      return
    }
  }
  // 否则，直接放行
  next()
})
