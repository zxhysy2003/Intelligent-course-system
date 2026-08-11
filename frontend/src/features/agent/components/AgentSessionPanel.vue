<template>
  <aside class="session-panel">
    <div class="panel-header">
      <div>
        <h2>学习助手</h2>
        <span>历史会话</span>
      </div>
      <el-button type="primary" :icon="Plus" circle :loading="creating" @click="emit('create')" />
    </div>

    <div v-loading="loading" class="session-list">
      <button
        v-for="session in sessions"
        :key="session.id"
        class="session-item"
        :class="{ active: session.id === currentSessionId }"
        @click="emit('select', session.id)"
      >
        <span class="session-title">{{ session.title }}</span>
        <span class="session-time">{{ formatDateTime(session.updateTime) }}</span>
      </button>
      <el-empty v-if="!loading && sessions.length === 0" description="暂无会话" :image-size="88" />
    </div>

    <div v-if="currentSessionId" class="session-actions">
      <el-button :icon="EditPen" plain @click="emit('rename')">重命名</el-button>
      <el-button :icon="Delete" plain type="danger" @click="emit('remove')">删除</el-button>
    </div>
  </aside>
</template>

<script setup>
import { Delete, EditPen, Plus } from '@element-plus/icons-vue'
import { formatDateTime } from '../agentMessageModel'

defineProps({
  sessions: {
    type: Array,
    default: () => [],
  },
  currentSessionId: {
    type: [Number, String],
    default: null,
  },
  loading: {
    type: Boolean,
    default: false,
  },
  creating: {
    type: Boolean,
    default: false,
  },
})

const emit = defineEmits(['create', 'select', 'rename', 'remove'])
</script>

<style scoped>
.session-panel {
  background: #ffffff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 18px;
  border-bottom: 1px solid #e5e7eb;
}

.panel-header h2 {
  margin: 0;
  font-size: 18px;
  line-height: 1.3;
}

.panel-header span {
  margin: 4px 0 0;
  color: #6b7280;
  font-size: 13px;
}

.session-list {
  flex: 1;
  overflow-y: auto;
  padding: 10px;
}

.session-item {
  width: 100%;
  border: 1px solid transparent;
  background: transparent;
  text-align: left;
  padding: 10px;
  border-radius: 6px;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.session-item:hover,
.session-item.active {
  background: #eef6ff;
  border-color: #bfdbfe;
}

.session-title {
  font-size: 14px;
  color: #111827;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.session-time {
  font-size: 12px;
  color: #6b7280;
}

.session-actions {
  padding: 12px;
  border-top: 1px solid #e5e7eb;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}

@media (max-width: 980px) {
  .session-panel {
    min-height: 260px;
  }
}
</style>
