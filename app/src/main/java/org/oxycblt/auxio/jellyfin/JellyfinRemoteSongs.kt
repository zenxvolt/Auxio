/*
 * Copyright (c) 2026 Auxio Project
 * JellyfinRemoteSongs.kt is part of Auxio.
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

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import org.oxycblt.musikr.remote.RemoteSong
import org.oxycblt.musikr.remote.RemoteSongs
import timber.log.Timber as L

/**
 * Provides the songs of the signed-in Jellyfin account to the music library.
 *
 * Whatever happens, this returns a catalog if there is any to return. When the server can't be
 * reached, is too slow, or answers with an error, the snapshot of the last successful load is used
 * instead, so being offline never makes the remote part of the library disappear. The outcome is
 * recorded, so that the settings can say what is going on.
 *
 * @param session The account to load the catalog of.
 * @param device How the app presents itself to the server.
 * @param fetch Gets the catalog from the server.
 * @param snapshots Where the last successful catalog is kept.
 * @param isCurrent Whether [session] is still the account that is signed in. If the user signs
 *   out or switches while loading, nothing is recorded for the old account.
 * @param onSynced Receives the outcome of the load.
 * @param map Translates a track into a song, or null if the track can't be played.
 * @param nowMs The current time in milliseconds since the epoch.
 * @param snapshotBudgetMs How long to wait for the server when there is a snapshot to fall back
 *   on. Short, as the library can't appear before this is done and a stale catalog is fine.
 * @param coldBudgetMs How long to wait for the server when there is no snapshot. Long, as it is
 *   the only way to get any remote songs at all.
 */
internal class JellyfinRemoteSongs(
    private val session: JellyfinSession,
    private val device: JellyfinDevice,
    private val fetch: suspend (JellyfinSession, JellyfinDevice) -> List<JellyfinTrack>,
    private val snapshots: JellyfinSnapshotStore,
    private val isCurrent: () -> Boolean,
    private val onSynced: (JellyfinSyncState) -> Unit,
    private val map: (JellyfinTrack) -> RemoteSong?,
    private val nowMs: () -> Long = System::currentTimeMillis,
    private val snapshotBudgetMs: Long = SNAPSHOT_BUDGET_MS,
    private val coldBudgetMs: Long = COLD_BUDGET_MS,
) : RemoteSongs {
    override suspend fun load(): List<RemoteSong> {
        val budget = if (snapshots.has(session)) snapshotBudgetMs else coldBudgetMs
        var failure: JellyfinException.Kind? = null
        val live =
            try {
                withTimeoutOrNull(budget) { fetch(session, device) }
                    .also { if (it == null) failure = JellyfinException.Kind.UNREACHABLE }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Anything at all that goes wrong here is a reason to fall back to the snapshot.
                L.w(e, "Could not load the Jellyfin catalog")
                failure = (e as? JellyfinException)?.kind ?: JellyfinException.Kind.MALFORMED
                null
            }
        val current = isCurrent()
        val tracks =
            if (live != null) {
                if (current) {
                    snapshots.write(session, live)
                }
                live
            } else {
                L.w("Falling back to the Jellyfin snapshot (${failure})")
                snapshots.read(session).orEmpty()
            }
        if (current) {
            onSynced(JellyfinSyncState(nowMs(), tracks.size, failure))
        }
        val songs = tracks.mapNotNull(map)
        L.d("Loaded ${songs.size}/${tracks.size} Jellyfin songs")
        return songs
    }

    private companion object {
        const val SNAPSHOT_BUDGET_MS = 20_000L
        const val COLD_BUDGET_MS = 60_000L
    }
}
