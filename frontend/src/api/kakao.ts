import { fetchApi } from './client'

export type TransitType = 'SUBWAY' | 'BUS' | 'WALK'
export type TransitRouteLeg = { type: TransitType; sectionTimeMinutes: number; distanceMeters: number; stationCount: number; startName: string | null; startLatitude: number | null; startLongitude: number | null; endName: string | null; endLatitude: number | null; endLongitude: number | null; directionName: string | null; laneNames: string[] }
export type TransitRoute = { pathType: 'SUBWAY' | 'BUS' | 'BUS_SUBWAY' | 'UNKNOWN'; totalTimeMinutes: number; totalWalkMeters: number; fareWon: number; transferCount: number; legs: TransitRouteLeg[] }
export type TransitRouteSortMode = 'OPTIMAL' | 'FEWEST_TRANSFERS' | 'FASTEST'

const numberValue = (value: unknown) => Number.isFinite(Number(value)) ? Number(value) : 0
const textValue = (value: unknown) => typeof value === 'string' && value.trim() ? value.trim() : null
const coordinate = (value: unknown) => Number.isFinite(Number(value)) ? Number(value) : null
const KAKAO_ROUTE_TIMEOUT_MS = 25_000

function sectionType(section: any): TransitType {
  const route = section?.route ?? {}
  const shortName = textValue(route.route_short_name) ?? ''
  const fullName = textValue(route.route_full_name) ?? ''
  if (shortName.toLowerCase() === 'walk' || fullName.toLowerCase() === 'walk') return 'WALK'
  const vehicleType = textValue(route.vehicle_type_name) ?? ''
  const looksLikeSubway = /호선|철도|신분당|공항철도|경전철|선$/.test(`${shortName} ${fullName}`)
    || ['LIMITED_EXPRESS'].includes(vehicleType)
  return looksLikeSubway ? 'SUBWAY' : 'BUS'
}

function sectionStops(section: any) {
  const departure = section?.departure_stop ?? {}
  const arrival = section?.arrival_stop ?? {}
  const passing = Array.isArray(section?.passing_stops) ? section.passing_stops : []
  return { departure, arrival, stationCount: passing.length + (arrival.stop_name ? 1 : 0) }
}

function sectionPath(section: any) {
  const path = Array.isArray(section?.path) ? section.path : []
  const first = path.length >= 2 ? [coordinate(path[0]), coordinate(path[1])] : [null, null]
  const last = path.length >= 2 ? [coordinate(path.at(-2)), coordinate(path.at(-1))] : [null, null]
  return { first, last }
}

function routePathType(types: unknown): TransitRoute['pathType'] {
  const values = Array.isArray(types) ? types.map((value) => String(value).toLowerCase()) : []
  const hasSubway = values.includes('subway')
  const hasBus = values.includes('bus')
  if (hasSubway && hasBus) return 'BUS_SUBWAY'
  if (hasSubway) return 'SUBWAY'
  if (hasBus) return 'BUS'
  return 'UNKNOWN'
}

