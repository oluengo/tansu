package io.github.oluengo.tansu.data

/** Almacén clave → texto: SharedPreferences en Android, un archivo .properties en el escritorio. */
interface KeyValueStore {
    fun get(key: String): String?
    fun put(key: String, value: String?)
}

/** En memoria (tests). */
class MemoryStore : KeyValueStore {
    private val map = java.util.concurrent.ConcurrentHashMap<String, String>()
    override fun get(key: String) = map[key]
    override fun put(key: String, value: String?) { if (value == null) map.remove(key) else map[key] = value }
}
