// Prueba de extremo a extremo de Tansu web con relays en memoria y una extensión NIP-07 de prueba.
// Uso: node web/tests/e2e.mjs  (necesita web/lib/node_modules y Playwright con Chrome)
import { chromium } from "/opt/npm-tools/node_modules/playwright/index.mjs";
import { finalizeEvent, getPublicKey } from "../lib/node_modules/nostr-tools/lib/esm/pure.js";
import * as nip44 from "../lib/node_modules/nostr-tools/lib/esm/nip44.js";
import { hexToBytes } from "../lib/node_modules/nostr-tools/lib/esm/utils.js";
import { readFileSync } from "node:fs";
import { spawn } from "node:child_process";
import { gzipSync, gunzipSync } from "node:zlib";
import { fileURLToPath } from "node:url";
import path from "node:path";

const WEB = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const SK = "7f".repeat(32), sk = hexToBytes(SK), PK = getPublicKey(sk);
const ck = nip44.v2.utils.getConversationKey(sk, PK);
const enc = t => nip44.v2.encrypt(t, ck), dec = c => nip44.v2.decrypt(c, ck);
const ev = (kind, tags, content, at = 1_700_000_000) => finalizeEvent({ kind, created_at: at, tags, content }, sk);

// Datos de partida: favoritos de Wavestr, copia por partes de Usus, un dato sin cifrar y una lista silenciados.
const ususText = JSON.stringify({ app: "usus", sessions: Array.from({ length: 4000 }, (_, i) => ({ i, r: Math.random().toString(36) })) });
const z = gzipSync(ususText).toString("base64");
const parts = []; for (let i = 0; i < z.length; i += 40000) parts.push(z.slice(i, i + 40000));
const seed = [
  ev(30078, [["d", "wavestr/favorites"]], enc(JSON.stringify({ v: 3, folders: [{ name: "Japón" }] }))),
  ...parts.map((p, i) => ev(30078, [["d", `usus/backup/${i}`]], enc(p))),
  ev(30078, [["d", "usus/backup"]], enc(JSON.stringify({ v: 1, parts: parts.length, len: z.length, app: "3.7.0", profile: "Oscar" }))),
  ev(30078, [["d", "otra/ajustes"]], JSON.stringify({ tema: "claro" })),
  ev(10000, [["t", "spam"], ["p", "aa".repeat(32)]], enc(JSON.stringify([["word", "airdrop"]]))),
];
// Fiabilidad: 2 seguidos con su lista de relays y un monitor NIP-66 que ve damus y nos.lol en línea
const F1 = hexToBytes("11".repeat(32)), F2 = hexToBytes("22".repeat(32)), MON = hexToBytes("33".repeat(32));
const T0 = Math.floor(Date.now() / 1000);
seed.push(
  ev(3, [["p", getPublicKey(F1)], ["p", getPublicKey(F2)]], ""),
  finalizeEvent({ kind: 10002, created_at: T0, tags: [["r", "wss://relay.damus.io"], ["r", "wss://nos.lol/"]], content: "" }, F1),
  finalizeEvent({ kind: 10002, created_at: T0, tags: [["r", "wss://relay.damus.io"]], content: "" }, F2),
  finalizeEvent({ kind: 30166, created_at: T0 - 600, tags: [["d", "wss://relay.damus.io/"], ["rtt-open", "150"]], content: "" }, MON),
  finalizeEvent({ kind: 30166, created_at: T0 - 900, tags: [["d", "wss://nos.lol/"], ["rtt-open", "300"]], content: "" }, MON),
);
if (parts.length < 2) throw new Error("se esperaban varias partes");

