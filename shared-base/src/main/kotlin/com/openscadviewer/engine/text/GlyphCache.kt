package com.openscadviewer.engine.text

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.atomic.AtomicLong

/**
 * Thread-safe LRU cache for glyph outlines.
 * Stores outlines at a reference size; consumers scale on retrieval.
 *
 * Uses ConcurrentHashMap for thread-safe key-value storage and
 * ConcurrentLinkedDeque for tracking access order (most recent at front).
 * When inserting a new entry would exceed maxSizeBytes, least-recently-used
 * entries are evicted from the back of the deque until space is available.
 */
class GlyphCache(private val maxSizeBytes: Long = 4L * 1024L * 1024L) {

    private val cache = ConcurrentHashMap<GlyphCacheKey, GlyphOutline>()
    private val accessOrder = ConcurrentLinkedDeque<GlyphCacheKey>()
    private val currentSize = AtomicLong(0)

    /**
     * Retrieve a cached glyph outline by key.
     * Promotes the entry to most-recently-used on access.
     * Returns null if the key is not in the cache.
     */
    fun get(key: GlyphCacheKey): GlyphOutline? {
        val outline = cache[key] ?: return null
        // Promote to most-recently-used
        accessOrder.remove(key)
        accessOrder.addFirst(key)
        return outline
    }

    /**
     * Insert a glyph outline into the cache.
     * If the entry already exists, it is replaced and promoted.
     * Evicts least-recently-used entries if the cache would exceed maxSizeBytes.
     */
    fun put(key: GlyphCacheKey, outline: GlyphOutline) {
        // If key already exists, remove old entry's size contribution
        cache.remove(key)?.let { old ->
            accessOrder.remove(key)
            currentSize.addAndGet(-old.estimatedSizeBytes())
        }

        val size = outline.estimatedSizeBytes()

        // Evict LRU entries until there's room for the new entry
        while (currentSize.get() + size > maxSizeBytes && accessOrder.isNotEmpty()) {
            val evictKey = accessOrder.pollLast() ?: break
            cache.remove(evictKey)?.let { evicted ->
                currentSize.addAndGet(-evicted.estimatedSizeBytes())
            }
        }

        cache[key] = outline
        accessOrder.addFirst(key)
        currentSize.addAndGet(size)
    }

    /** Returns the current estimated size of cached data in bytes. */
    fun currentSizeBytes(): Long = currentSize.get()

    /** Returns the number of entries currently in the cache. */
    fun size(): Int = cache.size
}

/**
 * Cache key for glyph outlines, combining font name and character.
 * Outlines are cached at REFERENCE_SIZE and scaled on retrieval.
 */
data class GlyphCacheKey(val fontName: String, val char: Char)
