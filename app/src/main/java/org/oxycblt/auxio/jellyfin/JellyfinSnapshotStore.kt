/*
 * Copyright (c) 2026 Auxio Project
 * JellyfinSnapshotStore.kt is part of Auxio.
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
import java.io.File
import java.io.IOException
import org.json.JSONException
import org.json.JSONObject

/**
 * A copy of the last catalog that was loaded from a Jellyfin server.
 *
 * The music library is rebuilt every time that the app starts, and it can't be built without the
 * server unless there is something to fall back to. With a snapshot, the app still shows (and can
 * browse, search, and queue) the remote songs when the server is unreachable, instead of making
 * them vanish.
 *
 * The file has one JSON document per line, with a header first. That way it can be written and read
 * song by song, without ever holding the whole catalog as text.
 *
 * @param file The file to keep the snapshot in.
 */
class JellyfinSnapshotStore(private val file: File) {
    constructor(context: Context) : this(File(context.filesDir, "jellyfin/catalog.jsonl"))

    /** Replace the snapshot with [tracks], which are from the account of [session]. */
    fun write(session: JellyfinSession, tracks: List<JellyfinTrack>) {
        val directory = file.parentFile
        try {
            directory?.mkdirs()
            // Write beside the real file and swap, so that a crash or a full disk can never
            // leave behind a half-written snapshot.
            val partial = File(directory, "${file.name}.part")
            partial.bufferedWriter(Charsets.UTF_8).use { out ->
                out.write(header(session).toString())
                out.newLine()
                for (track in tracks) {
                    out.write(track.toJson().toString())
                    out.newLine()
                }
            }
            if (!partial.renameTo(file)) {
                partial.delete()
                throw IOException("Could not move snapshot into place")
            }
        } catch (e: IOException) {
            // A snapshot is only a nicety, never fail the load because of it. If this one can't be
            // replaced, a stale one is worse than none.
            file.delete()
        }
    }

    /** Whether there is a snapshot for the account of [session]. Cheaper than [read]. */
    fun has(session: JellyfinSession): Boolean =
        try {
            file.bufferedReader(Charsets.UTF_8).use { matches(it.readLine(), session) }
        } catch (e: IOException) {
            false
        }

    /**
     * Read the snapshot.
     *
     * @return The tracks, or null if there is no snapshot for the account of [session] or if it
     *   is damaged.
     */
    fun read(session: JellyfinSession): List<JellyfinTrack>? =
        try {
            file.bufferedReader(Charsets.UTF_8).use { reader ->
                if (!matches(reader.readLine(), session)) {
                    return null
                }
                val tracks = mutableListOf<JellyfinTrack>()
                for (line in reader.lineSequence()) {
                    if (line.isNotBlank()) {
                        JellyfinTrack.fromJson(JSONObject(line))?.let(tracks::add)
                    }
                }
                tracks
            }
        } catch (e: IOException) {
            null
        } catch (e: JSONException) {
            null
        }

    /** Delete the snapshot. */
    fun clear() {
        file.delete()
        File(file.parentFile, "${file.name}.part").delete()
    }

    private fun header(session: JellyfinSession) =
        JSONObject()
            .put("version", VERSION)
            .put("server", session.serverUrl)
            .put("user", session.userId)

    private fun matches(line: String?, session: JellyfinSession): Boolean {
        if (line == null) {
            return false
        }
        val json =
            try {
                JSONObject(line)
            } catch (e: JSONException) {
                return false
            }
        return json.optInt("version", -1) == VERSION &&
            json.optString("server") == session.serverUrl &&
            json.optString("user") == session.userId
    }

    private companion object {
        const val VERSION = 1
    }
}
