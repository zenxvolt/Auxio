/*
 * Copyright (c) 2026 Auxio Project
 * JellyfinAudioCache.kt is part of Auxio.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
 
package org.oxycblt.auxio.jellyfin

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheEvictor
import androidx.media3.datasource.cache.CacheSpan
import androidx.media3.datasource.cache.SimpleCache
import java.io.File
import java.util.TreeSet
import timber.log.Timber as L

/**
 * The songs streamed from Jellyfin that are kept on the device, so that playing them again (or
 * seeking back in them) doesn't download them again, and so that they can be played while the
 * server can't be reached.
 *
 * Everything that is streamed is written here as it is played, and the songs that haven't been
 * played for the longest time are dropped once the cache is over its limit. The limit is read every
 * time that it matters, so changing it takes effect right away.
 *
 * Entries are keyed by song and file version rather than by URL, see [JellyfinStreamKey], so they
 * stay valid when the address of the server changes. They belong to the account that streamed them,
 * so the cache must be [clear]ed whenever the account changes.
 *
 * There must only ever be one instance, [SimpleCache] locks its directory.
 *
 * @param context [Context] required to locate the cache.
 * @param limitBytes The amount of space that the cache may take up, 0 if it is turned off.
 */
@OptIn(UnstableApi::class)
class JellyfinAudioCache(context: Context, private val limitBytes: () -> Long) {
    private val appContext = context.applicationContext

    /** Whether new songs should be written to the cache at all. */
    val enabled: Boolean
        get() = limitBytes() > 0

    /**
     * The cache itself. Created on first use: setting it up reads its index from the disk, which
     * has no business on the main thread, nor at all for someone who never streams.
     */
    val cache: Cache by lazy {
        SimpleCache(
            File(appContext.cacheDir, DIRECTORY),
            JellyfinCacheEvictor(limitBytes),
            StandaloneDatabaseProvider(appContext),
        )
    }

    /** The amount of space that the cache takes up right now, in bytes. Blocks. */
    fun usedBytes(): Long =
        try {
            cache.cacheSpace
        } catch (e: Exception) {
            L.w(e, "Could not measure the Jellyfin audio cache")
            0L
        }

    /** Drop everything that is in the cache. Blocks. */
    fun clear() {
        L.d("Clearing the Jellyfin audio cache")
        try {
            for (key in cache.keys) {
                cache.removeResource(key)
            }
        } catch (e: Exception) {
            L.e(e, "Could not clear the Jellyfin audio cache")
        }
    }

    /**
     * Shrink the cache down to [limitBytes], dropping the songs that were played longest ago first.
     * The cache already does this by itself the next time that something is written to it, this is
     * for applying a new limit right away. Blocks.
     *
     * @param limitBytes The new limit, 0 to drop everything.
     */
    fun trim(limitBytes: Long) {
        if (limitBytes <= 0) {
            clear()
            return
        }
        try {
            // Only the public API is used here, the evictor keeps its own bookkeeping up to date
            // through the callbacks of the cache. Removing a span that is already gone is a no-op.
            var used = cache.cacheSpace
            if (used <= limitBytes) {
                return
            }
            val spans = cache.keys.flatMap { cache.getCachedSpans(it) }.sortedWith(LEAST_RECENT)
            for (span in spans) {
                if (used <= limitBytes) {
                    break
                }
                cache.removeSpan(span)
                used -= span.length
            }
        } catch (e: Exception) {
            L.e(e, "Could not trim the Jellyfin audio cache")
        }
    }

    private companion object {
        const val DIRECTORY = "jellyfin_audio"
    }
}

/**
 * Identifies the cached copy of a song that is streamed from Jellyfin.
 *
 * The key is made of the ID of the song and the size of its file. The ID keeps the key valid when
 * the server is reached over another address, which would make a key based on the URL useless. The
 * size makes sure that a file that is replaced on the server is downloaded again rather than mixed
 * with the remains of the old one. Jellyfin doesn't tell when a file changed, and the size is
 * enough in practice: changing the audio or the tags of a file practically always changes it, and
 * an exact match would only ever matter for the tags, which Auxio takes from the server anyway.
 */
object JellyfinStreamKey {
    private const val PREFIX = "jellyfin:"

    /**
     * Get the cache key of a song.
     *
     * @param url The URL of the song, see [JellyfinUrls.stream].
     * @param sizeBytes The size of the file of the song, or 0 or less if it's not known.
     * @return The key, or null if [url] doesn't stream a song from Jellyfin.
     */
    fun of(url: String, sizeBytes: Long): String? {
        val itemId = JellyfinUrls.streamItemId(url) ?: return null
        return if (sizeBytes > 0) "$PREFIX$itemId:$sizeBytes" else "$PREFIX$itemId"
    }
}

/**
 * Drops the spans of the cache that were used longest ago once the cache would go over its limit.
 *
 * This is the same as Media3's `LeastRecentlyUsedCacheEvictor`, except that the limit can change
 * while the app runs. All callbacks come from the cache while it holds its own lock, which is what
 * guards the state of this class.
 *
 * @param limitBytes The amount of space that the cache may take up.
 */
@OptIn(UnstableApi::class)
internal class JellyfinCacheEvictor(private val limitBytes: () -> Long) : CacheEvictor {
    private val spans = TreeSet(LEAST_RECENT)
    private var currentSize = 0L

    override fun requiresCacheSpanTouches() = true

    override fun onCacheInitialized() {}

    override fun onStartFile(cache: Cache, key: String, position: Long, length: Long) {
        if (length != C.LENGTH_UNSET.toLong()) {
            evict(cache, length)
        }
    }

    override fun onSpanAdded(cache: Cache, span: CacheSpan) {
        spans.add(span)
        currentSize += span.length
        evict(cache, 0)
    }

    override fun onSpanRemoved(cache: Cache, span: CacheSpan) {
        spans.remove(span)
        currentSize -= span.length
    }

    override fun onSpanTouched(cache: Cache, oldSpan: CacheSpan, newSpan: CacheSpan) {
        onSpanRemoved(cache, oldSpan)
        onSpanAdded(cache, newSpan)
    }

    private fun evict(cache: Cache, requiredSpace: Long) {
        val limit = limitBytes()
        while (currentSize + requiredSpace > limit && spans.isNotEmpty()) {
            // Calls back into onSpanRemoved, which shrinks the set.
            cache.removeSpan(spans.first())
        }
    }
}

/** Orders spans from the one that was used longest ago, like Media3's LRU evictor does. */
@OptIn(UnstableApi::class)
private val LEAST_RECENT =
    Comparator<CacheSpan> { a, b ->
        val byTime = a.lastTouchTimestamp.compareTo(b.lastTouchTimestamp)
        if (byTime != 0) byTime else a.compareTo(b)
    }
