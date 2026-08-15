import { flushPromises, shallowMount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import CourseActions from '../components/CourseActions.vue'
import CourseMediaPlayer from '../components/CourseMediaPlayer.vue'
import CourseDetail from '@/views/user/CourseDetail.vue'
import KnowledgePointList from '../components/KnowledgePointList.vue'
import { ENROLLMENT_STATUS } from '../enrollmentStatus'

const mocks = vi.hoisted(() => ({
  push: vi.fn(),
  createCoursePlayback: vi.fn(),
  getCourseRelation: vi.fn(),
  getCourseById: vi.fn(),
  getKnowledgePoints: vi.fn(),
  attendCourse: vi.fn(),
  updateProgress: vi.fn(),
  recordBehavior: vi.fn(),
  notification: {
    success: vi.fn(),
    error: vi.fn(),
    debug: vi.fn(),
  },
}))

vi.mock('vue-router', () => ({
  useRoute: () => ({ params: { courseId: '7' } }),
  useRouter: () => ({ push: mocks.push }),
}))

vi.mock('@/services/notification', () => ({ notification: mocks.notification }))

vi.mock('../../../api/course', () => ({
  createCoursePlayback: mocks.createCoursePlayback,
  getUserCourseRelation: mocks.getCourseRelation,
  getCourseById: mocks.getCourseById,
  getCourseKnowledgePoints: mocks.getKnowledgePoints,
  enrollCourse: mocks.attendCourse,
  updateCourseVideoProgressSeconds: mocks.updateProgress,
}))

vi.mock('../../../api/learningBehavior', () => ({
  recordLearningBehavior: mocks.recordBehavior,
}))

const success = (data) => Promise.resolve({ data: { code: 200, data } })

const deferred = () => {
  let resolve
  let reject
  const promise = new Promise((resolvePromise, rejectPromise) => {
    resolve = resolvePromise
    reject = rejectPromise
  })
  return { promise, resolve, reject }
}

const createPage = () =>
  shallowMount(CourseDetail, {
    global: {
      stubs: {
        'el-card': { template: '<section><slot /></section>' },
        'el-empty': {
          props: ['description'],
          template: '<div class="empty">{{ description }}</div>',
        },
        'el-icon': { template: '<i><slot /></i>' },
      },
    },
  })

const mountPage = async () => {
  const wrapper = createPage()
  await flushPromises()
  return wrapper
}

beforeEach(() => {
  vi.clearAllMocks()
  mocks.createCoursePlayback.mockImplementation(() =>
    success({ playbackUrl: '/videos/course-7.mp4?token=signed', expiresAt: '2026-08-14T12:00:00Z' }),
  )
  mocks.getCourseRelation.mockImplementation(() =>
    success({
      isFavorite: false,
      progressSeconds: 18,
    }),
  )
  mocks.getCourseById.mockImplementation(() =>
    success({
      id: 7,
      title: 'Vue 架构设计',
      description: '学习单向数据流',
    }),
  )
  mocks.getKnowledgePoints.mockImplementation(() =>
    success([{ id: 1, name: '组件通信', difficulty: 2 }]),
  )
  mocks.attendCourse.mockImplementation(() => success(null))
  mocks.updateProgress.mockImplementation(() => success(null))
  mocks.recordBehavior.mockImplementation(() => success(null))
})

describe('CourseDetail', () => {
  it('加载业务数据并通过 props 传给子组件', async () => {
    const wrapper = await mountPage()
    const player = wrapper.findComponent(CourseMediaPlayer)
    const actions = wrapper.findComponent(CourseActions)
    const knowledgePoints = wrapper.findComponent(KnowledgePointList)

    expect(player.props()).toMatchObject({
      videoUrl: '/videos/course-7.mp4?token=signed',
      mediaId: '7',
      startTime: 18,
    })
    expect(actions.props()).toMatchObject({
      enrollmentStatus: ENROLLMENT_STATUS.ENROLLED,
      isFavorite: false,
    })
    expect(knowledgePoints.props('knowledgePoints')).toEqual([
      { id: 1, name: '组件通信', difficulty: 2 },
    ])
    expect(wrapper.text()).toContain('Vue 架构设计')
  })

  it('没有视频时卸载不保存默认进度', async () => {
    mocks.createCoursePlayback.mockImplementation(() => success(null))
    const wrapper = await mountPage()

    expect(wrapper.text()).toContain('暂无视频资源')
    expect(wrapper.findComponent(CourseMediaPlayer).exists()).toBe(false)
    expect(mocks.getCourseRelation).not.toHaveBeenCalled()

    wrapper.unmount()
    await flushPromises()
    expect(mocks.updateProgress).not.toHaveBeenCalled()
  })

  it('视频接口失败时显示空状态并记录错误', async () => {
    const error = new Error('视频接口不可用')
    mocks.createCoursePlayback.mockRejectedValueOnce(error)
    const wrapper = await mountPage()

    expect(wrapper.text()).toContain('暂无视频资源')
    expect(mocks.notification.error).toHaveBeenCalledWith('获取课程视频异常，请稍后重试', error)
  })

  it('关系接口失败时进入 error，并可重试为未选课状态', async () => {
    const relationError = new Error('关系接口不可用')
    mocks.getCourseRelation
      .mockRejectedValueOnce(relationError)
      .mockImplementation(() => success(null))
    const wrapper = await mountPage()
    const actions = wrapper.findComponent(CourseActions)

    expect(actions.props('enrollmentStatus')).toBe(ENROLLMENT_STATUS.ERROR)
    expect(mocks.notification.error).toHaveBeenCalledWith('获取用户课程关系出错', relationError)

    actions.vm.$emit('retry-relation')
    await flushPromises()
    expect(actions.props('enrollmentStatus')).toBe(ENROLLMENT_STATUS.NOT_ENROLLED)
  })

  it('只在收藏和选课接口成功后更新父组件状态', async () => {
    mocks.getCourseRelation.mockImplementation(() => success(null))
    mocks.attendCourse
      .mockResolvedValueOnce({ data: { code: 500, msg: '选课失败' } })
      .mockImplementation(() => success(null))
    const wrapper = await mountPage()
    const actions = wrapper.findComponent(CourseActions)

    actions.vm.$emit('enroll')
    await flushPromises()
    expect(actions.props('enrollmentStatus')).toBe(ENROLLMENT_STATUS.NOT_ENROLLED)

    actions.vm.$emit('enroll')
    await flushPromises()
    expect(actions.props('enrollmentStatus')).toBe(ENROLLMENT_STATUS.ENROLLED)

    mocks.recordBehavior.mockResolvedValueOnce({ data: { code: 500, msg: '收藏失败' } })
    actions.vm.$emit('toggle-favorite')
    await flushPromises()
    expect(actions.props('isFavorite')).toBe(false)

    actions.vm.$emit('toggle-favorite')
    await flushPromises()
    expect(actions.props('isFavorite')).toBe(true)
  })

  it('播放器未 ready 时不保存进度', async () => {
    const wrapper = await mountPage()
    wrapper.findComponent(CourseMediaPlayer).vm.$emit('progress', {
      currentTime: 25,
      watchedSeconds: 5,
    })

    wrapper.unmount()
    await flushPromises()
    expect(mocks.updateProgress).not.toHaveBeenCalled()
  })

  it('累计观看十秒只上报一次 VIEW，并在 ready 后保存学习记录和进度', async () => {
    const wrapper = await mountPage()
    const player = wrapper.findComponent(CourseMediaPlayer)

    player.vm.$emit('ready')
    player.vm.$emit('progress', { currentTime: 30.8, watchedSeconds: 10.4 })
    player.vm.$emit('progress', { currentTime: 31.8, watchedSeconds: 11.4 })
    await flushPromises()

    expect(mocks.recordBehavior).toHaveBeenCalledTimes(1)
    expect(mocks.recordBehavior).toHaveBeenCalledWith({
      courseId: 7,
      behaviorType: 'VIEW',
    })

    wrapper.unmount()
    await flushPromises()

    expect(mocks.recordBehavior).toHaveBeenCalledWith({
      courseId: 7,
      behaviorType: 'STUDY',
      duration: 11,
    })
    expect(mocks.updateProgress).toHaveBeenCalledWith({
      courseId: 7,
      progressSeconds: 31,
    })
  })

  it('卸载后忽略迟到的视频响应', async () => {
    const videoRequest = deferred()
    mocks.createCoursePlayback.mockReturnValueOnce(videoRequest.promise)
    const wrapper = createPage()
    await flushPromises()

    wrapper.unmount()
    videoRequest.resolve({
      data: { code: 200, data: { playbackUrl: '/videos/late.mp4?token=late' } },
    })
    await flushPromises()

    expect(mocks.getCourseRelation).not.toHaveBeenCalled()
    expect(mocks.updateProgress).not.toHaveBeenCalled()
    expect(mocks.notification.error).not.toHaveBeenCalled()
  })

  it('卸载后忽略迟到的课程关系并停止后续读取', async () => {
    const relationRequest = deferred()
    mocks.getCourseRelation.mockReturnValueOnce(relationRequest.promise)
    const wrapper = createPage()
    await flushPromises()

    wrapper.unmount()
    relationRequest.resolve({
      data: { code: 200, data: { isFavorite: true, progressSeconds: 20 } },
    })
    await flushPromises()

    expect(mocks.getCourseById).not.toHaveBeenCalled()
    expect(mocks.getKnowledgePoints).not.toHaveBeenCalled()
    expect(mocks.updateProgress).not.toHaveBeenCalled()
    expect(mocks.notification.error).not.toHaveBeenCalled()
  })

  it('选课请求在卸载后返回时不更新状态或显示提示', async () => {
    mocks.getCourseRelation.mockImplementation(() => success(null))
    const enrollRequest = deferred()
    mocks.attendCourse.mockReturnValueOnce(enrollRequest.promise)
    const wrapper = await mountPage()

    wrapper.findComponent(CourseActions).vm.$emit('enroll')
    await Promise.resolve()
    wrapper.unmount()
    enrollRequest.resolve({ data: { code: 200, data: null } })
    await flushPromises()

    expect(mocks.notification.success).not.toHaveBeenCalled()
    expect(mocks.updateProgress).not.toHaveBeenCalled()
  })

  it('根据子组件事件跳转到当前课程的知识图谱', async () => {
    const wrapper = await mountPage()

    wrapper.findComponent(CourseActions).vm.$emit('open-graph')

    expect(mocks.push).toHaveBeenCalledWith({
      path: '/knowledge-graph',
      query: { courseId: '7' },
    })
  })

  it('视频首次失败时自动续签并从原位置恢复，第二次失败才提示', async () => {
    mocks.createCoursePlayback
      .mockImplementationOnce(() =>
        success({ playbackUrl: '/videos/course-7.mp4?token=first' }),
      )
      .mockImplementationOnce(() =>
        success({ playbackUrl: '/videos/course-7.mp4?token=second' }),
      )
    const wrapper = await mountPage()
    const player = wrapper.findComponent(CourseMediaPlayer)

    player.vm.$emit('error', {
      error: new Error('expired'),
      currentTime: 42,
      watchedSeconds: 9,
      wasPlaying: true,
    })
    await flushPromises()

    expect(mocks.createCoursePlayback).toHaveBeenCalledTimes(2)
    expect(player.props()).toMatchObject({
      videoUrl: '/videos/course-7.mp4?token=second',
      startTime: 42,
      resumeOnLoad: true,
    })
    expect(mocks.notification.error).not.toHaveBeenCalled()

    player.vm.$emit('error', { error: new Error('still unavailable') })
    await flushPromises()
    expect(mocks.createCoursePlayback).toHaveBeenCalledTimes(2)
    expect(mocks.notification.error).toHaveBeenCalledWith(
      '视频加载失败，请检查网络或权限',
      expect.any(Error),
    )
  })
})
