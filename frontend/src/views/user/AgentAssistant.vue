<template>
  <div class="agent-page">
    <aside class="session-panel">
      <div class="panel-header">
        <div>
          <h2>学习助手</h2>
          <span>历史会话</span>
        </div>
        <el-button type="primary" :icon="Plus" circle @click="createSession" :loading="creating" />
      </div>

      <div v-loading="loadingSessions" class="session-list">
        <button
          v-for="session in sessions"
          :key="session.id"
          class="session-item"
          :class="{ active: session.id === currentSessionId }"
          @click="selectSession(session.id)"
        >
          <span class="session-title">{{ session.title }}</span>
          <span class="session-time">{{ formatDateTime(session.updateTime) }}</span>
        </button>
        <el-empty v-if="!loadingSessions && sessions.length === 0" description="暂无会话" :image-size="88" />
      </div>

      <div class="session-actions" v-if="currentSessionId">
        <el-button :icon="EditPen" plain @click="renameSession">重命名</el-button>
        <el-button :icon="Delete" plain type="danger" @click="removeSession">删除</el-button>
      </div>
    </aside>

    <main class="chat-panel">
      <div class="chat-header">
        <div>
          <h3>{{ currentSessionTitle || "新的学习对话" }}</h3>
          <p>根据课程、推荐、进度和知识图谱给出只读学习建议</p>
        </div>
        <el-button :icon="Refresh" plain @click="reloadCurrent" :disabled="!currentSessionId || loadingMessages">
          刷新
        </el-button>
      </div>

      <div class="chat-body" v-loading="loadingMessages">
        <div v-if="messages.length === 0 && !loadingMessages" class="welcome-state">
          <el-icon><MagicStick /></el-icon>
          <h3>可以直接问学习路径、推荐原因或薄弱点</h3>
          <div class="quick-prompts">
            <button v-for="prompt in quickPrompts" :key="prompt" @click="usePrompt(prompt)">
              {{ prompt }}
            </button>
          </div>
        </div>

        <div v-for="message in messages" :key="message.id || `${message.role}-${message.createTime}`" class="message-row" :class="message.role.toLowerCase()">
          <div class="message-avatar">
            <el-icon v-if="message.role === 'ASSISTANT'"><MagicStick /></el-icon>
            <el-icon v-else><User /></el-icon>
          </div>
          <div class="message-bubble">
            <div class="message-meta">
              <span>{{ message.role === "ASSISTANT" ? "学习助手" : "我" }}</span>
              <time>{{ formatDateTime(message.createTime) }}</time>
            </div>
            <p>{{ message.content }}</p>
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

      <div class="source-strip" v-if="sources.length">
        <div v-for="source in sources" :key="`${source.type}-${source.referenceId}`" class="source-item">
          <span>{{ source.title }}</span>
          <small>{{ source.summary }}</small>
        </div>
      </div>

      <div class="composer">
        <el-input
          v-model="draft"
          type="textarea"
          :autosize="{ minRows: 2, maxRows: 5 }"
          maxlength="2000"
          show-word-limit
          placeholder="问问下一步学什么、为什么推荐这门课、哪些知识点需要先补齐"
          @keydown.ctrl.enter.prevent="sendMessage"
          @keydown.meta.enter.prevent="sendMessage"
        />
        <el-button type="primary" :icon="Promotion" @click="sendMessage" :loading="sending">
          发送
        </el-button>
      </div>
    </main>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from "vue";
import { ElMessage, ElMessageBox } from "element-plus";
import { Delete, EditPen, MagicStick, Plus, Promotion, Refresh, User } from "@element-plus/icons-vue";
import {
  CreateAgentSession,
  DeleteAgentSession,
  ListAgentMessages,
  ListAgentSessions,
  RenameAgentSession,
  SendAgentChat,
} from "@/api/agent";

const sessions = ref([]);
const messages = ref([]);
const sources = ref([]);
const currentSessionId = ref(null);
const draft = ref("");
const loadingSessions = ref(false);
const loadingMessages = ref(false);
const sending = ref(false);
const creating = ref(false);

const quickPrompts = [
  "我接下来适合学什么？",
  "为什么推荐这些课程给我？",
  "我有哪些薄弱知识点需要先补？",
];

const currentSessionTitle = computed(() => {
  return sessions.value.find((session) => session.id === currentSessionId.value)?.title || "";
});

onMounted(async () => {
  await loadSessions();
  if (sessions.value.length) {
    await selectSession(sessions.value[0].id);
  }
});

async function loadSessions() {
  loadingSessions.value = true;
  try {
    const res = await ListAgentSessions();
    sessions.value = unwrapData(res) || [];
  } catch (e) {
    showError(e, "获取会话失败");
  } finally {
    loadingSessions.value = false;
  }
}

async function selectSession(sessionId) {
  currentSessionId.value = sessionId;
  sources.value = [];
  await loadMessages(sessionId);
}

