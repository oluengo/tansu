// Screenshots of Tansu web with in-memory relays and sample data (English, phone size).
// Usage: node web/tests/screenshots.mjs <output-folder>  (needs web/lib/node_modules and Playwright)
import { chromium } from "/opt/npm-tools/node_modules/playwright/index.mjs";
import { finalizeEvent, getPublicKey } from "../lib/node_modules/nostr-tools/lib/esm/pure.js";
import * as nip44 from "../lib/node_modules/nostr-tools/lib/esm/nip44.js";
import { hexToBytes } from "../lib/node_modules/nostr-tools/lib/esm/utils.js";
import { readFileSync } from "node:fs";
import { spawn } from "node:child_process";
import { gzipSync } from "node:zlib";
import { fileURLToPath } from "node:url";
import path from "node:path";
const OUT = process.argv[2];
const WEB = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const SK = "7f".repeat(32), sk = hexToBytes(SK), PK = getPublicKey(sk);
const ck = nip44.v2.utils.getConversationKey(sk, PK);
const enc = t => nip44.v2.encrypt(t, ck);
const T0 = Math.floor(Date.now() / 1000);
const ev = (kind, tags, content, at = T0 - 3600) => finalizeEvent({ kind, created_at: at, tags, content }, sk);
const fav = { v: 3, folders: [
  { name: "Jazz", items: [{ type: "radio", name: "Jazz Radio Blues", url: "https://jazzblues.example/stream" }, { type: "radio", name: "Smooth Jazz 24/7", url: "https://smooth.example/live" }] },
  { name: "News", items: [{ type: "radio", name: "World News FM", url: "https://news.example/aac" }] },
  { name: "Streamers", items: [{ type: "streamer", name: "lofi beats", pubkey: "ab".repeat(32) }, { type: "streamer", name: "Bitcoin talk", pubkey: "cd".repeat(32) }] }
] };
const ususText = JSON.stringify({ app: "usus", profile: "Alex", sessions: Array.from({ length: 180 }, (_, i) => ({ day: `2026-${String(1 + (i % 11)).padStart(2, "0")}-${String(1 + (i % 27)).padStart(2, "0")}`, routine: ["Upper", "Lower", "Core"][i % 3], sets: 12 + (i % 6) })) });
const z = gzipSync(ususText).toString("base64");
const parts = []; for (let i = 0; i < z.length; i += 4000) parts.push(z.slice(i, i + 4000));
const seed = [
  ev(30078, [["d", "wavestr/favorites"]], enc(JSON.stringify(fav))),
  ev(30078, [["d", "wavestr/settings"]], enc(JSON.stringify({ theme: "dark", accent: "violet", buffer: 10, autoAudio: true }))),
  ...parts.map((p, i) => ev(30078, [["d", `usus/backup/${i}`]], enc(p))),
  ev(30078, [["d", "usus/backup"]], enc(JSON.stringify({ v: 1, parts: parts.length, len: z.length, app: "3.9.1", profile: "Alex" }))),
  ev(30078, [["d", "mado/settings"]], enc(JSON.stringify({ layout: "grid", theme: "ocean", font: "sans" }))),
  ev(10000, [["t", "spam"], ["p", "aa".repeat(32)]], enc(JSON.stringify([["word", "airdrop"], ["t", "giveaway"]]))),
  ev(10003, [["r", "https://nostr.com"]], enc(JSON.stringify([["e", "ee".repeat(32)], ["r", "https://github.com/nostr-protocol/nips"]]))),
];
const F1 = hexToBytes("11".repeat(32)), F2 = hexToBytes("22".repeat(32)), MON = hexToBytes("33".repeat(32));
seed.push(
  ev(3, [["p", getPublicKey(F1)], ["p", getPublicKey(F2)]], ""),
  finalizeEvent({ kind: 10002, created_at: T0, tags: [["r", "wss://relay.damus.io"], ["r", "wss://nos.lol/"]], content: "" }, F1),
  finalizeEvent({ kind: 10002, created_at: T0, tags: [["r", "wss://relay.damus.io"]], content: "" }, F2),
  finalizeEvent({ kind: 30166, created_at: T0 - 600, tags: [["d", "wss://relay.damus.io/"], ["rtt-open", "150"]], content: "" }, MON),
  finalizeEvent({ kind: 30166, created_at: T0 - 900, tags: [["d", "wss://nos.lol/"], ["rtt-open", "300"]], content: "" }, MON),
);
const FAKE = `;(function(){
  const real = TansuNostr; const DOWN = ["wss://relay.primal.net"];
  const load = () => JSON.parse(localStorage.getItem("__relays") || "{}"); const save = s => localStorage.setItem("__relays", JSON.stringify(s));
  const dTag = e => (e.tags.find(t => t[0] === "d") || [])[1] || "";
  const match = (e, f) => (!f.kinds || f.kinds.includes(e.kind)) && (!f.authors || f.authors.includes(e.pubkey));
  const at = (s, url) => (s[url] ||= JSON.parse(localStorage.getItem("__seed") || "[]").filter(e => !(url === "wss://nos.lol" && dTag(e) === "wavestr/favorites")));
  window.TansuNostr = Object.assign({}, real, {
    query: async (relays, filters) => { const s = load(), m = new Map(); for (const u of relays) if (!DOWN.includes(u)) for (const e of at(s, u)) if (filters.some(f => match(e, f))) m.set(e.id, e); save(s); return [...m.values()]; },
    queryEach: async (relays, filters) => { const s = load(); const r = relays.map(u => DOWN.includes(u) ? { url: u, reached: false, events: [] } : { url: u, reached: true, events: at(s, u).filter(e => filters.some(f => match(e, f))) }); save(s); return r; },
    publish: async () => 0,
    relayInfo: async u => u === "wss://relay.damus.io" ? real.parseRelayInfo({ name: "Damus", supported_nips: [1, 9, 11, 42], software: "git+https://github.com/x/strfry", version: "1.0", limitation: { payment_required: false } })
      : u === "wss://nos.lol" ? real.parseRelayInfo({ name: "nos.lol", supported_nips: [1, 9, 11], limitation: { payment_required: false } }) : null,
  });
})();`;
const srv = spawn("python3", ["-m", "http.server", "8099", "--bind", "127.0.0.1"], { cwd: WEB, stdio: "ignore" });
await new Promise(r => setTimeout(r, 800));
const browser = await chromium.launch();
const idle = async pg => { await pg.waitForTimeout(200); await pg.waitForSelector("#busy", { state: "detached", timeout: 15000 }).catch(() => {}); };
try {
  const ctx = await browser.newContext({ locale: "en-US", viewport: { width: 360, height: 780 }, deviceScaleFactor: 1.5, isMobile: true, hasTouch: true });
  await ctx.addInitScript({ content: readFileSync(path.join(WEB, "tests/mock.js"), "utf8") + `;window.nostr = Mock.makeExt("${SK}");` });
  await ctx.addInitScript({ content: `if (!localStorage.getItem("__seed")) localStorage.setItem("__seed", ${JSON.stringify(JSON.stringify(seed))});` });
  await ctx.route("**/nostr.js", async route => { const r = await route.fetch(); route.fulfill({ response: r, body: (await r.text()) + FAKE }); });
  const page = await ctx.newPage(); page.on("pageerror", e => console.log("ERR", e.message));
  const shot = async n => { await page.waitForTimeout(400); await page.screenshot({ path: `${OUT}/${n}.png` }); console.log("shot", n); };
  await page.goto("http://127.0.0.1:8099/");
  await page.waitForSelector(".welcome"); await shot("00-welcome");
  await page.click("text=Add with browser extension (NIP-07)");
  await page.waitForSelector("text=App data (NIP-78)", { timeout: 8000 }).catch(() => {});
  await idle(page); await shot("01-home");
  await page.click('.row .n:text-is("Wavestr")'); await idle(page); await shot("02-folder");
  await page.click("text=favorites"); await idle(page); await shot("03-item-relays");
  await page.click("button:text('Decrypt')").catch(e => console.log("no decrypt btn")); await idle(page); await page.waitForTimeout(500); await shot("04-visual");
  await page.goBack(); await page.goBack(); await idle(page);
  await page.click("button:text('3 relays')").catch(e => console.log("no relays btn")); await page.waitForSelector(".rcard").catch(() => {}); await page.waitForTimeout(600); await shot("05-relays");
  await page.goBack(); await idle(page);
  await page.click('.row .n:text-is("Lists")').catch(e => console.log("no lists")); await idle(page); await shot("06-lists");
} finally { await browser.close(); srv.kill(); }