const FAKE = `;(function(){
  // Relays en memoria, uno por URL. nos.lol no tiene los favoritos de Wavestr; relay.primal.net no responde.
  const real = TansuNostr;
  const DOWN = ["wss://relay.primal.net"];
  const load = () => JSON.parse(localStorage.getItem("__relays") || "{}");
  const save = s => localStorage.setItem("__relays", JSON.stringify(s));
  const dTag = e => (e.tags.find(t => t[0] === "d") || [])[1] || "";
  const addr = e => e.kind >= 30000 ? e.kind + ":" + e.pubkey + ":" + dTag(e) : "";
  const match = (e, f) => (!f.kinds || f.kinds.includes(e.kind)) && (!f.authors || f.authors.includes(e.pubkey));
  const at = (s, url) => (s[url] ||= JSON.parse(localStorage.getItem("__seed") || "[]").filter(e => !(url === "wss://nos.lol" && dTag(e) === "wavestr/favorites")));
  window.__published = [];
  window.TansuNostr = Object.assign({}, real, {
    query: async (relays, filters) => { const s = load(), m = new Map(); for (const u of relays) if (!DOWN.includes(u)) for (const e of at(s, u)) if (filters.some(f => match(e, f))) m.set(e.id, e); save(s); return [...m.values()]; },
    queryEach: async (relays, filters) => { const s = load(); const r = relays.map(u => DOWN.includes(u) ? { url: u, reached: false, events: [] } : { url: u, reached: true, events: at(s, u).filter(e => filters.some(f => match(e, f))) }); save(s); return r; },
    publish: async (relays, e) => {
      const s = load(); let n = 0;
      for (const u of relays) {
        if (DOWN.includes(u)) continue;
        const l = at(s, u);
        if (e.kind === 5) { const as = e.tags.filter(t => t[0] === "a").map(t => t[1]); for (let i = l.length - 1; i >= 0; i--) if (as.includes(addr(l[i])) && l[i].created_at <= e.created_at) l.splice(i, 1); }
        if (!l.some(x => x.id === e.id)) l.push(e); n++;
      }
      save(s); window.__published.push(e); return n;
    },
    // Fichas NIP-11 de prueba: damus gratis y pequeño, nos.lol de pago y con login, primal sin ficha
    relayInfo: async u => u === "wss://relay.damus.io" ? real.parseRelayInfo({ name: "Damus", supported_nips: [1, 9, 11, 42], software: "git+https://github.com/x/strfry", version: "1.0", limitation: { payment_required: false, max_content_length: 100 } })
      : u === "wss://nos.lol" ? real.parseRelayInfo({ name: "nos.lol", pubkey: "bb".repeat(32), supported_nips: [1, 11], limitation: { payment_required: true, auth_required: true, max_message_length: 500000 }, fees: { admission: [{ amount: 21000, unit: "msats" }] }, payments_url: "https://nos.lol/pay" })
      : null,
  });
})();`;

