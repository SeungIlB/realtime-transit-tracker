import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { SystemStatusPage } from './SystemStatusPage'

function renderPage() {
  const queryClient = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })

  return render(
    <QueryClientProvider client={queryClient}>
      <SystemStatusPage />
    </QueryClientProvider>,
  )
}

describe('SystemStatusPage', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('shows the connected state when the backend is healthy', async () => {
    vi.spyOn(globalThis, 'fetch').mockResolvedValue(
      new Response(
        JSON.stringify({
          success: true,
          code: 'SUCCESS',
          message: 'Success',
          data: { status: 'UP', checkedAt: '2026-08-27T00:00:00Z' },
        }),
        { status: 200, headers: { 'Content-Type': 'application/json' } },
      ),
    )

    renderPage()

    expect(await screen.findByText('개발 환경 연결 정상')).toBeInTheDocument()
  })
})
