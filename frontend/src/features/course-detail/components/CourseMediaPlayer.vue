<template>
  <div class="video-box">
    <video
      v-if="videoUrl"
      ref="videoRef"
      class="video"
      :src="videoUrl"
      controls
      @error="handleVideoError"
      @loadedmetadata="handleLoadedMetadata"
      @play="handleVideoPlay"
      @pause="handleVideoPause"
      @timeupdate="handleTimeUpdate"
    />
    <div v-else class="loading">加载中...</div>
  </div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'

const props = defineProps({
  videoUrl: {
    type: String,
    required: true,
  },
  startTime: {
    type: Number,
    default: 0,
  },
  mediaId: {
    type: [String, Number],
    required: true,
  },
  resumeOnLoad: {
    type: Boolean,
    default: false,
  },
})

const emit = defineEmits(['ready', 'progress', 'error'])

const videoRef = ref(null)
const watchedSeconds = ref(0)
const lastRecordTime = ref(0)
const isPlaying = ref(false)
const metadataLoaded = ref(false)
const hasPlaybackStarted = ref(false)
const startTimeApplied = ref(false)
const readyEmitted = ref(false)
const pendingResumeTime = ref(null)

const emitProgress = () => {
  emit('progress', {
    currentTime: videoRef.value?.currentTime || 0,
    watchedSeconds: watchedSeconds.value,
  })
}

const resetPlaybackState = () => {
  watchedSeconds.value = 0
  lastRecordTime.value = 0
  isPlaying.value = false
  metadataLoaded.value = false
  hasPlaybackStarted.value = false
  startTimeApplied.value = false
  readyEmitted.value = false
  pendingResumeTime.value = null
  emit('progress', { currentTime: 0, watchedSeconds: 0 })
}

const prepareSourceRefresh = () => {
  const video = videoRef.value
  pendingResumeTime.value = video?.currentTime || props.startTime || 0
  video?.pause()
  lastRecordTime.value = pendingResumeTime.value
  isPlaying.value = false
  metadataLoaded.value = false
  hasPlaybackStarted.value = false
  startTimeApplied.value = false
  readyEmitted.value = false
}

const applyStartTime = () => {
  const video = videoRef.value
  if (
    video
    && metadataLoaded.value
    && !hasPlaybackStarted.value
    && !startTimeApplied.value
    && (pendingResumeTime.value > 0 || props.startTime > 0)
  ) {
    const targetTime = pendingResumeTime.value > 0 ? pendingResumeTime.value : props.startTime
    video.currentTime = targetTime
    lastRecordTime.value = targetTime
    startTimeApplied.value = true
    pendingResumeTime.value = null
    emitProgress()
  }
}

watch(
  () => props.videoUrl,
  (nextUrl, previousUrl) => {
    if (!previousUrl || nextUrl === previousUrl) return
    prepareSourceRefresh()
  },
)

watch(() => props.mediaId, resetPlaybackState)
watch(() => props.startTime, applyStartTime)

const handleVideoError = (event) => {
  emitProgress()
  emit('error', {
    error: event,
    currentTime: videoRef.value?.currentTime || 0,
    watchedSeconds: watchedSeconds.value,
    wasPlaying: isPlaying.value,
  })
}

const handleVideoPlay = () => {
  hasPlaybackStarted.value = true
  isPlaying.value = true
  lastRecordTime.value = videoRef.value?.currentTime || 0
}

const handleVideoPause = () => {
  isPlaying.value = false
  lastRecordTime.value = videoRef.value?.currentTime || 0
  emitProgress()
}

const handleTimeUpdate = () => {
  const video = videoRef.value
  if (!video) return

  const currentTime = video.currentTime
  if (isPlaying.value) {
    const delta = currentTime - lastRecordTime.value
    // 拖拽产生的大跨度变化不计入有效观看时长。
    if (delta > 0 && delta < 3) {
      watchedSeconds.value += delta
    }
  }

  lastRecordTime.value = currentTime
  emitProgress()
}

const handleLoadedMetadata = () => {
  metadataLoaded.value = true
  applyStartTime()
  if (!readyEmitted.value) {
    readyEmitted.value = true
    emit('ready')
  }
  if (props.resumeOnLoad) {
    Promise.resolve(videoRef.value?.play()).catch(() => {
      // 浏览器可能阻止自动恢复，保留当前位置并由用户通过原生控件继续播放。
    })
  }
}

const handleVisibilityChange = () => {
  if (document.hidden && isPlaying.value) {
    videoRef.value?.pause()
    handleVideoPause()
  }
}

onMounted(() => {
  document.addEventListener('visibilitychange', handleVisibilityChange)
})

onBeforeUnmount(() => {
  videoRef.value?.pause()
  emitProgress()
  document.removeEventListener('visibilitychange', handleVisibilityChange)
})
</script>

<style scoped>
.video-box {
  width: 100%;
  background: black;
  border-radius: 12px;
  overflow: hidden;
  min-height: 300px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.video {
  width: 100%;
  max-height: 520px;
  object-fit: contain;
}

.loading {
  color: white;
  font-size: 16px;
}
</style>
