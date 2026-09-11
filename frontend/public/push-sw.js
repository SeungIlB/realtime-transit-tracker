self.addEventListener('push', (event) => {
  if (!event.data) return
  const data = event.data.json()
  event.waitUntil(self.registration.showNotification(data.title || '실시간 교통 알림', {
    body: data.body || '',
    tag: data.tag || 'realtime-transit',
    data: { url: data.url || '/' },
  }))
})

self.addEventListener('notificationclick', (event) => {
  event.notification.close()
  const url = event.notification.data?.url || '/'
  event.waitUntil(clients.matchAll({ type: 'window', includeUncontrolled: true }).then((windows) => {
    const existing = windows.find((window) => 'focus' in window)
    if (existing) {
      existing.navigate(url)
      return existing.focus()
    }
    return clients.openWindow(url)
  }))
})
