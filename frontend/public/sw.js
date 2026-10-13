// Minimal service worker so the app can be installed (desktop and mobile).
// It doesn't cache or intercept anything: attendance and points must always be live.
self.addEventListener("install", () => self.skipWaiting());
self.addEventListener("activate", (event) => event.waitUntil(self.clients.claim()));