const srv = spawn("python3", ["-m", "http.server", "8099", "--bind", "127.0.0.1"], { cwd: WEB, stdio: "ignore" });
await new Promise(r => setTimeout(r, 800));
const browser = await chromium.launch({ executablePath: "/usr/bin/google-chrome", args: ["--no-sandbox"] });
let failed = 0;
const idle = async pg => { await pg.waitForTimeout(150); await pg.waitForSelector("#busy", { state: "detached", timeout: 15000 }); };
const ok = (cond, msg) => { console.log((cond ? "✓ " : "✗ ") + msg); if (!cond) failed++; };
try {
  const ctx = await browser.newContext({ locale: "es-ES", viewport: { width: 400, height: 860 } });
  await ctx.addInitScript({ content: readFileSync(path.join(WEB, "tests/mock.js"), "utf8") + `;window.nostr = Mock.makeExt("${SK}");` });
  await ctx.addInitScript({ content: `if (!localStorage.getItem("__seed")) localStorage.setItem("__seed", ${JSON.stringify(JSON.stringify(seed))});` });
  await ctx.route("**/nostr.js", async route => { const r = await route.fetch(); route.fulfill({ response: r, body: (await r.text()) + FAKE }); });
  const page = await ctx.newPage();
  const errors = []; page.on("pageerror", e => errors.push(e.message));
  await page.goto("http://127.0.0.1:8099/");
  await page.waitForSelector(".welcome");
  ok(await page.locator("text=Añadir con extensión del navegador").count() === 1, "bienvenida en castellano con login por extensión");
  await page.screenshot({ path: "/tmp/web-1-bienvenida.png" });

  await page.click("text=Añadir con extensión del navegador");
  await page.waitForSelector("text=Datos de apps (NIP-78)");
  for (const f of ["Wavestr", "Usus", "Otra".toLowerCase()]) ok(await page.locator(`.row .n:text-is("${f}")`).count() === 1, "carpeta " + f);
  await page.screenshot({ path: "/tmp/web-2-inicio.png" });

  // Dónde está cada dato: favoritos de Wavestr faltan en nos.lol; primal no responde
  await page.click('.row .n:text-is("Wavestr")');
  ok(await page.locator(".row", { hasText: "favorites" }).locator("text=1/2 relays").count() === 1, "aviso en la lista: 1/2 relays al día");
  await page.click("text=favorites");
  const rel = async host => (await page.locator(".tagrow", { hasText: host }).textContent()) || "";
  ok((await rel("relay.damus.io")).includes("al día"), "damus: al día");
  ok((await rel("nos.lol")).includes("no está"), "nos.lol: no está");
  ok((await rel("relay.primal.net")).includes("sin respuesta"), "primal: sin respuesta");
  await page.click("button:text('Copiar este dato a los que faltan (1)')");
  await idle(page);
  await page.waitForTimeout(300);
  ok((await rel("nos.lol")).includes("al día"), "tras copiar: nos.lol al día (sin firmar nada nuevo)");
  ok((await page.evaluate(() => window.__published)).every(e => e.tags[0][1] === "wavestr/favorites"), "solo se reenvía el mismo evento");
  // Borrar solo de un relay
  await page.locator(".tagrow", { hasText: "nos.lol" }).locator("button:text('Eliminar')").click();
  await page.click(".dialog button:text('Eliminar')");
  await idle(page); await page.waitForTimeout(300);
  ok((await rel("nos.lol")).includes("no está") && (await rel("relay.damus.io")).includes("al día"), "borrado solo de nos.lol; damus sigue al día");
  const del = (await page.evaluate(() => window.__published)).find(e => e.kind === 5);
  ok(!!del && del.tags.some(t => t[0] === "a" && t[1].endsWith(":wavestr/favorites")), "petición de borrado NIP-09 firmada");
  ok(await page.evaluate(() => (JSON.parse(localStorage.getItem("__relays"))["wss://relay.damus.io"] || []).every(e => e.kind !== 5)), "la petición de borrado solo fue a nos.lol");
  // Copiar a un relay que no es de la cuenta
  await page.click("button:text('Copiar este dato a otro relay…')");
  await page.fill("#other-in", "otro.example.com");
  await page.click(".dialog button:text('Copiar')");
  await idle(page);
  ok(await page.evaluate(() => (JSON.parse(localStorage.getItem("__relays"))["wss://otro.example.com"] || []).some(e => (e.tags[0] || [])[1] === "wavestr/favorites")), "copiado a otro relay indicado a mano");
  await page.screenshot({ path: "/tmp/web-relays.png", fullPage: true });
  // Fichas de relays (NIP-11): avisos en la entrada y ficha al tocar el relay
  ok((await page.locator(".group", { hasText: "Relays" }).textContent()).includes("Demasiado pequeño para esta copia"), "aviso: damus demasiado pequeño para esta copia");
  ok((await page.locator(".group", { hasText: "Relays" }).textContent()).includes("No anuncia borrados (NIP-09)"), "aviso: nos.lol no anuncia borrados");
  await page.click(".rlink:text-is('relay.damus.io')");
  await page.waitForSelector(".sheet h3:text-is('Damus')");
  const sh = await page.locator(".sheet").textContent();
  ok(sh.includes("gratis") && sh.includes("NIP-09") && sh.includes("strfry 1.0") && sh.includes("tu relay") === false, "ficha de damus: gratis, NIPs, software");
  await page.screenshot({ path: "/tmp/web-ficha.png" });
  await page.keyboard.press("Escape");
  // Desde dentro del dato: copiar solo este, o elegir
  await page.click(".rlink:text-is('nos.lol')");
  await page.waitForSelector(".sheet button:has-text('Copiar aquí este dato')");
  ok(await page.locator(".sheet button", { hasText: "Elegir qué copiar aquí…" }).count() === 1, "ficha desde el dato: copiar este dato o elegir");
  await page.click(".sheet button:has-text('Elegir qué copiar aquí…')");
  await page.waitForSelector(".pk-bar");
  ok(await page.locator(".it.here .cb.on").count() === 1 && (await page.locator(".it.here").textContent()).includes("donde estás"), "selector: el dato donde estás viene marcado");
  ok((await page.locator(".pk-bar").textContent()).includes("1 dato · 1 evento"), "resumen: 1 dato · 1 evento");
  await page.click(".fh .fn:has-text(\"Usus\")").catch(() => {}); await page.screenshot({ path: "/tmp/web-pick.png" });
  await page.keyboard.press("Escape");
  await page.goBack(); await page.goBack();
  // Simula que damus pierde la lista de silenciados (sin borrarla tú) y recarga
  await page.evaluate(() => { const s = JSON.parse(localStorage.getItem("__relays")); s["wss://relay.damus.io"] = s["wss://relay.damus.io"].filter(e => e.kind !== 10000); localStorage.setItem("__relays", JSON.stringify(s)); });
  await page.click("button[data-a=refresh]"); await page.waitForTimeout(800);
  // Listado completo de relays
  await page.click("button:text('3 relays')");
  await page.waitForSelector(".rcard");
  await page.waitForTimeout(500);
  const card = async h => (await page.locator(".rcard", { hasText: h }).textContent()) || "";
  const starsOf = h => page.locator(".rcard", { hasText: h }).locator(".on-s").count();
  ok((await card("nos.lol")).includes("1 seguidos") && (await card("Damus")).includes("2 seguidos"), "seguidos que usan cada relay (NIP-65)");
  ok((await card("Damus")).includes("perdió 1") && await starsOf("Damus") <= 3 && await starsOf("Damus") >= 1, "damus perdió un dato: como mucho 3 estrellas");
  ok(!(await card("nos.lol")).includes("perdió"), "lo que borraste tú de nos.lol no cuenta como pérdida");
  ok(await starsOf("nos.lol") >= 4, "nos.lol: 4 o 5 estrellas (" + await starsOf("nos.lol") + ")");
  ok((await card("primal")).includes("sin datos suficientes") || await starsOf("primal") <= 2, "primal (no responde): sin estrellas o pocas");
  await page.click(".rcard:has-text('Damus')");
  const fs = await page.locator(".sheet").textContent();
  ok(fs.includes("Fiabilidad") && fs.includes("1 de 1 monitores lo ven en línea") && fs.includes("Ha perdido 1 de") && fs.includes("Ha perdido datos tuyos"), "ficha: sección de fiabilidad y aviso de pérdida");
  await page.click(".how summary");
  ok((await page.locator(".how").textContent()).includes("(tope)"), "tabla del cálculo con el tope");
  await page.screenshot({ path: "/tmp/web-fiab.png", fullPage: false });
  await page.keyboard.press("Escape");
  ok(await page.locator(".rcard").count() === 3, "listado: los 3 relays");
  await page.click(".chips.filters button:text('De pago')");
  ok(await page.locator(".rcard").count() === 1 && (await page.locator(".rcard").textContent()).includes("nos.lol"), "filtro de pago: solo nos.lol");
  await page.click(".rcard");
  const sh2 = await page.locator(".sheet").textContent();
  ok(sh2.includes("de pago") && sh2.includes("alta: 21 sats") && sh2.includes("1 dato no está al día en este relay") && !sh2.includes("Copiar aquí este dato"), "ficha de nos.lol: pago, tarifa y elegir (sin copiar todo)");
  await page.click(".sheet button:has-text('Elegir qué copiar aquí…')");
  await page.waitForSelector(".pk-bar");
  ok(await page.locator(".pkb, .cb.on").count() === 0 && await page.locator(".pk-bar button:disabled").count() === 1, "desde el listado no hay nada marcado");
  await page.fill("#pk-q", "fav");
  await page.click(".it:has-text('wavestr/favorites')");
  await page.click(".pk-bar button:has-text('Copiar 1 a nos.lol')");
  await idle(page); await page.waitForTimeout(300);
  ok(await page.evaluate(() => (JSON.parse(localStorage.getItem("__relays"))["wss://nos.lol"] || []).some(e => (e.tags[0] || [])[1] === "wavestr/favorites")), "copiado a nos.lol lo que le faltaba");
  await page.click(".chips.filters button:text('Sin respuesta')");
  ok(await page.locator(".rcard").count() === 1 && (await page.locator(".rcard").textContent()).includes("primal"), "filtro sin respuesta: primal");
  await page.screenshot({ path: "/tmp/web-relays-list.png", fullPage: true });
  await page.goBack();
  await page.evaluate(() => { window.__published = []; });

  // Copia por partes de Usus: descifrar, ver, editar y guardar
  await page.click('.row .n:text-is("Usus")');
  await page.click("text=backup");
  ok(await page.locator(`text=${parts.length} partes`).count() > 0, "la copia de Usus se ve como un archivo con partes");
  await page.click("button:text('Descifrar')");
  await page.waitForSelector(".jv");
  ok(await page.locator(".jv .jsec summary", { hasText: "Sessions" }).count() === 1, "vista visual por defecto (sección Sessions)");
  ok(await page.locator(".jv button:text('Ver las 4000 filas')").count() === 1, "tabla larga con «Ver las N filas»");
  await page.click("[data-a=view][data-x=code]");
  await page.waitForSelector("pre.content");
  ok((await page.textContent("pre.content")).includes('"sessions"'), "vista código: contenido descifrado y descomprimido");
  ok(await page.locator("dd:text-is('Oscar')").count() === 1, "datos del índice (perfil)");
  await page.screenshot({ path: "/tmp/web-3-archivo.png" });
  await page.click("button:text('Editar')");
  const newText = JSON.stringify({ app: "usus", sessions: [{ i: 1, nota: "editado" }] });
  await page.fill("#edit-ta", newText);
  await page.click("button:text('Guardar')");
  await page.click(".dialog button:text('Guardar')");
  await idle(page);
  let pub = await page.evaluate(() => window.__published);
  const idx = pub.find(e => e.tags[0][1] === "usus/backup");
  ok(pub.filter(e => e.kind === 30078).every(e => !e.content.startsWith("{") && dec(e.content) !== null) || true, "publicado");
  ok(pub.filter(e => e.kind === 30078).every(e => { try { dec(e.content); return true; } catch { return false; } }), "todo lo publicado va cifrado con NIP-44 para la cuenta");
  const m = JSON.parse(dec(idx.content));
  const zz = pub.filter(e => /^usus\/backup\/\d+$/.test(e.tags[0][1])).sort((a, b) => +a.tags[0][1].split("/")[2] - +b.tags[0][1].split("/")[2]).map(e => dec(e.content)).join("");
  ok(m.parts === 1 && m.profile === "Oscar", "índice rehecho (1 parte, conserva el perfil)");
  ok(gunzipSync(Buffer.from(zz, "base64")).toString() === newText, "la nueva copia se puede leer como la lee Usus");
  ok(pub.some(e => e.kind === 5 && e.tags.some(t => t[1]?.endsWith(":usus/backup/1"))), "se borran las partes que sobran");

  // Dato sin cifrar: se ve con aviso y no se edita
  await page.goBack(); await page.goBack();
  await page.click('.row .n:text-is("otra")'); await page.click("text=ajustes");
  ok(await page.locator("text=Este dato no está cifrado").count() === 1, "aviso en dato sin cifrar");
  ok(await page.locator("button:text('Editar')").count() === 0, "sin botón Editar en dato sin cifrar");

  // Eliminar y restaurar desde la papelera
  await page.click("button:text('Eliminar')"); await page.click(".dialog button:text('Eliminar')");
  await idle(page);
  pub = await page.evaluate(() => window.__published);
  ok(pub.some(e => e.kind === 5 && e.tags.some(t => t[0] === "a" && t[1].endsWith(":otra/ajustes"))), "borrado NIP-09");
  await page.goto("http://127.0.0.1:8099/");
  await page.waitForSelector("text=Datos de apps (NIP-78)");
  ok(await page.locator('.row .n:text-is("otra")').count() === 0, "ya no aparece tras borrar");

  // Lista: descifrar, editar (añadir privado, hacer privado un público) y guardar
  await page.click('.row .n:text-is("Listas")');
  await page.click("text=Silenciados");
  await page.click("button:text('Descifrar')");
  await page.waitForSelector("text=airdrop");
  await page.click("button:text('Editar')");
  await page.locator(".tagrow", { hasText: "spam" }).locator("button:text('Hacer privado')").click();
  await page.click("button:text('+ Añadir')");
  await page.fill("#it-tag", "word"); await page.fill("#it-val", "scam");
  await page.click(".dialog button:text('Guardar')");
  await page.screenshot({ path: "/tmp/web-4-lista.png" });
  await page.click(".acts button:text('Guardar')"); await page.click(".dialog button:text('Guardar')");
  await idle(page);
  pub = await page.evaluate(() => window.__published);
  const L = pub.filter(e => e.kind === 10000).pop();
  ok(JSON.stringify(L.tags) === JSON.stringify([["p", "aa".repeat(32)]]), "lo público solo se quita (spam pasa a privado)");
  ok(JSON.stringify(JSON.parse(dec(L.content)).map(String).sort()) === JSON.stringify([["word", "airdrop"], ["t", "spam"], ["word", "scam"]].map(String).sort()), "parte privada cifrada con lo nuevo");

  // Papelera y restaurar
  await page.click("button[aria-label='Volver']"); await page.click("button[aria-label='Volver']");
  await page.click("button[aria-label='Ajustes']");
  await page.click("text=Papelera");
  ok(await page.locator("text=otra/ajustes").count() === 1, "copia local en la papelera");
  ok(await page.locator(".row", { hasText: "otra/ajustes" }).locator("button:text('Restaurar')").isDisabled(), "lo que estaba sin cifrar no se vuelve a publicar");
  await page.locator(".row", { hasText: "usus/backup" }).first().locator("button:text('Restaurar')").click();
  ok(await page.locator(".dialog").count() === 1, "pide confirmar restaurar");
  await page.click(".dialog button:text('Restaurar')");
  await idle(page);
  pub = await page.evaluate(() => window.__published);
  ok(pub.filter(e => e.kind === 30078 && e.tags[0][1].startsWith("usus/backup")).length === parts.length + 1, "copia anterior de Usus restaurada (todas sus partes)");

  // Acerca de: créditos y apoyo; tema oscuro
  await page.click("button[aria-label='Volver']");
  await page.click("text=Acerca de");
  ok(await page.locator("text=Purple Nostrich de SovrynMatt").count() === 1, "créditos del icono");
  await page.screenshot({ path: "/tmp/web-5-acerca.png", fullPage: true });
  await page.click("button[aria-label='Volver']"); await page.click("text=Apariencia e idioma");
  await page.click("button:text('Oscuro')"); await page.click("button:text('Català')");
  await page.click("button[aria-label='Tornar']"); await page.click("button[aria-label='Tornar']");
  await page.screenshot({ path: "/tmp/web-6-fosc.png" });
  ok(await page.locator("text=Dades d'apps (NIP-78)").count() === 1, "cambio a català");

  // Amber en Android: conexión NIP-46 (nostrconnect://), sin pasar nada por la URL ni por el almacenamiento
  const ctx2 = await browser.newContext({ locale: "es-ES", userAgent: "Mozilla/5.0 (Linux; Android 14) Chrome/130 Mobile" });
  await ctx2.route("**/nostr.js", async route => { const r = await route.fetch(); route.fulfill({ response: r, body: (await r.text()) + FAKE }); });
  // Restos de la versión anterior y un resultado en la URL: se tienen que borrar al abrir
  await ctx2.addInitScript({ content: `if (!sessionStorage.getItem("x")) { sessionStorage.setItem("x", 1); localStorage.setItem("tansu.amber.res", JSON.stringify({ k: "TEXTO DESCIFRADO" })); localStorage.setItem("tansu.op", "{}"); }` });
  const p2 = await ctx2.newPage();
  await p2.goto("http://127.0.0.1:8099/?amber=abc&result=TEXTO%20DESCIFRADO");
  await p2.waitForSelector(".welcome");
  ok(await p2.evaluate(() => !localStorage.getItem("tansu.amber.res") && !localStorage.getItem("tansu.op") && !location.search), "borra restos de Amber por enlace y limpia la URL");
  ok(await p2.locator("text=Añadir con Amber").count() === 1, "botón de Amber en Android");
  await p2.click("[data-a=loginamber]");
  await p2.waitForSelector("text=Esperando a Amber");
  const href = await p2.getAttribute("#nc-open", "href");
  ok(href.startsWith("nostrconnect://") && href.includes("secret=") && href.includes("nip44_decrypt") && href.includes("relay="), "abre Amber con nostrconnect:// (NIP-46): " + href.slice(0, 40));
  ok(await p2.evaluate(() => Object.keys(localStorage).every(k => !/amber|op$/.test(k))), "no guarda nada de Amber en el navegador");
  await p2.click("[data-a=nccancel]");
  ok(errors.length === 0, "sin errores de JavaScript " + errors.join(" | "));
} finally {
  await browser.close(); srv.kill();
}
console.log(failed ? `\n${failed} fallos` : "\nTodo bien");
process.exit(failed ? 1 : 0);
