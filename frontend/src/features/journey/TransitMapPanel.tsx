import { useEffect, useMemo, useRef, useState } from 'react'
import type { VehicleBoardingPrediction } from '../../api/journey'
import type { DirectedStop } from '../../api/transit'

type Coordinate = {
  latitude: number
  longitude: number
}

type TransitMapPanelProps = {
  location: Coordinate
  boardingStop: DirectedStop
  routeStops: DirectedStop[]
  vehicles: VehicleBoardingPrediction[]
  recommendedVehicleId: string | null
  ncpKeyId?: string
}

type NaverMapInstance = {
  fitBounds: (bounds: unknown) => void
}

type NaverOverlay = {
  setMap: (map: NaverMapInstance | null) => void
}

type NaverMarker = NaverOverlay & {
  setPosition: (position: unknown) => void
}

type NaverPolyline = NaverOverlay & {
  setPath: (path: unknown[]) => void
}

type NaverMaps = {
  Map: new (element: HTMLElement, options: Record<string, unknown>) => NaverMapInstance
  LatLng: new (latitude: number, longitude: number) => unknown
  LatLngBounds: new () => { extend: (coordinate: unknown) => void }
  Marker: new (options: Record<string, unknown>) => NaverMarker
  Polyline: new (options: Record<string, unknown>) => NaverPolyline
  Event: { trigger: (target: NaverMapInstance, eventName: string) => void }
}

type NaverWindow = Window & {
  naver?: { maps: NaverMaps }
  navermap_authFailure?: () => void
}

const NAVER_MAP_SCRIPT_ID = 'naver-map-sdk'
let naverMapPromise: Promise<NaverMaps> | null = null

function loadNaverMap(ncpKeyId: string) {
  const browserWindow = window as NaverWindow
  if (browserWindow.naver?.maps) return Promise.resolve(browserWindow.naver.maps)
  if (naverMapPromise) return naverMapPromise

  naverMapPromise = new Promise<NaverMaps>((resolve, reject) => {
    const rejectLoad = () => {
      document.getElementById(NAVER_MAP_SCRIPT_ID)?.remove()
      naverMapPromise = null
      reject(new Error('NAVER_MAP_LOAD_FAILED'))
    }
    browserWindow.navermap_authFailure = rejectLoad

    const existingScript = document.getElementById(NAVER_MAP_SCRIPT_ID) as HTMLScriptElement | null
    const script = existingScript ?? document.createElement('script')
    script.addEventListener('load', () => browserWindow.naver?.maps ? resolve(browserWindow.naver.maps) : rejectLoad(), { once: true })
    script.addEventListener('error', rejectLoad, { once: true })

    if (!existingScript) {
      script.id = NAVER_MAP_SCRIPT_ID
      script.async = true
      script.src = `https://oapi.map.naver.com/openapi/v3/maps.js?ncpKeyId=${encodeURIComponent(ncpKeyId)}`
      document.head.appendChild(script)
    }
  })

  return naverMapPromise
}

function normalizeStopName(value: string) {
  return value.replace(/\s+/g, '').replace(/역$/u, '')
}

function coordinateForVehicle(vehicle: VehicleBoardingPrediction, routeStops: DirectedStop[]): Coordinate | null {
  if (typeof vehicle.latitude === 'number' && typeof vehicle.longitude === 'number') {
    return { latitude: vehicle.latitude, longitude: vehicle.longitude }
  }
  if (!vehicle.currentStopName) return null

  const currentStopName = normalizeStopName(vehicle.currentStopName)
  const matches = routeStops.filter((stop) => stop.latitude !== null
    && stop.longitude !== null
    && normalizeStopName(stop.stopName) === currentStopName)
  if (!matches.length) return null

  const stop = typeof vehicle.currentSequence === 'number'
    ? matches.reduce((nearest, candidate) => Math.abs(candidate.stopSequence - vehicle.currentSequence!) < Math.abs(nearest.stopSequence - vehicle.currentSequence!) ? candidate : nearest)
    : matches[0]
  return { latitude: stop.latitude!, longitude: stop.longitude! }
}

function markerContent(kind: 'location' | 'boarding' | 'vehicle', title: string, detail?: string, selected = false) {
  const marker = document.createElement('div')
  marker.className = `transit-map-marker transit-map-marker-${kind}${selected ? ' is-selected' : ''}`
  const dot = document.createElement('i')
  dot.setAttribute('aria-hidden', 'true')
  const copy = document.createElement('span')
  const strong = document.createElement('strong')
  strong.textContent = title
  copy.appendChild(strong)
  if (detail) {
    const small = document.createElement('small')
    small.textContent = detail
    copy.appendChild(small)
  }
  marker.append(dot, copy)
  return marker
}

