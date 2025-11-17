<template>
  <!-- 图片管理界面 -->
  <div id="pictureManagePage">
    <!-- 搜索表单 -->
    <a-form layout="inline" :model="searchParams" @finish="doSearch">
      <a-form-item label="关键词">
        <a-input v-model:value="searchParams.searchText" placeholder="从名称和简介搜索" allow-clear />
      </a-form-item>
      <a-form-item label="类型">
        <a-input v-model:value="searchParams.category" placeholder="请输入类型" allow-clear />
      </a-form-item>
      <a-form-item label="标签">
        <a-select
          v-model:value="searchParams.tags"
          mode="tags"
          placeholder="请输入标签"
          style="min-width: 180px"
          allow-clear
        />
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
      :scroll="{ x: 'max-content' }"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.dataIndex === 'url'">
          <a-image :src="record.url" :width="60" />
        </template>
        <!-- 图片标签列 -->
        <template v-if="column.dataIndex === 'tags'">
          <a-space wrap>
            <a-tag v-for="tag in JSON.parse(record.tags || '[]')" :key="tag">
              {{ tag }}
            </a-tag>
          </a-space>
        </template>
        <!-- 图片信息列 -->
        <template v-if="column.dataIndex === 'picInfo'">
          <div>格式：{{ record.picFormat }}</div>
          <div>宽度：{{ record.picWidth }}</div>
          <div>高度：{{ record.picHeight }}</div>
          <div>宽高比：{{ record.picScale }}</div>
          <div>大小：{{ (record.picSize / 1024).toFixed(2) }}KB</div>
        </template>
        <!-- 根据图片权限加标签 -->
        <template v-else-if="column.dataIndex === 'pictureRole'">
          <div v-if="record.pictureRole === 'admin'">
            <a-tag color="green">管理员</a-tag>
          </div>
          <div v-else>
            <a-tag color="blue">普通图片</a-tag>
          </div>
        </template>
        <!-- 创建时间:给时间格式化，使用 dayjs 工具库即可，类似于后端的Hutool工具库 -->
        <template v-if="column.dataIndex === 'createTime'">
          {{ dayjs(record.createTime).format('YYY-MM-DD HH:mm:ss') }}
        </template>
        <!-- 编辑时间 -->
        <template v-if="column.dataIndex === 'editTime'">
          {{ dayjs(record.editTime).format('YYY-MM-DD HH:mm:ss') }}
        </template>

        <!-- 因为传入的数据有行列，删除图片的话按照图片id删除 -->
        <template v-else-if="column.key === 'action'">
          <a-button type="link" :href="`/add_picture?id=${record.id}`" target="_blank">编辑</a-button>
          <a-button danger @click="doDelete(record.id)">删除</a-button>
        </template>
      </template>
    </a-table>
  </div>
</template>

<script lang="ts" setup>
import { computed, onMounted, reactive, ref } from 'vue'
import {
  listPictureByPageUsingPost,
} from '@/api/pictureController.ts'
import { message } from 'ant-design-vue'
import dayjs from 'dayjs'
const columns = [
  {
    title: 'id',
    dataIndex: 'id',
    width: 80,
  },
  {
    title: '图片',
    dataIndex: 'url',
  },
  {
    title: '名称',
    dataIndex: 'name',
  },
  {
    title: '简介',
    dataIndex: 'introduction',
    ellipsis: true,
  },
  {
    title: '类型',
    dataIndex: 'category',
  },
  {
    title: '标签',
    dataIndex: 'tags',
  },
  {
    title: '图片信息',
    dataIndex: 'picInfo',
  },
  {
    title: '用户id',
    dataIndex: 'userId',
    width: 80,
  },
  {
    title: '创建时间',
    dataIndex: 'createTime',
  },
  {
    title: '编辑时间',
    dataIndex: 'editTime',
  },
  // 操作使用key的原因：操作不是从数据库中取数据，而是单独的自定义空间
  {
    title: '操作',
    key: 'action',
  },
]

// 定义数据：从后端获取,后端管理员获取图片列表的返回值为PictureVO,所以前端从API拿到PictureVO即可
const dataList = ref<API.Picture[]>([])
const total = ref(0)

/**
 * 搜索条件(因为需要分页):因为搜索条件会改变页面，即切页号，页面数据动态改变，因此变为响应式变量reactive
 * reactive: 适用于对 对象内的字段监控 的场景，只要对象内任意值改变，都要重新获取页面
 * ref: 适应于单个变量（字符串、整型等）或者数组，只在乎 后面传的这个值整体有没有改变
 */
const searchParams = reactive<API.PictureQueryRequest>({
  current: 1,
  pageSize: 10,
  sortField: 'createTime',
  sortOrder: 'descend',
})

// 分页参数：使用computed渲染函数包裹一层，就可以实现动态改变值
const pagination = computed(() => {
  return {
    current: searchParams.current,
    pageSize: searchParams.pageSize,
    total: total.value,
    showSizeChanger: true,
    showTotal: (total) => '共${total}条',
  }
})

// 表格变化后重新获取数据
const doTableChange = (page: any) => {
  searchParams.current = page.current
  searchParams.pageSize = page.pageSize
  fetchData()
}

// 获取数据
const fetchData = async () => {
  const res = await listPictureByPageUsingPost({
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
  fetchData()
})

// 搜索图片数据:直接从searchParams中调数据就行，因为searchParams拿到了全部的搜索条件
const doSearch = () => {
  // 每次搜索都要重置页码，每次都从第一页开始搜
  searchParams.current = 1
  fetchData()
}

// 删除图片: 接收参数id
const doDelete = async (id) => {
  if (!id) {
    return
  }
  const res = await deletePictureUsingPost({ id })
  if (res.data.code === 0) {
    message.success('删除成功')
    // 刷新数据
    await fetchData()
  } else {
    message.error('删除失败')
  }
}
</script>
