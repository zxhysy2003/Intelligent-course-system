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

import router, { createNavigationGuard } from '../index'

const resolveCases = [
  ['/login', 'Login'],
  ['/register', 'Register'],
  ['/course', 'CourseList'],
  ['/courseDetail/42', 'CourseDetail'],
  ['/onboarding', 'Onboarding'],
  ['/recommend', 'Recommend'],
  ['/dashboard', 'Dashboard'],
  ['/agent', 'AgentAssistant'],
  ['/graph', 'KnowledgeGraph'],
  ['/profile', 'Profile'],
  ['/admin/course', 'AdminCourseList'],
  ['/admin/course/edit/3', 'CourseEdit'],
  ['/admin/course/register', 'CourseRegister'],
  ['/admin/users', 'AdminUserList'],
  ['/admin/users/edit/3', 'UserEdit'],
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

  it('keeps the existing CourseDetail named route', () => {
    expect(router.resolve({ name: 'CourseDetail', params: { id: 7 } }).fullPath).toBe(
      '/courseDetail/7',
    )
  })

  it('allows public routes without authentication', async () => {
    const result = await createNavigationGuard()({ meta: { public: true }, fullPath: '/login' })

    expect(result).toBe(true)
    expect(mocks.onboardingStore.reset).not.toHaveBeenCalled()
  })

  it('redirects unauthenticated users and resets onboarding state', async () => {
    const result = await createNavigationGuard()({ meta: {}, fullPath: '/course' })

    expect(result).toEqual({ name: 'Login' })
    expect(mocks.onboardingStore.reset).toHaveBeenCalledOnce()
  })

  it('redirects users without an allowed role', async () => {
    mocks.userStore.isLoggedIn = true
    mocks.userStore.userInfo.role = 'USER'

    const result = await createNavigationGuard()({
      meta: { roles: ['ADMIN'] },
      fullPath: '/admin/course',
    })

    expect(result).toEqual({ name: 'CourseList' })
    expect(mocks.onboardingStore.fetchStatus).not.toHaveBeenCalled()
  })

  it('redirects incomplete learners to onboarding', async () => {
    mocks.userStore.isLoggedIn = true
    mocks.userStore.userInfo.role = 'USER'
    mocks.onboardingStore.fetchStatus.mockResolvedValue({ completed: false })

    const result = await createNavigationGuard()({ meta: {}, fullPath: '/recommend?from=test' })

    expect(result).toEqual({
      name: 'Onboarding',
      query: { redirect: '/recommend?from=test' },
    })
  })

  it('allows navigation when onboarding status fails to load', async () => {
    mocks.userStore.isLoggedIn = true
    mocks.userStore.userInfo.role = 'USER'
    const error = new Error('network error')
    mocks.onboardingStore.fetchStatus.mockRejectedValue(error)
    const consoleError = vi.spyOn(console, 'error').mockImplementation(() => {})

    const result = await createNavigationGuard()({ meta: {}, fullPath: '/course' })

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
