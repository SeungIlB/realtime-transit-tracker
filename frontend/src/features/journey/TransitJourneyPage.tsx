import { useEffect, useRef, useState } from 'react'
import type { CSSProperties, FormEvent, PointerEvent as ReactPointerEvent } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Button, TextField } from '../../components/ui'
import { ApiError, getAnonymousKey } from '../../api/client'
import { searchPlaces, type PlaceSearchResult } from '../../api/geocoding'
import {
  addJourneyLocation,
  cancelJourney,
  createJourney,
  fetchBoardingDecision,
  type BoardingDecision,
  type PacePrediction,
  type VehicleBoardingPrediction,
} from '../../api/journey'
import { fetchSystemHealth } from '../../api/system'
import {
  fetchDestinationStops,
  fetchDirectedStops,
  searchTransitLines,
  type DestinationStop,
  type DirectedStop,
  type TransitLine,
  type TransitProvider,
} from '../../api/transit'

const steps = ['위치', '노선', '정류장', '판단']
const providerOptions: Array<{
  value: TransitProvider
  label: string
  description: string
  searchLabel: string
  placeholder: string
}> = [
  { value: 'NATIONAL_PRECISION_BUS', label: '전국 버스', description: '초정밀 위치', searchLabel: '버스 번호', placeholder: '예: 중구01, 1000' },
  { value: 'GBIS', label: '경기 버스', description: '도착 예정', searchLabel: '경기버스 번호', placeholder: '예: 6601, 7770' },
  { value: 'SEOUL_SUBWAY', label: '수도권 전철', description: '실시간 도착', searchLabel: '호선 이름', placeholder: '예: 1호선, 서해선' },
]

type LocationSnapshot = {
  latitude: number
  longitude: number
  accuracyM: number
  speedMps: number | null
  observedAt: string
  source: 'browser' | 'search'
}

type JourneyUiState = 'idle' | 'starting' | 'tracking' | 'error'

type BoardingAccess = {
  distanceM: number
  walkMinutes: number
  isRemote: boolean
}

const WALKING_DETOUR_FACTOR = 1.2
const WALKING_SPEED_MPS = 1.3
const DEPARTURE_PREPARATION_SECONDS = 10
const REMOTE_BOARDING_MINUTES = 30
const MANUAL_LOCATION_REFRESH_MS = 50_000

const decisionCopy: Record<string, { title: string; detail: string }> = {
  COMFORTABLE: { title: '여유 있게 탈 수 있어요', detail: '천천히 이동해도 목표 확률을 넘습니다.' },
  LEAVE_NOW: { title: '지금 출발하세요', detail: '보통 걸음으로 바로 움직이는 편이 안전합니다.' },
  HURRY: { title: '빠르게 움직이세요', detail: '빠른 걸음이나 달리기가 필요한 상황입니다.' },
  UNLIKELY: { title: '첫 차는 어려워요', detail: '무리하지 말고 다음 차량을 확인해 보세요.' },
  NO_VEHICLE: { title: '접근 중인 차량이 없어요', detail: '새 차량이 확인되면 자동으로 다시 계산합니다.' },
  INSUFFICIENT_DATA: { title: '위치를 다시 확인해 주세요', detail: '최근 위치가 있어야 탑승 가능성을 계산할 수 있습니다.' },
}

const paceLabels: Record<string, string> = {
  SLOW_WALK: '천천히 걷기',
  WALK: '보통 걸음',
  FAST_WALK: '빠른 걸음',
  RUN: '뛰기',
}

const confidenceLabels: Record<string, string> = {
  HIGH: '높음',
  MEDIUM: '보통',
  LOW: '낮음',
  UNKNOWN: '확인 중',
}

function formatTime(value: string) {
  return new Intl.DateTimeFormat('ko-KR', { hour: '2-digit', minute: '2-digit', hour12: false }).format(new Date(value))
}

function minutesUntil(expectedAt: string) {
  return Math.max(0, Math.ceil((new Date(expectedAt).getTime() - Date.now()) / 60_000))
}

function formatDistance(distanceM: number) {
  return distanceM < 1000 ? `${Math.round(distanceM)}m` : `${(distanceM / 1000).toFixed(1)}km`
}

function shortPlaceName(displayName: string) {
  return displayName.split(',').slice(0, 3).join(', ').trim()
}

function formatDuration(minutes: number) {
  if (minutes < 60) return `약 ${minutes}분`
  const hours = Math.floor(minutes / 60)
  const remainingMinutes = minutes % 60
  return remainingMinutes ? `약 ${hours}시간 ${remainingMinutes}분` : `약 ${hours}시간`
}

function distanceMeters(location: Pick<LocationSnapshot, 'latitude' | 'longitude'>, stop: DirectedStop) {
  if (stop.latitude === null || stop.longitude === null) return Number.POSITIVE_INFINITY
  const radius = 6_371_008.8
  const toRadians = (degrees: number) => degrees * Math.PI / 180
  const latitudeDelta = toRadians(stop.latitude - location.latitude)
  const longitudeDelta = toRadians(stop.longitude - location.longitude)
  const latitude1 = toRadians(location.latitude)
  const latitude2 = toRadians(stop.latitude)
  const value = Math.sin(latitudeDelta / 2) ** 2
    + Math.cos(latitude1) * Math.cos(latitude2) * Math.sin(longitudeDelta / 2) ** 2
  return radius * 2 * Math.asin(Math.sqrt(Math.min(1, value)))
}

function boardingAccess(location: LocationSnapshot | null, stop: DirectedStop | null): BoardingAccess | null {
  if (!location || !stop) return null
  const distanceM = distanceMeters(location, stop)
  if (!Number.isFinite(distanceM)) return null
  const walkSeconds = distanceM * WALKING_DETOUR_FACTOR / WALKING_SPEED_MPS + DEPARTURE_PREPARATION_SECONDS
  const walkMinutes = Math.max(1, Math.ceil(walkSeconds / 60))
  return { distanceM, walkMinutes, isRemote: walkMinutes >= REMOTE_BOARDING_MINUTES }
}

