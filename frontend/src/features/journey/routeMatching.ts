import type { DirectedStop, TransitLine } from '../../api/transit'

function compact(value: string) {
  return value.normalize('NFKC').toLocaleLowerCase('ko-KR').replace(/[^\p{L}\p{N}]/gu, '')
}

export function normalizeLineName(value: string) {
  return compact(value
    .replace(/^(수도권|서울|인천|부산|대구|대전|광주)\s*/u, '')
    .replace(/도시철도/gu, ''))
}

export function normalizeStopName(value: string) {
  return compact(value.replace(/\([^)]*\)/gu, '')).replace(/(정류장|역)$/u, '')
}

export function findUniqueLine(lines: TransitLine[], query: string) {
  const normalizedQuery = normalizeLineName(query)
  const matches = lines.filter((line) => normalizeLineName(line.publicName) === normalizedQuery)
  return matches.length === 1 ? matches[0] : null
}

export type StopPair = {
  boarding: DirectedStop
  alighting: DirectedStop
}

export function findUniqueStopPair(stops: DirectedStop[], startName: string, endName: string): StopPair | null {
  const normalizedStart = normalizeStopName(startName)
  const normalizedEnd = normalizeStopName(endName)
  const boardingStops = stops.filter((stop) => normalizeStopName(stop.stopName) === normalizedStart)
  const alightingStops = stops.filter((stop) => normalizeStopName(stop.stopName) === normalizedEnd)
  const pairs = boardingStops.flatMap((boarding) => alightingStops
    .filter((alighting) => alighting.directionId === boarding.directionId && alighting.stopSequence > boarding.stopSequence)
    .map((alighting) => ({ boarding, alighting })))
  const uniquePairs = Array.from(new Map(pairs.map((pair) => [
    `${pair.boarding.directionId}:${pair.boarding.stopId}:${pair.alighting.stopId}`,
    pair,
  ])).values())
  return uniquePairs.length === 1 ? uniquePairs[0] : null
}
