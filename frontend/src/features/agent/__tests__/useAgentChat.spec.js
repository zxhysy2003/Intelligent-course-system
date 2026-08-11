import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useAgentChat } from '../composables/useAgentChat'

const mocks = vi.hoisted(() => ({
  listAgentSessions: vi.fn(),
  createAgentSession: vi.fn(),
  renameAgentSession: vi.fn(),
  deleteAgentSession: vi.fn(),
  listAgentMessages: vi.fn(),
  sendAgentChat: vi.fn(),
  notification: {
    error: vi.fn(),
    warn: vi.fn(),
  },
}))

vi.mock('@/api/agent', () => ({
  listAgentSessions: mocks.listAgentSessions,
  createAgentSession: mocks.createAgentSession,
  renameAgentSession: mocks.renameAgentSession,
  deleteAgentSession: mocks.deleteAgentSession,
  listAgentMessages: mocks.listAgentMessages,
  sendAgentChat: mocks.sendAgentChat,
}))

vi.mock('@/services/notification', () => ({
  notification: mocks.notification,
}))

const ok = (data) => ({ data: { code: 200, data } })

const deferred = () => {
  let resolve
  let reject
  const promise = new Promise((resolvePromise, rejectPromise) => {
    resolve = resolvePromise
    reject = rejectPromise
  })
  return { promise, resolve, reject }
}

describe('useAgentChat', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mocks.listAgentSessions.mockResolvedValue(ok([]))
    mocks.listAgentMessages.mockResolvedValue(ok([]))
  })

  it('initializes sessions and selects the first session', async () => {
    mocks.listAgentSessions.mockResolvedValue(ok([{ id: 1, title: '第一课' }]))
    mocks.listAgentMessages.mockResolvedValue(ok([{ id: 11, role: 'ASSISTANT', content: '你好' }]))
    const chat = useAgentChat()

    await chat.initialize()

    expect(chat.currentSessionId.value).toBe(1)
    expect(chat.messages.value).toEqual([{ id: 11, role: 'ASSISTANT', content: '你好' }])
    expect(mocks.listAgentMessages).toHaveBeenCalledWith(1)
  })

  it('does not let a late message response overwrite the selected session', async () => {
    const first = deferred()
    const second = deferred()
    mocks.listAgentMessages
      .mockImplementationOnce(() => first.promise)
      .mockImplementationOnce(() => second.promise)
    const chat = useAgentChat()

    const firstSelection = chat.selectSession(1)
    const secondSelection = chat.selectSession(2)
    second.resolve(ok([{ id: 22, role: 'ASSISTANT', content: '第二个会话' }]))
    await secondSelection
    first.resolve(ok([{ id: 11, role: 'ASSISTANT', content: '第一个会话' }]))
    await firstSelection

    expect(chat.currentSessionId.value).toBe(2)
    expect(chat.messages.value).toEqual([{ id: 22, role: 'ASSISTANT', content: '第二个会话' }])
  })

  it('creates, renames, and deletes sessions', async () => {
    mocks.createAgentSession.mockResolvedValue(ok({ id: 2 }))
    mocks.renameAgentSession.mockResolvedValue(ok(null))
    mocks.deleteAgentSession.mockResolvedValue(ok(null))
    mocks.listAgentSessions
      .mockResolvedValueOnce(ok([{ id: 2, title: '新的学习对话' }]))
      .mockResolvedValueOnce(ok([{ id: 2, title: '重命名会话' }]))
      .mockResolvedValueOnce(ok([]))
    const chat = useAgentChat()

    await chat.createSession()
    expect(chat.currentSessionId.value).toBe(2)

    await chat.renameCurrentSession(' 重命名会话 ')
    expect(mocks.renameAgentSession).toHaveBeenCalledWith(2, '重命名会话')
    expect(chat.currentSessionTitle.value).toBe('重命名会话')

    await chat.removeCurrentSession()
    expect(mocks.deleteAgentSession).toHaveBeenCalledWith(2)
    expect(chat.currentSessionId.value).toBeNull()
    expect(chat.sessions.value).toEqual([])
  })

  it('replaces the local message after a successful send', async () => {
    mocks.sendAgentChat.mockImplementation(async (payload) =>
      ok({
        sessionId: 9,
        userMessage: {
          id: 91,
          role: 'USER',
          content: payload.message,
          clientMessageId: payload.clientMessageId,
        },
        assistantMessage: { id: 92, role: 'ASSISTANT', content: '学习建议' },
        sources: [{ type: 'COURSE', referenceId: 3, title: '课程', summary: '摘要' }],
      }),
    )
    const chat = useAgentChat()
    chat.draft.value = '下一步学什么'

    await chat.sendMessage()

    expect(chat.draft.value).toBe('')
    expect(chat.currentSessionId.value).toBe(9)
    expect(chat.messages.value.map((message) => message.id)).toEqual([91, 92])
    expect(chat.sources.value).toHaveLength(1)
    expect(mocks.listAgentSessions).toHaveBeenCalledOnce()
  })

  it('retries a failed message with the same client message id', async () => {
    mocks.sendAgentChat.mockImplementation(async (payload) =>
      ok({
        sessionId: 4,
        userMessage: {
          id: 41,
          role: 'USER',
          content: payload.message,
          clientMessageId: payload.clientMessageId,
        },
        assistantMessage: { id: 42, role: 'ASSISTANT', content: '重试成功' },
      }),
    )
    const chat = useAgentChat()
    chat.currentSessionId.value = 4
    chat.messages.value = [
      {
        sessionId: 4,
        clientMessageId: 'retry-1',
        role: 'USER',
        content: '重试问题',
        status: 'failed',
      },
    ]

    await chat.retryMessage(chat.messages.value[0])

    expect(mocks.sendAgentChat).toHaveBeenCalledWith({
      sessionId: 4,
      message: '重试问题',
      clientMessageId: 'retry-1',
    })
    expect(chat.messages.value.map((message) => message.id)).toEqual([41, 42])
  })

  it('marks the local message failed when chat returns empty data', async () => {
    mocks.sendAgentChat.mockResolvedValue(ok(null))
    const chat = useAgentChat()
    chat.draft.value = '没有响应的问题'

    await chat.sendMessage()

    expect(chat.messages.value[0].status).toBe('failed')
    expect(chat.messages.value[0].errorMessage).toBe('empty response')
    expect(mocks.notification.error).toHaveBeenCalledWith('empty response')
    expect(chat.sending.value).toBe(false)
  })

  it('ignores a late chat response after switching sessions', async () => {
    const response = deferred()
    mocks.sendAgentChat.mockImplementation(() => response.promise)
    mocks.listAgentMessages.mockResolvedValue(
      ok([{ id: 21, role: 'ASSISTANT', content: '当前会话' }]),
    )
    const chat = useAgentChat()
    chat.currentSessionId.value = 1
    chat.draft.value = '旧会话问题'

    const sending = chat.sendMessage()
    await chat.selectSession(2)
    response.resolve(
      ok({
        sessionId: 1,
        userMessage: { id: 11, role: 'USER', content: '旧会话问题' },
        assistantMessage: { id: 12, role: 'ASSISTANT', content: '迟到回答' },
        sources: [{ type: 'COURSE', referenceId: 1 }],
      }),
    )
    await sending

    expect(chat.currentSessionId.value).toBe(2)
    expect(chat.messages.value).toEqual([{ id: 21, role: 'ASSISTANT', content: '当前会话' }])
    expect(chat.sources.value).toEqual([])
    expect(mocks.listAgentSessions).toHaveBeenCalledOnce()
  })
})
