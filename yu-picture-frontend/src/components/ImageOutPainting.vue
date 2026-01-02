<template>
  <a-modal
    class="image-out-painting"
    v-model:open="visible"
    title="AI扩图"
    :width="900"
    :footer="false"
    @cancel="closeModal"
  >
    <!-- AI扩图左右两列展示 -->
    <a-row gutter="16">
      <a-col span="12">
        <h4>原始图片</h4>
        <img :src="picture?.url" alt="picture?.name" style="max-width: 100%" />
      </a-col>
      <a-col span="12">
        <h4>AI扩图结果</h4>
        <img
          v-if="resultImageUrl"
          :src="resultImageUrl"
          alt="picture?.name"
          style="max-width: 100%"
        />
      </a-col>
    </a-row>
    <div style="margin-bottom: 16px" />
    <a-flex justify="center" gap="16">
      <a-button type="primary" ghost @click="createTask">生成图片</a-button>
      <a-button type="primary" @click="handleUpload">应用结果</a-button>
    </a-flex>
  </a-modal>
</template>

<script lang="ts" setup>
import { nextTick, onUnmounted, ref, watch } from 'vue'
import {
  createPictureOutPaintingTaskUsingPost,
  getPictureOutPaintingTaskUsingGet,
  uploadPictureByUrlUsingPost,
  uploadPictureUsingPost,
} from '@/api/pictureController.ts'
import { message } from 'ant-design-vue'
import { VueCropper } from 'vue-cropper'

interface Props {
  picture?: API.PictureVO
  spaceId?: number
  onSuccess?: (newPicture: API.PictureVO) => void
}

const props = defineProps<Props>()

const resultImageUrl = ref<String>('')

const loading = ref(false)
// 任务Id
const taskId = ref<String>()

/**
 * 创建AI扩展图片的任务
 */
const createTask = async () => {
  if (!props.picture?.id) {
    return
  }
  const res = await createPictureOutPaintingTaskUsingPost({
    pictureId: props.picture.id,
    // 可以根据需要设置扩图参数
    parameters: {
      xScale: 2,
      yScale: 2,
    },
  })
  if (res.data.code === 0 && res.data.data) {
    message.success('创建任务成功，请耐心等待，不要退出界面')
    console.log(res.data.data.output.taskId)
    taskId.value = res.data.data.output.taskId

    // 开启轮询(使用前段轮询，即前端隔几秒给后端发送询问请求，后端问AI一次，AI完成后才回复后端，最后后端再给前端)
    startPolling()
  } else {
    message.error('创建任务失败，' + res.data.message)
  }
}

// 轮询定时器
let pollingTimer: NodeJS.Timeout = null

// 清理轮询定时器
const clearPolling = () => {
  if (pollingTimer) {
    clearInterval(pollingTimer)
    pollingTimer = null
    taskId.value = null
  }
}

// 开始轮询
const startPolling = () => {
  if (!taskId.value) return

  pollingTimer = setInterval(async () => {
    try {
      const res = await getPictureOutPaintingTaskUsingGet({
        taskId: taskId.value,
      })
      if (res.data.code === 0 && res.data.data) {
        const taskResult = res.data.data.output
        if (taskResult.taskStatus === 'SUCCEEDED') {
          message.success('扩图任务成功')
          resultImageUrl.value = taskResult.outputImageUrl
          clearPolling()
        } else if (taskResult.taskStatus === 'FAILED') {
          message.error('扩图任务失败')
          clearPolling()
        }
      }
    } catch (error) {
      console.error('轮询任务状态失败', error)
      message.error('检测任务状态失败，请稍后重试')
      clearPolling()
    }
  }, 3000) // 每隔 3 秒轮询一次
}

// 清理定时器，避免内存泄漏
onUnmounted(() => {
  clearPolling()
})

/**
 * 上传图片
 * @param file
 */
const uploadLoading = ref<boolean>(false)

const handleUpload = async () => {
  uploadLoading.value = true
  try {
    const params: API.PictureUploadRequest = {
      fileUrl: resultImageUrl.value,
      spaceId: props.spaceId,
    }
    if (props.picture) {
      params.id = props.picture.id
    }
    const res = await uploadPictureByUrlUsingPost(params)
    if (res.data.code === 0 && res.data.data) {
      message.success('图片上传成功')
      // 将上传成功的图片信息传递给父组件
      props.onSuccess?.(res.data.data)
      // 关闭弹窗
      closeModal()
    } else {
      message.error('图片上传失败，' + res.data.message)
    }
  } catch (error) {
    message.error('图片上传失败')
  } finally {
    uploadLoading.value = false
  }
}

// 是否可见
const visible = ref(false)

// 打开弹窗
const openModal = () => {
  visible.value = true
  nextTick(() => {
    cropperRef.value?.refresh?.()
  })
}

// 关闭弹窗
const closeModal = () => {
  visible.value = false
}

watch(
  () => props.imageUrl,
  async (newUrl) => {
    if (!newUrl) {
      return
    }
    // imageUrl 变了，但 vue-cropper 内部可能不会自动更新，强制重建/刷新
    cropperKey.value += 1
    await nextTick()
    cropperRef.value?.replace?.(newUrl)
    cropperRef.value?.refresh?.()
  },
)

// 暴露函数给父组件
defineExpose({
  openModal,
})
</script>

<style>
.image-out-painting {
  text-align: center;
}

.image-out-painting {
  width: 100%;
  height: 60vh !important;
  max-height: 650px;
}
</style>
