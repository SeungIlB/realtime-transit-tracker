import { useQuery } from '@tanstack/react-query'
import { fetchSystemHealth } from '../../api/system'

export function SystemStatusPage() {
  const healthQuery = useQuery({
    queryKey: ['system', 'health'],
    queryFn: ({ signal }) => fetchSystemHealth(signal),
    refetchInterval: 30_000,
  })

  const status = healthQuery.isPending ? 'loading' : healthQuery.isError ? 'down' : 'up'

  return (
    <main className="app-shell">
      <p className="eyebrow">Realtime Transit Tracker</p>
      <h1>첫 차와 다음 차를 한눈에</h1>
      <p className="description">
        노선과 승·하차 지점을 선택하면 접근 중인 차량의 위치와 도착 예상시간을 보여주는 서비스입니다.
      </p>

      <section className="status-card" data-status={status} aria-live="polite">
        <span className="status-dot" aria-hidden="true" />
        <div>
          <span className="status-label">
            {status === 'loading' && '백엔드 연결 확인 중'}
            {status === 'up' && '개발 환경 연결 정상'}
            {status === 'down' && '백엔드에 연결할 수 없음'}
          </span>
          <span className="status-detail">
            {healthQuery.data?.checkedAt ?? 'Spring Boot · PostgreSQL'}
          </span>
        </div>
      </section>
    </main>
  )
}
