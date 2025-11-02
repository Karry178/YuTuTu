/*全局请求文件 -> 看Axios文档：https://axios-http.com/zh/docs/intro*/
import axios from 'axios'
import { message } from 'ant-design-vue'

// 1.创建Axios实例
const myAxios = axios.create({
  baseURL: 'http://localhost:8123', // 设置请求的baseURL
  timeout: 60000, // 请求超时时间：60秒 -> 6万ms
  withCredentials: true, // 允许携带cookie
});

// 2.添加请求拦截器
myAxios.interceptors.request.use(function (config) {
  // 在发送请求之前做些什么
  return config;
}, function (error) {
  // 对请求错误做些什么
  return Promise.reject(error);
});

// 3.添加响应拦截器 -> 全局响应拦截器
myAxios.interceptors.response.use(
    function (response) {
      const { data } = response
      // 未登录
      if (data.code === 40100) {
        // 不是获取用户信息的请求，并且用户目前不是已经在用户登录界面，则跳转到登录界面
        if (
          !response.request.responseURL.includes('user/get/login') &&
          !window.location.pathname.includes('/user/login')
        ) {
          message.warning('请先登录')
          window.location.href = `/user/login?redirect=${window.location.href}`
        }
      }
      return response
  },
  function (error) {
    // 对请求错误做些什么
    return Promise.reject(error);
  });


export default myAxios;

