<template>
  <div v-if="loading" class="loading-container">
    <el-icon class="is-loading"><Loading /></el-icon>
    <span>加载中...</span>
  </div>
  <div v-else-if="videoUrl" class="course-player-wrapper">
    <el-card shadow="hover" class="player-card">
      <CourseMediaPlayer
        :video-url="videoUrl"
        :start-time="userCourseRelation.progressSeconds"
        @ready="handlePlayerReady"
        @progress="handlePlaybackProgress"
        @error="handleVideoError"
      />

      <div class="meta">
        <div class="title-bar">
          <h2 class="title">{{ courseInfo.title }}</h2>
          <CourseActions
            :enrollment-status="enrollmentStatus"
            :is-favorite="userCourseRelation.isFavorite"
            :enroll-loading="enrollLoading"
            :favorite-loading="favoriteLoading"
            @enroll="handleEnrollCourse"
            @toggle-favorite="handleFavorite"
            @open-graph="goToKnowledgeGraph"
            @retry-relation="loadCourseRelation"
          />
        </div>
        <div v-if="courseInfo.description" class="description">{{ courseInfo.description }}</div>
        <KnowledgePointList :knowledge-points="knowledgePoints" />
        <div class="watch-time">观看时长: {{ formatTime(playbackSnapshot.watchedSeconds) }}</div>
      </div>
    </el-card>
  </div>
  <el-empty v-else description="暂无视频资源" />
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, onUnmounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Loading } from '@element-plus/icons-vue'
import CourseActions from '@/features/course-detail/components/CourseActions.vue'
import CourseMediaPlayer from '@/features/course-detail/components/CourseMediaPlayer.vue'
import KnowledgePointList from '@/features/course-detail/components/KnowledgePointList.vue'
import { ENROLLMENT_STATUS } from '@/features/course-detail/enrollmentStatus'
import { useUserStore } from '@/store/user'
import { setAuthTokenToCookie, clearAuthTokenCookie } from '@/utils/authCookie'
import { notification } from '@/services/notification'
import { recordLearningBehavior } from '@/api/learningBehavior'
import {
  getCourseById,
  getCourseVideo,
  getCourseKnowledgePoints,
  getUserCourseRelation,
  updateCourseVideoProgressSeconds,
  enrollCourse,
} from '@/api/course'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const courseId = route.params.courseId

let disposed = false

const loading = ref(true)
const videoUrl = ref('')
const courseInfo = reactive({})
const knowledgePoints = ref([])
const enrollmentStatus = ref(ENROLLMENT_STATUS.LOADING)
const enrollLoading = ref(false)
const favoriteLoading = ref(false)
const viewRecorded = ref(false)
const playerReady = ref(false)

const userCourseRelation = reactive({
  isFavorite: false,
  progressSeconds: 0,
})

const playbackSnapshot = reactive({
  currentTime: 0,
  watchedSeconds: 0,
})

const canPersistProgress = computed(
  () =>
    enrollmentStatus.value === ENROLLMENT_STATUS.ENROLLED &&
    playerReady.value &&
    Boolean(videoUrl.value),
)

const loadCourseRelation = async () => {
  if (disposed) return

  enrollmentStatus.value = ENROLLMENT_STATUS.LOADING
  try {
    const res = await getUserCourseRelation(courseId)
    if (disposed) return

    if (res.data.code === 200 && res.data.data) {
      enrollmentStatus.value = ENROLLMENT_STATUS.ENROLLED
      userCourseRelation.isFavorite = Boolean(res.data.data.isFavorite)
      userCourseRelation.progressSeconds = res.data.data.progressSeconds || 0
    } else if (res.data.code === 200 && !res.data.data) {
      enrollmentStatus.value = ENROLLMENT_STATUS.NOT_ENROLLED
      userCourseRelation.isFavorite = false
      userCourseRelation.progressSeconds = 0
    } else {
      enrollmentStatus.value = ENROLLMENT_STATUS.ERROR
      notification.error('获取用户课程关系失败', res.data.msg)
    }
  } catch (error) {
    if (disposed) return
    enrollmentStatus.value = ENROLLMENT_STATUS.ERROR
    notification.error('获取用户课程关系出错', error)
  }
}

