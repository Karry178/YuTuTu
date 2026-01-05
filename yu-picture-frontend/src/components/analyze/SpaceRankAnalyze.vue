<template>
  <div class="space-rank-analyze">
    <a-card title="空间使用排行分析">
      <v-chart :option="options" style="height: 320px; max-width: 100%;" :loading="loading" />
    </a-card>
  </div>
</template>

<script setup lang="ts">
import VChart from 'vue-echarts'
import 'echarts'
import { computed, ref, watchEffect } from 'vue'
import { message } from 'ant-design-vue'
import { getSpaceRankAnalyzeUsingPost } from '@/api/spaceAnalyzeController.ts'

interface Props {
  // 可以直接调用spaceAnalyzeRequest的API，也可以如下全展示出来
  queryAll?: boolean
  queryPublic?: boolean
  spaceId?: string | number
}

// 定义一些默认值
const props = withDefaults(defineProps<Props>(), {
  queryAll: false,
  queryPublic: false,
})

// 图表数据
const dataList = ref<API.Space[]>([])

// 加载状态
const loading = ref(true)

// 获取数据
const fetchData = async () => {
  loading.value = true
  // 转换搜索参数
  const res = await getSpaceRankAnalyzeUsingPost({
    queryAll: props.queryAll,
    queryPublic: props.queryPublic,
    spaceId: props.spaceId,
    topN: 10, // 后端默认是10
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
  const spaceNames = dataList.value.map((item) => item.spaceName)
  const usageData = dataList.value.map((item) => (item.totalSize / (1024 * 1024)).toFixed(2)) // 转为 MB

  return {
    tooltip: { trigger: 'axis' },
    xAxis: {
      type: 'category',
      data: spaceNames,
    },
    yAxis: {
      type: 'value',
      name: '空间使用量 (MB)',
    },
    series: [
      {
        name: '空间使用量 (MB)',
        type: 'bar',
        data: usageData,
        itemStyle: {
          color: '#5470C6', // 自定义柱状图颜色
        },
      },
    ],
  }
})
</script>

<style scoped></style>
