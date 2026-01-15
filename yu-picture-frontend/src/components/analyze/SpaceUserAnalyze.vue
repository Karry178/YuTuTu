<template>
  <div class="space-user-analyze">
    <a-card title="空间图片用户上传分析">
      <v-chart :option="options" style="height: 320px; max-width: 100%" :loading="loading" />
      <!-- 在图表右侧补充内容 -->
      <template #extra>
        <a-segmented v-model:value="timeDimension" :options="timeDimensionOptions" />
        <a-input-search placeholder="请输入用户Id：" enter-button="搜索用户" :options="doSearch" />
      </template>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import VChart from 'vue-echarts'
import 'echarts'
import { computed, ref, watchEffect } from 'vue'
import { message } from 'ant-design-vue'
import { getSpaceUserAnalyzeUsingPost } from '@/api/spaceAnalyzeController.ts'

interface Props {
  // 可以直接调用spaceAnalyzeRequest的API，也可以如下全展示出来
  queryAll?: boolean
  queryPublic?: boolean
  spaceId?: string | number
  space?: API.SpaceVO
}

// 定义一些默认值
const props = withDefaults(defineProps<Props>(), {
  queryAll: false,
  queryPublic: false,
})

// 定义搜索的时间维度选项
const timeDimension = ref<'day' | 'week' | 'month'>('day')
// 分段选择器组件的选项
const timeDimensionOptions = [
  {
    label: '日',
    value: 'day',
  },
  {
    label: '周',
    value: 'week',
  },
  {
    label: '月',
    value: 'month',
  },
]

// 定义用户 Id 选项
const userId = ref<string>()

const doSearch = (value: string) => {
  userId.value = value
}

// 图表数据
const dataList = ref<API.SpaceUserAnalyzeResponse[]>([])

// 加载状态
const loading = ref(true)

// 获取数据
const fetchData = async () => {
  loading.value = true
  // 转换搜索参数
  const res = await getSpaceUserAnalyzeUsingPost({
    queryAll: props.queryAll,
    queryPublic: props.queryPublic,
    spaceId: props.spaceId,
    timeDimension: timeDimension.value,
    userId: userId.value,
  })
  // 如果res的数据存在且res.data.code === 0，获取records，如果records不存在获取空数组
  if (res.data.code === 0 && res.data.data) {
    dataList.value = (res.data.data ?? []) as any
  } else {
    message.error('获取消息失败' + res.data.message)
  }
  loading.value = false
}

/**
 * 监听变量，参数改变时触发数据的重新加载
 * 因此要使用watchEffect的钩子函数，而不是onMounted
 */
watchEffect(() => {
  fetchData()
})

// 图表选项 使用computed()可以动态的计算变量变化，保证图表始终获得最新的值
const options = computed(() => {
  const periods = dataList.value.map((item) => item.period) // 时间区间
  const counts = dataList.value.map((item) => item.count) // 上传数量

  return {
    tooltip: { trigger: 'axis' },
    xAxis: { type: 'category', data: periods, name: '时间区间' },
    yAxis: { type: 'value', name: '上传数量' },
    series: [
      {
        name: '上传数量',
        type: 'line',
        data: counts,
        smooth: true, // 平滑折线
        emphasis: {
          focus: 'series',
        },
      },
    ],
  }
})
</script>

<style scoped></style>
