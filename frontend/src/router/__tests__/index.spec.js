import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => ({
  userStore: {
    isLoggedIn: false,
    userInfo: { role: '' },
  },
  onboardingStore: {
    fetchStatus: vi.fn(),
    reset: vi.fn(),
  },
}))

vi.mock('@/store/user', () => ({
  useUserStore: () => mocks.userStore,
}))

vi.mock('@/store/onboarding', () => ({
  useOnboardingStore: () => mocks.onboardingStore,
}))

import router, { createNavigationGuard, routes } from '../index'

const resolveCases = [
  ['/login', 'Login'],
  ['/register', 'Register'],
  ['/courses', 'CourseList'],
  ['/courses/42', 'CourseDetail'],
  ['/onboarding', 'Onboarding'],
  ['/recommendations', 'Recommend'],
  ['/dashboard', 'Dashboard'],
  ['/assistant', 'AgentAssistant'],
  ['/knowledge-graph', 'KnowledgeGraph'],
  ['/profile', 'Profile'],
  ['/admin/courses', 'AdminCourseList'],
  ['/admin/courses/3/edit', 'CourseEdit'],
  ['/admin/courses/new', 'CourseRegister'],
  ['/admin/users', 'AdminUserList'],
  ['/admin/users/3/edit', 'UserEdit'],
]

const removedPaths = [
  '/course',
  '/courseDetail/42',
  '/recommend',
  '/agent',
  '/graph',
  '/admin/course',
  '/admin/course/edit/3',
  '/admin/course/register',
  '/admin/users/edit/3',
]

describe('router', () => {
  beforeEach(() => {
    mocks.userStore.isLoggedIn = false
    mocks.userStore.userInfo.role = ''
    mocks.onboardingStore.fetchStatus.mockReset()
    mocks.onboardingStore.reset.mockReset()
  })

  it.each(resolveCases)('keeps %s mapped to %s', (path, name) => {
    expect(router.resolve(path).name).toBe(name)
  })

  it('redirects the root child to the course list', () => {
    const layoutRoute = routes.find((route) => route.path === '/')
    expect(layoutRoute.children.find((route) => route.path === '').redirect).toEqual({
      name: 'CourseList',
    })
  })

  it('keeps the CourseDetail name with a semantic parameter', () => {
    expect(router.resolve({ name: 'CourseDetail', params: { courseId: 7 } }).fullPath).toBe(
      '/courses/7',
    )
    expect(router.resolve({ name: 'CourseEdit', params: { courseId: 7 } }).fullPath).toBe(
      '/admin/courses/7/edit',
    )
    expect(router.resolve({ name: 'UserEdit', params: { userId: 3 } }).fullPath).toBe(
      '/admin/users/3/edit',
    )
  })

  it.each(removedPaths)('does not resolve removed path %s', (path) => {
    const resolved = router.resolve(path)
    expect(resolved.name).toBe('NotFound')
    expect(resolved.meta.public).toBe(true)
  })

  it('allows public routes without authentication', async () => {
    const result = await createNavigationGuard()({ meta: { public: true }, fullPath: '/login' })

    expect(result).toBe(true)
    expect(mocks.onboardingStore.reset).not.toHaveBeenCalled()
  })

  it('redirects unauthenticated users and resets onboarding state', async () => {
    const result = await createNavigationGuard()({ meta: {}, fullPath: '/courses' })

    expect(result).toEqual({ name: 'Login' })
    expect(mocks.onboardingStore.reset).toHaveBeenCalledOnce()
  })

  it('redirects users without an allowed role', async () => {
    mocks.userStore.isLoggedIn = true
    mocks.userStore.userInfo.role = 'USER'

    const result = await createNavigationGuard()({
      meta: { roles: ['ADMIN'] },
      fullPath: '/admin/courses',
    })

    expect(result).toEqual({ name: 'CourseList' })
    expect(mocks.onboardingStore.fetchStatus).not.toHaveBeenCalled()
  })

  it('redirects incomplete learners to onboarding', async () => {
    mocks.userStore.isLoggedIn = true
    mocks.userStore.userInfo.role = 'USER'
    mocks.onboardingStore.fetchStatus.mockResolvedValue({ completed: false })

    const result = await createNavigationGuard()({
      meta: {},
      fullPath: '/recommendations?from=test',
    })

    expect(result).toEqual({
      name: 'Onboarding',
      query: { redirect: '/recommendations?from=test' },
    })
  })

  it('allows navigation when onboarding status fails to load', async () => {
    mocks.userStore.isLoggedIn = true
    mocks.userStore.userInfo.role = 'USER'
    const error = new Error('network error')
    mocks.onboardingStore.fetchStatus.mockRejectedValue(error)
    const consoleError = vi.spyOn(console, 'error').mockImplementation(() => {})

    const result = await createNavigationGuard()({ meta: {}, fullPath: '/courses' })

    expect(result).toBe(true)
    expect(consoleError).toHaveBeenCalledWith('获取引导状态失败', error)
    consoleError.mockRestore()
  })

  it('skips onboarding checks for the onboarding route', async () => {
    mocks.userStore.isLoggedIn = true
    mocks.userStore.userInfo.role = 'USER'

    const result = await createNavigationGuard()({
      meta: { skipOnboarding: true },
      fullPath: '/onboarding',
    })

    expect(result).toBe(true)
    expect(mocks.onboardingStore.fetchStatus).not.toHaveBeenCalled()
  })
})
