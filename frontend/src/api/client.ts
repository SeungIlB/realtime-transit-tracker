export type ApiResponse<T> = {
  success: boolean
  code: string
  message: string
  data: T
}

export class ApiError extends Error {
  readonly code: string
  readonly status: number

  constructor(code: string, message: string, status: number) {
    super(message)
    this.name = 'ApiError'
    this.code = code
    this.status = status
  }
}

export function resolveApiUrl(path: string, baseUrl = import.meta.env.VITE_API_BASE_URL): string {
  if (!baseUrl) {
    return path
  }

  const normalizedBaseUrl = baseUrl.replace(/\/+$/, '')
  const normalizedPath = path.startsWith('/') ? path : `/${path}`
  return `${normalizedBaseUrl}${normalizedPath}`
}

export async function requestApi<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers)
  if (init.body && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }

  const response = await fetch(resolveApiUrl(path), { ...init, headers })
  const responseText = await response.text()
  let body: ApiResponse<T> | null = null
  try {
    body = responseText ? JSON.parse(responseText) as ApiResponse<T> : null
  } catch {
    throw new ApiError(
      'INVALID_API_RESPONSE',
      `서버가 올바르지 않은 응답을 반환했습니다. (${response.status})`,
      response.status,
    )
  }

  if (!body) {
    throw new ApiError(
      'EMPTY_API_RESPONSE',
      `서버 응답이 비어 있습니다. (${response.status})`,
      response.status,
    )
  }

  if (!response.ok || !body.success) {
    throw new ApiError(
      body.code || 'HTTP_ERROR',
      body.message || `요청을 처리하지 못했습니다. (${response.status})`,
      response.status,
    )
  }

  return body.data
}

export function fetchApi<T>(path: string, signal?: AbortSignal): Promise<T> {
  return requestApi<T>(path, { signal })
}
