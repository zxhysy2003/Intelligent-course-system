import { describe, expect, it } from 'vitest'
import {
  applyChatMessages,
  formatDateTime,
  normalizeLoadedMessages,
  patchTrackedMessage,
} from '../agentMessageModel'

describe('agentMessageModel', () => {
  it('restores only unanswered user messages as retryable', () => {
    const messages = normalizeLoadedMessages([
      { id: 1, role: 'USER', clientMessageId: 'answered', content: '已回答' },
      { id: 2, role: 'ASSISTANT', clientMessageId: 'answered', content: '回答' },
      { id: 3, role: 'USER', clientMessageId: 'orphan', content: '未完成' },
    ])

    expect(messages[0].status).toBeUndefined()
    expect(messages[2]).toMatchObject({
      status: 'failed',
      errorMessage: '回答未完成，可重试',
    })
  })

  it('replaces a tracked local message without duplicating server messages', () => {
    const result = applyChatMessages(
      [
        { id: 1, role: 'ASSISTANT', content: '之前的消息' },
        { localKey: 'local-client-1', clientMessageId: 'client-1', role: 'USER' },
      ],
      {
        userMessage: { id: 2, clientMessageId: 'client-1', role: 'USER' },
        assistantMessage: { id: 3, role: 'ASSISTANT' },
      },
      'local-client-1',
    )

    expect(result.map((message) => message.id)).toEqual([1, 2, 3])
  })

  it('patches messages by local or client key and handles invalid dates', () => {
    const result = patchTrackedMessage(
      [{ clientMessageId: 'client-1', status: 'sending' }],
      'client-1',
      { status: 'failed' },
    )

    expect(result[0].status).toBe('failed')
    expect(formatDateTime('invalid')).toBe('')
  })
})
