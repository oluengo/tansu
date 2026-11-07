package io.github.oluengo.tansu.crypto

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Cifrado NIP-04 (AES-256-CBC con la clave ECDH). Es el que entienden todas las carteras NWC. */
object Nip04 {
    private val b64e = Base64.getEncoder()
    private val b64d = Base64.getDecoder()

    fun encrypt(plain: String, sk: ByteArray, peerPub: ByteArray, iv: ByteArray = ByteArray(16).also(SecureRandom()::nextBytes)): String {
        val key = Secp256k1.sharedX(sk, peerPub)
        val c = Cipher.getInstance("AES/CBC/PKCS5Padding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
        return b64e.encodeToString(c.doFinal(plain.toByteArray(Charsets.UTF_8))) + "?iv=" + b64e.encodeToString(iv)
    }

    fun decrypt(payload: String, sk: ByteArray, peerPub: ByteArray): String {
        val ct = payload.substringBefore("?iv=")
        val iv = payload.substringAfter("?iv=", "")
        require(iv.isNotEmpty()) { "sin iv" }
        val key = Secp256k1.sharedX(sk, peerPub)
        val c = Cipher.getInstance("AES/CBC/PKCS5Padding")
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(b64d.decode(iv)))
        return String(c.doFinal(b64d.decode(ct)), Charsets.UTF_8)
    }
}
