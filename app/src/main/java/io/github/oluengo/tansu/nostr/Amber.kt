package io.github.oluengo.tansu.nostr

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build

/**
 * Firma externa con Amber (NIP-55). La app nunca ve la clave privada.
 * Login, firma de datos de apps (30078) y borrados (5), y cifrado NIP-44 para uno mismo.
 * Descifrar NIP-04 solo para leer listas antiguas.
 * Primero se intenta en segundo plano (content provider, si el usuario dio permiso «siempre»);
 * si no, con un intent que abre Amber.
 */
object Amber {
    private val probe get() = Intent(Intent.ACTION_VIEW, Uri.parse("nostrsigner:"))

    fun isInstalled(context: Context): Boolean {
        val pm = context.packageManager
        val list = if (Build.VERSION.SDK_INT >= 33) {
            pm.queryIntentActivities(probe, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(probe, 0)
        }
        return list.isNotEmpty()
    }

    /** Permisos que se piden por adelantado (Amber deja elegir «siempre» o preguntar cada vez). */
    private const val PERMISSIONS =
        """[{"type":"sign_event","kind":30078},{"type":"sign_event","kind":5},{"type":"nip44_encrypt"},{"type":"nip44_decrypt"},{"type":"nip04_decrypt"}]"""

    fun loginIntent(): Intent = probe.apply {
        putExtra("type", "get_public_key")
        putExtra("permissions", PERMISSIONS)
    }

    /** Pide a Amber que firme un evento (NIP-55 sign_event). */
    fun signIntent(unsigned: Event, userPubkey: String, signerPackage: String?): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse("nostrsigner:" + unsigned.toJson().toString())).apply {
            signerPackage?.let { `package` = it }
            putExtra("type", "sign_event")
            putExtra("id", unsigned.id)
            putExtra("current_user", Nip19.hexToNpub(userPubkey))
        }

    /**
     * Amber devuelve el evento firmado en "event" o solo la firma en "result"/"signature".
     * Se comprueba la firma: si no cuadra con lo que pedimos, se descarta.
     */
    fun parseSigned(data: android.content.Intent?, unsigned: Event): Event? {
        data ?: return null
        val full = data.getStringExtra("event")?.let(Event::fromJson)
        val candidate = full ?: (data.getStringExtra("result") ?: data.getStringExtra("signature"))
            ?.takeIf { it.length == 128 }?.let { unsigned.copy(sig = it) }
        return candidate?.takeIf { EventSigning.verify(it) && it.pubkey == unsigned.pubkey && it.kind == unsigned.kind }
    }

    const val DEFAULT_PACKAGE = "com.greenart7c3.nostrsigner"

    fun nip44EncryptIntent(plaintext: String, toPubkey: String, userPubkey: String, signerPackage: String?): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse("nostrsigner:$plaintext")).apply {
            signerPackage?.let { `package` = it }
            putExtra("type", "nip44_encrypt")
            putExtra("pubkey", toPubkey)
            putExtra("current_user", Nip19.hexToNpub(userPubkey))
        }

    fun nip44DecryptIntent(ciphertext: String, fromPubkey: String, userPubkey: String, signerPackage: String?): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse("nostrsigner:$ciphertext")).apply {
            signerPackage?.let { `package` = it }
            putExtra("type", "nip44_decrypt")
            putExtra("pubkey", fromPubkey)
            putExtra("current_user", Nip19.hexToNpub(userPubkey))
        }

    fun nip04DecryptIntent(ciphertext: String, fromPubkey: String, userPubkey: String, signerPackage: String?): Intent =
        Intent(Intent.ACTION_VIEW, Uri.parse("nostrsigner:$ciphertext")).apply {
            signerPackage?.let { `package` = it }
            putExtra("type", "nip04_decrypt")
            putExtra("pubkey", fromPubkey)
            putExtra("current_user", Nip19.hexToNpub(userPubkey))
        }

    /** Consulta silenciosa al content provider de Amber. null = no autorizado o no disponible. */
    private fun provider(context: Context, pkg: String?, op: String, args: Array<String>): Map<String, String>? = runCatching {
        val uri = Uri.parse("content://${pkg ?: DEFAULT_PACKAGE}.$op")
        context.contentResolver.query(uri, args, null, null, null)?.use { c ->
            if (c.getColumnIndex("rejected") >= 0 || !c.moveToFirst()) return@use null
            c.columnNames.associateWith { name -> c.getString(c.getColumnIndex(name)).orEmpty() }
        }
    }.getOrNull()

    fun encryptSilently(context: Context, pkg: String?, plaintext: String, toPubkey: String, userPubkey: String): String? =
        provider(context, pkg, "NIP44_ENCRYPT", arrayOf(plaintext, toPubkey, Nip19.hexToNpub(userPubkey)))
            ?.let { it["result"] ?: it["signature"] }?.takeIf { it.isNotEmpty() }

    fun decryptSilently(context: Context, pkg: String?, ciphertext: String, fromPubkey: String, userPubkey: String): String? =
        provider(context, pkg, "NIP44_DECRYPT", arrayOf(ciphertext, fromPubkey, Nip19.hexToNpub(userPubkey)))
            ?.let { it["result"] ?: it["signature"] }?.takeIf { it.isNotEmpty() }

    fun nip04DecryptSilently(context: Context, pkg: String?, ciphertext: String, fromPubkey: String, userPubkey: String): String? =
        provider(context, pkg, "NIP04_DECRYPT", arrayOf(ciphertext, fromPubkey, Nip19.hexToNpub(userPubkey)))
            ?.let { it["result"] ?: it["signature"] }?.takeIf { it.isNotEmpty() }

    fun signSilently(context: Context, pkg: String?, unsigned: Event, userPubkey: String): Event? {
        val r = provider(context, pkg, "SIGN_EVENT", arrayOf(unsigned.toJson().toString(), "", Nip19.hexToNpub(userPubkey))) ?: return null
        val candidate = r["event"]?.takeIf { it.isNotEmpty() }?.let(Event::fromJson)
            ?: (r["result"] ?: r["signature"])?.takeIf { it.length == 128 }?.let { unsigned.copy(sig = it) }
        return candidate?.takeIf { EventSigning.verify(it) && it.pubkey == unsigned.pubkey && it.kind == unsigned.kind }
    }

    data class Login(val pubkeyHex: String, val signerPackage: String?)

    /** Amber devuelve la clave en "result" (npub o hex según versión) y su paquete en "package". */
    fun parseLogin(data: Intent?): Login? {
        val raw = data?.getStringExtra("result") ?: return null
        val hex = Nip19.normalizePubkey(raw) ?: return null
        return Login(hex, data.getStringExtra("package"))
    }
}
