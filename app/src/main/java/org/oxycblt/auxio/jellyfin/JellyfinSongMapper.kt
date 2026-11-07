/*
 * Copyright (c) 2026 Auxio Project
 * JellyfinSongMapper.kt is part of Auxio.
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

import android.net.Uri
import org.oxycblt.musikr.covers.Cover
import org.oxycblt.musikr.fs.Components
import org.oxycblt.musikr.fs.Path
import org.oxycblt.musikr.fs.Volume
import org.oxycblt.musikr.metadata.Properties
import org.oxycblt.musikr.remote.RemoteSong
import org.oxycblt.musikr.tag.Date
import org.oxycblt.musikr.tag.parse.ParsedTags

/**
 * Translates [JellyfinTrack]s into the [RemoteSong]s that Musikr works with.
 *
 * @param serverUrl The normalized base URL of the server that the tracks are from, see
 *   [JellyfinUrls.normalize].
 * @param coverOf Produces the cover of a track, or null if it shouldn't have one.
 */
internal class JellyfinSongMapper(
    private val serverUrl: String,
    private val coverOf: (JellyfinTrack) -> Cover?,
) {
    private val volume = Volume.ThirdParty(Uri.parse(serverUrl))

    /**
     * Map a [track].
     *
     * @return The song, or null if the track can't be played (i.e it has no length, or is in
     *   a format that Android can't decode).
     */
    fun map(track: JellyfinTrack): RemoteSong? {
        if (track.durationMs <= 0 || !JellyfinFormats.isPlayable(track.container, track.path)) {
            return null
        }
        val containerMime = JellyfinFormats.containerMimeType(track.container, track.path)
        val codecMime = JellyfinFormats.codecMimeType(track.codec, containerMime)
        val added = track.dateCreatedMs ?: 0L
        return RemoteSong(
            uri = Uri.parse(JellyfinUrls.stream(serverUrl, track.id)),
            path = Path(volume, Components.parseUnix(displayPath(track))),
            mimeType = containerMime,
            size = track.sizeBytes ?: 0L,
            addedMs = added,
            modifiedMs = added,
            properties =
                Properties(
                    mimeType = codecMime,
                    durationMs = track.durationMs,
                    bitrateKbps = (track.bitrateBps ?: 0) / 1000,
                    sampleRateHz = track.sampleRateHz ?: 0,
                ),
            tags =
                ParsedTags(
                    durationMs = track.durationMs,
                    replayGainTrackAdjustment = track.normalizationGain,
                    name = track.name,
                    track = track.trackNumber,
                    disc = track.discNumber,
                    date = parseDate(track.premiereDate, track.year),
                    albumName = track.albumName,
                    artistNames = track.artistNames,
                    albumArtistNames = track.albumArtistNames,
                    genreNames = track.genres,
                ),
            cover = coverOf(track),
        )
    }

    /**
     * The path that is shown for a track. The server's own path is preferred, as it's what the
     * user knows their library by, but Jellyfin hides it from non-administrators, so there has to
     * be something to fall back to that still ends in a sensible file name.
     */
    private fun displayPath(track: JellyfinTrack): String {
        track.path?.replace('\\', '/')?.takeIf { it.trim('/').isNotEmpty() }?.let {
            return it
        }
        val artist = track.albumArtistNames.firstOrNull() ?: track.artistNames.firstOrNull()
        val number = track.trackNumber?.let { "%02d ".format(it) }.orEmpty()
        val extension = JellyfinFormats.extension(track.container, null)?.let { ".$it" }.orEmpty()
        return listOf(artist, track.albumName, "$number${track.name ?: track.id}$extension")
            .filterNotNull()
            .joinToString("/") { it.replace('/', '-') }
    }

    companion object {
        private val DAY = Regex("""^\d{4}-\d{2}-\d{2}""")

        /**
         * Work out the release [Date] of a track.
         *
         * The server's timestamps carry a time and fractional seconds that [Date] can't parse and
         * that are meaningless for a release date anyway (the time is always midnight), so only
         * the day is used. Servers use year 1 for "unknown", which is not a real date.
         *
         * @param premiere The ISO-8601 premiere timestamp, if any.
         * @param year The production year, used if there is no premiere timestamp.
         */
        internal fun parseDate(premiere: String?, year: Int?): Date? {
            val day = premiere?.let { DAY.find(it)?.value }?.let { Date.from(it) }?.realOrNull()
            return day ?: year?.let { Date.from(it) }?.realOrNull()
        }

        private fun Date.realOrNull() = takeIf { year >= MIN_YEAR }

        private const val MIN_YEAR = 1000
    }
}