function includesStopQuery(stop: DirectedStop | DestinationStop, query: string) {
  const normalizedQuery = query.trim().toLocaleLowerCase('ko-KR')
  if (!normalizedQuery) return true
  return [stop.stopName, stop.directionName, String(stop.stopSequence), 'displayDirection' in stop ? stop.displayDirection : null]
    .filter((value): value is string => Boolean(value))
    .some((value) => value.toLocaleLowerCase('ko-KR').includes(normalizedQuery))
}

function lineErrorMessage(provider: TransitProvider, error: Error | null) {
  if (error instanceof ApiError && error.code === 'REQUEST_TIMEOUT') {
    return '노선 검색이 지연되고 있어요. 잠시 후 다시 시도해 주세요.'
  }
  if (provider === 'GBIS' && error instanceof ApiError && error.code === 'EXTERNAL_API_AUTHENTICATION_FAILED') {
    return '경기버스 노선정보 인증을 확인하지 못했어요. 잠시 후 다시 시도해 주세요.'
  }
  return `${providerOptions.find((option) => option.value === provider)?.label ?? '교통'} 노선을 불러오지 못했어요.`
}

function journeyErrorMessage(error: unknown) {
  if (!(error instanceof ApiError)) return '탑승 가능성 계산을 시작하지 못했어요. 잠시 후 다시 시도해 주세요.'
  if (error.code === 'INTERNAL_SERVER_ERROR') return '탑승 가능성 계산을 준비하지 못했어요. 잠시 후 다시 시도해 주세요.'
  if (error.code === 'INVALID_JOURNEY_STOPS') return '선택한 노선과 정류장 방향이 맞지 않아요. 승차 정류장을 다시 선택해 주세요.'
  if (error.code === 'BOARDING_STOP_COORDINATES_MISSING') return '선택한 승차 정류장의 위치 정보가 없어 계산할 수 없어요.'
  return error.message
}

function positionErrorMessage(error: GeolocationPositionError) {
  if (error.code === error.PERMISSION_DENIED) return '위치 권한이 꺼져 있어요. 브라우저에서 허용하거나 좌표를 직접 입력해 주세요.'
  if (error.code === error.POSITION_UNAVAILABLE) return '현재 위치를 확인할 수 없어요. 실외에서 다시 시도하거나 좌표를 직접 입력해 주세요.'
  return '위치 확인 시간이 초과됐어요. 다시 시도해 주세요.'
}

function getBrowserPosition() {
  return new Promise<GeolocationPosition>((resolve, reject) => {
    navigator.geolocation.getCurrentPosition(resolve, reject, {
      enableHighAccuracy: true,
      maximumAge: 10_000,
      timeout: 15_000,
    })
  })
}

function toLocationSnapshot(position: GeolocationPosition): LocationSnapshot {
  return {
    latitude: position.coords.latitude,
    longitude: position.coords.longitude,
    accuracyM: position.coords.accuracy,
    speedMps: position.coords.speed,
    observedAt: new Date(position.timestamp).toISOString(),
    source: 'browser',
  }
}

function QueryState({ message, action, onAction }: { message: string; action?: string; onAction?: () => void }) {
  return (
    <div className="feedback" role="status">
      <span className="feedback-dot" aria-hidden="true" />
      <p>{message}</p>
      {action && onAction ? <Button type="button" size="small" variant="weak" onClick={onAction}>{action}</Button> : null}
    </div>
  )
}

function probabilityPercent(value: number) {
  return Math.round(Math.max(0, Math.min(1, value)) * 100)
}

function manualLocationRefreshDue(lastUploadedAt: string | null) {
  return !lastUploadedAt
    || Date.now() - new Date(lastUploadedAt).getTime() >= MANUAL_LOCATION_REFRESH_MS
}

function recommendedPrediction(vehicle: VehicleBoardingPrediction) {
  return vehicle.pacePredictions.find((prediction) => prediction.recommended)
    ?? vehicle.pacePredictions.find((prediction) => prediction.paceType === vehicle.recommendedPace)
}

function vehiclePosition(vehicle: VehicleBoardingPrediction, routeStops: DirectedStop[]) {
  const observed = vehicle.observedAt ? `${formatTime(vehicle.observedAt)} 관측` : '관측 시각 확인 중'
  const remaining = typeof vehicle.remainingStops === 'number' ? `승차 지점까지 ${vehicle.remainingStops}정거장` : null
  if (vehicle.currentStopName) {
    const movement = {
      ARRIVED: '정차 중',
      DEPARTED: '출발',
      APPROACHING: '진입 중',
      BETWEEN: '다음 구간 이동 중',
    }[vehicle.movementStatus ?? ''] ?? '인근'
    return { label: `${vehicle.currentStopName} ${movement}`, detail: [remaining, observed].filter(Boolean).join(' · ') }
  }
  if (typeof vehicle.latitude === 'number' && typeof vehicle.longitude === 'number') {
    const coordinates = { latitude: vehicle.latitude, longitude: vehicle.longitude }
    const positionedStops = routeStops.filter((stop) => stop.latitude !== null && stop.longitude !== null)
    const nearest = positionedStops.length
      ? positionedStops.reduce((closest, stop) => distanceMeters(coordinates, stop) < distanceMeters(coordinates, closest) ? stop : closest)
      : null
    if (nearest) {
      return {
        label: `${nearest.stopName} 인근`,
        detail: [`정류장과 약 ${formatDistance(distanceMeters(coordinates, nearest))}`, remaining, observed].filter(Boolean).join(' · '),
      }
    }
  }
  return { label: remaining ?? '현재 위치 확인 중', detail: observed }
}

