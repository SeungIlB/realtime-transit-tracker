import { expect, test } from '@playwright/test'

const success = (data: unknown) => ({
  success: true,
  code: 'SUCCESS',
  message: 'Success',
  data,
})

test('creates a journey and shows the boarding decision', async ({ context, page }) => {
  await context.grantPermissions(['geolocation'])
  await context.setGeolocation({ latitude: 37.1, longitude: 127.1, accuracy: 12 })

  const now = Date.now()
  const firstVehicleAt = new Date(now + 6 * 60_000).toISOString()
  const nextVehicleAt = new Date(now + 13 * 60_000).toISOString()
  const travelerAt = new Date(now + 4 * 60_000).toISOString()

  await page.route('**/api/v1/**', async (route) => {
    const request = route.request()
    const url = new URL(request.url())
    let body: unknown = []

    if (url.pathname === '/api/v1/system/health') {
      body = { status: 'UP', checkedAt: new Date().toISOString() }
    } else if (url.pathname === '/api/v1/lines') {
      expect(url.searchParams.get('provider')).toBe('GBIS')
      body = [{ id: 'line-1', providerLineId: 'route-6601', publicName: '6601', operatorName: '테스트 운수', routeType: 'CITY_BUS' }]
    } else if (url.pathname === '/api/v1/lines/line-1/stops') {
      body = [
        { directionId: 'direction-1', directionName: '강남 방면', stopId: 'stop-1', stopName: '테스트 승차 정류장', stopSequence: 1, latitude: 37.101, longitude: 127.101, nextStopId: 'stop-2', displayDirection: '강남 방면' },
        { directionId: 'direction-1', directionName: '강남 방면', stopId: 'stop-2', stopName: '테스트 하차 정류장', stopSequence: 2, latitude: 37.2, longitude: 127.2, nextStopId: null, displayDirection: '강남 방면' },
      ]
    } else if (url.pathname.endsWith('/destinations')) {
      body = [{ directionId: 'direction-1', directionName: '강남 방면', stopId: 'stop-2', stopName: '테스트 하차 정류장', stopSequence: 2 }]
    } else if (url.pathname === '/api/v1/journeys' && request.method() === 'POST') {
      const payload = request.postDataJSON()
      expect(payload).toMatchObject({ lineId: 'line-1', directionId: 'direction-1', boardingStopId: 'stop-1', alightingStopId: 'stop-2', targetProbability: null })
      body = { journeyId: 'journey-1', ...payload, status: 'ACTIVE', expiresAt: new Date(now + 60 * 60_000).toISOString(), createdAt: new Date().toISOString() }
    } else if (url.pathname === '/api/v1/journeys/journey-1/locations') {
      expect(request.method()).toBe('POST')
      expect(request.postDataJSON()).toMatchObject({ latitude: 37.1, longitude: 127.1, accuracyM: 12 })
      body = { locationObservationId: 1, journeyId: 'journey-1' }
    } else if (url.pathname === '/api/v1/journeys/journey-1/decision') {
      const pacePredictions = [
        { paceType: 'SLOW_WALK', speedMps: 0.9, distanceM: 180, minExpectedAt: travelerAt, expectedAt: travelerAt, maxExpectedAt: travelerAt, boardingProbability: 0.62, recommended: false },
        { paceType: 'WALK', speedMps: 1.3, distanceM: 180, minExpectedAt: travelerAt, expectedAt: travelerAt, maxExpectedAt: travelerAt, boardingProbability: 0.92, recommended: true },
        { paceType: 'FAST_WALK', speedMps: 1.7, distanceM: 180, minExpectedAt: travelerAt, expectedAt: travelerAt, maxExpectedAt: travelerAt, boardingProbability: 0.98, recommended: false },
        { paceType: 'RUN', speedMps: 2.5, distanceM: 180, minExpectedAt: travelerAt, expectedAt: travelerAt, maxExpectedAt: travelerAt, boardingProbability: 1, recommended: false },
      ]
      body = {
        journeyId: 'journey-1',
        decision: 'LEAVE_NOW',
        recommendedVehicleId: '경기70바1000',
        recommendedPace: 'WALK',
        targetProbability: 0.8,
        confidence: 'HIGH',
        reasons: ['The least demanding pace meeting the target probability was selected'],
        vehicles: [
          { arrivalPredictionId: 1, vehicleRunObservationId: 1, providerVehicleId: '경기70바1000', serviceType: 'LOCAL', movementStatus: 'DEPARTED', currentStopName: '테스트 이전 정류장', currentSequence: 3, remainingStops: 2, latitude: null, longitude: null, observedAt: new Date().toISOString(), vehicleMinExpectedAt: firstVehicleAt, vehicleExpectedAt: firstVehicleAt, vehicleMaxExpectedAt: firstVehicleAt, decision: 'LEAVE_NOW', recommendedPace: 'WALK', confidence: 'HIGH', pacePredictions },
          { arrivalPredictionId: 2, vehicleRunObservationId: 2, providerVehicleId: '경기70바1001', serviceType: 'LOCAL', vehicleMinExpectedAt: nextVehicleAt, vehicleExpectedAt: nextVehicleAt, vehicleMaxExpectedAt: nextVehicleAt, decision: 'COMFORTABLE', recommendedPace: 'SLOW_WALK', confidence: 'HIGH', pacePredictions: pacePredictions.map((pace) => ({ ...pace, recommended: pace.paceType === 'SLOW_WALK' })) },
        ],
        calculatedAt: new Date().toISOString(),
        nextRefreshAt: new Date(now + 60_000).toISOString(),
      }
    } else if (url.pathname === '/api/v1/journeys/journey-1' && request.method() === 'DELETE') {
      body = { journeyId: 'journey-1', status: 'CANCELLED' }
    }

    await route.fulfill({ json: success(body) })
  })

  await page.goto('/')

  await expect(page.getByRole('heading', { name: '지금 나가면 탈 수 있을까?' })).toBeVisible()
  await page.getByRole('button', { name: '내 위치 확인' }).click()
  await expect(page.getByText('현재 위치를 사용하고 있어요', { exact: true })).toBeVisible()

  await page.getByLabel('경기버스 번호').fill('6601')
  await page.getByRole('button', { name: /노선 찾기/ }).click()
  await page.getByRole('button', { name: /6601 테스트 운수/ }).click()
  await page.getByRole('button', { name: /테스트 승차 정류장/ }).click()
  await page.getByLabel('하차 정류장').getByRole('button', { name: /테스트 하차 정류장/ }).click()
  await page.getByRole('button', { name: '탑승 가능성 계산' }).click()

  await expect(page.getByRole('heading', { name: '지금 출발하세요' })).toBeVisible()
  await expect(page.getByLabel('1번째 차량 이동 방법별 탑승 확률').getByText('92%')).toBeVisible()
  await expect(page.getByText('경기70바1000')).toBeVisible()
  await expect(page.getByText('경기70바1001')).toBeVisible()
  await expect(page.getByText('테스트 이전 정류장 출발')).toBeVisible()
  await expect(page.getByText(/1분마다 갱신/)).toBeVisible()
})

