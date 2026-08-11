import request from "./request";

export function getKnowledgeGraph(courseId) {
  return request.get("/analysis/knowledge-graph", {
    params: { courseId },
  });
}

export function getLearningProgress(days) {
  return request.get("/analysis/progress", {
    params: { days },
  });
}

export function getAbilityRadar() {
  return request.get("/analysis/ability-radar");
}
