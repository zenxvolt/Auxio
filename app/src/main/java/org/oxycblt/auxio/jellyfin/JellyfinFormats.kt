/*
 * Copyright (c) 2026 Auxio Project
 * JellyfinFormats.kt is part of Auxio.
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

import java.util.Locale

/**
 * Translates the vocabulary of Jellyfin (ffmpeg container and codec names) into the MIME types that
 * Musikr uses to work out what [org.oxycblt.musikr.fs.Format] a song has. The MIME types here are
 * deliberately the same ones that the on-device tag extractor reports, so that local and remote
 * songs of the same kind are shown identically.
 */
internal object JellyfinFormats {
    private const val FALLBACK_MIME = "application/octet-stream"

    private data class Container(
        val mimeType: String,
        val extension: String,
        val playable: Boolean = true,
    )

    /** Container names, as given by ffmpeg/Jellyfin or as a file extension. */
    private val CONTAINERS =
        mapOf(
            "flac" to Container("audio/flac", "flac"),
            "mp3" to Container("audio/mpeg", "mp3"),
            "mp4" to Container("audio/mp4", "m4a"),
            "m4a" to Container("audio/mp4", "m4a"),
            "m4b" to Container("audio/mp4", "m4a"),
            "mov" to Container("audio/mp4", "m4a"),
            "3gp" to Container("audio/mp4", "m4a"),
            "ogg" to Container("audio/ogg", "ogg"),
            "oga" to Container("audio/ogg", "ogg"),
            "opus" to Container("audio/ogg", "opus"),
            "wav" to Container("audio/wav", "wav"),
            "aac" to Container("audio/aac", "aac"),
            "aiff" to Container("audio/aiff", "aiff", playable = false),
            "aif" to Container("audio/aiff", "aiff", playable = false),
            "matroska" to Container("audio/x-matroska", "mka"),
            "mka" to Container("audio/x-matroska", "mka"),
            "webm" to Container("audio/webm", "webm"),
            "asf" to Container("audio/x-ms-wma", "wma", playable = false),
            "wma" to Container("audio/x-ms-wma", "wma", playable = false),
            "ape" to Container("audio/x-ape", "ape", playable = false),
            "wv" to Container("audio/x-wavpack", "wv", playable = false),
            "dsf" to Container("audio/x-dsf", "dsf", playable = false),
        )

    /** Codec names, as given by ffmpeg/Jellyfin. */
    private val CODECS =
        mapOf(
            "mp3" to "audio/mpeg",
            "aac" to "audio/aac",
            "alac" to "audio/alac",
            "flac" to "audio/flac",
            "opus" to "audio/opus",
            "vorbis" to "audio/vorbis",
        )

    /**
     * The MIME type of the container of a file.
     *
     * @param container The container as reported by Jellyfin. This may be a comma-separated list
     *   of names, since ffmpeg uses a single demuxer for several related formats (i.e
     *   `mov,mp4,m4a,3gp,3g2,mj2`).
     * @param path The path of the file, used if the container is unknown.
     */
    fun containerMimeType(container: String?, path: String?): String =
        find(container?.let(::tokens).orEmpty())?.mimeType
            ?: find(listOfNotNull(path?.let(::extensionOf)))?.mimeType
            ?: FALLBACK_MIME

    /**
     * The MIME type of the codec of a song.
     *
     * @param codec The codec as reported by Jellyfin.
     * @param containerMimeType The result of [containerMimeType], used when the codec has no MIME
     *   type of its own (i.e WMA), since the container is then the best description available.
     */
    fun codecMimeType(codec: String?, containerMimeType: String): String {
        val name = codec?.trim()?.lowercase(Locale.ROOT)
        return when {
            name == null -> containerMimeType
            name.startsWith("pcm_") -> "audio/wav"
            else -> CODECS[name] ?: containerMimeType
        }
    }

    /**
     * Whether the player can demux the container of a file. Formats that Android has no extractor
     * for (i.e WMA) would only ever fail at the moment they are played, so it is better to know
     * about them beforehand. Containers that aren't known at all are given the benefit of doubt.
     */
    fun isPlayable(container: String?, path: String?): Boolean =
        (find(container?.let(::tokens).orEmpty()) ?: find(listOfNotNull(path?.let(::extensionOf))))
            ?.playable != false

    /** The file extension that goes with a container, without the dot. */
    fun extension(container: String?, path: String?): String? =
        (find(container?.let(::tokens).orEmpty()) ?: find(listOfNotNull(path?.let(::extensionOf))))
            ?.extension

    private fun find(names: List<String>) = names.firstNotNullOfOrNull { CONTAINERS[it] }

    private fun tokens(container: String) =
        container.split(',').map { it.trim().lowercase(Locale.ROOT) }.filter { it.isNotEmpty() }

    private fun extensionOf(path: String): String? {
        val name = path.substringAfterLast('/').substringAfterLast('\\')
        return name.substringAfterLast('.', "").lowercase(Locale.ROOT).takeIf { it.isNotEmpty() }
    }
}
