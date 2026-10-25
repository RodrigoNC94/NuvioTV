package com.nuvio.tv.ui.screens.player

import android.content.SharedPreferences
import java.net.URI
import java.util.concurrent.ConcurrentHashMap

/**
 * What Automatic speed-up learnt per server: the rate one request carried, the downloads a film kept, the wait for
 * the first byte and the piece size the rate was carried on. The next film from the same server starts from it
 * instead of measuring again. Kept [KEEP_MS]. Entries saved before [VERSION] shared the rate by downloads while the
 * read-ahead's pieces ran too, so read up to four times too high, and are not used.
 */
internal object HostSpeeds {
    data class Entry(
        val perDownloadBps: Long,
        val downloads: Int,
        val firstByteMs: Long,
        val savedAtMs: Long,
        val pieceBytes: Long = 0L
    )

    const val KEEP_MS = 30L * 24 * 60 * 60 * 1000
    private const val PREFIX = "host:"

    @Volatile
    private var prefs: SharedPreferences? = null
    private val cache = ConcurrentHashMap<String, Entry>()

    fun attach(store: SharedPreferences) {
        if (prefs == null) prefs = store
    }

    /**
     * The server a link belongs to. The numbers in the first label name one machine (nexus-115, syd3-4, store-059,
     * 1-cdn2-ovh-bea), so they are left out and the place stays: nexus, syd, store, cdn-ovh-bea.
     */
    fun keyOf(url: String?): String? {
        val host = runCatching { URI(url ?: return null).host }.getOrNull()?.lowercase() ?: return null
        val labels = host.split('.')
        if (labels.size <= 2 || ':' in host || labels.last().all(Char::isDigit)) return host
        val first = labels[0].split('-').map { it.filterNot(Char::isDigit) }.filter { it.isNotEmpty() }.joinToString("-")
        if (first.isEmpty()) return host
        return (listOf(first) + labels.drop(1)).joinToString(".")
    }

    fun get(key: String, nowMs: Long = System.currentTimeMillis()): Entry? {
        val e = cache[key] ?: prefs?.getString(PREFIX + key, null)?.let(::decode)?.also { cache[key] = it } ?: return null
        return e.takeIf { nowMs - it.savedAtMs in 0 until KEEP_MS }
    }

    fun put(key: String, entry: Entry) {
        cache[key] = entry
        prefs?.edit()?.putString(PREFIX + key, encode(entry))?.apply()
    }

    /** Adds one film to what the server delivered before, so a single good or bad film does not decide the next. */
    fun record(key: String, film: Entry) = put(key, merge(get(key, film.savedAtMs), film))

    fun merge(old: Entry?, film: Entry): Entry {
        if (old == null) return film
        return Entry(
            perDownloadBps = ((old.perDownloadBps * OLD_SHARE + film.perDownloadBps * (100 - OLD_SHARE)) / 100)
                .coerceAtLeast(1L),
            downloads = ((old.downloads * OLD_SHARE + film.downloads * (100 - OLD_SHARE) + 50) / 100).coerceAtLeast(1),
            firstByteMs = if (film.firstByteMs >= 0L) film.firstByteMs else old.firstByteMs,
            savedAtMs = film.savedAtMs,
            pieceBytes = mergePiece(old.pieceBytes, film.pieceBytes)
        )
    }

    /** Averaged like the rate, then set to the nearest piece size Automatic uses. */
    fun mergePiece(old: Long, film: Long): Long {
        if (old <= 0L || film <= 0L) return if (film > 0L) film else old
        val mean = (old * OLD_SHARE + film * (100 - OLD_SHARE)) / 100
        return AutoSpeedupPolicy.PIECE_STEPS.minBy { kotlin.math.abs(it - mean) }
    }

    const val OLD_SHARE = 60

    const val VERSION = 2

    fun encode(e: Entry): String =
        "${e.perDownloadBps},${e.downloads},${e.firstByteMs},${e.savedAtMs},${e.pieceBytes},v$VERSION"

    fun decode(text: String): Entry? {
        val p = text.split(',')
        if (p.size != 6 || p[5] != "v$VERSION") return null
        val bps = p[0].toLongOrNull() ?: return null
        val n = p[1].toIntOrNull() ?: return null
        val fb = p[2].toLongOrNull() ?: return null
        val at = p[3].toLongOrNull() ?: return null
        val piece = p[4].toLongOrNull() ?: return null
        return if (bps > 0L && n > 0) Entry(bps, n, fb, at, piece.coerceAtLeast(0L)) else null
    }

    /**
     * The requests a server took before it answered "too many requests": [limit] at [atMs]. One more is allowed for
     * each [LIMIT_EASE_MS] without a refusal since, until [AutoSpeedup.MAX_IN_FLIGHT] lifts it.
     */
    data class Limit(val limit: Int, val atMs: Long)

    const val LIMIT_EASE_MS = 30L * 60 * 1000
    private const val LIMIT_PREFIX = "limit:"
    private val limits = ConcurrentHashMap<String, Limit>()

    /** The requests [key]'s server takes at once now, null when it never refused or has eased back. */
    fun limitOf(key: String, nowMs: Long = System.currentTimeMillis()): Int? {
        val l = limits[key] ?: prefs?.getString(LIMIT_PREFIX + key, null)?.let(::decodeLimit)?.also { limits[key] = it }
            ?: return null
        return easedLimit(l, nowMs)
    }

    fun easedLimit(l: Limit, nowMs: Long): Int? {
        val eased = l.limit + ((nowMs - l.atMs).coerceAtLeast(0L) / LIMIT_EASE_MS).toInt()
        return eased.takeIf { it < AutoSpeedup.MAX_IN_FLIGHT }
    }

    /** A refusal: the server is held to [limit] (or less, if it was held lower still) from [nowMs]. */
    fun noteLimit(key: String, limit: Int, nowMs: Long = System.currentTimeMillis()) {
        val held = limitOf(key, nowMs)
        val l = Limit(if (held != null) minOf(held, limit) else limit, nowMs)
        limits[key] = l
        prefs?.edit()?.putString(LIMIT_PREFIX + key, "${l.limit},${l.atMs}")?.apply()
    }

    private fun decodeLimit(text: String): Limit? {
        val p = text.split(',')
        val n = p.getOrNull(0)?.toIntOrNull() ?: return null
        val at = p.getOrNull(1)?.toLongOrNull() ?: return null
        return if (n > 0) Limit(n, at) else null
    }

    internal fun clearForTest() {
        cache.clear()
        limits.clear()
        prefs = null
    }
}
