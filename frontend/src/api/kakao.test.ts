import { describe, expect, it } from 'vitest'
import { parseKakaoRoutes } from './kakao'

describe('parseKakaoRoutes', () => {
  it('parses the Kakao map publictraffic response', () => {
    const routes = parseKakaoRoutes({
      status: 'OK',
      routes: [{
        properties: { type: 'BUS_AND_SUBWAY', totalTime: 600, transfers: 1, fare: { value: 1550 } },
        steps: [{
          properties: { type: 'SUBWAY', time: 300, distance: 3000, stops: [{ name: '시청' }, { name: '서울역' }], vehicles: [{ name: '1호선' }] },
          path: { points: [[126.9, 37.5], [126.95, 37.51]] },
        }],
      }],
    })

    expect(routes).toHaveLength(1)
    expect(routes[0].pathType).toBe('BUS_SUBWAY')
    expect(routes[0].legs[0].type).toBe('SUBWAY')
    expect(routes[0].legs[0].laneNames).toEqual(['1호선'])
  })

  it('parses the Kakao Mobility multimodal response', () => {
    const routes = parseKakaoRoutes({
      result_code: 0,
      journeys: [{
        summary: { total_time: 600, transfer_count: 0, transport_type: ['Subway'] },
        sections: [{
          time: 300,
          distance: 3000,
          departure_stop: { stop_name: '시청', x: 126.9, y: 37.5 },
          arrival_stop: { stop_name: '서울역', x: 126.95, y: 37.51 },
          passing_stops: [],
          route: { route_short_name: '1호선', route_full_name: '수도권 1호선', last_stop_name_of_trip: '인천' },
          path: [126.9, 37.5, 126.95, 37.51],
        }],
      }],
    })

    expect(routes).toHaveLength(1)
    expect(routes[0].pathType).toBe('SUBWAY')
    expect(routes[0].legs[0].directionName).toBe('인천 방면')
    expect(routes[0].legs[0].stationCount).toBe(1)
  })
})
