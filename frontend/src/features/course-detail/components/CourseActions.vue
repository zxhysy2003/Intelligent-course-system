<template>
  <div class="actions-col">
    <el-button
      v-if="enrollmentStatus === ENROLLMENT_STATUS.LOADING"
      class="action-btn"
      type="primary"
      loading
      disabled
    >
      加载选课状态
    </el-button>
    <el-button
      v-else-if="enrollmentStatus === ENROLLMENT_STATUS.ERROR"
      class="action-btn"
      type="warning"
      plain
      @click="emit('retry-relation')"
    >
      重试选课状态
    </el-button>
    <el-button
      v-else-if="enrollmentStatus === ENROLLMENT_STATUS.NOT_ENROLLED"
      class="action-btn"
      type="primary"
      :loading="enrollLoading"
      :disabled="enrollLoading"
      @click="emit('enroll')"
    >
      加入课程
    </el-button>
    <el-button
      v-else
      class="action-btn"
      :type="isFavorite ? 'warning' : 'default'"
      :icon="Star"
      :loading="favoriteLoading"
      :disabled="favoriteLoading"
      circle
      aria-label="切换收藏状态"
      @click="emit('toggle-favorite')"
    />
    <el-button
      class="action-btn graph-btn"
      type="primary"
      plain
      size="small"
      @click="emit('open-graph')"
    >
      查看图谱
    </el-button>
  </div>
</template>

<script setup>
import { Star } from '@element-plus/icons-vue'
import { ENROLLMENT_STATUS, ENROLLMENT_STATUS_VALUES } from '../enrollmentStatus'

defineProps({
  enrollmentStatus: {
    type: String,
    required: true,
    validator: (value) => ENROLLMENT_STATUS_VALUES.includes(value),
  },
  isFavorite: {
    type: Boolean,
    required: true,
  },
  enrollLoading: {
    type: Boolean,
    default: false,
  },
  favoriteLoading: {
    type: Boolean,
    default: false,
  },
})

const emit = defineEmits(['enroll', 'toggle-favorite', 'open-graph', 'retry-relation'])
</script>

<style scoped>
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

@media (max-width: 768px) {
  .actions-col {
    flex-direction: row;
    align-items: center;
    justify-content: flex-start;
    flex-wrap: wrap;
    min-width: 0;
  }
}
</style>
