export type SystemHealth = {
  status: 'UP'
  checkedAt: string
}

export async function fetchSystemHealth(signal?: AbortSignal): Promise<SystemHealth> {
  const response = await fetch('/api/v1/system/health', { signal })

  if (!response.ok) {
    throw new Error(`Backend health request failed with status ${response.status}`)
  }

  return response.json() as Promise<SystemHealth>
}
