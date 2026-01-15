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
    <!-- 协同编辑操作：仅团队空间展示这些按钮 -->
    <div class="image-edit-actions" v-if="isTeamSpace">
      <a-space>
        <a-button v-if="editingUser" disabled>{{ editingUser?.userName }}正在编辑</a-button>
        <a-button v-if="canEnterEdit" :disabled="!wsReady" type="primary" ghost @click="enterEdit">
          进入编辑
        </a-button>
        <a-button v-if="canExitEdit" danger ghost @click="exitEdit">退出编辑</a-button>
      </a-space>
    </div>

    <div style="margin-bottom: 16px" />
    <!-- 图片操作 -->
    <div class="image-cropper-actions">
      <a-space>
        <a-button @click="rotateLeft" :disabled="!canEdit">向左旋转</a-button>
        <a-button @click="rotateRight" :disabled="!canEdit">向右旋转</a-button>
        <a-button @click="changeScale(1)" :disabled="!canEdit">放大</a-button>
        <a-button @click="changeScale(-1)" :disabled="!canEdit">缩小</a-button>
        <a-button type="primary" :loading="loading" @click="handleConfirm" :disabled="!canEdit"
          >确认
        </a-button>
      </a-space>
    </div>
  </a-modal>
</template>

<script lang="ts" setup>
import { computed, nextTick, onUnmounted, ref, watch, watchEffect } from 'vue'
import { uploadPictureUsingPost } from '@/api/pictureController.ts'
import { message } from 'ant-design-vue'
import { VueCropper } from 'vue-cropper'
import { useLoginUserStore } from '@/stores/useLoginUserStore.ts'
import PictureEditWebSocket from '@/utils/pictureEditWebSocket.ts'
import { PICTURE_EDIT_ACTION_ENUM, PICTURE_EDIT_MESSAGE_TYPE_ENUM } from '@/constants/picture.ts'
import { SPACE_TYPE_ENUM } from '@/constants/space.ts'

interface Props {
  imageUrl?: string
  picture?: API.PictureVO
  spaceId?: string | number
  space?: API.SpaceVO
  onSuccess?: (newPicture: API.PictureVO) => void
}

const props = defineProps<Props>()

// 是否为团队空间
const isTeamSpace = computed(() => {
  // 临时定位
  console.log(
    props.space,
    props.space?.spaceType,
    typeof props.space?.spaceType,
    SPACE_TYPE_ENUM.TEAM,
  )
  const t = props.space?.spaceType
  return Number(t) === Number(SPACE_TYPE_ENUM.TEAM)
})

// 获取图片裁切器的引用
const cropperRef = ref()

// 用于强制重建裁剪器（避免切换图片后仍显示上一张）
const cropperKey = ref(0)

// 缩放比例
const changeScale = (num) => {
  cropperRef.value?.changeScale(num)
  if (num > 0) {
    // 放大操作
    editAction(PICTURE_EDIT_ACTION_ENUM.ZOOM_IN)
  } else {
    // 缩小操作
    editAction(PICTURE_EDIT_ACTION_ENUM.ZOOM_OUT)
  }
}

// 向左旋转
const rotateLeft = () => {
  cropperRef.value?.rotateLeft?.()
  editAction(PICTURE_EDIT_ACTION_ENUM.ROTATE_LEFT)
}

// 向右旋转
const rotateRight = () => {
  cropperRef.value?.rotateRight?.()
  editAction(PICTURE_EDIT_ACTION_ENUM.ROTATE_RIGHT)
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
  console.log(props.space, props.spaceId)
  visible.value = true
  nextTick(() => {
    cropperRef.value?.refresh?.()
  })
}

// 关闭弹窗时：也要断开WebSocket连接
const closeModal = () => {
  visible.value = false
  // 断开连接，并重置当前编辑的用户
  if (WebSocket) {
    websocket?.disconnect()
  }
  editingUser.value = undefined
}

// 监听
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

// ========== 实时编辑 ==========
const loginUserStore = useLoginUserStore()
const loginUser = loginUserStore.loginUser

// 正在编辑的用户
const editingUser = ref<API.UserVO>()
// 当前用户是否可进入编辑
const canEnterEdit = computed(() => {
  return !editingUser.value // 如果editingUser == false 才可编辑
})
// 当前用户是否可以退出编辑
const canExitEdit = computed(() => {
  // 当前编辑用户等于已登录的用户
  return editingUser.value?.id === loginUser.id
})

