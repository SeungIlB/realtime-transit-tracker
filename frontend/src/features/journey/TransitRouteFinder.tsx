import { useRef, useState } from 'react'
import type { FormEvent } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { searchPlaces, type PlaceSearchResult } from '../../api/geocoding'
import { calculateRouteDecision, type RouteDecision, type RouteTransitLegPayload } from '../../api/journey'
import { searchTransitRoutes, sortTransitRoutes, type TransitRouteLeg, type TransitRouteSortMode } from '../../api/kakao'
import { fetchDirectedStops, searchTransitLines, type TransitProvider } from '../../api/transit'
import { Button, TextField } from '../../components/ui'
import { findUniqueLine, findUniqueStopPair } from './routeMatching'

type Coordinate = {
  latitude: number
  longitude: number
  accuracyM?: number
  observedAt?: string
}

type TransitRouteFinderProps = {
  origin: Coordinate
  onChooseLeg: (leg: SuggestedTransitLeg) => void
}

export type SuggestedTransitLeg = {
  provider: TransitProvider
  query: string
  startName: string
  endName: string
  lineSearchLocation: Coordinate | null
}

type RouteCalculationState =
  | { state: 'loading' }
  | { state: 'error'; message: string }
  | { state: 'success'; decision: RouteDecision }

function shortPlaceName(displayName: string) {
  return displayName.split(',').slice(0, 3).join(', ').trim()
}

function formatFare(fareWon: number) {
  return new Intl.NumberFormat('ko-KR').format(fareWon)
}

function routeTypeLabel(pathType: string) {
  return { SUBWAY: '지하철', BUS: '버스', BUS_SUBWAY: '버스 + 지하철' }[pathType] ?? '대중교통'
}

function legQuery(leg: TransitRouteLeg) {
  return (leg.laneNames[0] ?? '')
    .replace(/^수도권\s*/u, '')
    .replace(/^서울\s*/u, '')
    .trim()
}

function routeErrorMessage(error: Error | null) { return error?.message ?? '대중교통 경로를 불러오지 못했어요. 잠시 후 다시 시도해 주세요.' }

function formatTime(value: string) {
  return new Intl.DateTimeFormat('ko-KR', { hour: '2-digit', minute: '2-digit', hour12: false }).format(new Date(value))
}

function probabilityPercent(value: number | null) {
  return value === null ? '—' : `${Math.round(Math.max(0, Math.min(1, value)) * 100)}%`
}

function routeDecisionCopy(decision: RouteDecision) {
  if (decision.decision === 'INSUFFICIENT_DATA') return '일부 구간의 실시간 차량을 확인하지 못했어요.'
  if (decision.decision === 'COMFORTABLE') return '환승까지 여유 있게 이어갈 수 있어요.'
  if (decision.decision === 'LEAVE_NOW') return '지금 출발하면 환승까지 이어갈 가능성이 높아요.'
  if (decision.decision === 'HURRY') return '서둘러야 이 환승 경로를 이어갈 수 있어요.'
  return '현재 차량 조합으로는 환승 성공 가능성이 낮아요.'
}

function transferWalkMinutesBefore(legs: TransitRouteLeg[], targetIndex: number) {
  let transitIndex = -1
  let pendingWalk = 0
  for (const leg of legs) {
    if (leg.type === 'WALK') {
      if (transitIndex >= 0) pendingWalk += leg.sectionTimeMinutes
      continue
    }
    transitIndex += 1
    if (transitIndex === targetIndex) return transitIndex === 0 ? 0 : pendingWalk
    pendingWalk = 0
  }
  return 0
}

function RouteDecisionSummary({ decision }: { decision: RouteDecision }) {
  return (
    <div className="route-live-result" aria-live="polite">
      <div className="route-live-summary">
        <div><small>전체 탑승·환승 확률</small><strong>{probabilityPercent(decision.overallProbability)}</strong></div>
        <div>
          <b>{routeDecisionCopy(decision)}</b>
          <p>{decision.expectedArrivalAt ? `${formatTime(decision.expectedArrivalAt)} 도착 예상` : decision.reasons[0]}</p>
        </div>
      </div>
      {decision.legs.length ? (
        <ol className="route-live-legs" aria-label="구간별 실시간 탑승 확률">
          {decision.legs.map((leg) => (
            <li key={`${leg.legIndex}-${leg.lineId}`}>
              <span>{leg.legIndex + 1}</span>
              <div><strong>{leg.lineName}</strong><p>{leg.boardingStopName} → {leg.alightingStopName}</p></div>
              <div><b>{probabilityPercent(leg.connectionProbability)}</b><small>{formatTime(leg.vehicleExpectedAt)} 탑승</small></div>
            </li>
          ))}
        </ol>
      ) : null}
      <p className="route-live-note">{decision.reasons.at(-1)}</p>
    </div>
  )
}

