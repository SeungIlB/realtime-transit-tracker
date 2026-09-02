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

export async function fetchApi<T>(path: string, signal?: AbortSignal): Promise<T> {
  const response = await fetch(path, { signal })
  const body = (await response.json()) as ApiResponse<T>

  if (!response.ok || !body.success) {
    throw new ApiError(
      body.code || 'HTTP_ERROR',
      body.message || `요청을 처리하지 못했습니다. (${response.status})`,
      response.status,
    )
  }

  return body.data
}
