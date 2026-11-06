'use strict';
const id = new URLSearchParams(location.hash.slice(1)).get('call') || '';
const valid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(id);
document.querySelector('#open').href = valid ? `deenly://family-call?callId=${encodeURIComponent(id)}` : 'deenly://family-call';
// Keep a visible user-initiated link: browsers may require confirmation before opening a native app.
