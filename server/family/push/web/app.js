'use strict';
const status = document.querySelector('#status');
const enable = document.querySelector('#enable');
const disable = document.querySelector('#disable');
const storageKey = 'deenly-family-alerts';
function saved() { try { return JSON.parse(localStorage.getItem(storageKey)); } catch { return null; } }
function refresh() {
  const value = saved();
  disable.hidden = !value;
  if (value) status.textContent = value.expires * 1000 > Date.now() ? `Alerts enabled until ${new Date(value.expires * 1000).toLocaleString()}.` : 'Alerts expired. Create a fresh pairing code in Deenly.';
}
async function post(path, body) {
  const response = await fetch(`api/${path}`, {method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify(body), credentials:'omit', cache:'no-store'});
  const result = await response.json();
  if (!response.ok) throw new Error(result.error || 'Please try again.');
  return result;
}
enable.addEventListener('click', async () => {
  enable.disabled = true;
  try {
    if (!('serviceWorker' in navigator) || !('PushManager' in window)) throw new Error('Web Push is unavailable. On iPhone, add this page to the Home Screen and open its icon.');
    const iphone = /iPhone|iPad|iPod/.test(navigator.userAgent) || (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1);
    if (iphone && !navigator.standalone && !matchMedia('(display-mode: standalone)').matches) throw new Error('Add this page to the Home Screen, then open its icon to enable alerts.');
    const code = document.querySelector('#pair').value.trim();
    if (!code) throw new Error('Paste the pairing code from Deenly first.');
    // Request permission directly from this user gesture; never prompt on page load.
    const permission = await Notification.requestPermission();
    if (permission !== 'granted') throw new Error('Allow notifications in Settings to receive call alerts.');
    const registration = await navigator.serviceWorker.register('sw.js', {scope:'./'});
    await navigator.serviceWorker.ready;
    const keyResponse = await fetch('api/key', {cache:'no-store'});
    if (!keyResponse.ok) throw new Error('Call alerts are temporarily unavailable.');
    const key = (await keyResponse.json()).publicKey;
    const raw = atob(key.replace(/-/g,'+').replace(/_/g,'/') + '='.repeat((4-key.length%4)%4));
    const applicationServerKey = Uint8Array.from(raw, c => c.charCodeAt(0));
    const subscription = await registration.pushManager.getSubscription() || await registration.pushManager.subscribe({userVisibleOnly:true, applicationServerKey});
    const result = await post('subscribe', {code, subscription:subscription.toJSON()});
    localStorage.setItem(storageKey, JSON.stringify(result));
    document.querySelector('#pair').value = '';
    refresh();
  } catch (error) { status.textContent = error.message || 'Could not enable call alerts.'; }
  finally { enable.disabled = false; }
});
disable.addEventListener('click', async () => {
  disable.disabled = true;
  try {
    const value = saved();
    if (value) await post('unsubscribe', value);
    const registration = await navigator.serviceWorker.getRegistration();
    const subscription = registration && await registration.pushManager.getSubscription();
    if (subscription) await subscription.unsubscribe();
    localStorage.removeItem(storageKey); refresh(); status.textContent = 'Call alerts disabled.';
  } catch (error) { status.textContent = error.message || 'Could not disable alerts. Check your connection and retry.'; }
  finally { disable.disabled = false; }
});
refresh();
