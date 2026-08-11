<template>
  <div class="agent-page">
    <AgentSessionPanel
      :sessions="sessions"
      :current-session-id="currentSessionId"
      :loading="loadingSessions"
      :creating="creating"
      @create="createSession"
      @select="selectSession"
      @rename="handleRenameSession"
      @remove="handleRemoveSession"
    />

    <main class="chat-panel">
      <div class="chat-header">
        <div>
          <h3>{{ currentSessionTitle || '新的学习对话' }}</h3>
          <p>根据课程、推荐、进度和知识图谱给出只读学习建议</p>
        </div>
        <el-button
          :icon="Refresh"
          plain
          :disabled="!currentSessionId || loadingMessages"
          @click="reloadCurrent"
        >
          刷新
        </el-button>
      </div>

      <AgentMessageList
        :messages="messages"
        :quick-prompts="AGENT_QUICK_PROMPTS"
        :loading="loadingMessages"
        :sending="sending"
        @use-prompt="usePrompt"
        @retry="retryMessage"
      />

      <div v-if="sources.length" class="source-strip">
        <div
          v-for="source in sources"
          :key="`${source.type}-${source.referenceId}`"
          class="source-item"
        >
          <span>{{ source.title }}</span>
          <small>{{ source.summary }}</small>
        </div>
      </div>

      <AgentComposer v-model="draft" :sending="sending" @send="sendMessage" />
    </main>
  </div>
</template>

<script setup>
import { onMounted } from 'vue'
import { ElMessageBox } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import AgentComposer from '@/features/agent/components/AgentComposer.vue'
import AgentMessageList from '@/features/agent/components/AgentMessageList.vue'
import AgentSessionPanel from '@/features/agent/components/AgentSessionPanel.vue'
import {
  AGENT_QUICK_PROMPTS,
  useAgentChat,
} from '@/features/agent/composables/useAgentChat'
import { notification } from '@/services/notification'

const {
  sessions,
  messages,
  sources,
  currentSessionId,
  currentSessionTitle,
  draft,
  loadingSessions,
  loadingMessages,
  sending,
  creating,
  initialize,
  selectSession,
  createSession,
  renameCurrentSession,
  removeCurrentSession,
  reloadCurrent,
  usePrompt,
  sendMessage,
  retryMessage,
} = useAgentChat()

onMounted(initialize)

async function handleRenameSession() {
  const currentTitle = currentSessionTitle.value || '新的学习对话'
  try {
    const { value } = await ElMessageBox.prompt('输入新的会话标题', '重命名', {
      inputValue: currentTitle,
      inputValidator: (title) => Boolean(title && title.trim()),
      inputErrorMessage: '标题不能为空',
    })
    await renameCurrentSession(value.trim())
  } catch (error) {
    if (error !== 'cancel') {
      notification.error(error?.message || '重命名失败')
    }
  }
}

async function handleRemoveSession() {
  try {
    await ElMessageBox.confirm('删除后会话将不再显示，历史消息不会出现在列表中。', '删除会话', {
      type: 'warning',
    })
    await removeCurrentSession()
  } catch (error) {
    if (error !== 'cancel') {
      notification.error(error?.message || '删除失败')
    }
  }
}
</script>

<style scoped>
.agent-page {
  min-height: calc(100vh - 104px);
  display: grid;
  grid-template-columns: 280px minmax(0, 1fr);
  gap: 16px;
  color: #1f2937;
}

.chat-panel {
  background: #ffffff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 18px;
  border-bottom: 1px solid #e5e7eb;
}

.chat-header h3 {
  margin: 0;
  font-size: 18px;
  line-height: 1.3;
}

.chat-header p {
  margin: 4px 0 0;
  color: #6b7280;
  font-size: 13px;
}

.source-strip {
  border-top: 1px solid #e5e7eb;
  padding: 10px 14px;
  display: flex;
  gap: 8px;
  overflow-x: auto;
  background: #ffffff;
}

.source-item {
  flex: 0 0 auto;
  border: 1px solid #dbeafe;
  background: #eff6ff;
  border-radius: 6px;
  padding: 8px 10px;
  display: flex;
  flex-direction: column;
  gap: 3px;
  max-width: 220px;
}

.source-item span {
  font-size: 13px;
  color: #1d4ed8;
  font-weight: 600;
}

.source-item small {
  color: #4b5563;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (max-width: 980px) {
  .agent-page {
    grid-template-columns: 1fr;
  }
}
</style>
