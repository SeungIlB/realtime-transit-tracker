import { describe, expect, it } from 'vitest'
import type { DirectedStop, TransitLine } from '../../api/transit'
import { findUniqueLine, findUniqueStopPair, normalizeStopName } from './routeMatching'

const lines: TransitLine[] = [
  { id: '1', providerLineId: 'A', publicName: '1호선', operatorName: null, routeType: null },
  { id: '2', providerLineId: 'B', publicName: '서해선', operatorName: null, routeType: null },
]

function stop(directionId: string, stopId: string, stopName: string, stopSequence: number): DirectedStop {
  return {
    directionId,
    directionName: directionId,
    stopId,
    stopName,
    stopSequence,
    latitude: null,
    longitude: null,
    nextStopId: null,
    displayDirection: null,
  }
}

describe('route matching', () => {
  it('matches a subway label to one internal line', () => {
    expect(findUniqueLine(lines, '수도권 1호선')?.id).toBe('1')
  })

  it('does not choose when the same line name is ambiguous', () => {
    expect(findUniqueLine([...lines, { ...lines[0], id: '3' }], '1호선')).toBeNull()
  })

  it('matches station suffixes and chooses the direction where the destination follows', () => {
    const stops = [
      stop('UP', 'city-up', '시청역', 10),
      stop('UP', 'bupyeong-up', '부평', 35),
      stop('DOWN', 'bupyeong-down', '부평역', 5),
      stop('DOWN', 'city-down', '시청', 30),
    ]

    expect(findUniqueStopPair(stops, '시청', '부평역')).toEqual({
      boarding: stops[0],
      alighting: stops[1],
    })
    expect(normalizeStopName('시청역(1호선)')).toBe('시청')
  })

  it('does not choose an ambiguous stop pair', () => {
    const stops = [
      stop('A', 'start-a', '중앙역', 1),
      stop('A', 'end-a', '시청역', 3),
      stop('B', 'start-b', '중앙역', 1),
      stop('B', 'end-b', '시청역', 3),
    ]

    expect(findUniqueStopPair(stops, '중앙', '시청')).toBeNull()
  })
})
