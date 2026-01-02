<template>
  <a-modal
    class="image-cropper"
    v-model:open="visible"
    title="编辑图片"
    :width="900"
    :footer="false"
    @cancel="closeModal"
  >
    <!-- 图片裁切组件 -->
    <VueCropper
      :key="cropperKey"
      ref="cropperRef"
      :img="imageUrl"
      mode="contain"
      output-type="png"
      :info="true"
      :can-move-box="true"
      :fixed-box="false"
      :auto-crop="true"
      :center-box="true"
    />
    <div style="margin-bottom: 16px" />
    <!-- 图片操作 -->
    <div class="image-cropper-actions">
      <a-space>
        <a-button @click="rotateLeft">向左旋转</a-button>
        <a-button @click="rotateRight">向右旋转</a-button>
        <a-button @click="changeScale(1)">放大</a-button>
        <a-button @click="changeScale(-1)">缩小</a-button>
        <a-button type="primary" :loading="loading" @click="handleConfirm">确认</a-button>
      </a-space>
    </div>
  </a-modal>
</template>

<script lang="ts" setup>
import { nextTick, ref, watch } from 'vue'
import { uploadPictureUsingPost } from '@/api/pictureController.ts'
import { message } from 'ant-design-vue'
import { VueCropper } from 'vue-cropper'

interface Props {
  imageUrl?: string
  picture?: API.PictureVO
  spaceId?: number
  onSuccess?: (newPicture: API.PictureVO) => void
}

const props = defineProps<Props>()

// 获取图片裁切器的引用
const cropperRef = ref()

// 用于强制重建裁剪器（避免切换图片后仍显示上一张）
const cropperKey = ref(0)

// 缩放比例
const changeScale = (num) => {
  cropperRef.value?.changeScale(num)
}

// 向左旋转
const rotateLeft = () => {
  cropperRef.value?.rotateLeft?.()
}

// 向右旋转
const rotateRight = () => {
  cropperRef.value?.rotateRight?.()
}

// 确认裁切
const handleConfirm = () => {
  cropperRef.value?.getCropBlob?.((blob: Blob) => {
    // blob 为已经裁切好的文件 -> 把图片加上名称后，再把blob对象转换为文件对象
    const fileName = (props.picture?.name || 'image') + '.png'
    const file = new File([blob], fileName, { type: blob.type })
    // 上传图片
    handleUpload({ file })
  })
}

const loading = ref(false)

/**
 * 上传图片
 * @param file
 */
const handleUpload = async ({ file }: any) => {
  loading.value = true
  try {
    const params: API.PictureUploadRequest = props.picture ? { id: props.picture.id } : {}
    params.spaceId = props.spaceId
    const res = await uploadPictureUsingPost(params, {}, file)
    if (res.data.code === 0 && res.data.data) {
      message.success('图片上传成功')
      // 将上传成功的图片信息传递给父组件
      props.onSuccess?.(res.data.data)
      closeModal()
    } else {
      message.error('图片上传失败，' + res.data.message)
    }
  } catch (error) {
    console.error('图片上传失败', error)
    message.error('图片上传失败，' + error.message)
  }
  loading.value = false
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
.image-cropper {
  text-align: center;
}

.image-cropper .vue-cropper {
  width: 100%;
  height: 60vh !important;
  max-height: 650px;
}
</style>
