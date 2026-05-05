import request from "./request";

export function GetKnowledgeGraph(courseId) {
  return request.get("/analysis/knowledge-graph", {
    params: { courseId },
  });
}

export function GetLearningProgress(days) {
  return request.get("/analysis/progress", {
    params: { days },
  });
}

export function GetAbilityRadar() {
  return request.get("/analysis/ability-radar");
}
