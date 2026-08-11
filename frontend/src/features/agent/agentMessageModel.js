export function normalizeLoadedMessages(loadedMessages) {
  const answeredClientMessageIds = new Set(
    loadedMessages
      .filter((message) => message.role === 'ASSISTANT' && message.clientMessageId)
      .map((message) => message.clientMessageId),
  )

  return loadedMessages.map((message) => {
    const canRestore =
      message.role === 'USER' &&
      message.clientMessageId &&
      !answeredClientMessageIds.has(message.clientMessageId)

    if (!canRestore) {
      return message
    }

    return {
      ...message,
      status: 'failed',
      errorMessage: '回答未完成，可重试',
    }
  })
}

export function applyChatMessages(messages, data, localKey) {
  const userMessage = data.userMessage
  const assistantMessage = data.assistantMessage
  const insertIndex = messages.findIndex((message) => {
    return isTrackedMessage(message, localKey) || (userMessage?.id && message.id === userMessage.id)
  })
  const nextMessages = messages.filter((message) => {
    return (
      !isTrackedMessage(message, localKey) &&
      (!userMessage?.id || message.id !== userMessage.id) &&
      (!assistantMessage?.id || message.id !== assistantMessage.id)
    )
  })
  const normalizedIndex =
    insertIndex >= 0 ? Math.min(insertIndex, nextMessages.length) : nextMessages.length
  nextMessages.splice(normalizedIndex, 0, ...[userMessage, assistantMessage].filter(Boolean))
  return nextMessages
}

export function patchTrackedMessage(messages, localKey, patch) {
  return messages.map((message) => {
    return isTrackedMessage(message, localKey) ? { ...message, ...patch } : message
  })
}

export function isTrackedMessage(message, localKey) {
  if (!localKey) {
    return false
  }
  return (
    message.localKey === localKey ||
    (message.clientMessageId && message.clientMessageId === localKey)
  )
}

export function createLocalUserMessage(content, sessionId, clientMessageId) {
  return {
    localKey: `local-${clientMessageId}`,
    sessionId,
    clientMessageId,
    role: 'USER',
    content,
    createTime: new Date().toISOString(),
    status: 'sending',
    errorMessage: '',
  }
}

export function createClientMessageId() {
  if (globalThis.crypto?.randomUUID) {
    return globalThis.crypto.randomUUID()
  }
  if (!globalThis.crypto?.getRandomValues) {
    return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 12)}`
  }
  const values = new Uint32Array(4)
  globalThis.crypto.getRandomValues(values)
  return Array.from(values, (value) => value.toString(16).padStart(8, '0')).join('-')
}

export function sameSession(left, right) {
  return String(left ?? '') === String(right ?? '')
}

export function canRetryMessage(message) {
  return message?.role === 'USER' && Boolean(message?.clientMessageId) && message?.status === 'failed'
}

export function unwrapData(response) {
  const body = response?.data
  if (!body || body.code !== 200) {
    const error = new Error(body?.msg || '请求失败')
    error.code = body?.code
    throw error
  }
  return body.data
}

export function formatDateTime(value) {
  if (!value) {
    return ''
  }
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) {
    return ''
  }
  const mm = String(date.getMonth() + 1).padStart(2, '0')
  const dd = String(date.getDate()).padStart(2, '0')
  const hh = String(date.getHours()).padStart(2, '0')
  const mi = String(date.getMinutes()).padStart(2, '0')
  return `${mm}-${dd} ${hh}:${mi}`
}
