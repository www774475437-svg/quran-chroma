// خدمة بسيطة لتثبيت الموقع كتطبيق على الماك والجوال
const CACHE = "qc-v12";
const CORE = ["./", "index.html", "fonts/hafs.18.woff2", "icon-192.png", "icon-512.png", "manifest.webmanifest"];
self.addEventListener("install", e => { e.waitUntil(caches.open(CACHE).then(c => c.addAll(CORE)).catch(()=>{})); self.skipWaiting(); });
self.addEventListener("activate", e => { e.waitUntil(caches.keys().then(ks => Promise.all(ks.filter(k => k !== CACHE && k !== "qc-offline-v1").map(k => caches.delete(k))))); self.clients.claim(); });
self.addEventListener("fetch", e => {
  const u = new URL(e.request.url);
  if (e.request.method !== "GET" || u.origin !== location.origin) return;
  // الشبكة أولاً حتى تصل التحديثات، والنسخة المحفوظة عند انقطاع الإنترنت
  e.respondWith(fetch(e.request).then(r => { const cp = r.clone(); caches.open(CACHE).then(c => c.put(e.request, cp)).catch(()=>{}); return r; }).catch(() => caches.match(e.request)));
});
