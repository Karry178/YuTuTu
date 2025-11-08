<template>
  <!-- 用户登录界面开发 -->
  <div id="userLoginPage">
    <h2 class="title">鱼图云图库 - 用户登录</h2>
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
      <div class="tips">
        没有账号？
        <RouterLink to="/user/register">去注册</RouterLink>
      </div>
      <a-form-item>
        <a-button type="primary" html-type="submit" style="width: 100%">登录</a-button>
      </a-form-item>
    </a-form>
  </div>
</template>

<script lang="ts" setup>
import { reactive } from 'vue'
import { userLoginUsingPost } from '@/api/userController.ts'
import { useLoginUserStore } from '@/stores/useLoginUserStore.ts'
import { message } from 'ant-design-vue'
import router from '@/router'

/* formState 是用户表单，在此处可以写用户登录 */
const formState = reactive<API.UserLoginRequest>({
  userAccount: '',
  userPassword: '',
})

const loginUserStore = useLoginUserStore()

// 点击登录按钮后，触发后端接口，把用户的输入保存在后端
const handleSubmit = async(values: any) => {
  try {
    const res = await userLoginUsingPost(values)
    // 登录成功，把登录态保存到全局状态中
    if (res.data.code === 0 && res.data.data) {
      await loginUserStore.fetchLoginUser();
      message.success("登录成功");
      // 登录成功后，跳转到主页,
      // todo 加入replace:true目的是覆盖掉登录页，后退的话直接返回主页，可以在登录页面加入redirect=xxx，在登录成功后，取出这个xxx值；点后退页面后，重定向到之前管理页面
      router.push({
        path:"/",
        replace: true,
      })
    } else {
      message.error("登录失败" + res.data.message);
    }
  } catch (e) {
    message.error("登录失败" + e.message);
  }
}
</script>

<style scoped>
/*style是CSS格式*/
#userLoginPage {
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
