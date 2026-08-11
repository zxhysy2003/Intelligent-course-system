import { describe, expect, it } from 'vitest'
import request from '../request'

describe('request', () => {
  it('uses the versioned API base URL', () => {
    expect(request.defaults.baseURL).toBe('/api/v1')
  })
})