// 可以点击编辑图片的操作按钮
const canEdit = computed(() => {
  // 不是团队空间，默认可编辑
  if (!isTeamSpace.value) {
    return true
  }
  // 否则，团队空间：只有编辑者才能协同编辑
  return editingUser.value?.id === loginUser.id
})

// ==========【重要】编写 WebSocket 逻辑 ==========
let websocket: PictureEditWebSocket | null

// 定义一个连接状态
const wsReady = ref(false)

// 初始化 WebSocket 连接，绑定监听事件
const initWebSocket = () => {
  // 先拿到pictureId，没有图片Id或者没有图片弹窗就不用初始化连接
  const pictureId = props.picture?.id
  if (!pictureId || !visible.value) {
    return
  }
  // 防止之前的连接未释放
  if (websocket) {
    websocket.disconnect()
  }
  // 创建 WebSocket 实例
  websocket = new PictureEditWebSocket(pictureId)

  // 连接状态 开/关
  websocket.on('open', () => {
    wsReady.value = true
  })
  websocket.on('close', () => {
    wsReady.value = false
  })

  // 监听一系列的事件
  websocket.on(PICTURE_EDIT_MESSAGE_TYPE_ENUM.INFO, (msg) => {
    console.log('收到通知消息:', msg)
    message.info(msg.message)
  })

  websocket.on(PICTURE_EDIT_MESSAGE_TYPE_ENUM.ERROR, (msg) => {
    console.log('收到错误通知:', msg)
    message.info(msg.message)
  })

  websocket.on(PICTURE_EDIT_MESSAGE_TYPE_ENUM.ENTER_EDIT, (msg) => {
    console.log('收到进入编辑状态的通知:', msg)
    message.info(msg.message)
    editingUser.value = msg.user // 设置当前编辑图片的用户
  })

  websocket.on(PICTURE_EDIT_MESSAGE_TYPE_ENUM.EDIT_ACTION, (msg) => {
    console.log('收到编辑操作的通知:', msg)
    message.info(msg.message)
    // 根据收到的编辑操作，执行动作方式
    switch (msg.editAction) {
      case PICTURE_EDIT_ACTION_ENUM.ROTATE_LEFT:
        // 左旋：调用rotateLeft()
        rotateLeft()
        break
      case PICTURE_EDIT_ACTION_ENUM.ROTATE_RIGHT:
        rotateRight()
        break
      case PICTURE_EDIT_ACTION_ENUM.ZOOM_IN:
        // 裁剪 放大：传入一个大于0的数字
        changeScale(1)
        break
      case PICTURE_EDIT_ACTION_ENUM.ZOOM_OUT:
        // 裁剪 缩小：传入一个小于0的数字
        changeScale(-1)
        break
    }
  })

  websocket.on(PICTURE_EDIT_MESSAGE_TYPE_ENUM.EXIT_EDIT, (msg) => {
    console.log('收到退出编辑状态的通知:', msg)
    message.info(msg.message)
    editingUser.value = undefined // 重置当前编辑的用户
  })

  // 建立连接
  websocket.connect()
}

// 监听：每次操作变化就初始化 websocket 连接方法
/*watchEffect(() => {
  // 只有团队空间才需要初始化
  if (isTeamSpace.value) {
    initWebSocket()
  }
})*/
watch(
  [() => visible.value, () => props.picture?.id, () => isTeamSpace.value],
  ([v, pid, team]) => {
    if (v && pid && team) {
      initWebSocket()
    }
  },
  { immediate: true },
)

// 组件销毁或浏览器关闭的时候都要触发：断开WebSocket连接
onUnmounted(() => {
  // 断开 WebSocket 连接
  if (WebSocket) {
    websocket?.disconnect()
  }
  // 断开连接，同时要重置当前编辑的用户
  editingUser.value = undefined
})

// 进入编辑状态
const enterEdit = () => {
  if (websocket) {
    // 发送进入编辑状态的请求
    websocket.sendMessage({
      type: PICTURE_EDIT_MESSAGE_TYPE_ENUM.ENTER_EDIT,
    })
  }
}

// 退出编辑状态
const exitEdit = () => {
  if (websocket) {
    // 发送退出编辑状态的请求
    websocket.sendMessage({
      type: PICTURE_EDIT_MESSAGE_TYPE_ENUM.EXIT_EDIT,
    })
  }
}

// 编辑图片操作
const editAction = (action: string) => {
  if (websocket) {
    // 发送编辑图片操作的请求
    websocket.sendMessage({
      type: PICTURE_EDIT_MESSAGE_TYPE_ENUM.EDIT_ACTION,
      editAction: action,
    })
  }
}
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
