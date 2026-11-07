/*
 * Copyright (c) 2026 Auxio Project
 * RemoteSongs.kt is part of Auxio.
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
 
package org.oxycblt.musikr.remote

import android.net.Uri
import org.oxycblt.musikr.covers.Cover
import org.oxycblt.musikr.fs.Path
import org.oxycblt.musikr.metadata.Properties
import org.oxycblt.musikr.tag.parse.ParsedTags

/**
 * A source of songs that don't live on the device's file system, such as a media server.
 *
 * Remote songs skip exploration and tag extraction entirely, since the source already knows all of
 * their metadata. They are fed straight into the same evaluation step as on-device songs, so once
 * they are in the library they behave like any other [org.oxycblt.musikr.Song]. The only
 * difference is that [org.oxycblt.musikr.Song.uri] points at something the player has to stream.
 *
 * Remote songs are always added *after* on-device songs. If a remote song would end up with the
 * same UID as an on-device song (i.e it has the same tags), the on-device song wins and the remote
 * one is dropped, exactly like any other duplicate.
 */
interface RemoteSongs {
    /**
     * Load every song of this source.
     *
     * This runs concurrently with the on-device pipeline, and implementations are responsible for
     * keeping themselves reasonably fast (timeouts, caching). Throwing is acceptable: Musikr logs
     * it and carries on with only the on-device songs, so an unreachable server can never prevent
     * the rest of the library from loading.
     *
     * @return The songs of the source. May be empty.
     */
    suspend fun load(): List<RemoteSong>
}

/**
 * A song provided by a [RemoteSongs] source.
 *
 * @param uri The [Uri] that the player should open to stream the song.
 * @param path A path to display for the song. It never has to point at anything real, but it should
 *   have a stable file-like name since parts of Musikr fall back to it when tags are missing.
 * @param mimeType The MIME type of the container, i.e `audio/flac`.
 * @param size The size of the song in bytes, or 0 if not known.
 * @param addedMs When the song was added to the source, in milliseconds since the epoch.
 * @param modifiedMs When the song was last modified, in milliseconds since the epoch.
 * @param properties Technical properties of the song.
 * @param tags The tags of the song.
 * @param cover The cover of the song, if there is any.
 */
data class RemoteSong(
    val uri: Uri,
    val path: Path,
    val mimeType: String,
    val size: Long,
    val addedMs: Long,
    val modifiedMs: Long,
    val properties: Properties,
    val tags: ParsedTags,
    val cover: Cover?,
)
