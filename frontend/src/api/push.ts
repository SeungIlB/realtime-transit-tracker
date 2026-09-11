import { requestApi } from './client'

type PushSubscriptionPayload = {
  endpoint: string
  expirationTime: number | null
  keys: { p256dh: string; auth: string }
}

export function savePushSubscription(subscription: PushSubscription, journeyId?: string) {
  const json = subscription.toJSON()
  const payload: PushSubscriptionPayload = {
    endpoint: json.endpoint || subscription.endpoint,
    expirationTime: json.expirationTime ?? null,
    keys: {
      p256dh: json.keys?.p256dh || '',
      auth: json.keys?.auth || '',
    },
  }
  return requestApi<void>('/api/v1/push/subscriptions', {
    method: 'POST',
    body: JSON.stringify({ ...payload, journeyId: journeyId || null }),
  })
}

export async function registerPush(journeyId?: string) {
  if (!('serviceWorker' in navigator) || !('PushManager' in window)) return false
  const publicKey = import.meta.env.VITE_VAPID_PUBLIC_KEY as string | undefined
  if (!publicKey) return false
  const registration = await navigator.serviceWorker.register('/push-sw.js')
  const existing = await registration.pushManager.getSubscription()
  const subscription = existing || await registration.pushManager.subscribe({
    userVisibleOnly: true,
    applicationServerKey: publicKey,
  })
  await savePushSubscription(subscription, journeyId)
  return true
}
