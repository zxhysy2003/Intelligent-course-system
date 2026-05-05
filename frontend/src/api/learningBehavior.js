import request from "./request";

export function RecordLearningBehavior(data) {
  return request.post("/behavior/record", null, { params: data });
}