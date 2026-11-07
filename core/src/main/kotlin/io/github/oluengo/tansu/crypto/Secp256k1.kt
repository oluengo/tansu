package io.github.oluengo.tansu.crypto

import java.math.BigInteger
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * secp256k1 y firmas Schnorr (BIP-340) en Kotlin puro, sin librerías nativas.
 * Solo se usa para la conexión NWC y los zaps anónimos: pocas operaciones, así que la claridad
 * pesa más que la velocidad. Probado con los vectores oficiales de BIP-340.
 */
object Secp256k1 {
    val P = BigInteger("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEFFFFFC2F", 16)
    val N = BigInteger("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141", 16)
    private val SEVEN = BigInteger.valueOf(7)
    private val TWO = BigInteger.valueOf(2)
    private val THREE = BigInteger.valueOf(3)

    data class Point(val x: BigInteger, val y: BigInteger)

    val G = Point(
        BigInteger("79BE667EF9DCBBAC55A06295CE870B07029BFCDB2DCE28D959F2815B16F81798", 16),
        BigInteger("483ADA7726A3C4655DA4FBFC0E1108A8FD17B448A68554199C47D08FFB10D4B8", 16),
    )

    // Coordenadas jacobianas (X, Y, Z) para no invertir en cada paso.
    private class J(val x: BigInteger, val y: BigInteger, val z: BigInteger)
    private val INF = J(BigInteger.ONE, BigInteger.ONE, BigInteger.ZERO)

    private fun dbl(p: J): J {
        if (p.z.signum() == 0 || p.y.signum() == 0) return INF
        val ysq = p.y.multiply(p.y).mod(P)
        val s = THREE.multiply(p.x).multiply(p.x).mod(P) // a = 0
        val m = BigInteger.valueOf(4).multiply(p.x).multiply(ysq).mod(P)
        val nx = s.multiply(s).subtract(TWO.multiply(m)).mod(P)
        val ny = s.multiply(m.subtract(nx)).subtract(BigInteger.valueOf(8).multiply(ysq).multiply(ysq)).mod(P)
        val nz = TWO.multiply(p.y).multiply(p.z).mod(P)
        return J(nx, ny, nz)
    }

    private fun add(p: J, q: J): J {
        if (p.z.signum() == 0) return q
        if (q.z.signum() == 0) return p
        val z1z1 = p.z.multiply(p.z).mod(P)
        val z2z2 = q.z.multiply(q.z).mod(P)
        val u1 = p.x.multiply(z2z2).mod(P)
        val u2 = q.x.multiply(z1z1).mod(P)
        val s1 = p.y.multiply(q.z).multiply(z2z2).mod(P)
        val s2 = q.y.multiply(p.z).multiply(z1z1).mod(P)
        if (u1 == u2) return if (s1 == s2) dbl(p) else INF
        val h = u2.subtract(u1).mod(P)
        val r = s2.subtract(s1).mod(P)
        val h2 = h.multiply(h).mod(P)
        val h3 = h2.multiply(h).mod(P)
        val u1h2 = u1.multiply(h2).mod(P)
        val nx = r.multiply(r).subtract(h3).subtract(TWO.multiply(u1h2)).mod(P)
        val ny = r.multiply(u1h2.subtract(nx)).subtract(s1.multiply(h3)).mod(P)
        val nz = h.multiply(p.z).multiply(q.z).mod(P)
        return J(nx, ny, nz)
    }

    private fun toAffine(p: J): Point? {
        if (p.z.signum() == 0) return null
        val zi = p.z.modInverse(P)
        val zi2 = zi.multiply(zi).mod(P)
        return Point(p.x.multiply(zi2).mod(P), p.y.multiply(zi2).multiply(zi).mod(P))
    }

    fun mul(p: Point, k: BigInteger): Point? {
        var r = INF
        var a = J(p.x, p.y, BigInteger.ONE)
        val e = k.mod(N)
        for (i in 0 until e.bitLength()) {
            if (e.testBit(i)) r = add(r, a)
            a = dbl(a)
        }
        return toAffine(r)
    }

