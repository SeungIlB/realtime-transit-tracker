import { requestApi } from './client'

export type JourneySession = {
  journeyId: string
  lineId: string
  directionId: string
  boardingStopId: string
  alightingStopId: string | null
  targetProbability: number
  desiredArrivalAt: string | null
  status: string
  expiresAt: string
  createdAt: string
}

export type JourneyLocation = {
  locationObservationId: number
  journeyId: string
  latitude: number
  longitude: number
  accuracyM: number
  speedMps: number | null
  observedAt: string
  receivedAt: string
  expiresAt: string
}

export type PacePrediction = {
  paceType: string
  speedMps: number
  distanceM: number
  minExpectedAt: string
  expectedAt: string
  maxExpectedAt: string
  boardingProbability: number
  recommended: boolean
}

export type VehicleBoardingPrediction = {
  arrivalPredictionId: number
  vehicleRunObservationId: number
  providerVehicleId: string
  serviceType: string
  alightingStopStatus: 'STOPS' | 'SKIPS' | 'UNKNOWN' | 'NOT_REQUESTED'
  movementStatus: string | null
  currentStopName: string | null
  destinationStopName: string | null
  currentSequence: number | null
  remainingStops: number | null
  latitude: number | null
  longitude: number | null
  observedAt: string | null
  vehicleMinExpectedAt: string
  vehicleExpectedAt: string
  vehicleMaxExpectedAt: string
  decision: string
  recommendedPace: string | null
  confidence: string
  pacePredictions: PacePrediction[]
}

export type BoardingDecision = {
  journeyId: string
  decision: string
  recommendedVehicleId: string | null
  recommendedPace: string | null
  targetProbability: number
  confidence: string
  reasons: string[]
  vehicles: VehicleBoardingPrediction[]
  calculatedAt: string
  nextRefreshAt: string
}

export type RouteTransitLegPayload = {
  lineId: string
  directionId: string
  boardingStopId: string
  alightingStopId: string
  lineName: string
  boardingStopName: string
  alightingStopName: string
  sectionTimeMinutes: number
  transferWalkTimeMinutes: number
}

export type RouteLegDecision = {
  legIndex: number
  lineId: string
  lineName: string
  boardingStopName: string
  alightingStopName: string
  providerVehicleId: string
  serviceType: string
  connectionProbability: number
  cumulativeProbability: number
  readyExpectedAt: string
  vehicleExpectedAt: string
  alightingExpectedAt: string
  confidence: string
}

export type RouteDecision = {
  decision: string
  recommendedPace: string | null
  overallProbability: number | null
  targetProbability: number
  confidence: string
  reasons: string[]
  legs: RouteLegDecision[]
  expectedArrivalAt: string | null
  calculatedAt: string
  nextRefreshAt: string
}

type RouteDecisionPayload = {
  latitude: number
  longitude: number
  accuracyM: number
  observedAt: string
  targetProbability: number | null
  legs: RouteTransitLegPayload[]
}

type JourneyCreatePayload = {
  anonymousKey: string
  lineId: string
  directionId: string
  boardingStopId: string
  alightingStopId: string | null
  targetProbability: number | null
  desiredArrivalAt: string | null
}

type JourneyLocationPayload = {
  latitude: number
  longitude: number
  accuracyM: number
  speedMps: number | null
  observedAt: string
}

export function createJourney(payload: JourneyCreatePayload, signal?: AbortSignal) {
  return requestApi<JourneySession>('/api/v1/journeys', {
    method: 'POST',
    body: JSON.stringify(payload),
    signal,
  })
}

export function addJourneyLocation(
  journeyId: string,
  payload: JourneyLocationPayload,
  signal?: AbortSignal,
) {
  return requestApi<JourneyLocation>(`/api/v1/journeys/${journeyId}/locations`, {
    method: 'POST',
    body: JSON.stringify(payload),
    signal,
  })
}

export function fetchBoardingDecision(journeyId: string, signal?: AbortSignal) {
  return requestApi<BoardingDecision>(`/api/v1/journeys/${journeyId}/decision`, { signal })
}

export function cancelJourney(journeyId: string, signal?: AbortSignal) {
  return requestApi<JourneySession>(`/api/v1/journeys/${journeyId}`, {
    method: 'DELETE',
    signal,
  })
}

export function calculateRouteDecision(payload: RouteDecisionPayload, signal?: AbortSignal) {
  return requestApi<RouteDecision>('/api/v1/journeys/route-decisions', {
    method: 'POST',
    body: JSON.stringify(payload),
    signal,
  })
}
