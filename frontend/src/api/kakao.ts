import { fetchApi } from './client'

export type TransitType = 'SUBWAY' | 'BUS' | 'WALK'
export type TransitRouteLeg = { type: TransitType; sectionTimeMinutes: number; distanceMeters: number; stationCount: number; startName: string | null; startLatitude: number | null; startLongitude: number | null; endName: string | null; endLatitude: number | null; endLongitude: number | null; directionName: string | null; laneNames: string[] }
export type TransitRoute = { pathType: 'SUBWAY' | 'BUS' | 'BUS_SUBWAY' | 'UNKNOWN'; totalTimeMinutes: number; totalWalkMeters: number; fareWon: number; transferCount: number; legs: TransitRouteLeg[] }
export type TransitRouteSortMode = 'OPTIMAL' | 'FEWEST_TRANSFERS' | 'FASTEST'

const numberValue = (value: unknown) => Number.isFinite(Number(value)) ? Number(value) : 0
const textValue = (value: unknown) => typeof value === 'string' && value.trim() ? value.trim() : null
const coordinate = (value: unknown) => Number.isFinite(Number(value)) ? Number(value) : null

export function parseKakaoRoutes(response: any): TransitRoute[] {
  if (response?.status !== 'OK') return []
  return (Array.isArray(response.routes) ? response.routes : []).map((route: any) => {
    const properties = route.properties ?? {}
    const type = properties.type === 'BUS' ? 'BUS' : properties.type === 'SUBWAY' ? 'SUBWAY' : properties.type === 'BUS_AND_SUBWAY' ? 'BUS_SUBWAY' : 'UNKNOWN'
    const legs = (Array.isArray(route.steps) ? route.steps : []).map((step: any): TransitRouteLeg => {
      const info = step.properties ?? {}
      const stops = Array.isArray(info.stops) ? info.stops : []
      const points = Array.isArray(step.path?.points) ? step.path.points : []
      const first = Array.isArray(points[0]) ? points[0] : []
      const last = Array.isArray(points.at(-1)) ? points.at(-1) : []
      const vehicles = Array.isArray(info.vehicles) ? info.vehicles : []
      return { type: info.type === 'BUS' ? 'BUS' : info.type === 'SUBWAY' ? 'SUBWAY' : 'WALK', sectionTimeMinutes: Math.ceil(numberValue(info.time) / 60), distanceMeters: numberValue(info.distance), stationCount: stops.length, startName: textValue(stops[0]?.name), startLatitude: coordinate(first[1]), startLongitude: coordinate(first[0]), endName: textValue(stops.at(-1)?.name), endLatitude: coordinate(last[1]), endLongitude: coordinate(last[0]), directionName: null, laneNames: vehicles.map((vehicle: any) => textValue(vehicle.name)).filter((name: string | null): name is string => name !== null) }
    })
    const walkMeters = legs.filter((leg: TransitRouteLeg) => leg.type === 'WALK').reduce((sum: number, leg: TransitRouteLeg) => sum + leg.distanceMeters, 0)
    return { pathType: type, totalTimeMinutes: Math.ceil(numberValue(properties.totalTime) / 60), totalWalkMeters: walkMeters, fareWon: numberValue(properties.fare?.value), transferCount: numberValue(properties.transfers), legs }
  })
}

export function sortTransitRoutes(routes: TransitRoute[], mode: TransitRouteSortMode) {
  return [...routes].sort((a, b) => mode === 'FEWEST_TRANSFERS' ? a.transferCount - b.transferCount || a.totalTimeMinutes - b.totalTimeMinutes : mode === 'FASTEST' ? a.totalTimeMinutes - b.totalTimeMinutes || a.transferCount - b.transferCount : a.totalTimeMinutes + a.transferCount * 8 + a.totalWalkMeters / 100 - (b.totalTimeMinutes + b.transferCount * 8 + b.totalWalkMeters / 100))
}

export function searchTransitRoutes(start: { latitude: number; longitude: number }, end: { latitude: number; longitude: number }, signal?: AbortSignal) {
  const params = new URLSearchParams({ startLongitude: String(start.longitude), startLatitude: String(start.latitude), endLongitude: String(end.longitude), endLatitude: String(end.latitude) })
  return fetchApi<unknown>(`/api/v1/routes/search?${params}`, signal).then(parseKakaoRoutes)
}
