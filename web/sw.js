// PWA opcional: guarda la app para abrirla sin conexión. Los datos siempre se leen de los relays.
// Cambia la versión al actualizar para forzar la recarga de la caché.
const CACHE = "tansu-0.2.0";
const CORE = ["./", "index.html", "app.js", "app.css", "i18n.js", "nostr.js", "manifest.webmanifest", "icon.svg", "icon-192.png", "icon-512.png", "icon-maskable-512.png", "favicon-48.png"];

self.addEventListener("install", e => {
  e.waitUntil(caches.open(CACHE).then(c => c.addAll(CORE)).then(() => self.skipWaiting()));
});
// La beta (…/beta/) comparte dominio: cada una borra solo sus propias cachés y la principal no toca /beta/.
const FAM = CACHE.startsWith("tansu-beta-") ? "tansu-beta-" : "tansu-";
const mine = k => k.startsWith(FAM) && (FAM === "tansu-beta-" || !k.startsWith("tansu-beta-"));
self.addEventListener("activate", e => {
  e.waitUntil(caches.keys().then(ks => Promise.all(ks.filter(k => mine(k) && k !== CACHE).map(k => caches.delete(k)))).then(() => self.clients.claim()));
});
self.addEventListener("fetch", e => {
  const req = e.request;
  if (req.method !== "GET" || new URL(req.url).origin !== location.origin) return;
  if (FAM === "tansu-" && new URL(req.url).pathname.startsWith(new URL("beta/", self.registration.scope).pathname)) return;
  // Red primero (coge las actualizaciones); caché si no hay conexión.
  e.respondWith(fetch(req).then(r => {
    if (r.ok) { const c = r.clone(); caches.open(CACHE).then(x => x.put(req, c)); }
    return r;
  }).catch(() => caches.match(req).then(hit => hit || (req.mode === "navigate" ? caches.match("index.html") : undefined))));
});
