/*
 * Copyright (c) 2026 Auxio Project
 * JellyfinApi.kt is part of Auxio.
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

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.URL
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject

/**
 * What a server calls itself.
 *
 * @param url The base URL of the server, see [JellyfinUrls.normalize].
 * @param name The name of the server.
 * @param version The version of the server, if it said.
 */
data class JellyfinServer(val url: String, val name: String, val version: String?)

/**
 * The few parts of the Jellyfin HTTP API that Auxio needs. Every failure is reported as a
 * [JellyfinException], no matter where it came from.
 *
 * This uses the `HttpURLConnection` that comes with the platform, in order to not add a networking
 * dependency to an app that otherwise doesn't need one. Credentials only ever go in headers or
 * request bodies, never into URLs.
 */
class JellyfinApi @Inject constructor() {
    /**
     * Check that there is a Jellyfin server at [serverUrl].
     *
     * @param serverUrl The normalized base URL to check, see [JellyfinUrls.normalize].
     */
    suspend fun probe(serverUrl: String): JellyfinServer {
        val response = execute("GET", "$serverUrl/System/Info/Public", null, null)
        if (response.code in 400..499) {
            // Whatever answered here doesn't have the endpoint that every Jellyfin server has.
            throw JellyfinException(JellyfinException.Kind.NOT_JELLYFIN, "HTTP ${response.code}")
        }
        response.requireSuccess(JellyfinException.Kind.NOT_JELLYFIN)
        val json =
            try {
                JSONObject(response.body)
            } catch (e: JSONException) {
                throw JellyfinException(JellyfinException.Kind.NOT_JELLYFIN, "Not JSON", e)
            }
        // A bare "ProductName" check would reject forks, so check for the shape instead.
        val id = json.optString("Id").takeIf { it.isNotBlank() }
        val version = json.optString("Version").takeIf { it.isNotBlank() }
        if (id == null || version == null) {
            throw JellyfinException(JellyfinException.Kind.NOT_JELLYFIN, "Not a Jellyfin server")
        }
        val name = json.optString("ServerName").takeIf { it.isNotBlank() } ?: serverUrl
        return JellyfinServer(serverUrl, name, version)
    }

    /**
     * Sign in with a username and password. The password is sent once, and not kept.
     *
     * @param server The server to sign in to, as found by [probe].
     * @param username The name of the user.
     * @param password The password of the user. May be empty, as Jellyfin allows that.
     * @param device How to present this device to the server.
     */
    suspend fun signIn(
        server: JellyfinServer,
        username: String,
        password: String,
        device: JellyfinDevice,
    ): JellyfinSession {
        val body = JSONObject().put("Username", username).put("Pw", password).toString()
        val response =
            execute(
                "POST",
                "${server.url}/Users/AuthenticateByName",
                JellyfinUrls.authorization(device, null),
                body,
            )
        response.requireSuccess(JellyfinException.Kind.INVALID_CREDENTIALS)
        val json = response.json()
        val user =
            json.optJSONObject("User")
                ?: throw JellyfinException(JellyfinException.Kind.MALFORMED, "No user")
        val userId = user.optString("Id").takeIf { it.isNotBlank() }
        val token = json.optString("AccessToken").takeIf { it.isNotBlank() }
        if (userId == null || token == null) {
            throw JellyfinException(JellyfinException.Kind.MALFORMED, "No user ID or token")
        }
        return JellyfinSession(
            serverUrl = server.url,
            serverName = server.name,
            userId = userId,
            userName = user.optString("Name").takeIf { it.isNotBlank() } ?: username,
            accessToken = token,
        )
    }

    /**
     * Revoke the token of [session] on the server. Best-effort, as the user must be able to sign
     * out when the server is gone.
     */
    suspend fun signOut(session: JellyfinSession, device: JellyfinDevice) {
        try {
            execute(
                "POST",
                "${session.serverUrl}/Sessions/Logout",
                JellyfinUrls.authorization(device, session.accessToken),
                null,
            )
        } catch (e: JellyfinException) {
            // Nothing to do, the token stays valid on the server until the user revokes it there.
        }
    }

    /**
     * Load every song that the user of [session] can see. All or nothing: if any part fails, the
     * whole call fails, so that a partial catalog is never mistaken for a complete one.
     */
    suspend fun songs(session: JellyfinSession, device: JellyfinDevice): List<JellyfinTrack> =
        coroutineScope {
            val authorization = JellyfinUrls.authorization(device, session.accessToken)

            suspend fun page(start: Int): Pair<List<JellyfinTrack>, Int> {
                val url = JellyfinUrls.songs(session.serverUrl, session.userId, start, PAGE_SIZE)
                val response = execute("GET", url, authorization, null)
                response.requireSuccess(JellyfinException.Kind.UNAUTHORIZED)
                val json = response.json()
                val items =
                    json.optJSONArray("Items")
                        ?: throw JellyfinException(JellyfinException.Kind.MALFORMED, "No items")
                val tracks =
                    (0 until items.length()).mapNotNull { index ->
                        items.optJSONObject(index)?.let { JellyfinTrack.fromJson(it) }
                    }
                return tracks to json.optInt("TotalRecordCount", 0)
            }

            val (firstTracks, total) = page(0)
            // The remaining pages are independent of each other. A small amount of parallelism
            // makes a big difference on libraries with tens of thousands of songs, while not
            // overloading what is probably a small home server.
            val gate = Semaphore(PARALLEL_PAGES)
            val rest =
                (PAGE_SIZE until total step PAGE_SIZE)
                    .map { start -> async { gate.withPermit { page(start).first } } }
                    .awaitAll()
                    .flatten()
            // The server may have changed while paging, so the same song can appear twice.
            (firstTracks + rest).distinctBy { it.id }
        }

