import { useState } from 'react'
import type { FormEvent } from 'react'
import { useQuery } from '@tanstack/react-query'
import { ApiError } from '../../api/client'
import { fetchSystemHealth } from '../../api/system'
import {
  fetchDestinationStops,
  fetchDirectedStops,
  fetchUpcomingArrivals,
  searchTransitLines,
  type DestinationStop,
  type DirectedStop,
  type TransitLine,
  type TransitProvider,
  type UpcomingArrival,
} from '../../api/transit'

const steps = ['노선', '승차', '하차', '도착']
const providerOptions: Array<{
  value: TransitProvider
  label: string
  description: string
  searchLabel: string
  placeholder: string
}> = [
  {
    value: 'NATIONAL_PRECISION_BUS',
    label: '전국 버스',
    description: '초정밀 위치',
    searchLabel: '버스 번호',
    placeholder: '예: 중구01, 1000',
  },
  {
    value: 'GBIS',
    label: '경기 버스',
    description: '도착 예정',
    searchLabel: '경기버스 번호',
    placeholder: '예: 6601, 7770',
  },
  {
    value: 'SEOUL_SUBWAY',
    label: '서울 지하철',
    description: '실시간 도착',
    searchLabel: '호선 이름',
    placeholder: '예: 1호선, 경의중앙선',
  },
]

function minutesUntil(expectedAt: string) {
  return Math.max(0, Math.ceil((new Date(expectedAt).getTime() - Date.now()) / 60_000))
}

function formatTime(value: string) {
  return new Intl.DateTimeFormat('ko-KR', {
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  }).format(new Date(value))
}

function includesStopQuery(
  stop: DirectedStop | DestinationStop,
  query: string,
) {
  const normalizedQuery = query.trim().toLocaleLowerCase('ko-KR')
  if (!normalizedQuery) return true

  return [stop.stopName, stop.directionName, String(stop.stopSequence),
    'displayDirection' in stop ? stop.displayDirection : null]
    .filter((value): value is string => Boolean(value))
    .some((value) => value.toLocaleLowerCase('ko-KR').includes(normalizedQuery))
}

function lineErrorMessage(provider: TransitProvider, error: Error | null) {
  if (
    provider === 'GBIS' &&
    error instanceof ApiError &&
    error.code === 'EXTERNAL_API_AUTHENTICATION_FAILED'
  ) {
    return '차량 위치 API는 연결됐지만 경기버스 노선정보 게이트웨이가 인증키를 거절했어요(코드 30). 포털의 승인 반영 상태를 확인해 주세요.'
  }
  return `${providerOptions.find((option) => option.value === provider)?.label ?? '교통'} 노선을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.`
}

function QueryState({
  message,
  action,
  onAction,
}: {
  message: string
  action?: string
  onAction?: () => void
}) {
  return (
    <div className="query-state" role="status">
      <span className="query-state-mark" aria-hidden="true" />
      <p>{message}</p>
      {action && onAction ? (
        <button className="text-button" type="button" onClick={onAction}>
          {action}
        </button>
      ) : null}
    </div>
  )
}

function ArrivalBoard({ arrival, index }: { arrival: UpcomingArrival; index: number }) {
  const minutes = minutesUntil(arrival.expectedAt)

  return (
    <article className="arrival-board">
      <div className="arrival-rank">0{index + 1}</div>
      <div className="arrival-main">
        <p className="arrival-label">{index === 0 ? '먼저 오는 차량' : '다음 차량'}</p>
        <div className="arrival-time">
          <strong>{minutes}</strong>
          <span>분</span>
        </div>
      </div>
      <dl className="arrival-meta">
        <div>
          <dt>예정 시각</dt>
          <dd>{formatTime(arrival.expectedAt)}</dd>
        </div>
        <div>
          <dt>남은 정류장</dt>
          <dd>{arrival.remainingStops ?? '—'}</dd>
        </div>
        <div>
          <dt>차량</dt>
          <dd>{arrival.providerVehicleId}</dd>
        </div>
      </dl>
    </article>
  )
}