function VehicleCard({ vehicle, index, selected, routeStops }: { vehicle: VehicleBoardingPrediction; index: number; selected: boolean; routeStops: DirectedStop[] }) {
  const recommended = recommendedPrediction(vehicle)
  const position = vehiclePosition(vehicle, routeStops)
  const serviceLabel = { LOCAL: '일반', EXPRESS: '급행', RAPID: '특급' }[vehicle.serviceType] ?? null
  return (
    <article className="vehicle-card" data-selected={selected}>
      <header>
        <div>
          <span className="vehicle-order">{index === 0 ? '첫 번째 차량' : '다음 차량'}</span>
          <strong>{minutesUntil(vehicle.vehicleExpectedAt)}분 뒤</strong>
        </div>
        {selected ? <span className="recommendation-mark">추천 차량</span> : null}
      </header>
      <p className="vehicle-caption">
        {formatTime(vehicle.vehicleExpectedAt)} 도착 예상 · {vehicle.destinationStopName ? `${vehicle.destinationStopName}행 · ` : ''}{vehicle.providerVehicleId}
        {serviceLabel ? <span className="service-type-badge" data-service={vehicle.serviceType}>{serviceLabel}</span> : null}
      </p>
      <div className="vehicle-position"><span aria-hidden="true" /><div><small>현재 위치</small><strong>{position.label}</strong><p>{position.detail}</p></div></div>
      <ul className="pace-list" aria-label={`${index + 1}번째 차량 이동 방법별 탑승 확률`}>
        {vehicle.pacePredictions.map((prediction) => (
          <li key={prediction.paceType} data-recommended={prediction.recommended}>
            <span>{paceLabels[prediction.paceType] ?? prediction.paceType}</span>
            <strong>{probabilityPercent(prediction.boardingProbability)}%</strong>
          </li>
        ))}
      </ul>
      <p className="vehicle-summary">
        {recommended
          ? `${paceLabels[recommended.paceType]}로 ${formatTime(recommended.expectedAt)} 도착 예상`
          : '목표 확률을 만족하는 이동 방법이 없어요.'}
      </p>
    </article>
  )
}

function Timeline({ vehicle, pace }: { vehicle: VehicleBoardingPrediction; pace: PacePrediction }) {
  const timestamps = [
    pace.minExpectedAt, pace.expectedAt, pace.maxExpectedAt,
    vehicle.vehicleMinExpectedAt, vehicle.vehicleExpectedAt, vehicle.vehicleMaxExpectedAt,
  ].map((value) => new Date(value).getTime())
  const start = Math.min(...timestamps)
  const end = Math.max(...timestamps)
  const span = Math.max(1, end - start)
  const position = (value: string) => ((new Date(value).getTime() - start) / span) * 100
  const rangeStyle = (minimum: string, maximum: string) => ({
    '--range-start': `${position(minimum)}%`,
    '--range-width': `${Math.max(2, position(maximum) - position(minimum))}%`,
  } as CSSProperties)

  return (
    <div className="timing-comparison" aria-label="사용자와 차량의 예상 도착 시간 비교">
      <div className="timing-row">
        <span>내 도착</span>
        <div className="timing-track"><i style={rangeStyle(pace.minExpectedAt, pace.maxExpectedAt)} /><b style={{ left: `${position(pace.expectedAt)}%` }} /></div>
        <strong>{formatTime(pace.expectedAt)}</strong>
      </div>
      <div className="timing-row vehicle">
        <span>차량 도착</span>
        <div className="timing-track"><i style={rangeStyle(vehicle.vehicleMinExpectedAt, vehicle.vehicleMaxExpectedAt)} /><b style={{ left: `${position(vehicle.vehicleExpectedAt)}%` }} /></div>
        <strong>{formatTime(vehicle.vehicleExpectedAt)}</strong>
      </div>
    </div>
  )
}

function DecisionPanel({ decision, routeStops, access, boardingStopName, locationAccuracyM, onStop }: {
  decision: BoardingDecision
  routeStops: DirectedStop[]
  access: BoardingAccess | null
  boardingStopName: string | null
  locationAccuracyM?: number
  onStop: () => void
}) {
  const copy = decision.decision === 'UNLIKELY' && access?.isRemote
    ? {
        title: '승차역까지 너무 멀어요',
        detail: `현재 위치에서 ${boardingStopName ?? '승차 지점'}까지 ${formatDistance(access.distanceM)}예요. 보통 걸음으로 ${formatDuration(access.walkMinutes)}이 걸립니다.`,
      }
    : decisionCopy[decision.decision] ?? decisionCopy.INSUFFICIENT_DATA
  const selectedVehicle = decision.vehicles.find((vehicle) => vehicle.providerVehicleId === decision.recommendedVehicleId)
  const selectedPace = selectedVehicle ? recommendedPrediction(selectedVehicle) : undefined
  const shownProbability = selectedPace?.boardingProbability
    ?? selectedVehicle?.pacePredictions.at(-1)?.boardingProbability
    ?? decision.vehicles[0]?.pacePredictions.at(-1)?.boardingProbability
  const selectedIndex = selectedVehicle ? decision.vehicles.indexOf(selectedVehicle) : -1
  const title = selectedIndex === 1 && decision.decision !== 'UNLIKELY'
    ? <>다음 차를 추천해요.<br />{copy.title}</>
    : copy.title

  return (
    <div className="decision-content" aria-live="polite">
      <div className="decision-hero" data-decision={decision.decision}>
        <div><p className="decision-kicker">지금의 추천</p><h2>{title}</h2><p>{copy.detail}</p></div>
        <dl>
          <div><dt>예상 탑승</dt><dd>{shownProbability === undefined ? '—' : `${probabilityPercent(shownProbability)}%`}</dd></div>
          <div><dt>추천 속도</dt><dd>{decision.recommendedPace ? paceLabels[decision.recommendedPace] : '대기'}</dd></div>
          <div>
            <dt>데이터</dt>
            <dd>{confidenceLabels[decision.confidence] ?? decision.confidence}</dd>
            {decision.confidence === 'LOW' && locationAccuracyM && locationAccuracyM > 50
              ? <small>위치 오차 ±{Math.round(locationAccuracyM)}m 영향</small>
              : null}
          </div>
        </dl>
      </div>
      {selectedVehicle && selectedPace ? <Timeline vehicle={selectedVehicle} pace={selectedPace} /> : null}
      {decision.vehicles.length ? (
        <>
          <div className="vehicle-grid">
            {decision.vehicles.map((vehicle, index) => <VehicleCard key={vehicle.arrivalPredictionId} vehicle={vehicle} index={index} selected={vehicle.providerVehicleId === decision.recommendedVehicleId} routeStops={routeStops} />)}
          </div>
          {decision.vehicles.length === 1 ? <p className="vehicle-count-note">현재 이 구간에 정차하는 실시간 차량은 1대만 확인됐어요. 다음 차량이 잡히면 함께 표시해요.</p> : null}
        </>
      ) : null}
      <div className="decision-footer"><span>{formatTime(decision.calculatedAt)} 계산 · 1분마다 자동 갱신</span><Button type="button" color="light" variant="weak" size="small" onClick={onStop}>모니터링 종료</Button></div>
    </div>
  )
}

