import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { searchPlaces } from '../../api/geocoding'
import { calculateRouteDecision } from '../../api/journey'
import { searchTransitRoutes } from '../../api/kakao'
import { fetchDirectedStops, searchTransitLines } from '../../api/transit'
import { TransitRouteFinder } from './TransitRouteFinder'

vi.mock('../../api/geocoding', () => ({ searchPlaces: vi.fn() }))
vi.mock('../../api/kakao', async (importOriginal) => {
  const original = await importOriginal<typeof import('../../api/kakao')>()
  return { ...original, searchTransitRoutes: vi.fn() }
})
vi.mock('../../api/journey', () => ({ calculateRouteDecision: vi.fn() }))
vi.mock('../../api/transit', () => ({ fetchDirectedStops: vi.fn(), searchTransitLines: vi.fn() }))

afterEach(() => {
  cleanup()
  vi.clearAllMocks()
})

describe('TransitRouteFinder', () => {
  it('passes a recommended transit leg to the existing line search', async () => {
    vi.mocked(searchPlaces).mockResolvedValue([{
      id: 1,
      displayName: '부평역, 부평구, 인천광역시',
      latitude: 37.4895,
      longitude: 126.724,
    }])
    vi.mocked(searchTransitRoutes).mockResolvedValue([{
      pathType: 'SUBWAY',
      totalTimeMinutes: 49,
      totalWalkMeters: 310,
      fareWon: 1950,
      transferCount: 0,
      legs: [{
        type: 'SUBWAY',
        sectionTimeMinutes: 44,
        distanceMeters: 24000,
        stationCount: 14,
        startName: '시청',
        startLatitude: 37.5657,
        startLongitude: 126.977,
        endName: '부평',
        endLatitude: 37.4895,
        endLongitude: 126.724,
        directionName: '인천행',
        laneNames: ['수도권 1호선'],
      }],
    }])
    const onChooseLeg = vi.fn()
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const user = userEvent.setup()
    render(
      <QueryClientProvider client={queryClient}>
        <TransitRouteFinder origin={{ latitude: 37.5663, longitude: 126.9779 }} onChooseLeg={onChooseLeg} />
      </QueryClientProvider>,
    )

    await user.type(screen.getByLabelText('목적지'), '부평역')
    fireEvent.submit(screen.getByRole('search'))
    await user.click(await screen.findByRole('button', { name: /부평역/ }))
    await user.click(screen.getByRole('button', { name: '대중교통 경로 찾기' }))

    expect(await screen.findByText('49분')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: '이 구간 계산' }))

    await waitFor(() => expect(onChooseLeg).toHaveBeenCalledWith({
      provider: 'SEOUL_SUBWAY',
      query: '1호선',
      startName: '시청',
      endName: '부평',
      lineSearchLocation: { latitude: 37.5657, longitude: 126.977 },
    }))
    expect(searchTransitRoutes).toHaveBeenCalledTimes(1)
  })

  it('matches every transit leg and requests one route-wide decision', async () => {
    vi.mocked(searchPlaces).mockResolvedValue([{
      id: 2,
      displayName: '명동역, 서울특별시',
      latitude: 37.5609,
      longitude: 126.9862,
    }])
    vi.mocked(searchTransitRoutes).mockResolvedValue([{
      pathType: 'SUBWAY',
      totalTimeMinutes: 25,
      totalWalkMeters: 180,
      fareWon: 1500,
      transferCount: 1,
      legs: [
        { type: 'SUBWAY', sectionTimeMinutes: 8, distanceMeters: 5000, stationCount: 3, startName: '시청', startLatitude: 37.5657, startLongitude: 126.977, endName: '서울역', endLatitude: 37.5547, endLongitude: 126.9707, directionName: null, laneNames: ['수도권 1호선'] },
        { type: 'WALK', sectionTimeMinutes: 3, distanceMeters: 180, stationCount: 0, startName: null, startLatitude: null, startLongitude: null, endName: null, endLatitude: null, endLongitude: null, directionName: null, laneNames: [] },
        { type: 'SUBWAY', sectionTimeMinutes: 5, distanceMeters: 2200, stationCount: 2, startName: '서울역', startLatitude: 37.5547, startLongitude: 126.9707, endName: '명동', endLatitude: 37.5609, endLongitude: 126.9862, directionName: null, laneNames: ['수도권 4호선'] },
      ],
    }])
    vi.mocked(searchTransitLines)
      .mockResolvedValueOnce([{ id: 'line-1', providerLineId: '1', publicName: '1호선', operatorName: null, routeType: null }])
      .mockResolvedValueOnce([{ id: 'line-4', providerLineId: '4', publicName: '4호선', operatorName: null, routeType: null }])
    vi.mocked(fetchDirectedStops)
      .mockResolvedValueOnce([
        { directionId: 'direction-1', directionName: '하행', stopId: 'city-hall', stopName: '시청', stopSequence: 1, latitude: 37.5657, longitude: 126.977, nextStopId: null, displayDirection: null },
        { directionId: 'direction-1', directionName: '하행', stopId: 'seoul-1', stopName: '서울역', stopSequence: 2, latitude: 37.5547, longitude: 126.9707, nextStopId: null, displayDirection: null },
      ])
      .mockResolvedValueOnce([
        { directionId: 'direction-4', directionName: '하행', stopId: 'seoul-4', stopName: '서울역', stopSequence: 1, latitude: 37.5547, longitude: 126.9707, nextStopId: null, displayDirection: null },
        { directionId: 'direction-4', directionName: '하행', stopId: 'myeongdong', stopName: '명동', stopSequence: 2, latitude: 37.5609, longitude: 126.9862, nextStopId: null, displayDirection: null },
      ])
    vi.mocked(calculateRouteDecision).mockResolvedValue({
      decision: 'COMFORTABLE', recommendedPace: 'SLOW_WALK', overallProbability: 0.9,
      targetProbability: 0.8, confidence: 'HIGH', reasons: ['ok'], legs: [],
      expectedArrivalAt: '2026-09-10T01:25:00Z', calculatedAt: '2026-09-10T01:00:00Z', nextRefreshAt: '2026-09-10T01:01:00Z',
    })
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    const user = userEvent.setup()
    render(
      <QueryClientProvider client={queryClient}>
        <TransitRouteFinder origin={{ latitude: 37.5663, longitude: 126.9779, accuracyM: 10, observedAt: '2026-09-10T01:00:00Z' }} onChooseLeg={vi.fn()} />
      </QueryClientProvider>,
    )

    await user.type(screen.getByLabelText('목적지'), '명동역')
    fireEvent.submit(screen.getByRole('search'))
    await user.click(await screen.findByRole('button', { name: /명동역/ }))
    await user.click(screen.getByRole('button', { name: '대중교통 경로 찾기' }))
    await user.click(await screen.findByRole('button', { name: '실시간 확률 계산' }))

    await waitFor(() => expect(calculateRouteDecision).toHaveBeenCalledTimes(1))
    expect(vi.mocked(calculateRouteDecision).mock.calls[0][0].legs.map((leg) => leg.transferWalkTimeMinutes)).toEqual([0, 3])
    expect(await screen.findByText('90%')).toBeInTheDocument()
  })
})