export function TransitMapPanel({
  location,
  boardingStop,
  routeStops,
  vehicles,
  recommendedVehicleId,
  ncpKeyId = import.meta.env.VITE_NAVER_MAP_NCP_KEY_ID,
}: TransitMapPanelProps) {
  const [open, setOpen] = useState(false)
  const [status, setStatus] = useState<'idle' | 'loading' | 'ready' | 'error' | 'missing-key' | 'missing-coordinate'>('idle')
  const mapRoot = useRef<HTMLDivElement>(null)
  const mapInstance = useRef<NaverMapInstance | null>(null)
  const mapApi = useRef<NaverMaps | null>(null)
  const locationMarker = useRef<NaverMarker | null>(null)
  const walkingLine = useRef<NaverPolyline | null>(null)
  const latestLocation = useRef(location)
  const vehiclePoints = useMemo(() => vehicles
    .map((vehicle) => ({ vehicle, coordinate: coordinateForVehicle(vehicle, routeStops) }))
    .filter((item): item is { vehicle: VehicleBoardingPrediction; coordinate: Coordinate } => item.coordinate !== null), [routeStops, vehicles])

  useEffect(() => {
    latestLocation.current = location
  }, [location])

  useEffect(() => {
    if (!open) return
    const boardingLatitude = boardingStop.latitude
    const boardingLongitude = boardingStop.longitude
    if (!ncpKeyId || boardingLatitude === null || boardingLongitude === null) return

    let disposed = false
    const overlays: NaverOverlay[] = []
    void loadNaverMap(ncpKeyId).then((maps) => {
      if (disposed || !mapRoot.current) return
      const map = new maps.Map(mapRoot.current, {
        center: new maps.LatLng(boardingLatitude, boardingLongitude),
        zoom: 14,
        zoomControl: true,
      })
      mapInstance.current = map
      mapApi.current = maps
      const shownLocation = latestLocation.current
      const bounds = new maps.LatLngBounds()
      const addMarker = (coordinate: Coordinate, content: HTMLElement, zIndex: number) => {
        const position = new maps.LatLng(coordinate.latitude, coordinate.longitude)
        bounds.extend(position)
        const marker = new maps.Marker({ map, position, icon: { content }, zIndex })
        overlays.push(marker)
        return marker
      }

      locationMarker.current = addMarker(shownLocation, markerContent('location', '내 위치'), 30)
      addMarker(
        { latitude: boardingLatitude, longitude: boardingLongitude },
        markerContent('boarding', boardingStop.stopName, '승차 지점'),
        40,
      )
      walkingLine.current = new maps.Polyline({
        map,
        path: [
          new maps.LatLng(shownLocation.latitude, shownLocation.longitude),
          new maps.LatLng(boardingStop.latitude!, boardingStop.longitude!),
        ],
        strokeColor: '#3182f6',
        strokeOpacity: 0.55,
        strokeStyle: 'shortdash',
        strokeWeight: 3,
      })
      overlays.push(walkingLine.current)
      vehiclePoints.forEach(({ vehicle, coordinate }, index) => {
        const selected = vehicle.providerVehicleId === recommendedVehicleId
        addMarker(
          coordinate,
          markerContent('vehicle', selected ? '추천 차량' : `접근 차량 ${index + 1}`, vehicle.currentStopName ?? vehicle.providerVehicleId, selected),
          selected ? 50 : 20,
        )
      })
      map.fitBounds(bounds)
      maps.Event.trigger(map, 'resize')
      setStatus('ready')
    }).catch(() => {
      if (!disposed) setStatus('error')
    })

    return () => {
      disposed = true
      overlays.forEach((overlay) => overlay.setMap(null))
      mapInstance.current = null
      mapApi.current = null
      locationMarker.current = null
      walkingLine.current = null
    }
  }, [boardingStop, ncpKeyId, open, recommendedVehicleId, vehiclePoints])

  useEffect(() => {
    const maps = mapApi.current
    if (!maps || !locationMarker.current) return
    locationMarker.current.setPosition(new maps.LatLng(location.latitude, location.longitude))
    walkingLine.current?.setPath([
      new maps.LatLng(location.latitude, location.longitude),
      new maps.LatLng(boardingStop.latitude!, boardingStop.longitude!),
    ])
  }, [boardingStop.latitude, boardingStop.longitude, location.latitude, location.longitude])

  return (
    <section className="transit-map-panel" aria-labelledby="transit-map-title">
      <div className="transit-map-header">
        <div>
          <h3 id="transit-map-title">차량 위치 한눈에 보기</h3>
          <p>내 위치와 {boardingStop.stopName}, 확인된 차량 {vehiclePoints.length}대를 함께 표시해요.</p>
        </div>
        <button type="button" className="transit-map-toggle" aria-expanded={open} aria-controls="transit-map-content" onClick={() => {
          if (!open) {
            setStatus(!ncpKeyId ? 'missing-key' : boardingStop.latitude === null || boardingStop.longitude === null ? 'missing-coordinate' : 'loading')
          }
          setOpen((current) => !current)
        }}>
          {open ? '지도 닫기' : '지도에서 보기'}
        </button>
      </div>
      {open ? (
        <div id="transit-map-content" className="transit-map-content">
          {status === 'missing-key' ? <p className="transit-map-message" role="status">네이버 지도 연결 키가 아직 설정되지 않았어요.</p> : null}
          {status === 'missing-coordinate' ? <p className="transit-map-message" role="status">승차 지점의 좌표가 없어 지도에 표시할 수 없어요.</p> : null}
          {status === 'error' ? <p className="transit-map-message" role="status">지도를 불러오지 못했어요. 잠시 후 다시 열어 주세요.</p> : null}
          {status === 'loading' ? <p className="transit-map-message" data-overlay="true" role="status">지도를 불러오고 있어요.</p> : null}
          {status === 'loading' || status === 'ready' ? <div ref={mapRoot} className="transit-map" data-visible={status === 'ready'} aria-label="내 위치와 승차 지점 및 접근 차량 지도" /> : null}
          {status === 'ready' ? (
            <ul className="transit-map-legend" aria-label="지도 범례">
              <li data-kind="location"><i aria-hidden="true" />내 위치</li>
              <li data-kind="boarding"><i aria-hidden="true" />승차 지점</li>
              <li data-kind="vehicle"><i aria-hidden="true" />접근 차량</li>
            </ul>
          ) : null}
        </div>
      ) : null}
    </section>
  )
}
