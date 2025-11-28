<template>
  <div id="spaceDetailPage">
    <!-- 展示空间基本信息 -->
    <a-flex justify="space-between" align="middle" >
      <h2>{{ space.spaceName }} {私有空间}</h2>
      <a-space size="middle">
        <!-- 前面有 :表示Vue在做属性绑定 -->
        <a-button type="primary" :href="`/add_picture?spaceId=${id}`" target="_blank">+ 创建图片</a-button>
        <a-tooltip :title="`占用空间 ${formatSize(space.totalSize)} / ${formatSize(space.maxSize)}`">
          <a-progress
            type="circle"
            :size="40"
            :percent="space.maxSize ? Number(((space.totalSize || 0) * 100 / space.maxSize).toFixed(1)) : 0"
          />
        </a-tooltip>
      </a-space>
    </a-flex>
    <div style="margin-bottom: 16px" />
    <!-- 图片列表,直接调用,加上showOp可以只在空间详情页显示编辑等按钮 -->
    <PictureList :dataList="dataList" :loading="loading" :showOp="true" :onReload="fetchData" />
    <!-- 分页 -->
    <a-pagination
      style="text-align: right"
      v-model:current="searchParams.current"
      v-model:pageSize="searchParams.pageSize"
      :total="total"
      @change="onPageChange"
    />
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import { getSpaceVoByIdUsingGet } from '@/api/spaceController.ts'
import { listPictureVoByPageUsingPost } from '@/api/pictureController.ts'
import { formatSize } from '@/utils'
import PictureList from '@/components/PictureList.vue'

interface Props {
  id: string | number
}

const props = defineProps<Props>()
const space = ref<API.SpaceVO>({})

// ======================= 获取空间详情 =======================
const fetchSpaceDetail = async () => {
  try {
    const res = await getSpaceVoByIdUsingGet({
      id: props.id,
    })
    if (res.data.code === 0 && res.data.data) {
      space.value = res.data.data
    } else {
      message.error('获取空间详情失败' + res.data.message)
    }
  } catch (e: any) {
    message.error('获取空间详情失败' + e.message)
  }
}

// 首次加载页面时，获取空间详情
onMounted(() => {
  fetchSpaceDetail()
})

// ======================= 获取图片列表 =======================
// 定义数据：从后端获取,后端管理员获取图片列表的返回值为PictureVO,所以前端从API拿到PictureVO即可
const dataList = ref<API.PictureVO[]>([])
const total = ref(0)
const loading = ref(true)

/**
 * 搜索条件(因为需要分页):因为搜索条件会改变页面，即切页号，页面数据动态改变，因此变为响应式变量reactive
 * reactive: 适用于对 对象内的字段监控 的场景，只要对象内任意值改变，都要重新获取页面
 * ref: 适应于单个变量（字符串、整型等）或者数组，只在乎 后面传的这个值整体有没有改变
 */
const searchParams = reactive<API.PictureQueryRequest>({
  current: 1,
  pageSize: 12,
  sortField: 'createTime',
  sortOrder: 'descend',
})

// 获取数据
const fetchData = async () => {
  loading.value = true
  // 转换搜索参数
  const params = {
    spaceId: props.id,
    ...searchParams,
  }

  const res = await listPictureVoByPageUsingPost(params)
  // 如果res的数据存在且res.data.code === 0，获取records，如果records不存在获取空数组
  if (res.data.code === 0 && res.data.data) {
    dataList.value = res.data.data.records ?? []
    total.value = res.data.data.total ?? 0
  } else {
    message.error('获取消息失败' + res.data.message)
  }
  loading.value = false
}

// 页面加载时获取数据，请求一次
onMounted(() => {
  fetchData()
})

// 分页参数
const onPageChange = (page: number, pageSize: number) => {
  searchParams.current = page
  searchParams.pageSize = pageSize
  fetchData()
}
</script>

<style scoped>
#spaceDetailPage {
  margin-bottom: 16px;
}
</style>
