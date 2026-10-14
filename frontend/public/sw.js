// Service worker: lets the app be installed (desktop and mobile) and shows push notifications.
// It doesn't cache or intercept requests: attendance and points must always be live.
self.addEventListener("install", () => self.skipWaiting());
self.addEventListener("activate", (event) => event.waitUntil(self.clients.claim()));

// A push from the server: { title, body, url, tag }. The browser requires every push to show something.
self.addEventListener("push", (event) => {
  let data = {};
  try {
    data = event.data ? event.data.json() : {};
  } catch {
    data = { body: event.data ? event.data.text() : "" };
  }
  event.waitUntil(
    self.registration.showNotification(data.title || "خدمة إعدادي", {
      body: data.body || "",
      icon: "/icon-192.png",
      dir: "rtl",
      lang: "ar",
      // The same tag replaces the earlier notification instead of stacking a new one.
      tag: data.tag,
      renotify: Boolean(data.tag),
      data: { url: data.url || "/" },
    }),
  );
});

// Tapping it opens the app on the right page, reusing a window that is already open.
self.addEventListener("notificationclick", (event) => {
  event.notification.close();
  const url = new URL(event.notification.data?.url || "/", self.location.origin).href;
  event.waitUntil(
    self.clients.matchAll({ type: "window", includeUncontrolled: true }).then((windows) => {
      for (const client of windows) {
        if ("focus" in client) {
          return client.focus().then((focused) => ("navigate" in focused ? focused.navigate(url) : undefined));
        }
      }
      return self.clients.openWindow(url);
    }),
  );
});
