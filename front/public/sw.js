self.addEventListener("install", () => {
  self.skipWaiting();
});

self.addEventListener("activate", (event) => {
  event.waitUntil(self.clients.claim());
});

self.addEventListener("fetch", () => {});

self.addEventListener("push", (event) => {
  if (!event.data) return;

  let payload;
  try {
    payload = event.data.json();
  } catch {
    payload = { notification: { body: event.data.text() } };
  }

  const notification = payload.notification ?? {};
  const data = payload.data ?? {};
  const title = notification.title ?? data.title ?? "TripLog";
  const targetUrl = data.targetUrl ?? "/";

  event.waitUntil(
    self.registration.showNotification(title, {
      body: notification.body ?? data.body ?? "",
      icon: "/icon-192.png",
      badge: "/icon-192.png",
      tag: data.postId
        ? `${data.type ?? "triplog-reminder"}-${data.postId}`
        : data.type ?? "triplog-reminder",
      data: { targetUrl },
    }),
  );
});

self.addEventListener("notificationclick", (event) => {
  event.notification.close();

  const targetUrl = event.notification.data?.targetUrl ?? "/";
  const parsedUrl = new URL(targetUrl, self.location.origin);
  const url = parsedUrl.origin === self.location.origin
    ? parsedUrl.href
    : self.location.origin;

  event.waitUntil(
    self.clients.matchAll({ type: "window", includeUncontrolled: true }).then((clientList) => {
      for (const client of clientList) {
        if ("focus" in client) {
          client.navigate(url);
          return client.focus();
        }
      }
      return self.clients.openWindow(url);
    }),
  );
});
