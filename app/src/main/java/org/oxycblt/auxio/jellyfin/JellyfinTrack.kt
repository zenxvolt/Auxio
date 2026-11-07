/*
 * Copyright (c) 2026 Auxio Project
 * JellyfinTrack.kt is part of Auxio.
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

import java.time.Instant
import org.json.JSONArray
import org.json.JSONObject

/**
 * A song as reported by a Jellyfin server, reduced to the parts that Auxio cares about.
 *
 * This is the only type that knows what the Jellyfin API's JSON looks like. [toJson] writes the
 * same shape (a subset of it), so that the local catalog snapshot can be read by the exact same
 * parser as a live server response.
 *
 * @param id The Jellyfin item ID of the song.
 * @param name The name of the song.
 * @param albumId The Jellyfin item ID of the album of the song.
 * @param albumName The name of the album of the song.
 * @param albumImageTag The tag of the primary image of the album, if it has one.
 * @param imageTag The tag of the primary image of the song itself, if it has one.
 * @param artistNames The names of the artists of the song.
 * @param albumArtistNames The names of the artists of the album of the song.
 * @param genres The names of the genres of the song.
 * @param trackNumber The track number of the song.
 * @param discNumber The disc number of the song.
 * @param premiereDate An ISO-8601 timestamp of when the song was released.
 * @param year The year the song was released, used when there is no [premiereDate].
 * @param durationMs The duration of the song in milliseconds.
 * @param dateCreatedMs When the song was added to the server in milliseconds since the epoch.
 * @param path The path of the song's file on the server.
 * @param container The container of the file, i.e `flac`. Might be a comma-separated list.
 * @param sizeBytes The size of the file in bytes.
 * @param bitrateBps The bitrate of the file in bits per second.
 * @param codec The audio codec of the file, i.e `aac`.
 * @param sampleRateHz The sample rate of the audio in hertz.
 * @param normalizationGain The ReplayGain track adjustment in decibels.
 */
