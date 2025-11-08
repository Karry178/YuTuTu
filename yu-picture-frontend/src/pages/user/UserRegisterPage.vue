<template>
  <!-- 用户注册界面开发 -->
  <div id="userRegisterPage">
    <h2 class="title">鱼图云图库 - 用户注册</h2>
    <div class="desc">企业级智能协同云图库</div>
    <!-- 表单代码： formState表示用户的输入表单到底要填充到哪个变量中 -->
    <a-form :model="formState" name="basic" autocomplete="off" @finish="handleSubmit">
      <!-- a-form-item 是表单项 -->
      <a-form-item name="userAccount" :rules="[{ required: true, message: '请输入账号!' }]">
        <!-- 指明对接的接口是userAccount,且placeholder是表框中的浅色提示 -->
        <a-input v-model:value="formState.userAccount" placeholder="请输入账号" />
      </a-form-item>

      <a-form-item
        name="userPassword"
        :rules="[
          { required: true, message: '请输入密码!' },
          { min: 8, message: '密码长度不能小于8位' },
        ]"
      >
        <a-input-password v-model:value="formState.userPassword" placeholder="请输入密码" />
      </a-form-item>

      <a-form-item
        name="checkPassword"
        :rules="[
          { required: true, message: '请重新输入密码!' },
          { min: 8, message: '确认密码长度不能小于8位' },
        ]"
      >
        <a-input-password v-model:value="formState.checkPassword" placeholder="请输入确认密码" />
      </a-form-item>
      <div class="tips">
        已有账号？
        <RouterLink to="/user/login">去登录</RouterLink>
      </div>
      <a-form-item>
        <a-button type="primary" html-type="submit" style="width: 100%">注册</a-button>
      </a-form-item>
    </a-form>
  </div>
</template>

<script lang="ts" setup>
import { reactive } from 'vue'
import { userLoginUsingPost, userRegisterUsingPost } from '@/api/userController.ts'
import { useLoginUserStore } from '@/stores/useLoginUserStore.ts'
import { message } from 'ant-design-vue'
import router from '@/router'

/* formState 是用户表单，在此处可以写用户注册 */
const formState = reactive<API.UserRegisterRequest>({
  userAccount: '',
  userPassword: '',
  checkPassword: '',
})

const loginUserStore = useLoginUserStore()

// 点击注册按钮后，触发后端接口，把用户的输入保存在后端
const handleSubmit = async (values: any) => {
  try {
    const res = await userRegisterUsingPost(values)
    // 注册成功，界面跳转到登录页
    if (res.data.code === 0 && res.data.data) {
      message.success('注册成功')
      // 注册成功后，跳转到主页,
      // todo 加入replace:true目的是覆盖掉注册页，后退的话直接返回主页，可以在注册页面加入redirect=xxx，在注册成功后，取出这个xxx值；点后退页面后，重定向到之前管理页面
      router.push({
        path: '/user/login',
        replace: true,
      })
    } else {
      message.error('注册失败' + res.data.message)
    }
  } catch (e) {
    message.error('注册失败' + e.message)
  }
}
</script>

<style scoped>
/*style是CSS格式*/
#userRegisterPage {
  max-width: 360px;
  margin: 0 auto;
}

.title {
  text-align: center;
  margin-bottom: 16px;
}

.desc {
  text-align: center;
  color: #bbb;
  margin-bottom: 16px;
}

.tips {
  color: #bbb;
  text-align: right;
  font-size: 13px;
  margin-bottom: 16px;
}
</style>
