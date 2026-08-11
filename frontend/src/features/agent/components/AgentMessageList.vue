<template>
  <div v-loading="loading" class="chat-body">
    <div v-if="messages.length === 0 && !loading" class="welcome-state">
      <el-icon><MagicStick /></el-icon>
      <h3>可以直接问学习路径、推荐原因或薄弱点</h3>
      <div class="quick-prompts">
        <button v-for="prompt in quickPrompts" :key="prompt" @click="emit('use-prompt', prompt)">
          {{ prompt }}
        </button>
      </div>
    </div>

    <div
      v-for="message in messages"
      :key="message.id || message.localKey || message.clientMessageId || `${message.role}-${message.createTime}`"
      class="message-row"
      :class="[message.role.toLowerCase(), message.status]"
    >
      <div class="message-avatar">
        <el-icon v-if="message.role === 'ASSISTANT'"><MagicStick /></el-icon>
        <el-icon v-else><User /></el-icon>
      </div>
      <div class="message-bubble">
        <div class="message-meta">
          <span>{{ message.role === 'ASSISTANT' ? '学习助手' : '我' }}</span>
          <time>{{ formatDateTime(message.createTime) }}</time>
          <span v-if="message.status === 'sending'">发送中</span>
          <span v-else-if="message.status === 'failed'">
            {{ message.errorMessage || '发送失败' }}
          </span>
        </div>
        <p>{{ message.content }}</p>
        <div v-if="canRetryMessage(message)" class="message-actions">
          <el-button
            size="small"
            :icon="Refresh"
            :loading="sending"
            @click="emit('retry', message)"
          >
            重试
          </el-button>
        </div>
      </div>
    </div>

    <div v-if="sending" class="message-row assistant pending">
      <div class="message-avatar">
        <el-icon><MagicStick /></el-icon>
      </div>
      <div class="message-bubble">
        <div class="message-meta">
          <span>学习助手</span>
        </div>
        <p>正在读取学习上下文并生成建议...</p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { MagicStick, Refresh, User } from '@element-plus/icons-vue'
import { canRetryMessage, formatDateTime } from '../agentMessageModel'

defineProps({
  messages: {
    type: Array,
    default: () => [],
  },
  quickPrompts: {
    type: Array,
    default: () => [],
  },
  loading: {
    type: Boolean,
    default: false,
  },
  sending: {
    type: Boolean,
    default: false,
  },
})

const emit = defineEmits(['use-prompt', 'retry'])
</script>

<style scoped>
.chat-body {
  flex: 1;
  overflow-y: auto;
  padding: 22px;
  background: #f8fafc;
}

.welcome-state {
  min-height: 300px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 14px;
  color: #4b5563;
  text-align: center;
}

.welcome-state .el-icon {
  font-size: 40px;
  color: #2563eb;
}

.welcome-state h3 {
  margin: 0;
  font-size: 18px;
  color: #111827;
}

.quick-prompts {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 8px;
}

.quick-prompts button {
  border: 1px solid #d1d5db;
  background: #ffffff;
  border-radius: 6px;
  padding: 8px 10px;
  cursor: pointer;
  color: #374151;
}

.message-row {
  display: flex;
  gap: 10px;
  margin-bottom: 18px;
}

.message-row.user {
  flex-direction: row-reverse;
}

.message-avatar {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  background: #dbeafe;
  color: #1d4ed8;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.message-row.user .message-avatar {
  background: #e5e7eb;
  color: #374151;
}

.message-bubble {
  max-width: min(720px, 78%);
  background: #ffffff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 12px 14px;
  box-shadow: 0 4px 14px rgba(15, 23, 42, 0.04);
}

.message-row.user .message-bubble {
  background: #2563eb;
  color: #ffffff;
  border-color: #2563eb;
}

.message-row.user.failed .message-bubble {
  background: #b91c1c;
  border-color: #b91c1c;
}

.message-row.user.sending .message-bubble {
  opacity: 0.82;
}

.message-meta {
  display: flex;
  gap: 10px;
  align-items: center;
  font-size: 12px;
  color: #6b7280;
  margin-bottom: 8px;
}

.message-row.user .message-meta {
  color: rgba(255, 255, 255, 0.78);
}

.message-bubble p {
  margin: 0;
  white-space: pre-wrap;
  line-height: 1.7;
  font-size: 14px;
}

.message-actions {
  margin-top: 10px;
  display: flex;
  justify-content: flex-end;
}

@media (max-width: 980px) {
  .message-bubble {
    max-width: 86%;
  }
}
</style>
