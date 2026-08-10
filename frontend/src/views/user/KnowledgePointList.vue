<template>
  <div class="kp-section">
    <div class="kp-title">课程知识点</div>
    <div v-if="knowledgePoints.length" class="kp-list">
      <el-tag
        v-for="knowledgePoint in knowledgePoints"
        :key="knowledgePoint.id"
        effect="light"
        :style="getDifficultyStyle(knowledgePoint.difficulty)"
      >
        {{ knowledgePoint.name }} · 难度 {{ knowledgePoint.difficulty }}
      </el-tag>
    </div>
    <div v-else class="kp-empty">暂无知识点数据</div>
  </div>
</template>

<script setup>
defineProps({
  knowledgePoints: {
    type: Array,
    default: () => [],
  },
})

const difficultyStyleMap = {
  1: { backgroundColor: '#f1f8e9', borderColor: '#c5e1a5', color: '#33691e' },
  2: { backgroundColor: '#e3f2fd', borderColor: '#90caf9', color: '#0d47a1' },
  3: { backgroundColor: '#fff3e0', borderColor: '#ffcc80', color: '#e65100' },
  4: { backgroundColor: '#ffebee', borderColor: '#ef9a9a', color: '#b71c1c' },
}

const getDifficultyStyle = (difficulty) => difficultyStyleMap[difficulty] || difficultyStyleMap[1]
</script>

<style scoped>
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
</style>
