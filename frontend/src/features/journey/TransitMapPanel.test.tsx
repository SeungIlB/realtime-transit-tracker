import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { expect, test } from 'vitest'
import { TransitMapPanel } from './TransitMapPanel'

const boardingStop = {
  directionId: 'direction-1',
  directionName: '도심 방면',
  stopId: 'stop-1',
  stopName: '시청',
  stopSequence: 10,
  latitude: 37.5657,
  longitude: 126.9769,
  nextStopId: null,
  displayDirection: '도심 방면',
}

test('keeps the decision usable when the NAVER Maps key is missing', async () => {
  const user = userEvent.setup()
  render(
    <TransitMapPanel
      location={{ latitude: 37.5665, longitude: 126.978 }}
      boardingStop={boardingStop}
      routeStops={[boardingStop]}
      vehicles={[]}
      recommendedVehicleId={null}
      ncpKeyId=""
    />,
  )

  const toggle = screen.getByRole('button', { name: '지도에서 보기' })
  await user.click(toggle)

  expect(toggle).toHaveAttribute('aria-expanded', 'true')
  expect(screen.getByRole('status')).toHaveTextContent('네이버 지도 연결 키가 아직 설정되지 않았어요.')
})
