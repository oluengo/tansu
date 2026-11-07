package io.github.oluengo.tansu.nostr

import android.content.Context
import android.content.Intent
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import io.github.oluengo.tansu.crypto.Secp256k1
import io.github.oluengo.tansu.data.Account
import io.github.oluengo.tansu.data.AccountCrypto
import io.github.oluengo.tansu.data.Method
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.security.KeyStore
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Firma y cifrado de cada cuenta con su firmante:
 * - **Amber** (NIP-55): primero en silencio; si hace falta su pantalla, se publica en [pending] para que
 *   la UI la abra. Amber guarda varias cuentas; se le indica cuál con `current_user`.
 * - **Bunker** (NIP-46): una conexión por cuenta, con su clave de cliente cifrada con el Android Keystore.
 */
class Signer(private val app: Context, private val pool: RelayPool) {

    /** Petición a Amber que necesita abrir su pantalla (la lanza la UI). */
    class Call(val intent: Intent, internal val result: CompletableDeferred<Intent?>) {
        fun complete(data: Intent?) { result.complete(data) }
    }

    private val _pending = MutableStateFlow<Call?>(null)
    val pending: StateFlow<Call?> = _pending.asStateFlow()
    private val _authUrl = MutableStateFlow<String?>(null)
    /** El firmante remoto pide aprobar en su web. */
    val authUrl: StateFlow<String?> = _authUrl.asStateFlow()
    fun authUrlShown() { _authUrl.value = null }

    private val prefs = app.getSharedPreferences("bunkers", Context.MODE_PRIVATE)
    private val clients = ConcurrentHashMap<String, Nip46Client>()

    /** Firma y cifrado de [account] para el explorador. [interactive] = puede abrir Amber. */
    fun crypto(account: Account, interactive: Boolean = true): AccountCrypto = object : AccountCrypto {
        override val pubkey = account.pubkey
        override suspend fun sign(unsigned: Event) = this@Signer.sign(account, unsigned, interactive)
        override suspend fun nip44Encrypt(plain: String, peer: String) = op(account, interactive, Op.ENC44, plain, peer)
        override suspend fun nip44Decrypt(cipher: String, peer: String) = op(account, interactive, Op.DEC44, cipher, peer)
        override suspend fun nip04Decrypt(cipher: String, peer: String) = op(account, interactive, Op.DEC04, cipher, peer)
    }

    private enum class Op { ENC44, DEC44, DEC04 }

    suspend fun sign(a: Account, unsigned: Event, interactive: Boolean): Event? {
        if (a.pubkey != unsigned.pubkey) return null
        return when (a.method) {
            Method.AMBER ->
                withContext(Dispatchers.IO) { Amber.signSilently(app, a.signerPackage, unsigned, a.pubkey) }
                    ?: if (interactive) Amber.parseSigned(ask(Amber.signIntent(unsigned, a.pubkey, a.signerPackage)), unsigned) else null
            Method.BUNKER -> remote(a) { it.sign(unsigned) }
            Method.READ_ONLY -> null
        }
    }

    private suspend fun op(a: Account, interactive: Boolean, op: Op, text: String, peer: String): String? = when (a.method) {
        Method.AMBER -> withContext(Dispatchers.IO) {
            when (op) {
                Op.ENC44 -> Amber.encryptSilently(app, a.signerPackage, text, peer, a.pubkey)
                Op.DEC44 -> Amber.decryptSilently(app, a.signerPackage, text, peer, a.pubkey)
                Op.DEC04 -> Amber.nip04DecryptSilently(app, a.signerPackage, text, peer, a.pubkey)
            }
        } ?: if (!interactive) null else ask(
            when (op) {
                Op.ENC44 -> Amber.nip44EncryptIntent(text, peer, a.pubkey, a.signerPackage)
                Op.DEC44 -> Amber.nip44DecryptIntent(text, peer, a.pubkey, a.signerPackage)
                Op.DEC04 -> Amber.nip04DecryptIntent(text, peer, a.pubkey, a.signerPackage)
            }
        )?.getStringExtra("result")
        Method.BUNKER -> remote(a) {
            when (op) {
                Op.ENC44 -> it.nip44Encrypt(text, peer)
                Op.DEC44 -> it.nip44Decrypt(text, peer)
                Op.DEC04 -> it.nip04Decrypt(text, peer)
            }
        }
        Method.READ_ONLY -> null
    }

    // ---------- Bunker (NIP-46) ----------

    sealed interface BunkerResult {
        data class Ok(val pubkey: String) : BunkerResult
        data object BadUri : BunkerResult
        data class Failed(val message: String?) : BunkerResult
    }

    /** Conecta con el firmante del enlace `bunker://` y guarda la conexión para esa cuenta. */
    suspend fun connectBunker(uri: String): BunkerResult {
        val b = BunkerUri.parse(uri) ?: return BunkerResult.BadUri
        val secret = Secp256k1.randomSecret()
        val cl = Nip46Client(pool, b, secret, onAuthUrl = { _authUrl.value = it })
        return try {
            val pk = cl.connect()
            prefs.edit().putString("uri_$pk", encrypt(b.copy(secret = null).toUri())).putString("key_$pk", encrypt(secret.toHex())).apply()
            clients[pk] = cl
            BunkerResult.Ok(pk)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            BunkerResult.Failed(e.message)
        }
    }

    /** Al quitar una cuenta se olvida su conexión. */
    fun forget(pubkey: String) {
        clients.remove(pubkey)
        prefs.edit().remove("uri_$pubkey").remove("key_$pubkey").apply()
    }

    private fun bunkerClient(pk: String): Nip46Client? {
        clients[pk]?.let { return it }
        val uri = prefs.getString("uri_$pk", null)?.let(::decrypt)?.let(BunkerUri::parse) ?: return null
        val key = prefs.getString("key_$pk", null)?.let(::decrypt)?.hexToBytes()?.takeIf(Secp256k1::isValidSecret) ?: return null
        return Nip46Client(pool, uri, key, onAuthUrl = { _authUrl.value = it }).also { clients[pk] = it }
    }

    private suspend fun <T> remote(a: Account, op: suspend (Nip46Client) -> T): T? {
        val cl = bunkerClient(a.pubkey) ?: return null
        return try { op(cl) } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
    }

    // ---------- Amber interactivo ----------

    private suspend fun ask(intent: Intent): Intent? {
        val call = Call(intent, CompletableDeferred())
        _pending.value = call
        return try {
            withTimeoutOrNull(180_000) { call.result.await() }
        } finally {
            if (_pending.value === call) _pending.value = null
        }
    }

    // ---------- Cifrado local (Android Keystore) ----------

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
        }.generateKey()
    }

    private fun encrypt(plain: String): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key())
        val enc = Base64.getEncoder()
        return enc.encodeToString(c.iv) + ":" + enc.encodeToString(c.doFinal(plain.toByteArray()))
    }

    private fun decrypt(s: String): String? = runCatching {
        val dec = Base64.getDecoder()
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, dec.decode(s.substringBefore(':'))))
        String(c.doFinal(dec.decode(s.substringAfter(':'))))
    }.getOrNull()

    private companion object {
        const val ALIAS = "tansu_bunker"
    }
}
