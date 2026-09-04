import { afterEach, describe, expect, it, vi } from 'vitest'

import { requestApi } from './client'

describe('requestApi', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('returns a typed API error when the response is not JSON', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('<html>gateway error</html>', {
      status: 502,
      headers: { 'Content-Type': 'text/html' },
    })))

    await expect(requestApi('/api/test')).rejects.toMatchObject({
      name: 'ApiError',
      code: 'INVALID_API_RESPONSE',
      status: 502,
    })
  })

  it('returns a typed API error when the response is empty', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(null, { status: 503 })))

    await expect(requestApi('/api/test')).rejects.toMatchObject({
      name: 'ApiError',
      code: 'EMPTY_API_RESPONSE',
      status: 503,
    })
  })
})