    fun addPoints(a: Point?, b: Point?): Point? {
        if (a == null) return b
        if (b == null) return a
        return toAffine(add(J(a.x, a.y, BigInteger.ONE), J(b.x, b.y, BigInteger.ONE)))
    }

    /** Punto con coordenada x dada e y par (BIP-340). */
    fun liftX(x: BigInteger): Point? {
        if (x.signum() < 0 || x >= P) return null
        val c = x.modPow(THREE, P).add(SEVEN).mod(P)
        val y = c.modPow(P.add(BigInteger.ONE).shiftRight(2), P)
        if (y.modPow(TWO, P) != c) return null
        return Point(x, if (y.testBit(0)) P.subtract(y) else y)
    }

    fun bytes32(v: BigInteger): ByteArray {
        val b = v.toByteArray()
        return when {
            b.size == 32 -> b
            b.size > 32 -> b.copyOfRange(b.size - 32, b.size)
            else -> ByteArray(32 - b.size) + b
        }
    }

    fun int(b: ByteArray) = BigInteger(1, b)
    fun sha256(vararg parts: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").run { parts.forEach { update(it) }; digest() }

    private fun taggedHash(tag: String, vararg msgs: ByteArray): ByteArray {
        val t = sha256(tag.toByteArray())
        return sha256(t, t, *msgs)
    }

    fun isValidSecret(sk: ByteArray) = sk.size == 32 && int(sk).let { it.signum() > 0 && it < N }

    /** Clave pública x-only (32 bytes) de una secreta. */
    fun pubkey(sk: ByteArray): ByteArray {
        require(isValidSecret(sk)) { "clave secreta no válida" }
        return bytes32(mul(G, int(sk))!!.x)
    }

    fun randomSecret(rng: SecureRandom = SecureRandom()): ByteArray {
        while (true) {
            val b = ByteArray(32).also(rng::nextBytes)
            if (isValidSecret(b)) return b
        }
    }

    /** Firma Schnorr BIP-340 de un mensaje de 32 bytes. */
    fun sign(msg: ByteArray, sk: ByteArray, aux: ByteArray = ByteArray(32).also(SecureRandom()::nextBytes)): ByteArray {
        require(msg.size == 32 && aux.size == 32)
        val d0 = int(sk)
        require(d0.signum() > 0 && d0 < N)
        val pp = mul(G, d0)!!
        val d = if (pp.y.testBit(0)) N.subtract(d0) else d0
        val px = bytes32(pp.x)
        val t = bytes32(d).let { db -> val h = taggedHash("BIP0340/aux", aux); ByteArray(32) { (db[it].toInt() xor h[it].toInt()).toByte() } }
        val k0 = int(taggedHash("BIP0340/nonce", t, px, msg)).mod(N)
        require(k0.signum() != 0)
        val r = mul(G, k0)!!
        val k = if (r.y.testBit(0)) N.subtract(k0) else k0
        val rx = bytes32(r.x)
        val e = int(taggedHash("BIP0340/challenge", rx, px, msg)).mod(N)
        return rx + bytes32(k.add(e.multiply(d)).mod(N))
    }

    fun verify(msg: ByteArray, pub: ByteArray, sig: ByteArray): Boolean = runCatching {
        if (msg.size != 32 || pub.size != 32 || sig.size != 64) return false
        val pp = liftX(int(pub)) ?: return false
        val r = int(sig.copyOfRange(0, 32))
        val s = int(sig.copyOfRange(32, 64))
        if (r >= P || s >= N) return false
        val e = int(taggedHash("BIP0340/challenge", sig.copyOfRange(0, 32), pub, msg)).mod(N)
        val sg = mul(G, s)
        val ep = mul(pp, N.subtract(e))
        val rr = addPoints(sg, ep) ?: return false
        !rr.y.testBit(0) && rr.x == r
    }.getOrDefault(false)

    /** ECDH de Nostr (NIP-04): coordenada x de sk·P, sin hash. */
    fun sharedX(sk: ByteArray, pubX: ByteArray): ByteArray {
        val pp = liftX(int(pubX)) ?: throw IllegalArgumentException("clave pública no válida")
        return bytes32(mul(pp, int(sk))!!.x)
    }
}
