import { computed, ref } from 'vue'
import {
  createAgentSession,
  deleteAgentSession,
  listAgentMessages,
  listAgentSessions,
  renameAgentSession,
  sendAgentChat,
} from '@/api/agent'
import { notification } from '@/services/notification'
import {
  applyChatMessages,
  createClientMessageId,
  createLocalUserMessage,
  normalizeLoadedMessages,
  patchTrackedMessage,
  sameSession,
  unwrapData,
} from '../agentMessageModel'

export const AGENT_QUICK_PROMPTS = [
  '我接下来适合学什么？',
  '为什么推荐这些课程给我？',
  '我有哪些薄弱知识点需要先补？',
]

export function useAgentChat() {
  const sessions = ref([])
  const messages = ref([])
  const sources = ref([])
  const currentSessionId = ref(null)
  const draft = ref('')
  const loadingSessions = ref(false)
  const loadingMessages = ref(false)
  const sending = ref(false)
  const creating = ref(false)

  const currentSessionTitle = computed(() => {
    return sessions.value.find((session) => session.id === currentSessionId.value)?.title || ''
  })

  async function initialize() {
    await loadSessions()
    if (sessions.value.length) {
      await selectSession(sessions.value[0].id)
    }
  }

  async function loadSessions() {
    loadingSessions.value = true
    try {
      const response = await listAgentSessions()
      sessions.value = unwrapData(response) || []
    } catch (error) {
      showError(error, '获取会话失败')
    } finally {
      loadingSessions.value = false
    }
  }

  async function selectSession(sessionId) {
    currentSessionId.value = sessionId
    sources.value = []
    await loadMessages(sessionId)
  }

  async function loadMessages(sessionId) {
    if (!sessionId) {
      if (!currentSessionId.value) {
        messages.value = []
      }
      return
    }

    const requestedSessionId = sessionId
    loadingMessages.value = true
    try {
      const response = await listAgentMessages(requestedSessionId)
      if (sameSession(currentSessionId.value, requestedSessionId)) {
        messages.value = normalizeLoadedMessages(unwrapData(response) || [])
      }
    } catch (error) {
      if (sameSession(currentSessionId.value, requestedSessionId)) {
        showError(error, '获取消息失败')
      }
    } finally {
      if (sameSession(currentSessionId.value, requestedSessionId)) {
        loadingMessages.value = false
      }
    }
  }

  async function createSession() {
    creating.value = true
    try {
      const response = await createAgentSession('新的学习对话')
      const session = unwrapData(response)
      await loadSessions()
      if (session?.id) {
        await selectSession(session.id)
      }
    } catch (error) {
      showError(error, '创建会话失败')
    } finally {
      creating.value = false
    }
  }

  async function renameCurrentSession(title) {
    try {
      await renameAgentSession(currentSessionId.value, title.trim())
      await loadSessions()
      return true
    } catch (error) {
      showError(error, '重命名失败')
      return false
    }
  }

  async function removeCurrentSession() {
    try {
      const response = await deleteAgentSession(currentSessionId.value)
      unwrapData(response)
      currentSessionId.value = null
      messages.value = []
      sources.value = []
      await loadSessions()
      if (sessions.value.length) {
        await selectSession(sessions.value[0].id)
      }
      return true
    } catch (error) {
      showError(error, '删除失败')
      return false
    }
  }

  async function reloadCurrent() {
    await loadMessages(currentSessionId.value)
  }

  function usePrompt(prompt) {
    draft.value = prompt
  }

  async function sendMessage() {
    if (sending.value) {
      return
    }
    const content = draft.value.trim()
    if (!content) {
      notification.warn('请输入问题')
      return
    }

    const sendingSessionId = currentSessionId.value
    const clientMessageId = createClientMessageId()
    const localMessage = createLocalUserMessage(content, sendingSessionId, clientMessageId)
    messages.value = [...messages.value, localMessage]
    draft.value = ''
    await submitChat({
      sessionId: sendingSessionId,
      content,
      clientMessageId,
      localKey: localMessage.localKey,
    })
  }

  async function retryMessage(message) {
    if (sending.value) {
      return
    }
    if (!message?.clientMessageId || !message?.content) {
      notification.warn('无法重试该消息')
      return
    }

    const localKey = message.localKey || message.clientMessageId
    messages.value = patchTrackedMessage(messages.value, localKey, {
      status: 'sending',
      errorMessage: '',
    })
    await submitChat({
      sessionId: message.sessionId ?? null,
      content: message.content,
      clientMessageId: message.clientMessageId,
      localKey,
    })
  }

  async function submitChat({ sessionId, content, clientMessageId, localKey }) {
    const sendingSessionId = sessionId
    sending.value = true
    try {
      const response = await sendAgentChat({
        sessionId: sendingSessionId,
        message: content,
        clientMessageId,
      })
      const data = unwrapData(response)
      if (!data) {
        throw new Error('empty response')
      }
      if (shouldApplyChatResponse(sendingSessionId)) {
        currentSessionId.value = data.sessionId
        messages.value = applyChatMessages(messages.value, data, localKey)
        sources.value = data.sources || []
      }
      await loadSessions()
    } catch (error) {
      messages.value = patchTrackedMessage(messages.value, localKey, {
        status: 'failed',
        errorMessage: error?.message || '发送失败',
      })
      showError(error, '发送失败，请稍后重试')
    } finally {
      sending.value = false
    }
  }

  function shouldApplyChatResponse(sendingSessionId) {
    // 用户切到其他会话后，迟到响应只刷新会话列表，不再把当前视图拉回旧会话。
    if (sendingSessionId == null) {
      return currentSessionId.value == null
    }
    return sameSession(currentSessionId.value, sendingSessionId)
  }

  function showError(error, fallback) {
    notification.error(error?.response?.data?.msg || error?.message || fallback)
  }

  return {
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
  }
}
