import request from "./request";

export function listAgentSessions() {
  return request.get("/agent/sessions");
}

export function createAgentSession(title) {
  return request.post("/agent/sessions", { title });
}

export function renameAgentSession(sessionId, title) {
  return request.patch(`/agent/sessions/${sessionId}`, { title });
}

export function deleteAgentSession(sessionId) {
  return request.delete(`/agent/sessions/${sessionId}`);
}

export function listAgentMessages(sessionId) {
  return request.get(`/agent/sessions/${sessionId}/messages`);
}

export function sendAgentChat(payload) {
  return request.post("/agent/chat", payload, {
    timeout: 60000,
  });
}
