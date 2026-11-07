/*
 * Copyright (c) 2026 Auxio Project
 * JellyfinUrls.kt is part of Auxio.
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

import java.net.URI
import java.net.URLEncoder
import java.util.Locale

/** Pure helpers for building the URLs and headers needed to talk to a Jellyfin server. */
object JellyfinUrls {
    private const val STREAM_PATH = "/Audio/%s/stream?static=true"

    /**
     * Interpret whatever the user typed as a server address.
     *
     * If they left out the scheme, both HTTPS and plain HTTP are possible, so both are returned,
     * with HTTPS first. The results are not verified in any way, see [normalize].
     *
     * @param input The text the user entered.
     * @return The candidate base URLs to try, in order of preference. Empty if the input is not
     *   something that could be an address.
     */
    fun candidates(input: String): List<String> {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) {
            return emptyList()
        }
        val withScheme =
            if (trimmed.contains("://")) {
                listOf(trimmed)
            } else {
                listOf("https://$trimmed", "http://$trimmed")
            }
        return withScheme.mapNotNull(::normalize).distinct()
    }

    /**
     * Normalize a server address into the form used everywhere else: `scheme://host[:port][/base]`
     * with no trailing slash. Anything that isn't part of the address of the API itself is removed:
     * credentials, query, fragment, and the `/web` path that people tend to copy from their
     * browser.
     *
     * @param url The URL to normalize.
     * @return The normalized URL, or null if [url] is not an HTTP(S) URL with a host.
     */
    fun normalize(url: String): String? {
        val uri =
            try {
                URI(url.trim())
            } catch (e: java.net.URISyntaxException) {
                return null
            }
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        if (scheme != "http" && scheme != "https") {
            return null
        }
        val host = uri.host?.lowercase(Locale.ROOT)
        if (host.isNullOrEmpty()) {
            return null
        }
        val authority = if (uri.port != -1) "$host:${uri.port}" else host
        // The base path is where the server is mounted when it sits behind a reverse proxy, i.e
        // https://example.com/jellyfin. The web client lives in /web below it.
        var path = uri.rawPath.orEmpty()
        val web = Regex("(?i)/web(/.*)?$").find(path)
        if (web != null) {
            path = path.substring(0, web.range.first)
        }
        path = path.trimEnd('/')
        return "$scheme://$authority$path"
    }

    /**
     * The URL that streams the original file of a song.
     *
     * @param serverUrl The base URL of the server.
     * @param itemId The ID of the song.
     */
    fun stream(serverUrl: String, itemId: String) = serverUrl + STREAM_PATH.format(encode(itemId))

    /**
     * The URL of the primary image of an item.
     *
     * @param serverUrl The base URL of the server.
     * @param itemId The ID of the item.
     * @param tag The tag of the image. Lets the server say that the image is cacheable forever.
     * @param maxSize The maximum width and height the server should scale the image down to.
     * @param quality The JPEG quality (0-100) to ask the server to re-encode the image with. Only
     *   applies when the image is scaled.
     */
    fun image(
        serverUrl: String,
        itemId: String,
        tag: String?,
        maxSize: Int?,
        quality: Int?,
    ): String {
        val query = mutableListOf<String>()
        if (tag != null) query.add("tag=${encode(tag)}")
        if (maxSize != null) query.add("maxWidth=$maxSize&maxHeight=$maxSize")
        if (quality != null) query.add("quality=$quality")
        val suffix = if (query.isEmpty()) "" else "?" + query.joinToString("&")
        return "$serverUrl/Items/${encode(itemId)}/Images/Primary$suffix"
    }

    /**
     * The URL of one page of every song that the user can see, across all of their libraries.
     *
     * @param serverUrl The base URL of the server.
     * @param userId The ID of the user.
     * @param startIndex The index of the first song in the page.
     * @param limit The amount of songs in the page.
     */
    fun songs(serverUrl: String, userId: String, startIndex: Int, limit: Int) =
        "$serverUrl/Items?userId=${encode(userId)}&includeItemTypes=Audio&recursive=true" +
            "&fields=Genres,DateCreated,MediaSources,Path&sortBy=SortName&sortOrder=Ascending" +
            "&startIndex=$startIndex&limit=$limit"

    /** Build the `Authorization` header value that identifies [device] and optionally a user. */
    fun authorization(device: JellyfinDevice, accessToken: String?): String {
        val parts =
            mutableListOf(
                "Client" to device.client,
                "Device" to device.name,
                "DeviceId" to device.id,
                "Version" to device.version,
            )
        if (accessToken != null) {
            parts.add("Token" to accessToken)
        }
        return "MediaBrowser " +
            parts.joinToString(", ") { (key, value) -> "$key=\"${clean(value)}\"" }
    }

    /**
     * Whether [url] is on the server at [serverUrl]. The access token must only ever be sent to
     * the server it belongs to, so this must be true before it is attached to a request.
     */
    fun isOnServer(serverUrl: String, url: String): Boolean {
        val server = parse(serverUrl) ?: return false
        val target = parse(url) ?: return false
        return server.scheme == target.scheme &&
            server.host == target.host &&
            server.port == target.port &&
            (target.path == server.path || target.path.startsWith(server.path + "/"))
    }

    private data class Address(
        val scheme: String,
        val host: String,
        val port: Int,
        val path: String,
    )

    private fun parse(url: String): Address? {
        val uri =
            try {
                URI(url)
            } catch (e: java.net.URISyntaxException) {
                return null
            }
        val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return null
        val host = uri.host?.lowercase(Locale.ROOT) ?: return null
        val port =
            when {
                uri.port != -1 -> uri.port
                scheme == "https" -> 443
                else -> 80
            }
        return Address(scheme, host, port, uri.rawPath.orEmpty().trimEnd('/'))
    }

    private fun encode(value: String) = URLEncoder.encode(value, "UTF-8")

    /** Header parameters are quoted and must be plain ASCII, so drop anything that isn't. */
    private fun clean(value: String) =
        value.filter { it in ' '..'~' && it != '"' && it != '\\' }
}
