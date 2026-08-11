import request from './request'

export function getCategories() {
  return request.get('/courses/categories')
}

export function getCourses(params) {
  return request.post('/courses/search', params)
}

export function getAdminCourses(params) {
  return request.post('/admin/courses/search', params)
}

export function enrollCourse(courseId) {
  return request.post(`/courses/${courseId}/enrollment`)
}

export function getCourseVideo(courseId) {
  return request.get(`/courses/${courseId}/video`)
}

export function getUserCourseRelation(courseId) {
  return request.get(`/courses/${courseId}/enrollment`)
}

export function updateCourseVideoProgressSeconds({ courseId, progressSeconds }) {
  return request.patch(`/courses/${courseId}/enrollment/progress`, { progressSeconds })
}

export function getCourseById(courseId) {
  return request.get(`/courses/${courseId}`)
}

export function getAdminCourseDetail(courseId) {
  return request.get(`/admin/courses/${courseId}`)
}

export function getCoursesByKnowledgePoint(knowledgePointId) {
  return request.get(`/knowledge-points/${knowledgePointId}/courses`)
}

export function getCourseKnowledgePoints(courseId) {
  return request.get(`/courses/${courseId}/knowledge-points`)
}

export function deleteCourses(courseIds) {
  return request.delete('/admin/courses', {
    data: { courseIds },
  })
}

export function updateCourseStatus(courseId, status) {
  return request.patch(`/admin/courses/${courseId}/status`, { status })
}

export function getCourseRegisterOptions() {
  return request.get('/admin/courses/form-options')
}

export function registerCourse(data) {
  return request.post('/admin/courses', data)
}

export function updateCourse(courseId, data) {
  return request.put(`/admin/courses/${courseId}`, data)
}

export function uploadCourseVideo(courseId, file) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post(`/admin/courses/${courseId}/video`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
}
