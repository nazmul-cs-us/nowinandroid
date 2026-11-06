'use strict';
self.addEventListener('push', event => {
  let data = {};
  try { data = event.data.json(); } catch {}
  const valid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(data.callId || '');
  const fresh = valid && Number(data.expires) > Date.now();
  // Every push displays a notification, including expired/malformed deliveries.
  event.waitUntil(self.registration.showNotification(fresh ? 'Incoming family call' : 'Family call alert', {
    body: fresh ? `${data.kind === 'video' ? 'Video' : 'Voice'} call. Open Deenly to answer.` : 'Open Deenly to check your family calls.',
    icon:'icon.png', badge:'icon.png', tag:valid ? `family-${data.callId}` : 'family-alert',
    data:{callId:valid ? data.callId : '', expires:Number(data.expires) || 0}
  }));
});
self.addEventListener('notificationclick', event => {
  event.notification.close();
  const id = event.notification.data.callId || '';
  event.waitUntil(self.clients.openWindow(`${self.registration.scope}open.html#call=${encodeURIComponent(id)}`));
});
