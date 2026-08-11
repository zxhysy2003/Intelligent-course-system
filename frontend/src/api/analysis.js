import request from './request'

export function getKnowledgeGraph(courseId) {
  return request.get('/learning-analytics/knowledge-graph', {
    params: { courseId },
  })
}

export function getLearningProgress(days) {
  return request.get('/learning-analytics/progress', {
    params: { days },
  })
}

export function getAbilityRadar() {
  return request.get('/learning-analytics/ability-radar')
}
