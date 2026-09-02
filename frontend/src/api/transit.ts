import { fetchApi } from './client'

export type TransitProvider = 'NATIONAL_PRECISION_BUS' | 'GBIS' | 'SEOUL_SUBWAY'

export type TransitLine = {
  id: string
  providerLineId: string
  publicName: string
  operatorName: string | null
  routeType: string | null
}

export type DirectedStop = {
  directionId: string
  directionName: string
  stopId: string
  stopName: string
  stopSequence: number
  latitude: number | null
  longitude: number | null
  nextStopId: string | null
  displayDirection: string | null
}

export type DestinationStop = {
  directionId: string
  directionName: string
  stopId: string
  stopName: string
  stopSequence: number
}

export type UpcomingArrival = {
  arrivalPredictionId: number
  vehicleRunObservationId: number
  providerVehicleId: string
  lineId: string
  boardingStopId: string
  expectedAt: string
  minExpectedAt: string | null
  maxExpectedAt: string | null
  remainingStops: number | null
  source: string
  confidence: string
  movementStatus: string
  currentStopId: string | null
  currentSequence: number | null
  observedAt: string
  receivedAt: string
}

export function searchTransitLines(
  provider: TransitProvider,
  query: string,
  signal?: AbortSignal,
) {
  const params = new URLSearchParams({ provider, query, limit: '20' })
  return fetchApi<TransitLine[]>(`/api/v1/lines?${params}`, signal)
}

export function fetchDirectedStops(lineId: string, signal?: AbortSignal) {
  return fetchApi<DirectedStop[]>(`/api/v1/lines/${lineId}/stops`, signal)
}

export function fetchDestinationStops(
  lineId: string,
  boardingStopId: string,
  signal?: AbortSignal,
) {
  return fetchApi<DestinationStop[]>(
    `/api/v1/lines/${lineId}/stops/${boardingStopId}/destinations`,
    signal,
  )
}

export function fetchUpcomingArrivals(
  lineId: string,
  boardingStopId: string,
  alightingStopId: string,
  signal?: AbortSignal,
) {
  const params = new URLSearchParams({ boardingStopId, alightingStopId })
  return fetchApi<UpcomingArrival[]>(`/api/v1/lines/${lineId}/arrivals?${params}`, signal)
}
