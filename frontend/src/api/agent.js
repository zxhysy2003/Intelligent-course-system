import request from "./request";

export function ListAgentSessions() {
  return request.get("/agent/sessions");
}

export function CreateAgentSession(title) {
  return request.post("/agent/sessions", { title });
}

export function RenameAgentSession(sessionId, title) {
  return request.patch(`/agent/sessions/${sessionId}`, { title });
}

export function DeleteAgentSession(sessionId) {
  return request.delete(`/agent/sessions/${sessionId}`);
}

export function ListAgentMessages(sessionId) {
  return request.get(`/agent/sessions/${sessionId}/messages`);
}

export function SendAgentChat(payload) {
  return request.post("/agent/chat", payload, {
    timeout: 60000,
  });
}
