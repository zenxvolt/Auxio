/*
 * Copyright (c) 2026 Auxio Project
 * JellyfinRemoteSongsFactory.kt is part of Auxio.
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
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import org.oxycblt.auxio.image.CoverMode
import org.oxycblt.auxio.image.ImageSettings
import org.oxycblt.musikr.covers.Cover
import org.oxycblt.musikr.remote.RemoteSongs

/** Creates the [RemoteSongs] for the Jellyfin account that is signed in, if there is one. */
class JellyfinRemoteSongsFactory
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val store: JellyfinSessionStore,
    private val api: JellyfinApi,
    private val snapshots: JellyfinSnapshotStore,
    private val imageSettings: ImageSettings,
) {
    /** @return The songs to add to the library, or null if there's nothing to add. */
    fun create(): RemoteSongs? {
        val session = store.session() ?: return null
        val covers = JellyfinCovers(context)
        val variant = coverVariant()
        val mapper =
            JellyfinSongMapper(session.serverUrl) { track ->
                variant?.let { coverOf(covers, it, track) }
            }
        return JellyfinRemoteSongs(
            session = session,
            device = store.device(),
            fetch = api::songs,
            snapshots = snapshots,
            isCurrent = { store.session() == session },
            onSynced = { store.syncState = it },
            map = mapper::map,
        )
    }

    private fun coverVariant() =
        // Mirrors SettingCovers, so that remote and local covers follow the same setting.
        when (imageSettings.coverMode) {
            CoverMode.OFF -> null
            CoverMode.SAVE_SPACE -> JellyfinCoverVariant(500, 70)
            CoverMode.BALANCED -> JellyfinCoverVariant(750, 85)
            CoverMode.HIGH_QUALITY -> JellyfinCoverVariant(1000, 100)
            CoverMode.AS_IS -> JellyfinCoverVariant(null, null)
        }

    private fun coverOf(
        covers: JellyfinCovers,
        variant: JellyfinCoverVariant,
        track: JellyfinTrack,
    ): Cover? {
        // Prefer the image of the album, so that every song of an album shares one cover (and one
        // download) rather than each getting a copy.
        val album = track.albumId?.let { id -> track.albumImageTag?.let { id to it } }
        val own = track.imageTag?.let { track.id to it }
        val (itemId, tag) = album ?: own ?: return null
        return covers.cover(variant, itemId, tag)
    }
}
