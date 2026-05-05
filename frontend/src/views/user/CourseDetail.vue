<template>
  <div v-if="loading" class="loading-container">
    <el-icon class="is-loading"><Loading /></el-icon>
    <span>加载中...</span>
  </div>
  <CourseMediaPlayer
    v-else-if="courseVideoUrl"
    :videoUrl="courseVideoUrl"
    :courseId="courseId"
  />
  <el-empty v-else description="暂无视频资源" />
</template>

<script setup>
  import CourseMediaPlayer from './CourseMediaPlayer.vue';
  import { useRoute } from 'vue-router';
  import { GetCourseVideo } from '../../api/course';
  import { onMounted, ref } from 'vue';
  import { logger } from '../../utils/logger';
  import { Loading } from '@element-plus/icons-vue';

  const route = useRoute();
  const loading = ref(true);
  
  const courseId = route.params.id; // 从路由参数中获取课程ID
  
  const courseVideoUrl = ref(""); // 课程视频URL

  onMounted(async () => {
    if (!courseId) {
      logger.error('课程ID不存在');
      loading.value = false;
      return;
    }
    
    try {
      const response = await GetCourseVideo(courseId);
      if(response.data.code === 200) {
        courseVideoUrl.value = String(response.data.data || '');
      } else {
        logger.error(`获取课程视频失败: ${response.data.msg}`, response.data);
      }
    } catch (error) {
      logger.error('获取课程视频异常，请稍后重试', error);
    } finally {
      loading.value = false;
    }
  });
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
</style>