data class JellyfinTrack(
    val id: String,
    val name: String?,
    val albumId: String?,
    val albumName: String?,
    val albumImageTag: String?,
    val imageTag: String?,
    val artistNames: List<String>,
    val albumArtistNames: List<String>,
    val genres: List<String>,
    val trackNumber: Int?,
    val discNumber: Int?,
    val premiereDate: String?,
    val year: Int?,
    val durationMs: Long,
    val dateCreatedMs: Long?,
    val path: String?,
    val container: String?,
    val sizeBytes: Long?,
    val bitrateBps: Int?,
    val codec: String?,
    val sampleRateHz: Int?,
    val normalizationGain: Float?,
) {
    /** Serialize this track into the same JSON shape that the Jellyfin API uses. */
    fun toJson(): JSONObject {
        val json = JSONObject()
        json.put("Id", id)
        json.put("Name", name)
        json.put("AlbumId", albumId)
        json.put("Album", albumName)
        json.put("AlbumPrimaryImageTag", albumImageTag)
        if (imageTag != null) {
            json.put("ImageTags", JSONObject().put("Primary", imageTag))
        }
        json.put("Artists", JSONArray(artistNames))
        json.put(
            "AlbumArtists",
            JSONArray().apply { albumArtistNames.forEach { put(JSONObject().put("Name", it)) } },
        )
        json.put("Genres", JSONArray(genres))
        json.put("IndexNumber", trackNumber)
        json.put("ParentIndexNumber", discNumber)
        json.put("PremiereDate", premiereDate)
        json.put("ProductionYear", year)
        json.put("RunTimeTicks", durationMs * TICKS_PER_MS)
        json.put("DateCreated", dateCreatedMs?.let { Instant.ofEpochMilli(it).toString() })
        json.put("Path", path)
        json.put("NormalizationGain", normalizationGain)
        if (container != null || sizeBytes != null || bitrateBps != null || codec != null) {
            val stream =
                JSONObject()
                    .put("Type", "Audio")
                    .put("Codec", codec)
                    .put("SampleRate", sampleRateHz)
                    .put("BitRate", bitrateBps)
            val source =
                JSONObject()
                    .put("Container", container)
                    .put("Size", sizeBytes)
                    .put("Bitrate", bitrateBps)
                    .put("MediaStreams", JSONArray().put(stream))
            json.put("MediaSources", JSONArray().put(source))
        }
        return json
    }

    companion object {
        private const val TICKS_PER_MS = 10_000L

        /**
         * Parse a track out of a Jellyfin `BaseItemDto`.
         *
         * @param json The item to parse.
         * @return The track, or null if the item can't be a playable song (i.e it has no ID).
         */
        fun fromJson(json: JSONObject): JellyfinTrack? {
            val id = json.string("Id") ?: return null
            val source = json.optJSONArray("MediaSources")?.optJSONObject(0)
            val audio =
                source?.optJSONArray("MediaStreams")?.firstObject { it.string("Type") == "Audio" }
            val ticks = json.long("RunTimeTicks") ?: source?.long("RunTimeTicks") ?: 0L
            return JellyfinTrack(
                id = id,
                name = json.string("Name"),
                albumId = json.string("AlbumId"),
                albumName = json.string("Album"),
                albumImageTag = json.string("AlbumPrimaryImageTag"),
                imageTag = json.optJSONObject("ImageTags")?.string("Primary"),
                artistNames = json.strings("Artists"),
                albumArtistNames = json.names("AlbumArtists"),
                genres = json.strings("Genres"),
                trackNumber = json.int("IndexNumber"),
                discNumber = json.int("ParentIndexNumber"),
                premiereDate = json.string("PremiereDate"),
                year = json.int("ProductionYear"),
                durationMs = ticks / TICKS_PER_MS,
                dateCreatedMs = json.string("DateCreated")?.let(::parseTimestamp),
                path = json.string("Path"),
                container = source?.string("Container") ?: json.string("Container"),
                sizeBytes = source?.long("Size"),
                bitrateBps = audio?.int("BitRate") ?: source?.int("Bitrate"),
                codec = audio?.string("Codec"),
                sampleRateHz = audio?.int("SampleRate"),
                normalizationGain = json.float("NormalizationGain"),
            )
        }

        /** Parse an ISO-8601 timestamp (as given by the server) into epoch milliseconds. */
        private fun parseTimestamp(timestamp: String) =
            try {
                Instant.parse(timestamp).toEpochMilli()
            } catch (e: java.time.format.DateTimeParseException) {
                null
            }
    }
}

// org.json's opt* methods are very lenient about nulls: optString on a JSON null returns the
// literal text "null", so everything below checks isNull first.

private fun JSONObject.string(key: String): String? =
    if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

private fun JSONObject.int(key: String): Int? = (opt(key) as? Number)?.toInt()

private fun JSONObject.long(key: String): Long? = (opt(key) as? Number)?.toLong()

private fun JSONObject.float(key: String): Float? = (opt(key) as? Number)?.toFloat()

/** Read an array of strings. */
private fun JSONObject.strings(key: String): List<String> {
    val array = optJSONArray(key) ?: return emptyList()
    return (0 until array.length()).mapNotNull { index ->
        if (array.isNull(index)) null else array.optString(index).takeIf { it.isNotBlank() }
    }
}

/** Read the `Name` of every object in an array of objects. */
private fun JSONObject.names(key: String): List<String> {
    val array = optJSONArray(key) ?: return emptyList()
    return (0 until array.length()).mapNotNull { index ->
        array.optJSONObject(index)?.string("Name")
    }
}

private fun JSONArray.firstObject(predicate: (JSONObject) -> Boolean): JSONObject? {
    for (index in 0 until length()) {
        val item = optJSONObject(index) ?: continue
        if (predicate(item)) {
            return item
        }
    }
    return null
}