const loadCourseInfo = async () => {
  try {
    const res = await getCourseById(courseId)
    if (disposed) return

    if (res.data.code === 200) {
      Object.assign(courseInfo, res.data.data)
    } else {
      notification.error('获取课程信息失败', res.data.msg)
    }
  } catch (error) {
    if (disposed) return
    notification.error('获取课程信息出错', error)
  }
}

const loadKnowledgePoints = async () => {
  try {
    const res = await getCourseKnowledgePoints(courseId)
    if (disposed) return

    if (res.data.code === 200) {
      knowledgePoints.value = Array.isArray(res.data.data) ? res.data.data : []
    } else {
      knowledgePoints.value = []
      notification.error('获取课程知识点失败', res.data.msg)
    }
  } catch (error) {
    if (disposed) return
    knowledgePoints.value = []
    notification.error('获取课程知识点出错', error)
  }
}

const loadCourseDetail = async () => {
  if (!courseId) {
    notification.error('课程ID不存在')
    loading.value = false
    return
  }

  let response
  try {
    response = await getCourseVideo(courseId)
  } catch (error) {
    if (disposed) return
    notification.error('获取课程视频异常，请稍后重试', error)
    loading.value = false
    return
  }

  if (disposed) return
  if (response.data.code !== 200) {
    notification.error(`获取课程视频失败: ${response.data.msg}`, response.data)
    loading.value = false
    return
  }

  const nextVideoUrl = String(response.data.data || '')
  if (!nextVideoUrl) {
    videoUrl.value = ''
    loading.value = false
    return
  }

  if (userStore.token) {
    // 先写入 Cookie 再渲染 video，避免首个资源请求缺少鉴权信息。
    setAuthTokenToCookie(userStore.token, 10)
  }
  playerReady.value = false
  videoUrl.value = nextVideoUrl

  await loadCourseRelation()
  if (disposed) return

  // 关系状态确定后再挂载播放器，确保首次渲染即可获得稳定的断点位置。
  loading.value = false

  await loadCourseInfo()
  if (disposed) return
  await loadKnowledgePoints()
}

const sendViewRecord = async () => {
  if (viewRecorded.value) return
  viewRecorded.value = true

  try {
    const res = await recordLearningBehavior({
      courseId: Number(courseId),
      behaviorType: 'VIEW',
    })
    if (res.data.code !== 200) {
      viewRecorded.value = false
      if (!disposed) {
        notification.error('记录学习行为失败', res.data.msg)
      }
    }
  } catch (error) {
    viewRecorded.value = false
    if (!disposed) {
      notification.error('记录学习行为出错', error)
    }
  }
}

const handlePlayerReady = () => {
  if (!disposed) {
    playerReady.value = true
  }
}

const handlePlaybackProgress = (snapshot) => {
  playbackSnapshot.currentTime = Number(snapshot?.currentTime) || 0
  playbackSnapshot.watchedSeconds = Number(snapshot?.watchedSeconds) || 0

  if (playbackSnapshot.watchedSeconds >= 10 && !viewRecorded.value) {
    void sendViewRecord()
  }
}

const handleVideoError = (error) => {
  if (!disposed) {
    notification.error('视频加载失败，请检查网络或权限', error)
  }
}

const handleFavorite = async () => {
  if (favoriteLoading.value || enrollmentStatus.value !== ENROLLMENT_STATUS.ENROLLED) return
  favoriteLoading.value = true
  const targetFavoriteState = !userCourseRelation.isFavorite
  const behaviorType = targetFavoriteState ? 'FAVORITE' : 'UNFAVORITE'

  try {
    const res = await recordLearningBehavior({
      courseId: Number(courseId),
      behaviorType,
    })
    if (disposed) return
    if (res.data.code !== 200) {
      notification.error('操作失败，请重试', res.data.msg)
      return
    }

    userCourseRelation.isFavorite = targetFavoriteState
    notification.success(targetFavoriteState ? '已收藏' : '已取消收藏')
    notification.debug('收藏操作成功', behaviorType)
  } catch (error) {
    if (!disposed) {
      notification.error('操作失败，请重试', error)
    }
  } finally {
    if (!disposed) {
      favoriteLoading.value = false
    }
  }
}

