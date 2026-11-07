/*
 * Copyright (c) 2026 Auxio Project
 * JellyfinSession.kt is part of Auxio.
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

import java.io.IOException

/**
 * A signed-in Jellyfin account on a particular server.
 *
 * @param serverUrl The canonical base URL of the server, without a trailing slash. See
 *   [JellyfinUrls.normalize].
 * @param serverName The human-readable name of the server.
 * @param userId The Jellyfin ID of the user.
 * @param userName The name of the user.
 * @param accessToken The token that authorizes requests on behalf of the user. This is **not** the
 *   password, which is never stored.
 */
data class JellyfinSession(
    val serverUrl: String,
    val serverName: String,
    val userId: String,
    val userName: String,
    val accessToken: String,
) {
    // Keep the token out of logs.
    override fun toString() = "JellyfinSession(user=$userName, server=$serverUrl)"
}

/**
 * Identifies this installation of the app to the server. Jellyfin lists these under "Devices" in
 * its dashboard.
 *
 * @param client The name of this app.
 * @param name The name of this device.
 * @param id A unique ID of this installation.
 * @param version The version of this app.
 */
data class JellyfinDevice(val client: String, val name: String, val id: String, val version: String)

/**
 * Outcome of the most recent attempt to synchronize the catalog with the server.
 *
 * @param timeMs When the attempt finished, in milliseconds since the epoch.
 * @param songCount The amount of songs that the catalog contained after the attempt.
 * @param failure Why the attempt failed, or null if it succeeded.
 */
data class JellyfinSyncState(
    val timeMs: Long,
    val songCount: Int,
    val failure: JellyfinException.Kind?,
)

/** Something went wrong while talking to a Jellyfin server. */
class JellyfinException(val kind: Kind, message: String, cause: Throwable? = null) :
    IOException(message, cause) {
    enum class Kind {
        /** The URL that the user entered can't be a server URL. */
        INVALID_URL,
        /** Couldn't connect to the server at all. */
        UNREACHABLE,
        /** There is something at the URL, but it isn't a Jellyfin server. */
        NOT_JELLYFIN,
        /** The server rejected the username or password. */
        INVALID_CREDENTIALS,
        /** The server rejected the access token, the user has to sign in again. */
        UNAUTHORIZED,
        /** The server answered with an error. */
        SERVER_ERROR,
        /** The server answered with something that doesn't make sense. */
        MALFORMED,
    }
}
