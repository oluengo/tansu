package io.github.oluengo.tansu.data

import io.github.oluengo.tansu.crypto.Nip04
import io.github.oluengo.tansu.crypto.Nip44
import io.github.oluengo.tansu.crypto.Secp256k1
import io.github.oluengo.tansu.nostr.Event
import io.github.oluengo.tansu.nostr.EventSigning
import io.github.oluengo.tansu.nostr.Kinds
import io.github.oluengo.tansu.nostr.NostrJson
import io.github.oluengo.tansu.nostr.RelayPool
import io.github.oluengo.tansu.nostr.hexToBytes
import io.github.oluengo.tansu.nostr.toHex
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Cripto local con una clave de prueba (en la app la ponen Amber o el bunker). */
private class LocalCrypto(private val sk: ByteArray) : AccountCrypto {
    override val pubkey: String = Secp256k1.pubkey(sk).toHex()
    private fun ck(peer: String) = Nip44.conversationKey(sk, peer.hexToBytes())
    override suspend fun sign(unsigned: Event) = EventSigning.sign(unsigned.kind, unsigned.tags, unsigned.content, sk, unsigned.createdAt)
    override suspend fun nip44Encrypt(plain: String, peer: String) = Nip44.encrypt(plain, ck(peer))
    override suspend fun nip44Decrypt(cipher: String, peer: String) = Nip44.decrypt(cipher, ck(peer))
    override suspend fun nip04Decrypt(cipher: String, peer: String) = Nip04.decrypt(cipher, sk, peer.hexToBytes())
}

class AppDataTest {
    private val sk = ByteArray(32) { (it + 7).toByte() }
    private val crypto = LocalCrypto(sk)
    private val vault = Vault(RelayPool(okhttp3.OkHttpClient()))

    private fun ev(d: String, content: String, at: Long = 100) =
        EventSigning.sign(Kinds.APP_DATA, listOf(listOf("d", d)), content, sk, at)

    @Test fun agrupaPorAppYUnePartes() {
        val list = AppData.group(
            listOf(
                ev("wavestr/favorites", "{}"), ev("usus/backup", "{}"), ev("usus/backup/0", "a"), ev("usus/backup/1", "b"),
                ev("ajustes", "{}"), ev("usus/backup", "{}", at = 50), ev("otra/3", "x"),
            )
        )
        assertEquals(listOf("otra/3", "usus/backup", "wavestr/favorites", "ajustes"), list.map { it.d })
        val usus = list.first { it.d == "usus/backup" }
        assertTrue(usus.chunked)
        assertEquals(listOf("usus/backup/0", "usus/backup/1"), usus.parts.map { it.tag("d") })
        assertEquals(100, usus.main.createdAt)
        assertEquals("favorites", list.first { it.app == "wavestr" }.name)
        assertEquals("", list.last().app)
        // «otra/3» no tiene índice «otra»: es una entrada normal de la app «otra».
        assertFalse(list.first().chunked)
    }

    @Test fun detectaElCifrado() {
        val nip44 = Nip44.encrypt("hola", Nip44.conversationKey(sk, crypto.pubkey.hexToBytes()))
        assertEquals(Encoding.NIP44, detectEncoding(nip44))
        assertEquals(Encoding.NIP04, detectEncoding(Nip04.encrypt("hola", sk, crypto.pubkey.hexToBytes())))
        assertEquals(Encoding.PLAIN, detectEncoding("{\"a\":1}"))
        assertEquals(Encoding.EMPTY, detectEncoding(" "))
    }

    @Test fun gzipYPartes() {
        val text = (1..5000).joinToString(",") { "\"ejercicio $it\"" }
        val z = AppData.gzipB64(text)
        assertEquals(text, AppData.gunzipB64(z))
        val parts = AppData.chunks("x".repeat(90_001))
        assertEquals(listOf(40_000, 40_000, 10_001), parts.map { it.length })
    }

    @Test fun copiaPorPartesIdaYVuelta() = runTest {
        // Contenido grande y poco comprimible para que salgan varias partes.
        val rnd = java.util.Random(1)
        val text = "{\"datos\":\"" + (1..120_000).map { "abcdefghijklmnopqrstuvwxyz0123456789"[rnd.nextInt(36)] }.joinToString("") + "\"}"
        val manifest = buildJsonObject { put("v", 1); put("parts", 1); put("app", "3.7.0"); put("profile", "Oscar") }
        val (writes, extra) = vault.plan("usus/backup", text, manifest, oldParts = 5)
        assertEquals("usus/backup", writes.last().first)
        val n = writes.size - 1
        assertTrue(n >= 2)
        assertEquals((n until 5).map { "usus/backup/$it" }, extra)
        // Se cifra y firma como lo haría la app, y se vuelve a abrir.
        val events = writes.map { (d, plain) -> ev(d, crypto.nip44Encrypt(plain, crypto.pubkey)) }
        val entry = AppData.group(events).single()
        assertEquals(n, entry.parts.size)
        val opened = vault.open(entry, crypto)
        assertEquals(text, opened.text)
        assertEquals("Oscar", opened.manifest!!["profile"]!!.jsonPrimitive.content)
        assertEquals(n.toString(), opened.manifest!!["parts"]!!.jsonPrimitive.content)
    }

    @Test fun entradaSimpleSinPartes() = runTest {
        val (writes, extra) = vault.plan("wavestr/favorites", "{\"v\":3}", null, oldParts = 0)
        assertEquals(listOf("wavestr/favorites" to "{\"v\":3}"), writes)
        assertTrue(extra.isEmpty())
        val e = AppData.group(listOf(ev("wavestr/favorites", crypto.nip44Encrypt("{\"v\":3}", crypto.pubkey)))).single()
        val o = vault.open(e, crypto)
        assertTrue(o.isJson)
        assertNull(o.manifest)
    }

    @Test fun etiquetasDeBorrado() {
        val e = AppData.group(listOf(ev("usus/backup", "{}"), ev("usus/backup/0", "a"))).single()
        val tags = AppData.deletionTags(e.events)
        assertEquals(5, tags.size)
        assertTrue(tags.contains(listOf("a", "30078:${crypto.pubkey}:usus/backup/0")))
        assertEquals(listOf("k", "30078"), tags.last())
    }

    @Test fun listasPrivadas() {
        assertEquals(listOf(listOf("p", "abc"), listOf("t", "nostr")), Lists.parsePrivate("[[\"p\",\"abc\"],[\"t\",\"nostr\"],[\"x\"]]"))
        assertNull(Lists.parsePrivate("no"))
    }

    @Test fun papelera() {
        val f = File.createTempFile("trash", ".json").also { it.delete() }
        val t = Trash(f)
        val e = AppData.group(listOf(ev("wavestr/favorites", "x"))).single()
        val item = t.add(crypto.pubkey, e)
        assertEquals(1, Trash(f).of(crypto.pubkey).size)
        assertEquals(e.main, Trash(f).all().single().events.single())
        t.remove(item.id)
        assertTrue(t.all().isEmpty())
        assertTrue(NostrJson.parseToJsonElement(f.readText()).toString() == "[]")
    }
}
