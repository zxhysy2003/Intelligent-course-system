import { beforeEach, describe, expect, it, vi } from 'vitest'

const request = vi.hoisted(() => ({
  get: vi.fn(),
  post: vi.fn(),
  put: vi.fn(),
  patch: vi.fn(),
  delete: vi.fn(),
}))

vi.mock('../request', () => ({ default: request }))

import {
  deleteCourses,
  createCoursePlayback,
  enrollCourse,
  getAdminCourses,
  getCourseKnowledgePoints,
  getCourses,
  getCoursesByKnowledgePoint,
  updateCourse,
  updateCourseStatus,
  updateCourseVideoProgressSeconds,
} from '../course'
import { recordLearningBehavior } from '../learningBehavior'
import { submitOnboarding } from '../onboarding'
import { getHybridRecommend } from '../recommend'
import { sendAgentChat } from '../agent'
import {
  deleteAdminUsers,
  login,
  updateAdminUser,
  updateAdminUserRole,
  updateAdminUserStatus,
} from '../user'

describe('API v1 contracts', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('uses resource paths and write methods for learner courses', () => {
    getCourses({ page: 1 })
    createCoursePlayback(7)
    enrollCourse(7)
    getCoursesByKnowledgePoint(9)
    getCourseKnowledgePoints(7)
    updateCourseVideoProgressSeconds({ courseId: 7, progressSeconds: 36 })

    expect(request.post).toHaveBeenCalledWith('/courses/search', { page: 1 })
    expect(request.post).toHaveBeenCalledWith('/courses/7/playback')
    expect(request.post).toHaveBeenCalledWith('/courses/7/enrollment')
    expect(request.get).toHaveBeenCalledWith('/knowledge-points/9/courses')
    expect(request.get).toHaveBeenCalledWith('/courses/7/knowledge-points')
    expect(request.patch).toHaveBeenCalledWith('/courses/7/enrollment/progress', {
      progressSeconds: 36,
    })
  })

  it('sends learning and onboarding writes as JSON', () => {
    const behavior = {
      eventId: 'study-event-7',
      courseId: 7,
      behaviorType: 'STUDY',
      duration: 12,
    }
    recordLearningBehavior(behavior)
    submitOnboarding({ currentLevel: 2 })

    expect(request.post).toHaveBeenCalledWith('/learning-behaviors', behavior)
    expect(request.put).toHaveBeenCalledWith('/onboarding/profile', { currentLevel: 2 })
  })

  it('uses version-neutral recommendation and assistant resource names', () => {
    getHybridRecommend()
    sendAgentChat({ message: 'hello' })

    expect(request.get).toHaveBeenCalledWith('/recommendations')
    expect(request.post).toHaveBeenCalledWith(
      '/assistant/messages',
      { message: 'hello' },
      { timeout: 60000 },
    )
  })

  it('keeps search and batch deletion bodies for admin courses', () => {
    getAdminCourses({ status: 0 })
    deleteCourses([1, 2])
    updateCourseStatus(7, 2)
    updateCourse(7, { title: 'updated' })

    expect(request.post).toHaveBeenCalledWith('/admin/courses/search', { status: 0 })
    expect(request.delete).toHaveBeenCalledWith('/admin/courses', { data: { courseIds: [1, 2] } })
    expect(request.patch).toHaveBeenCalledWith('/admin/courses/7/status', { status: 2 })
    expect(request.put).toHaveBeenCalledWith('/admin/courses/7', { title: 'updated' })
  })

  it('uses JSON patch bodies and path IDs for admin users', () => {
    login({ username: 'student' })
    updateAdminUserRole(3, 'ADMIN')
    updateAdminUserStatus(3, 0)
    updateAdminUser(3, { username: 'updated' })
    deleteAdminUsers([3])

    expect(request.post).toHaveBeenCalledWith('/auth/login', { username: 'student' })
    expect(request.patch).toHaveBeenCalledWith('/admin/users/3/role', { role: 'ADMIN' })
    expect(request.patch).toHaveBeenCalledWith('/admin/users/3/status', { status: 0 })
    expect(request.put).toHaveBeenCalledWith('/admin/users/3', { username: 'updated' })
    expect(request.delete).toHaveBeenCalledWith('/admin/users', { data: { userIds: [3] } })
  })
})
