<template>
  <div class="picture-list">
    <!-- 图片列表 -->
    <a-list
      :loading="props.loading"
      :grid="{ gutter: 16, xs: 1, sm: 2, md: 3, lg: 4, xl: 5, xxl: 6 }"
      :data-source="props.dataList"
    >
      <template #renderItem="{ item: picture }">
        <a-list-item style="padding: 0">
          <!-- 展示单张图片 -->
          <a-card hoverable @click="doClickPicture(picture)">
            <template #cover>
              <img
                :alt="picture.name"
                :src="picture.thumbnailUrl ?? picture.url"
                style="height: 180px; object-fit: cover"
              />
            </template>
            <a-card-meta :title="picture.name">
              <template #description>
                <a-flex>
                  <a-tag color="green">
                    {{ picture.category ?? '默认' }}
                  </a-tag>
                  <a-tag v-for="tag in picture.tags" :key="tag">
                    {{ tag }}
                  </a-tag>
                </a-flex>
              </template>
            </a-card-meta>
            <template v-if="showOp" #actions>
              <a-space @click="(e) => doSearch(picture, e)">
                <SearchOutlined />
                搜索
              </a-space>
              <a-space @click="(e) => doEdit(picture, e)">
                <EditOutlined />
                编辑
              </a-space>
              <a-space @click="(e) => doShare(picture, e)">
                <ShareAltOutlined />
                分享
              </a-space>
              <a-space @click="(e) => doDelete(picture, e)">
                <DeleteOutlined />
                删除
              </a-space>
            </template>
          </a-card>
        </a-list-item>
      </template>
    </a-list>
    <!-- 传入分享组件：ShareModal -->
    <ShareModal ref="shareModalRef" :link="shareLink" />
  </div>
</template>

<script setup lang="ts">
import { computed, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  DeleteOutlined,
  EditOutlined,
  SearchOutlined,
  ShareAltOutlined,
} from '@ant-design/icons-vue'
import { deletePicUsingPost } from '@/api/pictureController.ts'
import { message } from 'ant-design-vue'
import ShareModal from '@/components/ShareModal.vue'

// 定义一个属性：用来接收dataList
interface Props {
  dataList?: API.PictureVO[]
  loading?: boolean
  showOp?: boolean
  onReload?: () => void // 重新出发加载的组件：告诉外层，做了操作，需要在外层重新查询数据
}
// 定义一个默认值
const props = withDefaults(defineProps<Props>(), {
  dataList: () => [],
  loading: false,
  showOp: false, // 这样主页就不会显示空间页面的编辑按钮了
})

// 定义数据：从后端获取,后端管理员获取图片列表的返回值为PictureVO,所以前端从API拿到PictureVO即可
const dataList = ref<API.PictureVO[]>([])
const loading = ref(true)

// 通过route获取信息，通过router实现跳转页面
const route = useRoute()
const router = useRouter()

// 点击图片实现图片详情页的跳转
const doClickPicture = (picture: API.PictureVO) => {
  router.push({
    path: `/picture/${picture.id}`,
  })
}

// 搜索图片
const doSearch = (picture, e) => {
  // 阻止冒泡
  e.stopPropagation()
  // 打开新的页面
  window.open(`/searchPicture?pictureId=${picture.id}`)
}
// 编辑图片
const doEdit = (picture, e) => {
  // 阻止冒泡
  e.stopPropagation()
  // 跳转时一定要携带spaceId
  router.push({
    path: '/add_picture',
    query: {
      id: picture.id,
      spaceId: picture.spaceId,
    },
  })
}
// 删除图片
const doDelete = async (picture, e) => {
  // 阻止冒泡
  e.stopPropagation()
  const id = picture.id
  if (!id) {
    return
  }
  const res = await deletePicUsingPost({ id })
  if (res.data.code === 0) {
    message.success('删除成功')
    props.onReload?.() // 重新查询数据
  } else {
    message.error('删除失败')
  }
}

// =========== 分享操作 ===========
const shareModalRef = ref()
// 分享链接
const shareLink = ref<String>()
// 分享函数
const doShare = (picture, e) => {
  // 阻止冒泡
  e.stopPropagation()
  shareLink.value = `${window.location.protocol}//${window.location.host}/picture/${picture.id}`
  if (shareModalRef.value) {
    shareModalRef.value.openModal()
  }
}
</script>

<style scoped></style>