test('explains missing GBIS authorization on the provider screen', async ({ page }) => {
  await page.route('**/api/v1/**', async (route) => {
    const url = new URL(route.request().url())
    if (url.pathname === '/api/v1/system/health') {
      await route.fulfill({ json: success({ status: 'UP', checkedAt: new Date().toISOString() }) })
      return
    }
    await route.fulfill({ status: 502, json: { success: false, code: 'EXTERNAL_API_AUTHENTICATION_FAILED', message: 'External transit API authentication failed', data: null } })
  })

  await page.goto('/')
  await page.getByLabel('경기버스 번호').fill('6601')
  await page.getByRole('button', { name: /노선 찾기/ }).click()

  await expect(page.getByText(/경기버스 노선정보 인증을 확인하지 못했어요/)).toBeVisible()
})

test('uses a searched place instead of raw coordinates', async ({ page }) => {
  await page.route('**/api/v1/system/health', async (route) => {
    await route.fulfill({ json: success({ status: 'UP', checkedAt: new Date().toISOString() }) })
  })
  await page.route('https://nominatim.openstreetmap.org/search?**', async (route) => {
    const url = new URL(route.request().url())
    expect(url.searchParams.get('q')).toBe('용산역')
    expect(url.searchParams.get('countrycodes')).toBe('kr')
    await route.fulfill({ json: [{
      place_id: 1,
      display_name: '용산역, 한강로동, 용산구, 서울특별시, 대한민국',
      lat: '37.5298022',
      lon: '126.9646385',
      boundingbox: ['37.5283289', '37.5312705', '126.9627381', '126.9665148'],
    }] })
  })

  await page.goto('/')
  await page.getByRole('button', { name: '장소 검색' }).click()
  await page.getByLabel('주소 또는 장소명').fill('용산역')
  await page.getByRole('button', { name: '위치 찾기' }).click()
  await page.getByRole('button', { name: /용산역.*서울특별시/ }).click()

  await expect(page.getByText('용산역, 한강로동, 용산구', { exact: true })).toBeVisible()
  await expect(page.getByText(/검색한 위치를 출발점으로 사용해요/)).toBeVisible()
})

