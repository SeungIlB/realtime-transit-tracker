import { fetchApi } from './client'

export type SystemHealth = {
  status: 'UP'
  checkedAt: string
}

export async function fetchSystemHealth(signal?: AbortSignal): Promise<SystemHealth> {
  return fetchApi<SystemHealth>('/api/v1/system/health', signal)
}