async function loadMessages(sessionId) {
  if (!sessionId) {
    if (!currentSessionId.value) {
      messages.value = [];
    }
    return;
  }
  const requestedSessionId = sessionId;
  loadingMessages.value = true;
  try {
    const res = await ListAgentMessages(requestedSessionId);
    if (sameSession(currentSessionId.value, requestedSessionId)) {
      messages.value = unwrapData(res) || [];
    }
  } catch (e) {
    if (sameSession(currentSessionId.value, requestedSessionId)) {
      showError(e, "获取消息失败");
    }
  } finally {
    if (sameSession(currentSessionId.value, requestedSessionId)) {
      loadingMessages.value = false;
    }
  }
}

async function createSession() {
  creating.value = true;
  try {
    const res = await CreateAgentSession("新的学习对话");
    const session = unwrapData(res);
    await loadSessions();
    if (session?.id) {
      await selectSession(session.id);
    }
  } catch (e) {
    showError(e, "创建会话失败");
  } finally {
    creating.value = false;
  }
}

async function renameSession() {
  const currentTitle = currentSessionTitle.value || "新的学习对话";
  try {
    const { value } = await ElMessageBox.prompt("输入新的会话标题", "重命名", {
      inputValue: currentTitle,
      inputValidator: (value) => Boolean(value && value.trim()),
      inputErrorMessage: "标题不能为空",
    });
    await RenameAgentSession(currentSessionId.value, value.trim());
    await loadSessions();
  } catch (e) {
    if (e !== "cancel") {
      showError(e, "重命名失败");
    }
  }
}

async function removeSession() {
  try {
    await ElMessageBox.confirm("删除后会话将不再显示，历史消息不会出现在列表中。", "删除会话", {
      type: "warning",
    });
    const res = await DeleteAgentSession(currentSessionId.value);
    unwrapData(res);
    currentSessionId.value = null;
    messages.value = [];
    sources.value = [];
    await loadSessions();
    if (sessions.value.length) {
      await selectSession(sessions.value[0].id);
    }
  } catch (e) {
    if (e !== "cancel") {
      showError(e, "删除失败");
    }
  }
}

async function reloadCurrent() {
  await loadMessages(currentSessionId.value);
}

function usePrompt(prompt) {
  draft.value = prompt;
}

async function sendMessage() {
  const content = draft.value.trim();
  if (!content) {
    ElMessage.warning("请输入问题");
    return;
  }
  const sendingSessionId = currentSessionId.value;
  sending.value = true;
  draft.value = "";
  try {
    const res = await SendAgentChat({
      sessionId: sendingSessionId,
      message: content,
    });
    const data = unwrapData(res);
    if (!data) {
      throw new Error("empty response");
    }
    if (sendingSessionId == null || sameSession(currentSessionId.value, sendingSessionId)) {
      currentSessionId.value = data.sessionId;
      appendChatMessages(data);
      sources.value = data.sources || [];
    }
    await loadSessions();
  } catch (e) {
    draft.value = content;
    showError(e, "发送失败，请稍后重试");
  } finally {
    sending.value = false;
  }
}

function appendChatMessages(data) {
  messages.value = [
    ...messages.value,
    data.userMessage,
    data.assistantMessage,
  ].filter(Boolean);
}

function sameSession(left, right) {
  return String(left ?? "") === String(right ?? "");
}

function unwrapData(res) {
  const body = res?.data;
  if (!body || body.code !== 200) {
    throw new Error(body?.msg || "请求失败");
  }
  return body.data;
}

function showError(error, fallback) {
  ElMessage.error(error?.response?.data?.msg || error?.message || fallback);
}

function formatDateTime(value) {
  if (!value) {
    return "";
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return "";
  }
  const mm = String(date.getMonth() + 1).padStart(2, "0");
  const dd = String(date.getDate()).padStart(2, "0");
  const hh = String(date.getHours()).padStart(2, "0");
  const mi = String(date.getMinutes()).padStart(2, "0");
  return `${mm}-${dd} ${hh}:${mi}`;
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

.session-panel,
.chat-panel {
  background: #ffffff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  min-height: 0;
}

.session-panel {
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.panel-header,
.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 18px;
  border-bottom: 1px solid #e5e7eb;
}

.panel-header h2,
.chat-header h3 {
  margin: 0;
  font-size: 18px;
  line-height: 1.3;
}

.panel-header span,
.chat-header p {
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

.chat-panel {
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

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

.composer {
  border-top: 1px solid #e5e7eb;
  padding: 14px;
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 10px;
  align-items: end;
  background: #ffffff;
}

@media (max-width: 980px) {
  .agent-page {
    grid-template-columns: 1fr;
  }

  .session-panel {
    min-height: 260px;
  }

  .message-bubble {
    max-width: 86%;
  }
}
</style>
