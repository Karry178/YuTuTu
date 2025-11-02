import { ref, computed } from 'vue'
import { defineStore } from 'pinia'

/**
 * 存储登录用户信息的状态(store)
 */
export const useLoginUserStore = defineStore('loginUser', () => {
  const loginUser = ref<any>({
    userName: '未登录',
  })

  async function fetchLoginUser(){
    // todo 后端还未提供接口，暂时注释
    /*const res = await getCurrentUser();
    if (res.data.code === 0 && res.data.data) {
      loginUser.value = res.data.data;
    }*/

    // 测试用户登录，3s后自动登录
    setTimeout(() => {
      loginUser.value = {
        userName : '测试用户', id: 1
      }
    }, 3000)
  }

  /**
   * 设置登录用户信息
   * @param newLoginUser
   */
  function setLoginUser(newLoginUser: any) {
    loginUser.value = newLoginUser
  }

  // 返回状态和操作方法
  return { loginUser, fetchLoginUser, setLoginUser }
})
