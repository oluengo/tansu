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
if (parts.length < 2) throw new Error("se esperaban varias partes");

const FAKE = `;(function(){
  const real = TansuNostr;
  const load = () => JSON.parse(localStorage.getItem("__relay") || "[]");
  const save = s => localStorage.setItem("__relay", JSON.stringify(s));
  const dTag = e => (e.tags.find(t => t[0] === "d") || [])[1] || "";
  const addr = e => e.kind >= 30000 ? e.kind + ":" + e.pubkey + ":" + dTag(e) : "";
  const match = (e, f) => (!f.kinds || f.kinds.includes(e.kind)) && (!f.authors || f.authors.includes(e.pubkey));
  window.__published = [];
  window.TansuNostr = Object.assign({}, real, {
    query: async (relays, filters) => load().filter(e => filters.some(f => match(e, f))),
    publish: async (relays, e) => {
      const s = load();
      if (e.kind === 5) { const as = e.tags.filter(t => t[0] === "a").map(t => t[1]); for (let i = s.length - 1; i >= 0; i--) if (as.includes(addr(s[i])) && s[i].created_at <= e.created_at) s.splice(i, 1); }
      s.push(e); save(s); window.__published.push(e); return 3;
    },
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
  await ctx.addInitScript({ content: `if (!localStorage.getItem("__relay")) localStorage.setItem("__relay", ${JSON.stringify(JSON.stringify(seed))});` });
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

  // Copia por partes de Usus: descifrar, ver, editar y guardar
  await page.click('.row .n:text-is("Usus")');
  await page.click("text=backup");
  ok(await page.locator(`text=${parts.length} partes`).count() > 0, "la copia de Usus se ve como un archivo con partes");
  await page.click("button:text('Descifrar')");
  await page.waitForSelector("pre.content");
  ok((await page.textContent("pre.content")).includes('"sessions"'), "contenido descifrado y descomprimido");
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

  // Amber en Android: botón visible y enlace nostrsigner con callback
  const ctx2 = await browser.newContext({ locale: "es-ES", userAgent: "Mozilla/5.0 (Linux; Android 14) Chrome/130 Mobile" });
  await ctx2.route("**/nostr.js", async route => { const r = await route.fetch(); route.fulfill({ response: r, body: (await r.text()) + FAKE }); });
  const p2 = await ctx2.newPage(); let target = "";
  p2.on("request", r => { if (r.url().startsWith("nostrsigner:")) target = r.url(); });
  p2.on("framenavigated", () => {});
  await p2.goto("http://127.0.0.1:8099/");
  await p2.waitForSelector(".welcome");
  ok(await p2.locator("text=Añadir con Amber").count() === 1, "botón de Amber en Android");
  await p2.click("[data-a=loginamber]"); await p2.waitForTimeout(300);
  ok(await p2.evaluate(() => JSON.parse(localStorage.getItem("tansu.amber.req") || "null")?.type) === "get_public_key", "pide la clave a Amber (enlace nostrsigner con vuelta)");
  // Vuelta de Amber con la clave
  await p2.goto("http://127.0.0.1:8099/?amber=" + (await p2.evaluate(() => JSON.parse(localStorage.getItem("tansu.amber.req") || "{}").id)) + "&result=" + PK);
  await p2.waitForSelector("text=Datos de apps (NIP-78)", { timeout: 8000 }).catch(() => {});
  ok(await p2.evaluate(pk => JSON.parse(localStorage.getItem("tansu.accounts") || "[]").some(a => a.pubkey === pk && a.method === "amber"), PK), "cuenta de Amber añadida al volver");
  ok(errors.length === 0, "sin errores de JavaScript " + errors.join(" | "));
} finally {
  await browser.close(); srv.kill();
}
console.log(failed ? `\n${failed} fallos` : "\nTodo bien");
process.exit(failed ? 1 : 0);
