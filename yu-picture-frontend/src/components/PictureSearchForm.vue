<template>
  <!-- 图片管理界面 -->
  <div id="pictureSearchForm" class="picture-search-form">
    <!-- 搜索表单 -->
    {{ searchParams }}
    <a-form name="searchForm" layout="inline" :model="searchParams" @finish="doSearch">
      <a-form-item label="关键词">
        <a-input
          v-model:value="searchParams.searchText"
          placeholder="从名称和简介搜索"
          allow-clear
        />
      </a-form-item>
      <a-form-item label="日期">
        <a-range-picker
          style="width: 400px"
          show-time
          format="YYYY/MM/DD HH:mm:ss"
          v-model:value="dateRange"
          :placeholder="['编辑开始时间', '编辑结束时间']"
          :presets="rangePresets"
          @change="onRangeChange"
        />
      </a-form-item>
      <a-form-item name="category" label="分类">
        <a-auto-complete
          v-model:value="searchParams.category"
          style="min-width: 180px"
          :options="categoryOptions"
          placeholder="请输入分类"
          allow-clear
        />
      </a-form-item>
      <a-form-item name="tags" label="标签">
        <a-select
          v-model:value="searchParams.tags"
          style="min-width: 180px"
          mode="tags"
          :options="tagOptions"
          placeholder="请输入标签，支持直接输入新标签"
          allow-clear
        />
      </a-form-item>
      <a-form-item label="名称" name="name">
        <a-input v-model:value="searchParams.name" placeholder="请输入名称" allow-clear />
      </a-form-item>
      <a-form-item label="简介" name="introduction">
        <a-input v-model:value="searchParams.introduction" placeholder="请输入简介" allow-clear />
      </a-form-item>
      <a-form-item label="宽度" name="picWidth">
        <a-input-number v-model:value="searchParams.picWidth" allow-clear />
      </a-form-item>
      <a-form-item label="高度" name="picHeight">
        <a-input-number v-model:value="searchParams.picHeight" allow-clear />
      </a-form-item>
      <a-form-item label="格式" name="picFormat">
        <a-input
          v-model:value="searchParams.introduction"
          placeholder="请输入图片格式"
          allow-clear
        />
      </a-form-item>
      <a-form-item>
        <a-space>
          <a-button type="primary" html-type="submit" style="width: 96px">搜索</a-button>
          <a-button html-type="reset" @click="doClear">重置</a-button>
        </a-space>
      </a-form-item>
    </a-form>
  </div>
</template>

<script lang="ts" setup>
import dayjs from 'dayjs'
import { reactive, ref, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { listPictureTagCategoryUsingGet } from '@/api/pictureController'

// 定义组件的属性
interface Props {
  onSearch?: (searchParams: API.PictureQueryRequest) => void
}

const props = defineProps<Props>()

/**
 * 搜索条件(因为需要分页):因为搜索条件会改变页面，即切页号，页面数据动态改变，因此变为响应式变量reactive
 * reactive: 适用于对 对象内的字段监控 的场景，只要对象内任意值改变，都要重新获取页面
 * ref: 适应于单个变量（字符串、整型等）或者数组，只在乎 后面传的这个值整体有没有改变
 */
const searchParams = reactive<API.PictureQueryRequest>({})

// 搜索图片数据:直接从searchParams中调数据就行，因为searchParams拿到了全部的搜索条件
const doSearch = () => {
  props.onSearch?.(searchParams)
}

// 定义俩选项：分类选项与标签选项，默认为空数组
const categoryOptions = ref<Array<{ value: string; label: string }>>([])
const tagOptions = ref<Array<{ value: string; label: string }>>([])

/**
 * 获取标签和分类选项
 */
const getTagCategoryOptions = async () => {
  const res = await listPictureTagCategoryUsingGet()
  // 操作成功
  if (res.data.code === 0 && res.data.data) {
    tagOptions.value = (res.data.data.tagList ?? []).map((data: string) => {
      return {
        value: data,
        label: data,
      }
    })
    categoryOptions.value = (res.data.data.categoryList ?? []).map((data: string) => {
      return {
        value: data,
        label: data,
      }
    })
  } else {
    message.error('获取标签和分类失败' + res.data.message)
  }
}

onMounted(() => {
  getTagCategoryOptions()
})

const dateRange = ref<[]>([])

/**
 * 日期范围更改时触发
 * @param dates
 * @param dateStrings
 */
const onRangeChange = (dates: any[], dateStrings: string[]) => {
  if (dates?.length >= 2) {
    searchParams.startEditTime = dates[0].toDate()
    searchParams.endEditTime = dates[1].toDate()
  } else {
    searchParams.startEditTime = undefined
    searchParams.endEditTime = undefined
  }
}

// 时间范围预设
const rangePresets = ref([
  { label: '过去 7天', value: [dayjs().add(-7, 'd'), dayjs()] },
  { label: '过去 14天', value: [dayjs().add(-14, 'd'), dayjs()] },
  { label: '过去 30天', value: [dayjs().add(-30, 'd'), dayjs()] },
  { label: '过去 90天', value: [dayjs().add(-90, 'd'), dayjs()] },
])

//  清理选项
const doClear = () => {
  // 取消所有对象的值
  Object.keys(searchParams).forEach((key) => {
    searchParams[key] = undefined
  })
  // 日期筛选项单独清空，必须定义为空数组
  dateRange.value = []
  // 清空后重新搜索
  props.onSearch?.(searchParams)
}
</script>

<style scoped>
.picture-search-form .ant-form-item {
  margin-top: 16px;
}
</style>
