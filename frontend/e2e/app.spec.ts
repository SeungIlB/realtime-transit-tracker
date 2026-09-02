import { expect, test } from '@playwright/test'

const success = (data: unknown) => ({
  success: true,
  code: 'SUCCESS',
  message: 'Success',
  data,
})

test('finds upcoming vehicles through the complete stop selection flow', async ({ page }) => {
  const expectedAt = new Date(Date.now() + 5 * 60_000).toISOString()

  await page.route('**/api/v1/**', async (route) => {
    const url = new URL(route.request().url())
    let body: unknown = []

    if (url.pathname === '/api/v1/system/health') {
      body = { status: 'UP', checkedAt: new Date().toISOString() }
    } else if (url.pathname === '/api/v1/lines') {
      expect(url.searchParams.get('provider')).toBe('NATIONAL_PRECISION_BUS')
      body = [{ id: 'line-1', providerLineId: 'route-1000', publicName: '1000', operatorName: '테스트 운수', routeType: 'CITY_BUS' }]
    } else if (url.pathname === '/api/v1/lines/line-1/stops') {
      body = [
        { directionId: 'direction-1', directionName: '강남 방면', stopId: 'stop-1', stopName: '테스트 승차 정류장', stopSequence: 1, latitude: 37.1, longitude: 127.1, nextStopId: 'stop-2', displayDirection: '강남 방면' },
        { directionId: 'direction-1', directionName: '강남 방면', stopId: 'stop-2', stopName: '테스트 하차 정류장', stopSequence: 2, latitude: 37.2, longitude: 127.2, nextStopId: null, displayDirection: '강남 방면' },
      ]
    } else if (url.pathname.endsWith('/destinations')) {
      body = [{ directionId: 'direction-1', directionName: '강남 방면', stopId: 'stop-2', stopName: '테스트 하차 정류장', stopSequence: 2 }]
    } else if (url.pathname.endsWith('/arrivals')) {
      body = [{ arrivalPredictionId: 1, vehicleRunObservationId: 1, providerVehicleId: '경기70바1000', lineId: 'line-1', boardingStopId: 'stop-1', expectedAt, minExpectedAt: null, maxExpectedAt: null, remainingStops: 2, source: 'PROVIDER', confidence: 'HIGH', movementStatus: 'APPROACHING', currentStopId: 'stop-1', currentSequence: 1, observedAt: new Date().toISOString(), receivedAt: new Date().toISOString() }]
    }

    await route.fulfill({ json: success(body) })
  })

  await page.goto('/')

  await expect(page.getByRole('heading', { name: '첫 차와 다음 차를 한눈에' })).toBeVisible()
  await expect(page.getByRole('status', { name: '서버 연결 정상' })).toBeVisible()

  await page.getByLabel('버스 번호').fill('1000')
  await page.getByRole('button', { name: /노선 찾기/ }).click()
  await page.getByRole('button', { name: /1000 테스트 운수/ }).click()
  await page.getByLabel('승차 정류장 검색').fill('승차')
  await expect(page.getByLabel('승차 정류장').getByRole('button', { name: /테스트 하차 정류장/ })).toHaveCount(0)
  await page.getByRole('button', { name: /테스트 승차 정류장/ }).click()
  await page.getByLabel('하차 정류장 검색').fill('하차')
  await page.getByLabel('하차 정류장').getByRole('button', { name: /테스트 하차 정류장/ }).click()

  await expect(page.getByRole('heading', { name: '곧 도착하는 차량' })).toBeVisible()
  await expect(page.getByText('경기70바1000')).toBeVisible()
  await expect(page.getByText('2', { exact: true })).toBeVisible()
})

test('explains missing GBIS authorization on the provider screen', async ({ page }) => {
  await page.route('**/api/v1/**', async (route) => {
    const url = new URL(route.request().url())
    if (url.pathname === '/api/v1/system/health') {
      await route.fulfill({ json: success({ status: 'UP', checkedAt: new Date().toISOString() }) })
      return
    }
    await route.fulfill({
      status: 502,
      json: {
        success: false,
        code: 'EXTERNAL_API_AUTHENTICATION_FAILED',
        message: 'External transit API authentication failed',
        data: null,
      },
    })
  })

  await page.goto('/')
  await page.getByText('경기 버스', { exact: true }).click()
  await page.getByLabel('경기버스 번호').fill('6601')
  await page.getByRole('button', { name: /노선 찾기/ }).click()

  await expect(page.getByText(/경기버스 노선정보 게이트웨이가 인증키를 거절했어요/)).toBeVisible()
})
