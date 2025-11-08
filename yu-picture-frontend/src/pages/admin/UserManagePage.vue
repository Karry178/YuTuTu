<template>
  <!-- 用户管理界面 -->
  <div id="userManagePage">
    <!-- 搜索表单 -->
    <a-form layout="inline" :model="searchParams" @finish="doSearch">
      <a-form-item label="账号">
        <a-input v-model:value="searchParams.userAccount" placeholder="输入账号" allow-clear />
      </a-form-item>
      <a-form-item label="用户名">
        <a-input v-model:value="searchParams.userName" placeholder="输入用户名" allow-clear />
      </a-form-item>
      <a-form-item>
        <a-button type="primary" html-type="submit">搜索</a-button>
      </a-form-item>
    </a-form>
    <div style="margin-bottom: 16px" />

    <!-- 表格 -->
    <a-table
      :columns="columns"
      :data-source="dataList"
      :pagination="pagination"
      @change="doTableChange"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.dataIndex === 'userAvatar'">
          <a-image :src="record.userAvatar" :width="60" />
        </template>
        <!-- 根据用户权限加标签 -->
        <template v-else-if="column.dataIndex === 'userRole'">
          <div v-if="record.userRole === 'admin'">
            <a-tag color="green">管理员</a-tag>
          </div>
          <div v-else>
            <a-tag color="blue">普通用户</a-tag>
          </div>
        </template>
        <!-- 创建时间:给时间格式化，使用 dayjs 工具库即可，类似于后端的Hutool工具库 -->
        <template v-if="column.dataIndex === 'createTime'">
          {{ dayjs(record.createTime).format('YYY-MM-DD HH:mm:ss') }}
        </template>

        <!-- 因为传入的数据有行列，删除用户的话按照图片id删除 -->
        <template v-else-if="column.key === 'action'">
          <a-button danger @click="doDelete(record.id)">删除</a-button>
        </template>
      </template>
    </a-table>
  </div>
</template>

<script lang="ts" setup>
import { SmileOutlined, DownOutlined } from '@ant-design/icons-vue'
import { computed, onMounted, reactive, ref } from 'vue'
import { deleteUserUsingPost, listUserVoByPageUsingPost } from '@/api/userController.ts'
import { message } from 'ant-design-vue'
import dayjs from 'dayjs'
const columns = [
  {
    title: 'id',
    dataIndex: 'id',
  },
  {
    title: '账号',
    dataIndex: 'userAccount',
  },
  {
    title: '用户名',
    dataIndex: 'userName',
  },
  {
    title: '头像',
    dataIndex: 'userAvatar',
  },
  {
    title: '简介',
    dataIndex: 'userProfile',
  },
  {
    title: '用户角色',
    dataIndex: 'userRole',
  },
  {
    title: '创建时间',
    dataIndex: 'createTime',
  },
  // 操作使用key的原因：操作不是从数据库中取数据，而是单独的自定义空间
  {
    title: '操作',
    key: 'action',
  },
]

// 定义数据：从后端获取,后端管理员获取用户列表的返回值为UserVO,所以前端从API拿到UserVO即可
const dataList = ref<API.UserVO[]>([])
const total = ref(0)

/**
 * 搜索条件(因为需要分页):因为搜索条件会改变页面，即切页号，页面数据动态改变，因此变为响应式变量reactive
 * reactive: 适用于对 对象内的字段监控 的场景，只要对象内任意值改变，都要重新获取页面
 * ref: 适应于单个变量（字符串、整型等）或者数组，只在乎 后面传的这个值整体有没有改变
 */
const searchParams = reactive<API.UserQueryRequest>({
  current: 1,
  pageSize: 10,
  sortField: "createTime",
  sortOrder: "ascend"
})

// 分页参数：使用computed渲染函数包裹一层，就可以实现动态改变值
const pagination = computed(() => {
  return {
    current: searchParams.current,
    pageSize: searchParams.pageSize,
    total: total.value,
    showSizeChanger: true,
    showTotal: (total) => '共${total}条'
  }
})

// 表格变化后重新获取数据
const doTableChange = (page: any) => {
  searchParams.current = page.current
  searchParams.pageSize = page.pageSize
  fetchData();
}

// 获取数据
const fetchData = async () => {
  const res = await listUserVoByPageUsingPost({
    ...searchParams,
  })
  // 如果res的数据存在且res.data.code === 0，获取records，如果records不存在获取空数组
  if (res.data.code === 0 && res.data.data) {
    dataList.value = res.data.data.records ?? []
    total.value = res.data.data.total ?? 0
  } else {
    message.error('获取消息失败' + res.data.message)
  }
}

// 页面加载时获取数据，请求一次
onMounted(() => {
  fetchData();
})

// 搜索用户数据:直接从searchParams中调数据就行，因为searchParams拿到了全部的搜索条件
const doSearch = () => {
  // 每次搜索都要重置页码，每次都从第一页开始搜
  searchParams.current = 1
  fetchData();
}

// 删除用户: 接收参数id
const doDelete = async (id) => {
  if (!id) {
    return
  }
  const res = await deleteUserUsingPost({ id })
  if (res.data.code === 0) {
    message.success('删除成功')
    // 刷新数据
    await fetchData()
  } else {
    message.error('删除失败')
  }
}
</script>
