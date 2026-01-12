<template>
  <!-- 移除了测试用的 "Open Modal" 按钮，只保留弹窗 -->
  <a-modal v-model:visible="visible" :title="title" :footer="false" @cancel="closeModal">
    <h4>复制分享链接</h4>
    <a-typography-link copyable>
      {{ link }}
    </a-typography-link>
    <div style="margin-bottom: 16px" />
    <h4>手机扫码查看</h4>
    <a-qrcode :value="link" :icon="qrcodeIcon" />
  </a-modal>
</template>

<script lang="ts" setup>
import { ref, toRefs } from 'vue'
import qrcodeIcon from '@/assets/github.png'

interface Props {
  title: string
  link: string
}

const props = withDefaults(defineProps<Props>(), {
  title: '分享图片',
  link: 'https://github.com/Karry178',
})

const { title, link } = toRefs(props)

// const open = ref<boolean>(false)

// 是否可见
const visible = ref(false)

// 打开弹窗
const openModal = () => {
  console.log('openModal called, visible=', visible.value)
  visible.value = true
}

// 关闭弹窗
const closeModal = () => {
  visible.value = false
}

// 暴露函数给副组件引入
defineExpose({
  openModal,
})
</script>
