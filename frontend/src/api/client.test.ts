import { afterEach, describe, expect, it, vi } from 'vitest'

import { fetchApi, requestApi, resolveApiUrl } from './client'

describe('resolveApiUrl', () => {
  it('uses the relative path during local development', () => {
    expect(resolveApiUrl('/api/test', '')).toBe('/api/test')
  })

  it('joins the deployed backend origin and API path without duplicate slashes', () => {
    expect(resolveApiUrl('/api/test', 'https://backend.example.com/'))
      .toBe('https://backend.example.com/api/test')
  })
})

describe('requestApi', () => {
  afterEach(() => {
    vi.useRealTimers()
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

  it('aborts and returns a typed error when the request timeout is exceeded', async () => {
    vi.useFakeTimers()
    vi.stubGlobal('fetch', vi.fn((_url: string | URL | Request, init?: RequestInit) => new Promise<Response>((_resolve, reject) => {
      init?.signal?.addEventListener('abort', () => reject(new DOMException('Aborted', 'AbortError')))
    })))

    const request = fetchApi('/api/test', undefined, 100)
    const expectation = expect(request).rejects.toMatchObject({
      name: 'ApiError',
      code: 'REQUEST_TIMEOUT',
      status: 408,
    })
    await vi.advanceTimersByTimeAsync(100)

    await expectation
  })
})
