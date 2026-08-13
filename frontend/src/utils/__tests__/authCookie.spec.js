/**
 * @vitest-environment jsdom
 * @vitest-environment-options {"url":"https://course.test/courses/1"}
 */

import { afterEach, describe, expect, it, vi } from 'vitest'

import { clearAuthTokenCookie, setAuthTokenToCookie } from '../authCookie'

describe('authCookie', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('scopes the token to videos and removes the legacy root cookie', () => {
    const cookieSetter = vi.spyOn(Document.prototype, 'cookie', 'set')

    setAuthTokenToCookie('signed-token', 30)

    expect(cookieSetter).toHaveBeenCalledTimes(2)
    expect(cookieSetter.mock.calls[0][0]).toContain('auth_token=')
    expect(cookieSetter.mock.calls[0][0]).toContain('path=/;')
    expect(cookieSetter.mock.calls[1][0]).toContain('auth_token=signed-token')
    expect(cookieSetter.mock.calls[1][0]).toContain('path=/videos;')
    expect(cookieSetter.mock.calls[1][0]).toContain('SameSite=Strict')
    expect(cookieSetter.mock.calls[1][0]).toContain('Secure')
  })

  it('clears both the video cookie and the legacy root cookie', () => {
    const cookieSetter = vi.spyOn(Document.prototype, 'cookie', 'set')

    clearAuthTokenCookie()

    expect(cookieSetter).toHaveBeenCalledTimes(2)
    expect(cookieSetter.mock.calls[0][0]).toContain('path=/videos;')
    expect(cookieSetter.mock.calls[1][0]).toContain('path=/;')
    expect(cookieSetter.mock.calls.every(([cookie]) => cookie.includes('SameSite=Strict'))).toBe(true)
    expect(cookieSetter.mock.calls.every(([cookie]) => cookie.includes('Secure'))).toBe(true)
  })
})
