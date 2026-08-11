import request from "./request";

export function recordLearningBehavior(data) {
  return request.post("/behavior/record", null, { params: data });
}