export function parseKakaoRoutes(response: any): TransitRoute[] {
  if (response?.status === 'OK') {
    return (Array.isArray(response.routes) ? response.routes : []).map((route: any): TransitRoute => {
      const properties = route.properties ?? {}
      const legs = (Array.isArray(route.steps) ? route.steps : []).map((step: any): TransitRouteLeg => {
        const info = step.properties ?? {}
        const stops = Array.isArray(info.stops) ? info.stops : []
        const points = Array.isArray(step.path?.points) ? step.path.points : []
        const first = Array.isArray(points[0]) ? points[0] : []
        const last = Array.isArray(points.at(-1)) ? points.at(-1) : []
        const vehicles = Array.isArray(info.vehicles) ? info.vehicles : []
        const type = info.type === 'BUS' ? 'BUS' : info.type === 'SUBWAY' ? 'SUBWAY' : 'WALK'
        return {
          type,
          sectionTimeMinutes: Math.ceil(numberValue(info.time) / 60),
          distanceMeters: numberValue(info.distance),
          stationCount: type === 'WALK' ? 0 : stops.length,
          startName: textValue(stops[0]?.name),
          startLatitude: coordinate(first[1]),
          startLongitude: coordinate(first[0]),
          endName: textValue(stops.at(-1)?.name),
          endLatitude: coordinate(last[1]),
          endLongitude: coordinate(last[0]),
          directionName: null,
          laneNames: vehicles.map((vehicle: any) => textValue(vehicle.name)).filter((name: string | null): name is string => name !== null),
        }
      })
      const walkMeters = legs.filter((leg: TransitRouteLeg) => leg.type === 'WALK').reduce((sum: number, leg: TransitRouteLeg) => sum + leg.distanceMeters, 0)
      const type = properties.type === 'BUS' ? 'BUS' : properties.type === 'SUBWAY' ? 'SUBWAY' : properties.type === 'BUS_AND_SUBWAY' ? 'BUS_SUBWAY' : 'UNKNOWN'
      return {
        pathType: type,
        totalTimeMinutes: Math.ceil(numberValue(properties.totalTime) / 60),
        totalWalkMeters: walkMeters,
        fareWon: numberValue(properties.fare?.value),
        transferCount: numberValue(properties.transfers),
        legs,
      }
    })
  }
  if (response?.result_code !== 0) return []
  return (Array.isArray(response.journeys) ? response.journeys : []).map((journey: any): TransitRoute => {
    const summary = journey.summary ?? {}
    const legs = (Array.isArray(journey.sections) ? journey.sections : []).map((section: any): TransitRouteLeg => {
      const type = sectionType(section)
      const { departure, arrival, stationCount } = sectionStops(section)
      const { first, last } = sectionPath(section)
      const route = section.route ?? {}
      const shortName = textValue(route.route_short_name)
      const fullName = textValue(route.route_full_name)
      const laneName = shortName && shortName.toLowerCase() !== 'walk' ? shortName : fullName && fullName.toLowerCase() !== 'walk' ? fullName : null
      const direction = textValue(route.last_stop_name_of_trip)
      return {
        type,
        sectionTimeMinutes: Math.ceil(numberValue(section.time) / 60),
        distanceMeters: numberValue(section.distance),
        stationCount: type === 'WALK' ? 0 : stationCount,
        startName: textValue(departure.stop_name),
        startLatitude: coordinate(departure.y) ?? first[1],
        startLongitude: coordinate(departure.x) ?? first[0],
        endName: textValue(arrival.stop_name),
        endLatitude: coordinate(arrival.y) ?? last[1],
        endLongitude: coordinate(arrival.x) ?? last[0],
        directionName: direction ? `${direction} 방면` : null,
        laneNames: laneName ? [laneName] : [],
      }
    })
    const walkMeters = legs.filter((leg: TransitRouteLeg) => leg.type === 'WALK').reduce((sum: number, leg: TransitRouteLeg) => sum + leg.distanceMeters, 0)
    const fare = summary.fare?.value ?? summary.fare?.total ?? summary.fare
    return {
      pathType: routePathType(summary.transport_type),
      totalTimeMinutes: Math.ceil(numberValue(summary.total_time) / 60),
      totalWalkMeters: walkMeters,
      fareWon: numberValue(fare),
      transferCount: numberValue(summary.transfer_count),
      legs,
    }
  })
}

export function sortTransitRoutes(routes: TransitRoute[], mode: TransitRouteSortMode) {
  return [...routes].sort((a, b) => mode === 'FEWEST_TRANSFERS' ? a.transferCount - b.transferCount || a.totalTimeMinutes - b.totalTimeMinutes : mode === 'FASTEST' ? a.totalTimeMinutes - b.totalTimeMinutes || a.transferCount - b.transferCount : a.totalTimeMinutes + a.transferCount * 8 + a.totalWalkMeters / 100 - (b.totalTimeMinutes + b.transferCount * 8 + b.totalWalkMeters / 100))
}

export function searchTransitRoutes(start: { latitude: number; longitude: number }, end: { latitude: number; longitude: number }, signal?: AbortSignal) {
  const params = new URLSearchParams({ startLongitude: String(start.longitude), startLatitude: String(start.latitude), endLongitude: String(end.longitude), endLatitude: String(end.latitude) })
  return fetchApi<unknown>(`/api/v1/routes/search?${params}`, signal, KAKAO_ROUTE_TIMEOUT_MS).then(parseKakaoRoutes)
}