    /**
     * Download an image into a file.
     *
     * @param url The URL of the image, which must be on the server of [session].
     * @param into The file to write. Only replaced once the whole image has been received.
     */
    suspend fun download(
        session: JellyfinSession,
        device: JellyfinDevice,
        url: String,
        into: File,
    ): Unit =
        withContext(Dispatchers.IO) {
            if (!JellyfinUrls.isOnServer(session.serverUrl, url)) {
                throw JellyfinException(JellyfinException.Kind.INVALID_URL, "Not on the server")
            }
            val connection =
                open("GET", url, JellyfinUrls.authorization(device, session.accessToken))
            val partial = File(into.parentFile, "${into.name}.${System.nanoTime()}.part")
            try {
                if (connection.responseCode !in 200..299) {
                    throw JellyfinException(
                        JellyfinException.Kind.SERVER_ERROR,
                        "HTTP ${connection.responseCode}",
                    )
                }
                partial.outputStream().use { out ->
                    connection.inputStream.use { it.copyLimited(out, MAX_IMAGE_BYTES) }
                }
                if (!partial.renameTo(into)) {
                    throw IOException("Could not move image into place")
                }
            } catch (e: JellyfinException) {
                throw e
            } catch (e: IOException) {
                throw JellyfinException(JellyfinException.Kind.UNREACHABLE, "Download failed", e)
            } finally {
                connection.disconnect()
                partial.delete()
            }
        }

    private class Response(val code: Int, val body: String) {
        fun requireSuccess(unauthorized: JellyfinException.Kind) {
            when (code) {
                in 200..299 -> {}
                401,
                403 -> throw JellyfinException(unauthorized, "HTTP $code")
                else -> throw JellyfinException(JellyfinException.Kind.SERVER_ERROR, "HTTP $code")
            }
        }

        fun json(): JSONObject =
            try {
                JSONObject(body)
            } catch (e: JSONException) {
                throw JellyfinException(JellyfinException.Kind.MALFORMED, "Bad JSON", e)
            }
    }

    private suspend fun execute(
        method: String,
        url: String,
        authorization: String?,
        body: String?,
    ): Response =
        withContext(Dispatchers.IO) {
            val connection = open(method, url, authorization)
            try {
                if (body != null) {
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                }
                val code = connection.responseCode
                val stream =
                    if (code in 200..299) connection.inputStream else connection.errorStream
                Response(code, stream?.use { it.readLimited(MAX_RESPONSE_BYTES) }.orEmpty())
            } catch (e: JellyfinException) {
                throw e
            } catch (e: IOException) {
                throw JellyfinException(JellyfinException.Kind.UNREACHABLE, "Request failed", e)
            } finally {
                connection.disconnect()
            }
        }

    private fun open(method: String, url: String, authorization: String?): HttpURLConnection {
        val connection =
            try {
                URL(url).openConnection() as HttpURLConnection
            } catch (e: MalformedURLException) {
                throw JellyfinException(JellyfinException.Kind.INVALID_URL, "Bad URL", e)
            } catch (e: IOException) {
                throw JellyfinException(JellyfinException.Kind.UNREACHABLE, "Can't connect", e)
            } catch (e: ClassCastException) {
                throw JellyfinException(JellyfinException.Kind.INVALID_URL, "Not HTTP", e)
            }
        connection.requestMethod = method
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.setRequestProperty("Accept", "application/json")
        if (authorization != null) {
            connection.setRequestProperty("Authorization", authorization)
        }
        return connection
    }

    /** Read a whole stream as text, refusing to buffer an absurd amount of it. */
    private fun InputStream.readLimited(limit: Int): String {
        val out = java.io.ByteArrayOutputStream()
        copyLimited(out, limit)
        return out.toString(Charsets.UTF_8.name())
    }

    private fun InputStream.copyLimited(out: java.io.OutputStream, limit: Int) {
        val buffer = ByteArray(BUFFER_BYTES)
        var total = 0
        while (true) {
            val read = read(buffer)
            if (read < 0) {
                return
            }
            total += read
            if (total > limit) {
                throw JellyfinException(JellyfinException.Kind.MALFORMED, "Response too large")
            }
            out.write(buffer, 0, read)
        }
    }

    private companion object {
        const val PAGE_SIZE = 500
        const val PARALLEL_PAGES = 4
        const val CONNECT_TIMEOUT_MS = 8_000
        const val READ_TIMEOUT_MS = 30_000
        const val BUFFER_BYTES = 16 * 1024
        const val MAX_RESPONSE_BYTES = 64 * 1024 * 1024
        const val MAX_IMAGE_BYTES = 24 * 1024 * 1024
    }
}