export function TransitRouteFinder({ origin, onChooseLeg }: TransitRouteFinderProps) {
  const queryClient = useQueryClient()
  const [destinationQuery, setDestinationQuery] = useState('')
  const [submittedDestinationQuery, setSubmittedDestinationQuery] = useState('')
  const [destination, setDestination] = useState<PlaceSearchResult | null>(null)
  const [routeRequest, setRouteRequest] = useState<Coordinate | null>(null)
  const [routeSortMode, setRouteSortMode] = useState<TransitRouteSortMode>('OPTIMAL')
  const [selectedRouteIndex, setSelectedRouteIndex] = useState<number | null>(null)
  const routeSearchInFlight = useRef(false)
  const routeCalculationInFlight = useRef(new Set<number>())
  const [routeCalculations, setRouteCalculations] = useState<Record<number, RouteCalculationState>>({})

  const destinationResults = useQuery({
    queryKey: ['route-destination', submittedDestinationQuery],
    queryFn: ({ signal }) => searchPlaces(submittedDestinationQuery, signal),
    enabled: submittedDestinationQuery.length > 0,
    staleTime: Infinity,
    retry: 1,
  })
  const routeResults = useQuery({
    queryKey: ['kakao-routes', origin.latitude, origin.longitude, routeRequest?.latitude, routeRequest?.longitude],
    queryFn: ({ signal }) => searchTransitRoutes(origin, routeRequest!, signal),
    enabled: routeRequest !== null,
    staleTime: 24 * 60 * 60 * 1000,
    retry: false,
  })

  function submitDestination(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const query = destinationQuery.trim()
    if (!query) return
    if (query === submittedDestinationQuery) {
      void destinationResults.refetch()
      return
    }
    setSubmittedDestinationQuery(query)
  }

  function selectDestination(result: PlaceSearchResult) {
    setDestination(result)
    setDestinationQuery(shortPlaceName(result.displayName))
    setSubmittedDestinationQuery('')
    setRouteRequest(null)
    setRouteCalculations({})
    setSelectedRouteIndex(null)
  }

  async function findRoutes() {
    if (!destination || routeSearchInFlight.current) return
    routeSearchInFlight.current = true
    setRouteCalculations({})
    try {
      const sameRequest = routeRequest?.latitude === destination.latitude
        && routeRequest.longitude === destination.longitude
      if (sameRequest) {
        await routeResults.refetch()
      } else {
        setRouteRequest({ latitude: destination.latitude, longitude: destination.longitude })
      }
    } finally {
      routeSearchInFlight.current = false
    }
  }

  async function resolveRouteLeg(leg: TransitRouteLeg, transferWalkTimeMinutes: number): Promise<RouteTransitLegPayload> {
    const query = legQuery(leg)
    if (!query || !leg.startName || !leg.endName) throw new Error('노선이나 정류장 이름이 없는 구간은 자동 계산할 수 없어요.')
    const provider: TransitProvider = leg.type === 'SUBWAY' ? 'SEOUL_SUBWAY' : 'NATIONAL_PRECISION_BUS'
    const lineLocation = leg.startLatitude !== null && leg.startLongitude !== null
      ? { latitude: leg.startLatitude, longitude: leg.startLongitude }
      : { latitude: origin.latitude, longitude: origin.longitude }
    const lines = await queryClient.fetchQuery({
      queryKey: ['transit-lines', provider, query, lineLocation.latitude, lineLocation.longitude],
      queryFn: ({ signal }) => searchTransitLines(provider, query, lineLocation, signal),
    })
    const line = findUniqueLine(lines, query)
    if (!line) throw new Error(`${query} 노선을 하나로 확정하지 못했어요. 아래에서 구간을 직접 계산해 주세요.`)
    const stops = await queryClient.fetchQuery({
      queryKey: ['directed-stops', line.id],
      queryFn: ({ signal }) => fetchDirectedStops(line.id, signal),
    })
    const pair = findUniqueStopPair(stops, leg.startName, leg.endName)
    if (!pair) throw new Error(`${leg.startName} → ${leg.endName} 방향을 하나로 확정하지 못했어요.`)
    return {
      lineId: line.id,
      directionId: pair.boarding.directionId,
      boardingStopId: pair.boarding.stopId,
      alightingStopId: pair.alighting.stopId,
      lineName: line.publicName,
      boardingStopName: pair.boarding.stopName,
      alightingStopName: pair.alighting.stopName,
      sectionTimeMinutes: Math.max(1, leg.sectionTimeMinutes),
      transferWalkTimeMinutes,
    }
  }

  async function calculateLiveRoute(routeIndex: number) {
    const route = routeResults.data?.[routeIndex]
    if (!route || routeCalculationInFlight.current.has(routeIndex)) return
    const transitLegs = route.legs.filter((leg) => leg.type !== 'WALK')
    if (!transitLegs.length || transitLegs.length > 3) {
      setRouteCalculations((current) => ({ ...current, [routeIndex]: { state: 'error', message: '실시간 계산은 대중교통 1~3구간 경로에서 지원해요.' } }))
      return
    }
    routeCalculationInFlight.current.add(routeIndex)
    setRouteCalculations((current) => ({ ...current, [routeIndex]: { state: 'loading' } }))
    try {
      const legs: RouteTransitLegPayload[] = []
      for (let index = 0; index < transitLegs.length; index += 1) {
        legs.push(await resolveRouteLeg(transitLegs[index], transferWalkMinutesBefore(route.legs, index)))
      }
      const decision = await calculateRouteDecision({
        latitude: origin.latitude,
        longitude: origin.longitude,
        accuracyM: origin.accuracyM ?? 0,
        observedAt: origin.observedAt ?? new Date().toISOString(),
        targetProbability: null,
        legs,
      })
      setRouteCalculations((current) => ({ ...current, [routeIndex]: { state: 'success', decision } }))
    } catch (error) {
      const message = error instanceof Error ? error.message : '환승 확률을 계산하지 못했어요.'
      setRouteCalculations((current) => ({ ...current, [routeIndex]: { state: 'error', message } }))
    } finally {
      routeCalculationInFlight.current.delete(routeIndex)
    }
  }

  function selectRoute(routeIndex: number) {
    setSelectedRouteIndex(routeIndex)
    void calculateLiveRoute(routeIndex)
  }

  return (
    <section className="route-finder" aria-labelledby="route-finder-title">
      <header>
        <div>
          <h3 id="route-finder-title">목적지로 가는 경로 찾기</h3>
          <p>추천 경로를 확인하고, 탑승 확률을 계산할 구간을 고르세요.</p>
        </div>
        <span>선택</span>
      </header>

      <form className="route-destination-form" onSubmit={submitDestination} role="search">
        <TextField
          variant="box"
          label="목적지"
          labelOption="sustain"
          id="route-destination"
          name="routeDestination"
          type="search"
          value={destinationQuery}
          onChange={(event) => { setDestinationQuery(event.target.value); setDestination(null); setRouteRequest(null) }}
          placeholder="예: 부평역, 서울역"
          autoComplete="off"
        />
        <Button type="submit" display="full" size="large" loading={destinationResults.isFetching}>목적지 찾기</Button>
      </form>

      {destinationResults.isError ? <p className="inline-error" role="alert">목적지를 찾지 못했어요. 지역명을 함께 입력해 보세요.</p> : null}
      {destinationResults.data?.length ? (
        <ul className="route-place-list" aria-label="검색된 목적지">
          {destinationResults.data.map((result) => (
            <li key={result.id}>
              <button type="button" onClick={() => selectDestination(result)}>
                <strong>{shortPlaceName(result.displayName)}</strong>
                <small>{result.displayName}</small>
              </button>
            </li>
          ))}
        </ul>
      ) : null}

      {destination ? (
        <div className="route-search-action">
          <p><strong>{shortPlaceName(destination.displayName)}</strong>까지 새 경로를 조회합니다.</p>
          <Button type="button" size="large" onClick={findRoutes} loading={routeResults.isFetching}>대중교통 경로 찾기</Button>
        </div>
      ) : null}

      {routeResults.isError ? <p className="inline-error" role="alert">{routeErrorMessage(routeResults.error)}</p> : null}
      {routeResults.isSuccess && routeResults.data.length === 0 ? <p className="route-empty" role="status">이 구간의 대중교통 경로가 없어요.</p> : null}
      {routeResults.data?.length ? (
        <>
          <div className="route-sort-tabs" role="tablist" aria-label="경로 정렬 기준">
            {([
              ['OPTIMAL', '최적'],
              ['FEWEST_TRANSFERS', '최소 환승'],
              ['FASTEST', '가장 빠른'],
            ] as const).map(([mode, label]) => (
              <button
                key={mode}
                type="button"
                role="tab"
                aria-selected={routeSortMode === mode}
                className={routeSortMode === mode ? 'is-active' : undefined}
                onClick={() => { setRouteSortMode(mode); setRouteCalculations({}) }}
              >
                {label}
              </button>
            ))}
          </div>
          <ol className="route-result-list" aria-label="추천 대중교통 경로">
          {sortTransitRoutes(routeResults.data, routeSortMode).map((route, routeIndex) => {
            const transitLegs = route.legs.filter((leg) => leg.type !== 'WALK')
            const calculation = routeCalculations[routeIndex]
            const isCollapsed = selectedRouteIndex !== null && selectedRouteIndex !== routeIndex
            return (
              <li key={`${route.pathType}-${routeIndex}`} className={`route-result${isCollapsed ? ' is-collapsed' : ''}`}>
                <div className="route-result-summary" onClick={() => selectRoute(routeIndex)}>
                  <button type="button" className="route-result-select" aria-expanded={!isCollapsed} onClick={(event) => { event.stopPropagation(); selectRoute(routeIndex) }}>
                    <span>{routeTypeLabel(route.pathType)}</span><strong>{route.totalTimeMinutes}분</strong>
                  </button>
                  <p>도보 {route.totalWalkMeters}m · 환승 {route.transferCount}회 · {formatFare(route.fareWon)}원</p>
                  <button type="button" className="route-result-toggle" onClick={(event) => { event.stopPropagation(); selectRoute(routeIndex) }}>
                    {isCollapsed ? '경로 펼치기' : '경로 선택'}
                  </button>
                </div>
                {!isCollapsed ? <ol className="route-leg-list">
                  {transitLegs.map((leg, legIndex) => {
                    const query = legQuery(leg)
                    const provider: TransitProvider = leg.type === 'SUBWAY' ? 'SEOUL_SUBWAY' : 'NATIONAL_PRECISION_BUS'
                    return (
                      <li key={`${leg.type}-${legIndex}`}>
                        <div className="route-leg-symbol" data-type={leg.type}>{leg.type === 'SUBWAY' ? '철도' : '버스'}</div>
                        <div className="route-leg-copy">
                          <strong>{leg.laneNames.join(' · ') || routeTypeLabel(leg.type)}</strong>
                          <p>{leg.startName ?? '승차 지점'} → {leg.endName ?? '하차 지점'} · {leg.stationCount}정거장</p>
                          {leg.directionName ? <small>{leg.directionName}</small> : null}
                        </div>
                        <Button
                          type="button"
                          size="small"
                          variant="weak"
                          disabled={!query || !leg.startName || !leg.endName}
                          onClick={() => onChooseLeg({
                            provider,
                            query,
                            startName: leg.startName!,
                            endName: leg.endName!,
                            lineSearchLocation: leg.startLatitude !== null && leg.startLongitude !== null
                              ? { latitude: leg.startLatitude, longitude: leg.startLongitude }
                              : null,
                          })}
                        >
                          이 구간 탑승 계산
                        </Button>
                      </li>
                    )
                  })}
                </ol> : null}
                {!isCollapsed ? <div className="route-live-action">
                  <div><strong>환승까지 탈 수 있을까요?</strong><p>현재 위치와 각 구간의 실시간 차량을 한 번에 비교해요.</p></div>
                  <Button type="button" size="large" onClick={() => calculateLiveRoute(routeIndex)} loading={calculation?.state === 'loading'}>
                    실시간 확률 계산
                  </Button>
                </div> : null}
                {!isCollapsed && calculation?.state === 'error' ? <p className="inline-error route-live-error" role="alert">{calculation.message}</p> : null}
                {!isCollapsed && calculation?.state === 'success' ? <RouteDecisionSummary decision={calculation.decision} /> : null}
              </li>
            )
          })}
          </ol>
        </>
      ) : null}

      <p className="route-provider-note">경로 정보 © Kakao Mobility · 경로 결과는 잠시 캐시될 수 있어요.</p>
    </section>
  )
}
