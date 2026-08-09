package com.openscadviewer.engine.text

import net.jqwik.api.*
import net.jqwik.api.Combinators
import org.junit.jupiter.api.Assertions.*

/**
 * Feature: text-rendering
 *
 * Property-based tests for GlyphCache consistency.
 *
 * Property 7: Glyph cache returns identical data on cache hit
 * For any sequence of put/get operations with various keys and GlyphOutline values,
 * get after put with same key returns identical data; eviction only happens when
 * size limit exceeded.
 *
 * Validates: Requirements 3.4, 8.1
 */
class GlyphCachePropertyTest {

    /**
     * Feature: text-rendering, Property 7: Glyph cache returns identical data on cache hit
     *
     * For any random GlyphOutline inserted with a key, get(key) immediately returns
     * identical data.
     *
     * Validates: Requirements 3.4, 8.1
     */
    @Property(tries = 200)
    @Tag("property-7-glyph-cache-consistency")
    fun getAfterPutReturnsSameOutline(
        @ForAll("glyphCacheKeys") key: GlyphCacheKey,
        @ForAll("glyphOutlines") outline: GlyphOutline
    ) {
        val cache = GlyphCache()
        cache.put(key, outline)
        val retrieved = cache.get(key)

        assertNotNull(retrieved, "get() should return non-null after put() with same key")
        assertEquals(outline, retrieved, "get() should return identical outline to what was put()")
    }

    /**
     * Feature: text-rendering, Property 7: Glyph cache returns identical data on cache hit
     *
     * Multiple puts with different keys are all retrievable until eviction occurs.
     *
     * Validates: Requirements 3.4, 8.1
     */
    @Property(tries = 100)
    @Tag("property-7-glyph-cache-consistency")
    fun multiplePutsAllRetrievableWithinCapacity(
        @ForAll("smallEntryLists") entries: List<Pair<GlyphCacheKey, GlyphOutline>>
    ) {
        // Use a large cache so no eviction occurs
        val cache = GlyphCache(maxSizeBytes = 100L * 1024L * 1024L)

        for ((key, outline) in entries) {
            cache.put(key, outline)
        }

        // All entries should be retrievable (no eviction with large capacity)
        for ((key, outline) in entries) {
            val retrieved = cache.get(key)
            assertNotNull(retrieved, "get($key) should return non-null within capacity")
            assertEquals(outline, retrieved, "get($key) should return the put() value")
        }

        assertEquals(
            entries.map { it.first }.distinct().size,
            cache.size(),
            "Cache size should equal number of distinct keys"
        )
    }

    /**
     * Feature: text-rendering, Property 7: Glyph cache returns identical data on cache hit
     *
     * After eviction (when size exceeds max), get for evicted keys returns null, but
     * remaining keys still return correct data. Eviction removes LRU entries when adding
     * a new entry would exceed the size limit.
     *
     * Validates: Requirements 3.4, 8.1
     */
    @Property(tries = 100)
    @Tag("property-7-glyph-cache-consistency")
    fun evictionOccursOnlyWhenSizeLimitExceeded(
        @ForAll("uniformOutlineLists") outlines: List<GlyphOutline>
    ) {
        // Use uniform-sized entries to ensure predictable eviction behavior.
        // With uniform sizes, the max allows exactly 2 entries, so inserting 3+
        // guarantees eviction.
        val entrySize = outlines.first().estimatedSizeBytes()
        // Set max to hold exactly 2 entries
        val maxSize = entrySize * 2
        val cache = GlyphCache(maxSizeBytes = maxSize)

        val keys = outlines.mapIndexed { i, _ ->
            GlyphCacheKey("font$i", ('A' + (i % 26)))
        }

        // Put all entries — with uniform size and maxSize = 2*entrySize,
        // the cache should evict older entries to stay within bounds
        for (i in outlines.indices) {
            cache.put(keys[i], outlines[i])
        }

        // Cache should not exceed max size (uniform entries fit exactly)
        assertTrue(
            cache.currentSizeBytes() <= maxSize,
            "Cache size (${cache.currentSizeBytes()}) should not exceed max ($maxSize)"
        )

        // Only the 2 most recently inserted entries should remain
        assertTrue(
            cache.size() <= 2,
            "Cache should hold at most 2 entries with maxSize=$maxSize, entrySize=$entrySize"
        )

        // For entries still in cache, data must be identical to what was put
        for (i in outlines.indices) {
            val retrieved = cache.get(keys[i])
            if (retrieved != null) {
                assertEquals(
                    outlines[i], retrieved,
                    "Cached entry for key ${keys[i]} must be identical to what was put"
                )
            }
        }

        // The most recently inserted entry should always be present
        val lastRetrieved = cache.get(keys.last())
        assertNotNull(lastRetrieved, "Most recently inserted entry should be in cache")
        assertEquals(outlines.last(), lastRetrieved)
    }