function MobilePageActions({
  previousLabel,
  nextLabel,
  nextDisabled = false,
  onPrevious,
  onNext,
}: {
  previousLabel?: string
  nextLabel?: string
  nextDisabled?: boolean
  onPrevious?: () => void
  onNext?: () => void
}) {
  return (
    <div className="mobile-page-actions" data-direction={!previousLabel ? 'next' : !nextLabel ? 'previous' : 'both'}>
      {previousLabel && onPrevious ? <Button type="button" size="large" variant="weak" onClick={onPrevious}>{previousLabel}</Button> : <span />}
      {nextLabel && onNext ? <Button type="button" size="large" disabled={nextDisabled} onClick={onNext}>{nextLabel}</Button> : null}
    </div>
  )
}

export function TransitJourneyPage() {
  const [location, setLocation] = useState<LocationSnapshot | null>(null)
  const [locationStatus, setLocationStatus] = useState<'idle' | 'loading' | 'ready' | 'error'>('idle')
  const [locationError, setLocationError] = useState<string | null>(null)
  const [placeSearchOpen, setPlaceSearchOpen] = useState(false)
  const [placeQuery, setPlaceQuery] = useState('')
  const [submittedPlaceQuery, setSubmittedPlaceQuery] = useState('')
  const [locationLabel, setLocationLabel] = useState<string | null>(null)
  const [provider, setProvider] = useState<TransitProvider>('GBIS')
  const [queryInput, setQueryInput] = useState('')
  const [submittedQuery, setSubmittedQuery] = useState('')
  const [submittedLineLocation, setSubmittedLineLocation] = useState<Pick<LocationSnapshot, 'latitude' | 'longitude'> | null>(null)
  const [selectedLine, setSelectedLine] = useState<TransitLine | null>(null)
  const [boardingStop, setBoardingStop] = useState<DirectedStop | null>(null)
  const [alightingStop, setAlightingStop] = useState<DestinationStop | null>(null)
  const [boardingStopQuery, setBoardingStopQuery] = useState('')
  const [alightingStopQuery, setAlightingStopQuery] = useState('')
  const [journeyId, setJourneyId] = useState<string | null>(null)
  const [journeyState, setJourneyState] = useState<JourneyUiState>('idle')
  const [journeyError, setJourneyError] = useState<string | null>(null)
  const [healthCheckSlow, setHealthCheckSlow] = useState(false)
  const [mobilePage, setMobilePage] = useState(0)
  const lastUploadedLocation = useRef<string | null>(null)
  const mobileSwipeStart = useRef<{ x: number; y: number } | null>(null)
  const locationRequestInFlight = useRef(false)
  const journeyStartInFlight = useRef(false)
  const cancellingJourneyIds = useRef(new Set<string>())
  const locationSource = location?.source

  const healthQuery = useQuery({ queryKey: ['system', 'health'], queryFn: ({ signal }) => fetchSystemHealth(signal), refetchInterval: 30_000, retry: 1 })
  const lineQuery = useQuery({
    queryKey: ['transit-lines', provider, submittedQuery, submittedLineLocation?.latitude, submittedLineLocation?.longitude],
    queryFn: ({ signal }) => searchTransitLines(provider, submittedQuery, submittedLineLocation, signal),
    enabled: submittedQuery.length > 0 && (provider !== 'NATIONAL_PRECISION_BUS' || submittedLineLocation !== null),
    retry: provider === 'NATIONAL_PRECISION_BUS' ? false : 1,
  })
  const stopQuery = useQuery({ queryKey: ['directed-stops', selectedLine?.id], queryFn: ({ signal }) => fetchDirectedStops(selectedLine!.id, signal), enabled: selectedLine !== null })
  const destinationQuery = useQuery({ queryKey: ['destination-stops', selectedLine?.id, boardingStop?.stopId], queryFn: ({ signal }) => fetchDestinationStops(selectedLine!.id, boardingStop!.directionId, boardingStop!.stopId, signal), enabled: selectedLine !== null && boardingStop !== null })
  const placeQueryResult = useQuery({ queryKey: ['place-search', submittedPlaceQuery], queryFn: ({ signal }) => searchPlaces(submittedPlaceQuery, signal), enabled: submittedPlaceQuery.length > 0, staleTime: Infinity, retry: 1 })
  const decisionQuery = useQuery({
    queryKey: ['boarding-decision', journeyId],
    queryFn: async ({ signal }) => {
      if (location) {
        const lastUploadedAt = lastUploadedLocation.current
        const shouldUpload = location.source === 'search'
          ? manualLocationRefreshDue(lastUploadedAt)
          : lastUploadedAt !== location.observedAt
        if (shouldUpload) {
          const locationToUpload = location.source === 'search'
            ? { ...location, observedAt: new Date().toISOString() }
            : location
          await addJourneyLocation(journeyId!, locationToUpload, signal)
          lastUploadedLocation.current = locationToUpload.observedAt
        }
      }
      return fetchBoardingDecision(journeyId!, signal)
    },
    enabled: journeyId !== null,
    refetchInterval: 60_000,
  })

  useEffect(() => {
    if (!journeyId || locationSource !== 'browser' || !navigator.geolocation) return
    const watchId = navigator.geolocation.watchPosition(
      (position) => { setLocation(toLocationSnapshot(position)); setLocationStatus('ready'); setLocationError(null) },
      (error) => setLocationError(positionErrorMessage(error)),
      { enableHighAccuracy: true, maximumAge: 10_000, timeout: 20_000 },
    )
    return () => navigator.geolocation.clearWatch(watchId)
  }, [journeyId, locationSource])

  useEffect(() => {
    if (!healthQuery.isPending) return
    const timer = window.setTimeout(() => setHealthCheckSlow(true), 2_000)
    return () => window.clearTimeout(timer)
  }, [healthQuery.isPending])

  const providerOption = providerOptions.find((option) => option.value === provider)!
  const healthStatus = healthQuery.isPending ? 'checking' : healthQuery.isError ? 'offline' : 'live'
  const stopsWithCoordinates = (stopQuery.data ?? []).filter((stop) => stop.latitude !== null && stop.longitude !== null)
  const hasStopCoordinates = stopsWithCoordinates.length > 0
  const uniqueBoardingStops = Array.from(
    new Map((stopQuery.data ?? []).map((stop) => [stop.stopId, stop])).values(),
  )
  const sortedBoardingStops = uniqueBoardingStops
    .filter((stop) => includesStopQuery(stop, boardingStopQuery))
    .sort((left, right) => location && hasStopCoordinates
      ? distanceMeters(location, left) - distanceMeters(location, right)
      : left.stopSequence - right.stopSequence)
  const filteredAlightingStops = destinationQuery.data?.filter((stop) => includesStopQuery(stop, alightingStopQuery)) ?? []
  const nearestStop = location && stopsWithCoordinates.length
    ? stopsWithCoordinates.reduce((nearest, stop) => distanceMeters(location, stop) < distanceMeters(location, nearest) ? stop : nearest)
    : null
  const boardingStopOrderCopy = !selectedLine
    ? '먼저 노선을 선택하세요.'
    : location && hasStopCoordinates
      ? `${selectedLine.publicName} · 현재 위치에서 가까운 순이에요.`
      : stopQuery.isSuccess && !hasStopCoordinates
        ? `${selectedLine.publicName} · 역 위치 정보가 없어 운행 순서로 표시해요.`
        : `${selectedLine.publicName} · 정류장 정보를 확인하고 있어요.`
  const currentStep = !location ? 1 : !selectedLine ? 2 : !boardingStop ? 3 : 4
  const selectedBoardingAccess = boardingAccess(location, boardingStop)
  const mobilePageLabels = ['안내', '위치', '노선', '구간', '결과']
  const maxMobilePage = journeyState !== 'idle' || journeyId || decisionQuery.data
    ? 4
    : selectedLine ? 3 : location ? 2 : 1

  function goToMobilePage(page: number, allowResult = false) {
    const nextPage = Math.max(0, Math.min(allowResult ? 4 : maxMobilePage, page))
    setMobilePage(nextPage)
  }

  function handleMobileSwipeStart(event: ReactPointerEvent<HTMLDivElement>) {
    if (event.pointerType === 'mouse') return
    mobileSwipeStart.current = { x: event.clientX, y: event.clientY }
  }

  function handleMobileSwipeEnd(event: ReactPointerEvent<HTMLDivElement>) {
    const start = mobileSwipeStart.current
    mobileSwipeStart.current = null
    if (!start || event.pointerType === 'mouse') return
    const horizontalDistance = event.clientX - start.x
    const verticalDistance = event.clientY - start.y
    if (Math.abs(horizontalDistance) < 55 || Math.abs(horizontalDistance) <= Math.abs(verticalDistance) * 1.2) return
    goToMobilePage(mobilePage + (horizontalDistance < 0 ? 1 : -1))
  }

  function endActiveJourney() {
    const activeJourneyId = journeyId
    setJourneyId(null)
    setJourneyState('idle')
    setJourneyError(null)
    lastUploadedLocation.current = null
    if (activeJourneyId && !cancellingJourneyIds.current.has(activeJourneyId)) {
      cancellingJourneyIds.current.add(activeJourneyId)
      void cancelJourney(activeJourneyId).finally(() => cancellingJourneyIds.current.delete(activeJourneyId))
    }
  }

  async function requestLocation() {
    if (locationRequestInFlight.current) return
    if (!navigator.geolocation) {
      setLocationStatus('error')
      setLocationError('이 브라우저는 위치 확인을 지원하지 않아요. 주소나 장소명으로 찾아주세요.')
      setPlaceSearchOpen(true)
      return
    }
    locationRequestInFlight.current = true
    setLocationStatus('loading')
    setLocationError(null)
    try {
      const position = await getBrowserPosition()
      setLocation(toLocationSnapshot(position))
      setLocationLabel(null)
      setLocationStatus('ready')
    } catch (error) {
      setLocationStatus('error')
      setLocationError(positionErrorMessage(error as GeolocationPositionError))
      setPlaceSearchOpen(true)
    } finally {
      locationRequestInFlight.current = false
    }
  }

  function submitPlaceSearch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const nextQuery = placeQuery.trim()
    if (!nextQuery) {
      setLocationError('주소나 장소명을 입력해 주세요.')
      return
    }
    setLocationError(null)
    if (nextQuery === submittedPlaceQuery) {
      void placeQueryResult.refetch()
      return
    }
    setSubmittedPlaceQuery(nextQuery)
  }

  function selectPlace(result: PlaceSearchResult) {
    endActiveJourney()
    // A searched place is a user-selected calculation origin, not a GPS measurement.
    setLocation({ latitude: result.latitude, longitude: result.longitude, accuracyM: 0, speedMps: null, observedAt: new Date().toISOString(), source: 'search' })
    setLocationLabel(shortPlaceName(result.displayName))
    setLocationStatus('ready')
    setLocationError(null)
    setPlaceSearchOpen(false)
  }

  function changeProvider(nextProvider: TransitProvider) {
    endActiveJourney()
    setProvider(nextProvider)
    setQueryInput('')
    setSubmittedQuery('')
    setSubmittedLineLocation(null)
    setSelectedLine(null)
    setBoardingStop(null)
    setAlightingStop(null)
    setBoardingStopQuery('')
    setAlightingStopQuery('')
  }

  function submitSearch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const nextQuery = queryInput.trim()
    if (!nextQuery || (provider === 'NATIONAL_PRECISION_BUS' && !location)) return
    endActiveJourney()
    setSubmittedQuery(nextQuery)
    setSubmittedLineLocation(location ? { latitude: location.latitude, longitude: location.longitude } : null)
    setSelectedLine(null)
    setBoardingStop(null)
    setAlightingStop(null)
  }

  function selectLine(line: TransitLine) {
    endActiveJourney()
    setSelectedLine(line)
    setBoardingStop(null)
    setAlightingStop(null)
    setBoardingStopQuery('')
    setAlightingStopQuery('')
  }

  function selectBoardingStop(stop: DirectedStop) {
    endActiveJourney()
    setBoardingStop(stop)
    setAlightingStop(null)
    setAlightingStopQuery('')
  }

  async function startJourney() {
    if (journeyStartInFlight.current || !location || !selectedLine || !boardingStop) return
    journeyStartInFlight.current = true
    endActiveJourney()
    setJourneyState('starting')
    setJourneyError(null)
    goToMobilePage(4, true)
    let createdJourneyId: string | null = null
    try {
      const journey = await createJourney({
        anonymousKey: getAnonymousKey(),
        lineId: selectedLine.id,
        directionId: alightingStop?.directionId ?? boardingStop.directionId,
        boardingStopId: boardingStop.stopId,
        alightingStopId: alightingStop?.stopId ?? null,
        targetProbability: null,
        desiredArrivalAt: null,
      })
      createdJourneyId = journey.journeyId
      await addJourneyLocation(journey.journeyId, location)
      lastUploadedLocation.current = location.observedAt
      setJourneyId(journey.journeyId)
      setJourneyState('tracking')
      if (!window.matchMedia('(max-width: 760px)').matches) {
        document.getElementById('decision-section')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
      }
    } catch (error) {
      if (createdJourneyId) {
        try {
          await cancelJourney(createdJourneyId)
        } catch {
          // The backend expires abandoned anonymous journeys automatically.
        }
      }
      setJourneyState('error')
      setJourneyError(journeyErrorMessage(error))
    } finally {
      journeyStartInFlight.current = false
    }
  }

  const locationTitle = location
    ? location.source === 'search' && locationLabel
      ? locationLabel
      : nearestStop ? `${nearestStop.stopName} 인근` : locationLabel ?? '현재 위치'
    : locationStatus === 'loading' ? '현재 위치를 찾고 있어요' : '출발 위치를 알려주세요'
  const locationDetail = location
    ? location.source === 'search'
      ? '검색한 장소를 출발점으로 사용해요.'
      : nearestStop
        ? `${nearestStop.stopName}까지 ${formatDistance(distanceMeters(location, nearestStop))} · 정확도 약 ${Math.round(location.accuracyM)}m`
        : `위치 정확도 약 ${Math.round(location.accuracyM)}m`
    : '현재 위치를 쓰거나 장소를 직접 검색할 수 있어요.'

  return (
    <div className="app-shell">
      <a className="skip-link" href="#journey-content">여정 설정으로 건너뛰기</a>
      <header className="app-bar">
        <a className="brand" href="/" aria-label="첫차 홈">첫차</a>
        <span className="connection" data-status={healthStatus} role="status"><i aria-hidden="true" />{healthStatus === 'live' ? '실시간 연결' : healthStatus === 'checking' ? healthCheckSlow ? '서버 준비 중' : '연결 확인 중' : '연결 끊김'}</span>
      </header>

      <main id="journey-content" className="journey-layout">
        <nav className="mobile-page-indicator" aria-label={`현재 단계: ${mobilePageLabels[mobilePage]}`}>
          <ol>{mobilePageLabels.map((label, index) => <li key={label} data-state={index < mobilePage ? 'complete' : index === mobilePage ? 'current' : 'pending'} aria-current={index === mobilePage ? 'step' : undefined}><span>{index + 1}</span><b>{label}</b></li>)}</ol>
        </nav>

        <div className="mobile-pager" onPointerDown={handleMobileSwipeStart} onPointerUp={handleMobileSwipeEnd} onPointerCancel={() => { mobileSwipeStart.current = null }}>
        <div className="mobile-page mobile-intro-page" data-mobile-page="0" data-active={mobilePage === 0} aria-label="서비스 안내">
        <section className="hero" aria-labelledby="page-title">
          <span className="hero-label">실시간 탑승 판단</span>
          <h1 id="page-title">지금 나가면<br />탈 수 있을까요?</h1>
          <p>내 위치와 차량을 함께 계산해서, 어떤 차를 타려면 얼마나 빠르게 움직여야 하는지 알려드려요.</p>
        </section>

        <aside className="usage-guide" aria-labelledby="usage-guide-title">
          <div className="usage-guide-intro">
            <span>이렇게 사용해요</span>
            <h2 id="usage-guide-title">궁금한 승차 구간을<br />직접 선택하세요</h2>
            <p>전체 경로를 찾는 대신, 지금 탈 노선이나 다음 환승 구간의 탑승 가능성을 계산해요.</p>
          </div>
          <ol>
            <li>
              <b>01</b>
              <div><strong>처음 탈 때</strong><p>현재 위치와 승차 정류장을 선택해 접근 중인 차량을 비교하세요.</p></div>
            </li>
            <li>
              <b>02</b>
              <div><strong>환승을 앞두고 있을 때</strong><p>예정된 환승 지점을 출발 위치로 설정하고, 다음에 탈 노선과 승차 구간을 선택하세요.</p></div>
            </li>
          </ol>
        </aside>

        <MobilePageActions nextLabel="시작하기" onNext={() => goToMobilePage(1)} />
        </div>

        <nav className="step-rail" aria-label="탑승 판단 단계">
          <div className="step-track" aria-hidden="true"><span style={{ width: `${(currentStep - 1) / 3 * 100}%` }} /></div>
          <ol>{steps.map((step, index) => {
            const number = index + 1
            const state = number < currentStep ? 'complete' : number === currentStep ? 'current' : 'pending'
            return <li key={step} data-state={state} aria-current={state === 'current' ? 'step' : undefined}><b>{number < currentStep ? '✓' : number}</b><span>{step}</span></li>
          })}</ol>
        </nav>

        <div className="mobile-page" data-mobile-page="1" data-active={mobilePage === 1} aria-label="출발 위치 선택">
        <section className="flow-card location-card" aria-labelledby="location-heading">
          <header className="section-header"><span>1</span><div><h2 id="location-heading">어디서 출발하나요?</h2><p>선택한 위치는 탑승 가능성 계산에만 사용해요.</p></div></header>
          <div className="location-summary" data-ready={Boolean(location)}><div className="location-pin" aria-hidden="true"><i /></div><div><strong>{locationTitle}</strong><p>{locationDetail}</p></div></div>
          <div className="button-row">
            <Button type="button" display="block" size="large" loading={locationStatus === 'loading'} onClick={requestLocation}>{location ? '현재 위치로 다시 설정' : '내 위치 사용하기'}</Button>
            <Button type="button" display="block" size="large" variant="weak" aria-expanded={placeSearchOpen} aria-controls="place-search" onClick={() => setPlaceSearchOpen((open) => !open)}>장소 검색</Button>
          </div>
          {locationError ? <p className="inline-error" role="alert">{locationError}</p> : null}
          {placeSearchOpen ? <div id="place-search" className="reveal-panel">
            <form className="search-form" onSubmit={submitPlaceSearch} role="search">
              <TextField variant="box" label="주소 또는 장소명" labelOption="sustain" id="place-query" name="placeQuery" type="search" value={placeQuery} onChange={(event) => setPlaceQuery(event.target.value)} placeholder="예: 용산역, 서울시청" autoComplete="street-address" />
              <Button type="submit" display="full" size="large" loading={placeQueryResult.isFetching}>위치 찾기</Button>
            </form>
            {placeQueryResult.isError ? <QueryState message="장소를 찾지 못했어요. 잠시 후 다시 검색해 주세요." /> : null}
            {placeQueryResult.isSuccess && placeQueryResult.data.length === 0 ? <QueryState message="일치하는 장소가 없어요. 지역명을 함께 입력해 보세요." /> : null}
            {placeQueryResult.data?.length ? <ul className="selection-list" aria-label="검색된 장소">{placeQueryResult.data.map((result) => <li key={result.id}><button type="button" onClick={() => selectPlace(result)}><span className="list-copy"><strong>{shortPlaceName(result.displayName)}</strong><small>{result.displayName}</small></span><span className="chevron" aria-hidden="true">›</span></button></li>)}</ul> : null}
            <p className="attribution">장소 검색 © <a href="https://www.openstreetmap.org/copyright" target="_blank" rel="noreferrer">OpenStreetMap 기여자</a></p>
            <p className="privacy-note">검색어는 장소 확인을 위해 OpenStreetMap Nominatim으로 전송돼요. 상세한 개인 주소 입력은 피해주세요.</p>
          </div> : null}
        </section>

        <MobilePageActions previousLabel="안내" nextLabel="노선 선택" nextDisabled={!location} onPrevious={() => goToMobilePage(0)} onNext={() => goToMobilePage(2)} />
        </div>

        <div className="mobile-page" data-mobile-page="2" data-active={mobilePage === 2} aria-label="노선 선택">
        <section className="flow-card line-card" aria-labelledby="line-heading">
          <header className="section-header"><span>2</span><div><h2 id="line-heading">어떤 노선을 타나요?</h2><p>버스 번호나 지하철 호선으로 찾아보세요.</p></div></header>
          <fieldset className="provider-tabs"><legend>교통수단 선택</legend>{providerOptions.map((option) => <label key={option.value} data-selected={provider === option.value}><input type="radio" name="transitProvider" value={option.value} checked={provider === option.value} onChange={() => changeProvider(option.value)} /><span>{option.label}</span></label>)}</fieldset>
          <form className="search-form compact" onSubmit={submitSearch} role="search">
            <TextField variant="box" label={providerOption.searchLabel} labelOption="sustain" id="line-query" name="lineQuery" value={queryInput} onChange={(event) => setQueryInput(event.target.value)} placeholder={providerOption.placeholder} autoComplete="off" />
            <Button type="submit" display="full" size="large" loading={lineQuery.isFetching} disabled={provider === 'NATIONAL_PRECISION_BUS' && !location}>노선 찾기</Button>
          </form>
          {provider === 'SEOUL_SUBWAY' ? <p className="provider-note">김포골드라인은 공개된 실시간 데이터 API가 없어 실시간 조회를 지원하지 않아요.</p> : null}
          {provider === 'NATIONAL_PRECISION_BUS' && !location ? <QueryState message="전국 버스는 출발 위치 주변 지역의 노선을 검색해요. 먼저 위치를 선택해 주세요." /> : null}
          {lineQuery.isPending && submittedQuery ? <QueryState message="노선을 찾고 있어요." /> : null}
          {lineQuery.isError ? <QueryState message={lineErrorMessage(provider, lineQuery.error)} action="다시 시도" onAction={() => lineQuery.refetch()} /> : null}
          {lineQuery.data?.length === 0 ? <QueryState message="일치하는 노선이 없어요. 다른 번호로 검색해 보세요." /> : null}
          {lineQuery.data?.length ? <ul className="selection-list line-list" aria-label="검색된 노선">{lineQuery.data.map((line) => <li key={line.id}><button type="button" data-selected={selectedLine?.id === line.id} onClick={() => selectLine(line)}><span className="route-symbol">{line.publicName.slice(0, 3)}</span><span className="list-copy"><strong>{line.publicName}</strong><small>{line.operatorName ?? line.routeType ?? '운영 정보 없음'}</small></span><span className="chevron" aria-hidden="true">›</span></button></li>)}</ul> : null}
        </section>

        <MobilePageActions previousLabel="출발 위치" nextLabel="구간 선택" nextDisabled={!selectedLine} onPrevious={() => goToMobilePage(1)} onNext={() => goToMobilePage(3)} />
        </div>

        <div className="mobile-page mobile-stop-page" data-mobile-page="3" data-active={mobilePage === 3} aria-label="승차 및 하차 구간 선택">
        <section className="flow-card stop-card" aria-labelledby="boarding-heading" data-locked={!selectedLine}>
          <header className="section-header"><span>3</span><div><h2 id="boarding-heading">어디서 타나요?</h2><p>{boardingStopOrderCopy}</p></div></header>
          {selectedLine && stopQuery.isPending ? <QueryState message="정류장을 불러오고 있어요." /> : null}
          {stopQuery.isError ? <QueryState message="정류장을 불러오지 못했어요." action="다시 시도" onAction={() => stopQuery.refetch()} /> : null}
          {stopQuery.data?.length ? <TextField variant="box" label="승차 정류장 검색" labelOption="sustain" id="boarding-stop-query" name="boardingStopQuery" type="search" value={boardingStopQuery} onChange={(event) => setBoardingStopQuery(event.target.value)} placeholder="정류장 이름 또는 순번" autoComplete="off" /> : null}
          {stopQuery.data?.length && sortedBoardingStops.length === 0 ? <QueryState message={`“${boardingStopQuery.trim()}”과 일치하는 정류장이 없어요.`} /> : null}
          {sortedBoardingStops.length ? <ul className="selection-list stop-list" aria-label="승차 정류장">{sortedBoardingStops.map((stop, index) => {
            const distance = location ? distanceMeters(location, stop) : Number.POSITIVE_INFINITY
            const selected = boardingStop?.stopId === stop.stopId
            return <li key={stop.stopId}><button type="button" data-selected={selected} onClick={() => selectBoardingStop(stop)}><span className="stop-sequence">{String(stop.stopSequence).padStart(2, '0')}</span><span className="list-copy"><strong>{stop.stopName}</strong><small>목적지를 선택하면 방향을 자동으로 찾아요{Number.isFinite(distance) ? ` · ${formatDistance(distance)}` : ''}{index === 0 && location && Number.isFinite(distance) ? ' · 가장 가까움' : ''}</small></span><span className="select-mark" aria-hidden="true">{selected ? '✓' : '›'}</span></button></li>
          })}</ul> : null}
        </section>

        <section className="flow-card stop-card" aria-labelledby="alighting-heading" data-locked={!boardingStop}>
          <header className="section-header optional"><span>선택</span><div><h2 id="alighting-heading">어디서 내리나요?</h2><p>{boardingStop ? '내릴 곳을 고르면 정차 여부까지 확인해요.' : '승차 정류장을 먼저 선택해 주세요.'}</p></div></header>
          {boardingStop && destinationQuery.isPending ? <QueryState message="하차 가능한 정류장을 찾고 있어요." /> : null}
          {destinationQuery.isError ? <QueryState message="하차 정류장을 불러오지 못했어요." action="다시 시도" onAction={() => destinationQuery.refetch()} /> : null}
          {destinationQuery.data?.length ? <TextField variant="box" label="하차 정류장 검색" labelOption="sustain" id="alighting-stop-query" name="alightingStopQuery" type="search" value={alightingStopQuery} onChange={(event) => setAlightingStopQuery(event.target.value)} placeholder="정류장 이름 또는 순번" autoComplete="off" /> : null}
          {filteredAlightingStops.length ? <ul className="selection-list stop-list" aria-label="하차 정류장">{filteredAlightingStops.map((stop) => {
            const selected = alightingStop?.stopId === stop.stopId
            return <li key={stop.stopId}><button type="button" data-selected={selected} onClick={() => { endActiveJourney(); setAlightingStop(stop) }}><span className="stop-sequence">{String(stop.stopSequence).padStart(2, '0')}</span><span className="list-copy"><strong>{stop.stopName}</strong><small>이곳에 정차하는 차량만 비교해요</small></span><span className="select-mark" aria-hidden="true">{selected ? '✓' : '›'}</span></button></li>
          })}</ul> : null}
        </section>

        <section className="mobile-calculate-panel" aria-label="탑승 가능성 계산">
          <div><strong>{boardingStop ? `${boardingStop.stopName}${alightingStop ? ` → ${alightingStop.stopName}` : ''}` : '승차 구간을 선택해 주세요'}</strong><p>선택한 위치와 접근 차량을 비교해요.</p></div>
          <Button type="button" display="full" size="xlarge" onClick={startJourney} disabled={!location || !boardingStop} loading={journeyState === 'starting'}>{journeyId ? '다시 계산하기' : '탑승 가능성 계산'}</Button>
        </section>

        <MobilePageActions previousLabel="노선" onPrevious={() => goToMobilePage(2)} />
        </div>

        <div className="mobile-page mobile-result-page" data-mobile-page="4" data-active={mobilePage === 4} aria-label="탑승 가능성 결과">
        <section id="decision-section" className="decision-section" aria-labelledby="decision-heading" data-active={Boolean(boardingStop && location)}>
          <div className="decision-setup"><div><span>4 · 실시간 판단</span><h2 id="decision-heading">이제 탈 수 있는지<br />계산해 볼게요</h2><p>{boardingStop ? `${boardingStop.stopName}${alightingStop ? ` → ${alightingStop.stopName}` : ''}` : '위치와 승차 정류장을 선택해 주세요.'}</p></div><Button type="button" display="full" size="xlarge" color="light" onClick={startJourney} disabled={!location || !boardingStop} loading={journeyState === 'starting'}>{journeyId ? '다시 계산하기' : '탑승 가능성 계산'}</Button></div>
          {selectedBoardingAccess?.isRemote && boardingStop ? <div className="distance-notice" role="status"><strong>{boardingStop.stopName}까지 {formatDistance(selectedBoardingAccess.distanceM)}</strong><p>보통 걸음으로 {formatDuration(selectedBoardingAccess.walkMinutes)}이 예상돼요. 첫 차량은 놓칠 가능성이 높아요.</p></div> : null}
          {!location || !boardingStop ? <QueryState message={!location ? '먼저 출발 위치를 확인해 주세요.' : '승차 정류장을 선택하면 계산할 수 있어요.'} /> : null}
          {journeyState === 'starting' ? <QueryState message={healthQuery.isPending ? '서버를 준비하고 있어요. 무료 서버가 깨어나는 데 최대 2분 정도 걸릴 수 있어요.' : '여정과 현재 위치를 저장하고 있어요.'} /> : null}
          {journeyError ? <QueryState message={journeyError} action={journeyId ? '다시 계산' : '다시 시작'} onAction={() => journeyId ? decisionQuery.refetch() : startJourney()} /> : null}
          {journeyId && decisionQuery.isPending ? <QueryState message="내 도착 시간과 접근 차량을 비교하고 있어요." /> : null}
          {decisionQuery.isError ? <QueryState message="최신 탑승 판단을 불러오지 못했어요." action="다시 확인" onAction={() => decisionQuery.refetch()} /> : null}
          {decisionQuery.data ? <DecisionPanel decision={decisionQuery.data} routeStops={stopQuery.data ?? []} access={selectedBoardingAccess} boardingStopName={boardingStop?.stopName ?? null} locationAccuracyM={location?.accuracyM} onStop={endActiveJourney} /> : null}
        </section>
        <MobilePageActions previousLabel="구간 수정" onPrevious={() => goToMobilePage(3)} />
        </div>
        </div>
      </main>
      <footer className="site-footer"><strong>첫차</strong><span>실시간 데이터에 따라 탑승 가능성은 달라질 수 있어요.</span></footer>
    </div>
  )
}