test('cancels a newly created journey when its initial location cannot be saved', async ({ context, page }) => {
  await context.grantPermissions(['geolocation'])
  await context.setGeolocation({ latitude: 37.1, longitude: 127.1, accuracy: 12 })
  let cancelledJourneyId: string | null = null

  await page.route('**/api/v1/**', async (route) => {
    const request = route.request()
    const url = new URL(request.url())
    if (url.pathname === '/api/v1/system/health') {
      await route.fulfill({ json: success({ status: 'UP', checkedAt: new Date().toISOString() }) })
      return
    }
    if (url.pathname === '/api/v1/lines') {
      await route.fulfill({ json: success([{ id: 'line-1', providerLineId: 'route-1', publicName: '1', operatorName: '테스트', routeType: 'CITY_BUS' }]) })
      return
    }
    if (url.pathname === '/api/v1/lines/line-1/stops') {
      await route.fulfill({ json: success([{ directionId: 'direction-1', directionName: '종점 방면', stopId: 'stop-1', stopName: '승차 정류장', stopSequence: 1, latitude: 37.1, longitude: 127.1, nextStopId: null, displayDirection: '종점 방면' }]) })
      return
    }
    if (url.pathname === '/api/v1/journeys' && request.method() === 'POST') {
      await route.fulfill({ json: success({ journeyId: 'journey-orphan', status: 'ACTIVE' }) })
      return
    }
    if (url.pathname === '/api/v1/journeys/journey-orphan/locations') {
      await route.fulfill({ status: 503, json: { success: false, code: 'EXTERNAL_STORAGE_ERROR', message: 'location unavailable', data: null } })
      return
    }
    if (url.pathname === '/api/v1/journeys/journey-orphan' && request.method() === 'DELETE') {
      cancelledJourneyId = 'journey-orphan'
      await route.fulfill({ json: success({ journeyId: 'journey-orphan', status: 'CANCELLED' }) })
      return
    }
    await route.fulfill({ json: success([]) })
  })

  await page.goto('/')
  await page.getByRole('button', { name: '내 위치 확인' }).click()
  await page.getByLabel('경기버스 번호').fill('1')
  await page.getByRole('button', { name: /노선 찾기/ }).click()
  await page.getByRole('button', { name: /1 테스트/ }).click()
  await page.getByRole('button', { name: /승차 정류장/ }).click()
  await page.getByRole('button', { name: '탑승 가능성 계산' }).click()

  await expect(page.getByText('location unavailable')).toBeVisible()
  await expect.poll(() => cancelledJourneyId).toBe('journey-orphan')
})