    /**
     * Feature: text-rendering, Property 7: Glyph cache returns identical data on cache hit
     *
     * Re-putting same key updates the value and get returns the new value.
     *
     * Validates: Requirements 3.4, 8.1
     */
    @Property(tries = 200)
    @Tag("property-7-glyph-cache-consistency")
    fun rePuttingSameKeyUpdatesValue(
        @ForAll("glyphCacheKeys") key: GlyphCacheKey,
        @ForAll("glyphOutlines") outline1: GlyphOutline,
        @ForAll("glyphOutlines") outline2: GlyphOutline
    ) {
        val cache = GlyphCache()
        cache.put(key, outline1)
        cache.put(key, outline2)

        val retrieved = cache.get(key)
        assertNotNull(retrieved, "get() should return non-null after re-put()")
        assertEquals(outline2, retrieved, "get() should return the latest put() value")
        assertEquals(1, cache.size(), "Cache should contain only one entry for the same key")
    }

    // --- Generators ---

    @Provide
    fun glyphCacheKeys(): Arbitrary<GlyphCacheKey> {
        val fontNames = Arbitraries.strings()
            .withCharRange('a', 'z')
            .ofMinLength(3)
            .ofMaxLength(12)

        val chars = Arbitraries.chars().range('A', 'z')

        return Combinators.combine(fontNames, chars)
            .`as` { font, char -> GlyphCacheKey(font, char) }
    }

    @Provide
    fun glyphOutlines(): Arbitrary<GlyphOutline> {
        // Generate small contours (1-3 contours, 3-8 points each)
        val pointArb = Combinators.combine(
            Arbitraries.floats().between(-100f, 100f),
            Arbitraries.floats().between(-100f, 100f)
        ).`as` { x, y -> Pair(x, y) }

        val contourArb = pointArb.list().ofMinSize(3).ofMaxSize(8)
        val contoursArb = contourArb.list().ofMinSize(1).ofMaxSize(3)

        val advanceWidthArb = Arbitraries.floats().between(1f, 50f)
        val ascentArb = Arbitraries.floats().between(10f, 100f)
        val descentArb = Arbitraries.floats().between(-50f, -1f)

        return Combinators.combine(contoursArb, advanceWidthArb, ascentArb, descentArb)
            .`as` { contours, advanceWidth, ascent, descent ->
                GlyphOutline(contours, advanceWidth, ascent, descent)
            }
    }

    @Provide
    fun smallEntryLists(): Arbitrary<List<Pair<GlyphCacheKey, GlyphOutline>>> {
        return Combinators.combine(glyphCacheKeys(), glyphOutlines())
            .`as` { key, outline -> Pair(key, outline) }
            .list()
            .ofMinSize(2)
            .ofMaxSize(10)
            .filter { entries ->
                // Ensure distinct keys for this test
                entries.map { it.first }.distinct().size == entries.size
            }
    }

    @Provide
    fun glyphOutlineLists(): Arbitrary<List<GlyphOutline>> {
        return glyphOutlines().list().ofMinSize(3).ofMaxSize(10)
    }

    @Provide
    fun uniformOutlineLists(): Arbitrary<List<GlyphOutline>> {
        // Generate a list of outlines that all have the same structure (same number of
        // contours with same number of points) so they have identical estimatedSizeBytes.
        // This makes eviction behavior predictable.
        val pointArb = Combinators.combine(
            Arbitraries.floats().between(-100f, 100f),
            Arbitraries.floats().between(-100f, 100f)
        ).`as` { x, y -> Pair(x, y) }

        // Fixed structure: 1 contour with exactly 5 points
        val contourArb = pointArb.list().ofSize(5)

        return Arbitraries.integers().between(3, 8).flatMap { count ->
            contourArb.list().ofSize(count).map { contours ->
                contours.map { contour ->
                    GlyphOutline(
                        contours = listOf(contour),
                        advanceWidth = 10f,
                        ascent = 50f,
                        descent = -10f
                    )
                }
            }
        }
    }
}
