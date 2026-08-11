import request from './request'

export function listAgentSessions() {
  return request.get('/assistant/sessions')
}

export function createAgentSession(title) {
  return request.post('/assistant/sessions', { title })
}

export function renameAgentSession(sessionId, title) {
  return request.patch(`/assistant/sessions/${sessionId}`, { title })
}

export function deleteAgentSession(sessionId) {
  return request.delete(`/assistant/sessions/${sessionId}`)
}

export function listAgentMessages(sessionId) {
  return request.get(`/assistant/sessions/${sessionId}/messages`)
}

export function sendAgentChat(payload) {
  return request.post('/assistant/messages', payload, {
    timeout: 60000,
  })
}