const handleEnrollCourse = async () => {
  if (enrollLoading.value || enrollmentStatus.value !== ENROLLMENT_STATUS.NOT_ENROLLED) return
  enrollLoading.value = true

  try {
    const res = await enrollCourse(courseId)
    if (disposed) return
    if (res.data.code === 200) {
      enrollmentStatus.value = ENROLLMENT_STATUS.ENROLLED
      notification.success('成功加入课程')
    } else {
      notification.error('加入课程失败', res.data.msg)
    }
  } catch (error) {
    if (!disposed) {
      notification.error('加入课程异常', error)
    }
  } finally {
    if (!disposed) {
      enrollLoading.value = false
    }
  }
}

const goToKnowledgeGraph = () => {
  router.push({
    path: '/knowledge-graph',
    query: { courseId: String(courseId) },
  })
}

const formatTime = (seconds) => {
  const hours = Math.floor(seconds / 3600)
  const minutes = Math.floor((seconds % 3600) / 60)
  const remainingSeconds = Math.floor(seconds % 60)
  return [hours, minutes, remainingSeconds]
    .map((value) => value.toString().padStart(2, '0'))
    .join(':')
}

const persistPlaybackSession = async () => {
  if (!canPersistProgress.value) return

  if (viewRecorded.value && playbackSnapshot.watchedSeconds > 0) {
    try {
      await recordLearningBehavior({
        courseId: Number(courseId),
        behaviorType: 'STUDY',
        duration: Math.round(playbackSnapshot.watchedSeconds),
      })
      notification.debug('离开前记录观看时长', Math.round(playbackSnapshot.watchedSeconds))
    } catch (error) {
      notification.error('记录观看时长失败', error)
    }
  }

  try {
    const res = await updateCourseVideoProgressSeconds({
      courseId: Number(courseId),
      progressSeconds: Math.floor(playbackSnapshot.currentTime),
    })
    if (res.data.code === 200) {
      notification.debug('更新断点续播时间', Math.floor(playbackSnapshot.currentTime))
    } else {
      notification.error('更新断点续播时间失败', res.data.msg)
    }
  } catch (error) {
    notification.error('更新断点续播时间失败', error)
  }
}

onMounted(loadCourseDetail)

onBeforeUnmount(() => {
  disposed = true
  clearAuthTokenCookie()
})

// 父组件的 unmounted 在子组件 beforeUnmount 之后执行，可接收到播放器的最终快照。
onUnmounted(() => {
  void persistPlaybackSession()
})
</script>

<style scoped>
.loading-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 400px;
  gap: 12px;
  font-size: 16px;
  color: #666;
}

.course-player-wrapper {
  padding: 20px;
}

.player-card {
  border-radius: 16px;
}

.meta {
  margin-top: 16px;
  padding: 16px;
  border-radius: 12px;
  border: 1px solid #eceff5;
  background: linear-gradient(180deg, #fbfcfe 0%, #f6f8fc 100%);
}

.title-bar {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
}

.title {
  font-size: 24px;
  font-weight: 700;
  line-height: 1.4;
  margin: 0;
  flex: 1;
  color: #1f2a44;
  letter-spacing: 0.2px;
}

.description {
  font-size: 15px;
  color: #4a5670;
  line-height: 1.6;
  margin-top: 10px;
  padding: 0;
}

.watch-time {
  font-size: 14px;
  color: #666;
  margin-top: 8px;
}

@media (max-width: 768px) {
  .meta {
    padding: 12px;
  }

  .title-bar {
    flex-direction: column;
    align-items: stretch;
    gap: 12px;
  }
}
</style>
