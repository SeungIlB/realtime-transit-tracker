const GEOCODING_BASE_URL = 'https://nominatim.openstreetmap.org'
let nextRequestAt = 0

type NominatimSearchResult = {
  place_id: number
  display_name: string
  lat: string
  lon: string
}

export type PlaceSearchResult = {
  id: number
  displayName: string
  latitude: number
  longitude: number
}

async function respectPublicRateLimit(signal?: AbortSignal) {
  const delayMsec = Math.max(0, nextRequestAt - Date.now())
  if (delayMsec > 0) {
    await new Promise<void>((resolve, reject) => {
      const timeoutId = window.setTimeout(resolve, delayMsec)
      signal?.addEventListener('abort', () => {
        window.clearTimeout(timeoutId)
        reject(new DOMException('Aborted', 'AbortError'))
      }, { once: true })
    })
  }
  nextRequestAt = Date.now() + 1_100
}

export async function searchPlaces(query: string, signal?: AbortSignal): Promise<PlaceSearchResult[]> {
  await respectPublicRateLimit(signal)
  const parameters = new URLSearchParams({
    q: query,
    format: 'jsonv2',
    countrycodes: 'kr',
    'accept-language': 'ko',
    limit: '5',
  })
  const response = await fetch(`${GEOCODING_BASE_URL}/search?${parameters}`, { signal })
  if (!response.ok) throw new Error('PLACE_SEARCH_FAILED')
  const results = await response.json() as NominatimSearchResult[]
  return results.map((result) => ({
    id: result.place_id,
    displayName: result.display_name,
    latitude: Number(result.lat),
    longitude: Number(result.lon),
  })).filter((result) => Number.isFinite(result.latitude) && Number.isFinite(result.longitude))
}