export function TransitJourneyPage() {
  const [provider, setProvider] = useState<TransitProvider>('NATIONAL_PRECISION_BUS')
  const [queryInput, setQueryInput] = useState('')
  const [submittedQuery, setSubmittedQuery] = useState('')
  const [selectedLine, setSelectedLine] = useState<TransitLine | null>(null)
  const [boardingStop, setBoardingStop] = useState<DirectedStop | null>(null)
  const [alightingStop, setAlightingStop] = useState<DestinationStop | null>(null)
  const [boardingStopQuery, setBoardingStopQuery] = useState('')
  const [alightingStopQuery, setAlightingStopQuery] = useState('')

  const healthQuery = useQuery({
    queryKey: ['system', 'health'],
    queryFn: ({ signal }) => fetchSystemHealth(signal),
    refetchInterval: 30_000,
    retry: 1,
  })

  const lineQuery = useQuery({
    queryKey: ['transit-lines', provider, submittedQuery],
    queryFn: ({ signal }) => searchTransitLines(provider, submittedQuery, signal),
    enabled: submittedQuery.length > 0,
  })

  const stopQuery = useQuery({
    queryKey: ['directed-stops', selectedLine?.id],
    queryFn: ({ signal }) => fetchDirectedStops(selectedLine!.id, signal),
    enabled: selectedLine !== null,
  })

  const destinationQuery = useQuery({
    queryKey: ['destination-stops', selectedLine?.id, boardingStop?.stopId],
    queryFn: ({ signal }) =>
      fetchDestinationStops(selectedLine!.id, boardingStop!.stopId, signal),
    enabled: selectedLine !== null && boardingStop !== null,
  })

  const arrivalQuery = useQuery({
    queryKey: ['upcoming-arrivals', selectedLine?.id, boardingStop?.stopId, alightingStop?.stopId],
    queryFn: ({ signal }) =>
      fetchUpcomingArrivals(
        selectedLine!.id,
        boardingStop!.stopId,
        alightingStop!.stopId,
        signal,
      ),
    enabled: selectedLine !== null && boardingStop !== null && alightingStop !== null,
    refetchInterval: 30_000,
  })

  const currentStep = alightingStop ? 4 : boardingStop ? 3 : selectedLine ? 2 : 1
  const providerOption = providerOptions.find((option) => option.value === provider)!
  const healthStatus = healthQuery.isPending ? 'checking' : healthQuery.isError ? 'offline' : 'live'
  const filteredBoardingStops = stopQuery.data?.filter((stop) =>
    includesStopQuery(stop, boardingStopQuery)) ?? []
  const filteredAlightingStops = destinationQuery.data?.filter((stop) =>
    includesStopQuery(stop, alightingStopQuery)) ?? []

  function changeProvider(nextProvider: TransitProvider) {
    setProvider(nextProvider)
    setQueryInput('')
    setSubmittedQuery('')
    setSelectedLine(null)
    setBoardingStop(null)
    setAlightingStop(null)
    setBoardingStopQuery('')
    setAlightingStopQuery('')
  }

  function submitSearch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const nextQuery = queryInput.trim()
    if (!nextQuery) return
    setSubmittedQuery(nextQuery)
    setSelectedLine(null)
    setBoardingStop(null)
    setAlightingStop(null)
    setBoardingStopQuery('')
    setAlightingStopQuery('')
  }

  function selectLine(line: TransitLine) {
    setSelectedLine(line)
    setBoardingStop(null)
    setAlightingStop(null)
    setBoardingStopQuery('')
    setAlightingStopQuery('')
  }

  function selectBoardingStop(stop: DirectedStop) {
    setBoardingStop(stop)
    setAlightingStop(null)
    setAlightingStopQuery('')
  }

  return (
    <div className="site-shell">
      <a className="skip-link" href="#journey-content">
        조회 화면으로 건너뛰기
      </a>

      <header className="site-header">
        <a className="wordmark" href="/" aria-label="첫차 홈">
          첫차<span className="wordmark-dot" aria-hidden="true" />
        </a>
        <p>실시간 대중교통 도착 안내</p>
        <span
          className="live-indicator"
          data-status={healthStatus}
          title={healthQuery.data ? `서버 확인 ${formatTime(healthQuery.data.checkedAt)}` : undefined}
          aria-label={healthStatus === 'live' ? '서버 연결 정상' : healthStatus === 'checking' ? '서버 연결 확인 중' : '서버 연결 끊김'}
          role="status"
        >
          <i aria-hidden="true" />
          {healthStatus === 'live' ? 'LIVE' : healthStatus === 'checking' ? '연결 중' : 'OFFLINE'}
        </span>
      </header>

      <main id="journey-content">
        <section className="intro" aria-labelledby="page-title">
          <p className="section-code">Realtime transit / Nationwide</p>
          <h1 id="page-title">첫 차와 다음 차를<br />한눈에.</h1>
          <p className="intro-copy">타려는 노선과 내릴 정류장을 고르면 지금 접근 중인 차량 두 대를 보여드려요.</p>
        </section>

        <nav className="journey-progress" aria-label="도착 조회 단계">
          <div className="progress-line" aria-hidden="true">
            <span style={{ width: `${((currentStep - 1) / 3) * 100}%` }} />
          </div>
          <ol>
            {steps.map((step, index) => {
              const number = index + 1
              const state = number < currentStep ? 'complete' : number === currentStep ? 'current' : 'pending'
              return (
                <li key={step} data-state={state} aria-current={state === 'current' ? 'step' : undefined}>
                  <span>0{number}</span>
                  {step}
                </li>
              )
            })}
          </ol>
        </nav>

        <div className="journey-grid">
          <section className="selection-panel" aria-labelledby="line-heading">
            <div className="panel-heading">
              <span>01</span>
              <div>
                <h2 id="line-heading">어떤 노선을 탈까요?</h2>
                <p>{providerOption.label} 노선을 검색하세요.</p>
              </div>
            </div>
            <fieldset className="provider-switcher">
              <legend>교통수단 선택</legend>
              <div>
                {providerOptions.map((option) => (
                  <label key={option.value} data-selected={provider === option.value}>
                    <input
                      type="radio"
                      name="transitProvider"
                      value={option.value}
                      checked={provider === option.value}
                      onChange={() => changeProvider(option.value)}
                    />
                    <strong>{option.label}</strong>
                    <span>{option.description}</span>
                  </label>
                ))}
              </div>
            </fieldset>
            <form className="line-search" onSubmit={submitSearch} role="search">
              <label htmlFor="line-query">{providerOption.searchLabel}</label>
              <div>
                <input
                  id="line-query"
                  name="lineQuery"
                  value={queryInput}
                  onChange={(event) => setQueryInput(event.target.value)}
                  placeholder={providerOption.placeholder}
                  autoComplete="off"
                />
                <button type="submit">노선 찾기 <span aria-hidden="true">→</span></button>
              </div>
            </form>

            {lineQuery.isPending && submittedQuery ? <QueryState message="노선을 찾고 있어요." /> : null}
            {lineQuery.isError ? <QueryState message={lineErrorMessage(provider, lineQuery.error)} action="다시 시도" onAction={() => lineQuery.refetch()} /> : null}
            {lineQuery.data?.length === 0 ? <QueryState message="일치하는 노선이 없어요. 다른 번호로 검색해 보세요." /> : null}
            {lineQuery.data?.length ? (
              <ul className="option-list" aria-label="검색된 노선">
                {lineQuery.data.map((line) => (
                  <li key={line.id}>
                    <button type="button" data-selected={selectedLine?.id === line.id} onClick={() => selectLine(line)}>
                      <strong>{line.publicName}</strong>
                      <span>{line.operatorName ?? line.routeType ?? '운영 정보 없음'}</span>
                      <i aria-hidden="true">→</i>
                    </button>
                  </li>
                ))}
              </ul>
            ) : null}
          </section>

          <section className="selection-panel" aria-labelledby="boarding-heading" data-locked={!selectedLine}>
            <div className="panel-heading">
              <span>02</span>
              <div>
                <h2 id="boarding-heading">어디서 탈까요?</h2>
                <p>{selectedLine ? `${selectedLine.publicName} 노선의 승차 지점` : '먼저 노선을 선택하세요.'}</p>
              </div>
            </div>
            {selectedLine && stopQuery.isPending ? <QueryState message="정류장 순서를 불러오고 있어요." /> : null}
            {stopQuery.isError ? <QueryState message="정류장을 불러오지 못했어요." action="다시 시도" onAction={() => stopQuery.refetch()} /> : null}
            {stopQuery.data?.length === 0 ? <QueryState message="이 노선에는 선택할 수 있는 정류장이 없어요." /> : null}
            {stopQuery.data?.length ? (
              <div className="stop-search">
                <div>
                  <label htmlFor="boarding-stop-query">승차 정류장 검색</label>
                  <span aria-live="polite">{filteredBoardingStops.length}곳</span>
                </div>
                <input
                  id="boarding-stop-query"
                  name="boardingStopQuery"
                  type="search"
                  value={boardingStopQuery}
                  onChange={(event) => setBoardingStopQuery(event.target.value)}
                  placeholder="정류장 이름, 방향 또는 순번"
                  autoComplete="off"
                  aria-controls="boarding-stop-list"
                />
              </div>
            ) : null}
            {stopQuery.data?.length && filteredBoardingStops.length === 0 ? (
              <QueryState message={`“${boardingStopQuery.trim()}”과 일치하는 승차 정류장이 없어요.`} />
            ) : null}
            {filteredBoardingStops.length ? (
              <ul id="boarding-stop-list" className="option-list stop-list" aria-label="승차 정류장">
                {filteredBoardingStops.map((stop) => (
                  <li key={`${stop.directionId}-${stop.stopSequence}`}>
                    <button type="button" data-selected={boardingStop?.directionId === stop.directionId && boardingStop.stopSequence === stop.stopSequence} onClick={() => selectBoardingStop(stop)}>
                      <span className="sequence">{String(stop.stopSequence).padStart(2, '0')}</span>
                      <strong>{stop.stopName}</strong>
                      <span>{stop.displayDirection ?? stop.directionName}</span>
                    </button>
                  </li>
                ))}
              </ul>
            ) : null}
          </section>

          <section className="selection-panel" aria-labelledby="alighting-heading" data-locked={!boardingStop}>
            <div className="panel-heading">
              <span>03</span>
              <div>
                <h2 id="alighting-heading">어디서 내릴까요?</h2>
                <p>{boardingStop ? `${boardingStop.stopName} 이후 정류장` : '승차 정류장을 선택하세요.'}</p>
              </div>
            </div>
            {boardingStop && destinationQuery.isPending ? <QueryState message="하차 가능한 정류장을 찾고 있어요." /> : null}
            {destinationQuery.isError ? <QueryState message="하차 정류장을 불러오지 못했어요." action="다시 시도" onAction={() => destinationQuery.refetch()} /> : null}
            {destinationQuery.data?.length === 0 ? <QueryState message="이 지점 이후의 하차 정류장이 없어요." /> : null}
            {destinationQuery.data?.length ? (
              <div className="stop-search">
                <div>
                  <label htmlFor="alighting-stop-query">하차 정류장 검색</label>
                  <span aria-live="polite">{filteredAlightingStops.length}곳</span>
                </div>
                <input
                  id="alighting-stop-query"
                  name="alightingStopQuery"
                  type="search"
                  value={alightingStopQuery}
                  onChange={(event) => setAlightingStopQuery(event.target.value)}
                  placeholder="정류장 이름, 방향 또는 순번"
                  autoComplete="off"
                  aria-controls="alighting-stop-list"
                />
              </div>
            ) : null}
            {destinationQuery.data?.length && filteredAlightingStops.length === 0 ? (
              <QueryState message={`“${alightingStopQuery.trim()}”과 일치하는 하차 정류장이 없어요.`} />
            ) : null}
            {filteredAlightingStops.length ? (
              <ul id="alighting-stop-list" className="option-list stop-list" aria-label="하차 정류장">
                {filteredAlightingStops.map((stop) => (
                  <li key={`${stop.directionId}-${stop.stopSequence}`}>
                    <button type="button" data-selected={alightingStop?.directionId === stop.directionId && alightingStop.stopSequence === stop.stopSequence} onClick={() => setAlightingStop(stop)}>
                      <span className="sequence">{String(stop.stopSequence).padStart(2, '0')}</span>
                      <strong>{stop.stopName}</strong>
                      <span>{stop.directionName}</span>
                    </button>
                  </li>
                ))}
              </ul>
            ) : null}
          </section>
        </div>

        <section className="arrival-section" aria-labelledby="arrival-heading" data-active={Boolean(alightingStop)}>
          <div className="arrival-heading-row">
            <div className="panel-heading inverse">
              <span>04</span>
              <div>
                <h2 id="arrival-heading">곧 도착하는 차량</h2>
                <p>{alightingStop ? `${boardingStop?.stopName} → ${alightingStop.stopName}` : '승·하차 지점을 모두 선택하세요.'}</p>
              </div>
            </div>
            {arrivalQuery.dataUpdatedAt ? <p className="updated-at">30초마다 갱신 · {formatTime(new Date(arrivalQuery.dataUpdatedAt).toISOString())}</p> : null}
          </div>
          {alightingStop && arrivalQuery.isPending ? <QueryState message="실시간 차량 위치를 확인하고 있어요." /> : null}
          {arrivalQuery.isError ? <QueryState message="도착 정보를 불러오지 못했어요." action="다시 확인" onAction={() => arrivalQuery.refetch()} /> : null}
          {arrivalQuery.data?.length === 0 ? <QueryState message={provider === 'NATIONAL_PRECISION_BUS' ? '실시간 차량 위치는 수집됐지만 이 API는 도착 예정시각을 제공하지 않아요.' : '현재 접근 중인 차량이 없어요. 잠시 후 다시 확인해 주세요.'} action="새로고침" onAction={() => arrivalQuery.refetch()} /> : null}
          {arrivalQuery.data?.length ? (
            <div className="arrival-list" aria-live="polite">
              {arrivalQuery.data.map((arrival, index) => <ArrivalBoard key={arrival.arrivalPredictionId} arrival={arrival} index={index} />)}
            </div>
          ) : null}
        </section>
      </main>

      <footer className="site-footer">
        <span>첫차 / Realtime Transit Tracker</span>
        <span>도착 정보는 교통 상황에 따라 달라질 수 있습니다.</span>
      </footer>
    </div>
  )
}
