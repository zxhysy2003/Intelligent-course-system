<template>
  <div class="course-player-wrapper">
    <el-card shadow="hover" class="player-card">
      <!-- 视频区域 -->
      <div class="video-box">
        <video
          v-if="playerVideoUrl"
          ref="videoRef"
          class="video"
          :src="playerVideoUrl"
          controls
          @error="handleVideoError"
          @loadedmetadata="handleLoadedMetadata"
          @play="handleVideoPlay"
          @pause="handleVideoPause"
          @timeupdate="handleTimeUpdate"
        />
        <div v-else class="loading">加载中...</div>
      </div>

      <!-- 标题区 -->
      <div class="meta">
        <div class="title-bar">
          <h2 class="title">{{ courseInfo.title }}</h2>
          <div class="actions-col">
            <el-button
              v-if="!hasEnrolled"
              class="action-btn"
              type="primary"
              @click="handleEnrollCourse"
            >
              加入课程
            </el-button>
            <el-button
              v-else
              class="action-btn"
              :type="userCourseRelation.isFavorite ? 'warning' : 'default'"
              :icon="Star"
              circle
              @click="handleFavorite"
            />
            <el-button class="action-btn graph-btn" type="primary" plain size="small" @click="goToKnowledgeGraph">
              查看图谱
            </el-button>
          </div>
        </div>
        <div v-if="courseInfo.description" class="description">{{ courseInfo.description }}</div>
        <div class="kp-section">
          <div class="kp-title">课程知识点</div>
          <div v-if="knowledgePoints.length" class="kp-list">
            <el-tag
              v-for="kp in knowledgePoints"
              :key="kp.id"
              effect="light"
              :style="getDifficultyStyle(kp.difficulty)"
            >
              {{ kp.name }} · 难度 {{ kp.difficulty }}
            </el-tag>
          </div>
          <div v-else class="kp-empty">暂无知识点数据</div>
        </div>
        <div class="watch-time">观看时长: {{ formatTime(totalWatchTime) }}</div>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { onBeforeUnmount, watch, ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router';
import { useUserStore } from "../../store/user";
import { setAuthTokenToCookie, clearAuthTokenCookie } from "../../utils/authCookie";
import { logger } from '../../utils/logger';
import { Star } from '@element-plus/icons-vue';
import { RecordLearningBehavior } from '../../api/learningBehavior';
import {
  GetUserCourseRelation,
  UpdateCourseVideoProgressSeconds,
  GetCourseById,
  UserAttendCourse,
  GetKnowledgePointsByCourse
} from '../../api/course';

const store = useUserStore();
const router = useRouter();
const videoRef = ref(null);
const playerVideoUrl = ref('');

const courseInfo = reactive({});

const props = defineProps({
  videoUrl: {
    type: String,
    required: true
  },
  courseId: {
    type: [String, Number],
    required: true
  }
})

// 记录是否已发送过播放行为
const hasRecordedView = ref(false);

// 用户是否已注册该课程
const hasEnrolled = ref(true);

const userCourseRelation = reactive({
  // 与收藏相关
  isFavorite: false,
  progressSeconds: 0, // 断点续播的秒数

});

const knowledgePoints = ref([]);


// 观看时间相关
const totalWatchTime = ref(0); // 总观看时长（秒）
const lastRecordTime = ref(0); // 上次记录的时间戳
const isPlaying = ref(false); // 当前是否在播放

// 监听页面可见性变化，暂停视频播放
const handleVisibilityChange = () => {
  if (document.hidden && isPlaying.value) {
    const video = videoRef.value;
    if (video) video.pause();
    handleVideoPause();
  }
};

// 当视频URL变化时设置认证Cookie
watch(() => props.videoUrl, (newUrl) => {
  if (newUrl) {
    const token = store.token;
    if (token) {
      setAuthTokenToCookie(token, 10); // 设置Cookie，过期时间10分钟 
      // console.log('设置视频认证 token:', newUrl);
    } else {
      // console.warn('未找到用户 token');
      clearAuthTokenCookie();
    }

    // 在写入鉴权 Cookie 后再设置视频地址，避免首个视频请求抢在 Cookie 生效前发出。
    playerVideoUrl.value = newUrl;
  } else {
    playerVideoUrl.value = '';
  }
  // 暂停当前视频播放
  const video = videoRef.value;
  if (video) {
    video.pause();
  }
  // 重置标志位（切换视频时）
  hasRecordedView.value = false;
  totalWatchTime.value = 0;
  lastRecordTime.value = 0;
  isPlaying.value = false;
}, { immediate: true });

// 视频加载错误处理
const handleVideoError = (e) => {
  logger.error('视频加载失败，请检查网络或权限', e);
};

// 视频播放事件处理
const handleVideoPlay = () => {
  isPlaying.value = true;
  const video = videoRef.value;
  if (video) {
    lastRecordTime.value = video.currentTime;
  } 
};

// 视频暂停事件处理
const handleVideoPause = () => {
  isPlaying.value = false;
  const video = videoRef.value;
  if (video) {
    lastRecordTime.value = video.currentTime;
  }
};

// 时间更新事件（用于计算观看时长）
const handleTimeUpdate = () => {
  const video = videoRef.value;
  if (!video || !isPlaying.value) return;
  const currentTime = video.currentTime;
  const delta = currentTime - lastRecordTime.value;
  
  // 只累加合理的时间差（防止拖拽等导致的异常值）
  if (delta > 0 && delta < 3) {
    totalWatchTime.value += delta;
  }

  if (!hasRecordedView.value && totalWatchTime.value >= 10) {
    hasRecordedView.value = true;
    sendViewRecord();
  }
    
  lastRecordTime.value = currentTime;
  
};

// 视频元数据加载后设置断点续播时间
const handleLoadedMetadata = () => {
  const video = videoRef.value;
  if (video && userCourseRelation.progressSeconds > 0) {
    video.currentTime = userCourseRelation.progressSeconds;
  }
};

// 发送首次观看记录
const sendViewRecord = async () => {
  try {
    const res = await RecordLearningBehavior({
      courseId: Number(props.courseId),
      behaviorType: 'VIEW',
    });
    if (res.data.code !== 200) {
      hasRecordedView.value = false; // 重置标志位，允许重试
      logger.error('记录学习行为失败', res.data.msg);
    }
  } catch (error) {
    logger.error('记录学习行为出错', error);
    hasRecordedView.value = false; // 重置标志位，允许重试
  }
};

// 收藏按钮点击事件处理
const handleFavorite = async () => {
  try {
    // 先确定目标状态，但不更新 UI
    const targetFavoriteState = !userCourseRelation.isFavorite;
    const behaviorType = targetFavoriteState ? 'FAVORITE' : 'UNFAVORITE';
    
    const res = await RecordLearningBehavior({
      courseId: Number(props.courseId),
      behaviorType: behaviorType,
    });
    
    if (res.data.code !== 200) {
      logger.error('操作失败，请重试', res.data.msg);
    } else {
      // 请求成功后才更新 UI
      userCourseRelation.isFavorite = targetFavoriteState;
      const message = userCourseRelation.isFavorite ? '已收藏' : '已取消收藏';
      logger.success(message);
      logger.debug('收藏操作成功', behaviorType);
    }
  } catch (error) {
    logger.error('操作失败，请重试', error);
  }
};

// 加入课程按钮点击事件处理
const handleEnrollCourse = async () => {
  // 调用注册课程 API，成功后设置 hasEnrolled.value = true
  try{
    const res = await UserAttendCourse(props.courseId);
    if (res.data.code === 200) {
      hasEnrolled.value = true;
      logger.success('成功加入课程');
    } else {
      logger.error('加入课程失败', res.data.msg);
    }
  } catch (e) {
    logger.error('加入课程异常', e);
  }
};

const goToKnowledgeGraph = () => {
  router.push({
    path: '/graph',
    query: { courseId: String(props.courseId) }
  });
};

// 格式化时间显示（秒 -> HH:MM:SS）
const formatTime = (seconds) => {
  const h = Math.floor(seconds / 3600);
  const m = Math.floor((seconds % 3600) / 60);
  const s = Math.floor(seconds % 60);
  return `${h.toString().padStart(2, '0')}:${m.toString().padStart(2, '0')}:${s.toString().padStart(2, '0')}`;
};

const difficultyStyleMap = {
  1: { backgroundColor: '#f1f8e9', borderColor: '#c5e1a5', color: '#33691e' },
  2: { backgroundColor: '#e3f2fd', borderColor: '#90caf9', color: '#0d47a1' },
  3: { backgroundColor: '#fff3e0', borderColor: '#ffcc80', color: '#e65100' },
  4: { backgroundColor: '#ffebee', borderColor: '#ef9a9a', color: '#b71c1c' },
};

const getDifficultyStyle = (difficulty) => {
  return difficultyStyleMap[difficulty] || difficultyStyleMap[1];
};

// 页面加载时可以发送一些初始化请求，比如获取用户与课程的关系等(收藏相关，进度条断点记录和恢复等)
onMounted( async () => {
  document.addEventListener('visibilitychange', handleVisibilityChange);
  try {
    const res = await GetUserCourseRelation(props.courseId);
    if (res.data.code === 200 && res.data.data) {
      // 用户已注册该课程，有课程关系信息
      hasEnrolled.value = true;
      userCourseRelation.isFavorite = res.data.data.isFavorite;
      userCourseRelation.progressSeconds = res.data.data.progressSeconds || 0;
    } else if (res.data.code === 200 && !res.data.data) {
      // 用户未注册该课程
      hasEnrolled.value = false;
    } else {
      logger.error('获取用户课程关系失败', res.data.msg);
      hasEnrolled.value = false;
    }
  } catch (e) {
    logger.error('获取用户课程关系出错', e);
    hasEnrolled.value = false;
  }
  // 设置该课程的详细信息
  try {
    const res = await GetCourseById(props.courseId);
    if (res.data.code === 200) {
      Object.assign(courseInfo, res.data.data);
    } else {
      logger.error('获取课程信息失败', res.data.msg);
    }
  } catch (e) {
    logger.error('获取课程信息出错', e);
  }
  // 获取课程知识点并按难度渲染
  try {
    const res = await GetKnowledgePointsByCourse(props.courseId);
    if (res.data.code === 200) {
      knowledgePoints.value = Array.isArray(res.data.data) ? res.data.data : [];
    } else {
      knowledgePoints.value = [];
      logger.error('获取课程知识点失败', res.data.msg);
    }
  } catch (e) {
    knowledgePoints.value = [];
    logger.error('获取课程知识点出错', e);
  }
});

// 离开页面前发送观看时长记录
onBeforeUnmount(async () => {
  const video = videoRef.value;
  if (video) {
    video.pause();
  }
  // 发送观看时长记录
  if (hasEnrolled.value && hasRecordedView.value && totalWatchTime.value > 0) {
    try {
      // 发送观看时长记录（不一定是完成）
      await RecordLearningBehavior({
        courseId: Number(props.courseId),
        behaviorType: 'STUDY',
        duration: Math.round(totalWatchTime.value)
      });
      logger.debug('离开前记录观看时长', Math.round(totalWatchTime.value));
    } catch (e) {
      logger.error('记录观看时长失败', e);
    }
  }
  // 发送断点续播时间
  if (video && hasEnrolled.value) {
    const currentTime = Math.floor(video.currentTime);
    try {
      await UpdateCourseVideoProgressSeconds({
        courseId: Number(props.courseId),
        progressSeconds: currentTime
      });
      logger.debug('更新断点续播时间', currentTime);
    } catch (e) {
      logger.error('更新断点续播时间失败', e);
    }
  }
  // 清除认证Cookie
  clearAuthTokenCookie();
  document.removeEventListener('visibilitychange', handleVisibilityChange);
});

</script>

<style scoped>
.course-player-wrapper {
  padding: 20px;
}

.player-card {
  border-radius: 16px;
}

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

.actions-col {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 10px;
  min-width: 110px;
}

.action-btn {
  transition: transform 0.15s ease, box-shadow 0.15s ease;
}

.action-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 10px rgba(31, 42, 68, 0.12);
}

.graph-btn {
  min-width: 92px;
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

.kp-section {
  margin-top: 10px;
}

.kp-title {
  font-size: 13px;
  color: #666;
  margin-bottom: 8px;
}

.kp-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.kp-empty {
  font-size: 13px;
  color: #999;
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

  .actions-col {
    flex-direction: row;
    align-items: center;
    justify-content: flex-start;
    flex-wrap: wrap;
    min-width: 0;
  }
}
</style>
