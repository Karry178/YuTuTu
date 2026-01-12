<template>
  <div class="batch-edit-picture-modal">
    <!-- <a-button type="primary" @click="openModal">Open Modal</a-button> -->
    <a-modal v-model:visible="visible" title="批量编辑图片" :footer="false" @cancel="closeModal">
      <a-typography-link type="secondary">* 只对当前页面的图片生效</a-typography-link>

      <!-- 批量创建表单 -->
      <!-- 搜索表单 -->
      <a-form name="formData" layout="vertical" :model="formData" @finish="handleSubmit">
        <a-form-item name="category" label="分类">
          <a-auto-complete
            v-model:value="formData.category"
            :options="categoryOptions"
            placeholder="请输入分类"
            allow-clear
          />
        </a-form-item>
        <a-form-item name="tags" label="标签">
          <a-select
            v-model:value="formData.tags"
            mode="tags"
            :options="tagOptions"
            placeholder="请输入标签，支持直接输入新标签"
            allow-clear
          />
        </a-form-item>
        <!-- 名称规则 -->
        <a-form-item name="nameRule" label="命名规则">
          <a-input
            v-model:value="formData.name"
            placeholder="请输入命名规则，输入{序号}可动态生成"
            allow-clear
          />
        </a-form-item>
        <a-form-item>
          <a-button type="primary" html-type="submit" style="width: 100%">提交图片</a-button>
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script lang="ts" setup>
import { onMounted, reactive, ref, toRefs } from 'vue'
import qrcodeIcon from '@/assets/github.png'
import {
  editPictureByBatchUsingPost,
  listPictureTagCategoryUsingGet,
} from '@/api/pictureController.ts'
import { message } from 'ant-design-vue'

interface Props {
  pictureList: API.PictureVO[]
  spaceId: number
  onSuccess: () => void
}

const props = withDefaults(defineProps<Props>(), {})

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

const formData = reactive<API.PictureEditByBatchRequest>({
  category: '',
  tags: [],
  nameRule: '',
})

/**
 * 提交表单
 */
const handleSubmit = async (values: API.PictureEditRequest) => {
  if (!props.pictureList) {
    return;
  }
  const res = await editPictureByBatchUsingPost({
    pictureIdList: props.pictureList.map((picture) => picture.id),
    spaceId: props.spaceId,
    ...values,
  })
  // 操作成功
  if (res.data.code === 0 && res.data.data) {
    message.success('操作成功')
    // 然后关闭弹窗，且调用onSuccess的回调函数
    closeModal();
    props.onSuccess?.();
  } else {
    message.error('操作失败' + res.data.message)
  }
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
</script>
