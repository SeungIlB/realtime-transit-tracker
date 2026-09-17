import { fetchApi } from './client'

const HEALTH_CHECK_TIMEOUT_MS = 20_000

export type SystemHealth = {
  status: 'UP'
  checkedAt: string
}

export async function fetchSystemHealth(signal?: AbortSignal): Promise<SystemHealth> {
  return fetchApi<SystemHealth>('/api/v1/system/health', signal, HEALTH_CHECK_TIMEOUT_MS)
}
