/*
 * Copyright (c) 2026 Auxio Project
 * JellyfinAuthorizer.kt is part of Auxio.
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

import javax.inject.Inject

/**
 * Decides which requests the player may make, and authorizes them.
 *
 * The access token is a secret, so it must only ever go to the server it was issued by. Everything
 * that opens a network connection on behalf of the player has to go through here.
 */
class JellyfinAuthorizer @Inject constructor(private val store: JellyfinSessionStore) {
    /**
     * Get the headers that make a request to [url] authorized.
     *
     * @param url The URL that is about to be opened.
     * @return The headers to add to the request.
     * @throws JellyfinException If nobody is signed in or [url] is not on the server that they are
     *   signed in to. No request must be made in that case.
     */
    fun headersFor(url: String): Map<String, String> {
        val session =
            store.session()
                ?: throw JellyfinException(JellyfinException.Kind.UNAUTHORIZED, "Not signed in")
        if (!JellyfinUrls.isOnServer(session.serverUrl, url)) {
            throw JellyfinException(JellyfinException.Kind.INVALID_URL, "Not the signed-in server")
        }
        val authorization = JellyfinUrls.authorization(store.device(), session.accessToken)
        return mapOf("Authorization" to authorization)
    }
}
