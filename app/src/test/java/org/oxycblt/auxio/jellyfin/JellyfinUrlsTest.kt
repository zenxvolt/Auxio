/*
 * Copyright (c) 2026 Auxio Project
 * JellyfinUrlsTest.kt is part of Auxio.
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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JellyfinUrlsTest {
    @Test
    fun candidates_bareHost_triesHttpsFirst() {
        assertEquals(
            listOf("https://music.example.com:8096", "http://music.example.com:8096"),
            JellyfinUrls.candidates("Music.Example.com:8096/"),
        )
    }

    @Test
    fun candidates_explicitScheme_isKept() {
        assertEquals(
            listOf("http://192.168.1.5:8096"),
            JellyfinUrls.candidates(" http://192.168.1.5:8096/web/index.html#!/home "),
        )
    }

    @Test
    fun candidates_reverseProxyBasePath_isKept() {
        assertEquals(
            listOf("https://example.com/jellyfin"),
            JellyfinUrls.candidates("https://example.com/jellyfin/web/"),
        )
    }

    @Test
    fun candidates_nonsense_isRejected() {
        assertTrue(JellyfinUrls.candidates("   ").isEmpty())
        assertTrue(JellyfinUrls.candidates("ftp://example.com").isEmpty())
        assertTrue(JellyfinUrls.candidates("http://exa mple.com").isEmpty())
    }

    @Test
    fun normalize_dropsCredentialsQueryAndFragment() {
        assertEquals(
            "https://example.com",
            JellyfinUrls.normalize("https://u:p@Example.com/?a=b#c"),
        )
    }

    @Test
    fun stream_isStaticAndEncoded() {
        assertEquals(
            "https://example.com/jf/Audio/abc123/stream?static=true",
            JellyfinUrls.stream("https://example.com/jf", "abc123"),
        )
        assertEquals(
            "https://example.com/Audio/a%2Fb/stream?static=true",
            JellyfinUrls.stream("https://example.com", "a/b"),
        )
    }

    @Test
    fun image_includesOnlyWhatWasAskedFor() {
        assertEquals(
            "https://example.com/Items/i/Images/Primary" +
                "?tag=t+t&maxWidth=512&maxHeight=512&quality=90",
            JellyfinUrls.image("https://example.com", "i", "t t", 512, 90),
        )
        assertEquals(
            "https://example.com/Items/i/Images/Primary",
            JellyfinUrls.image("https://example.com", "i", null, null, null),
        )
    }

    @Test
    fun authorization_isQuotedAsciiAndOnlyHasTokenWhenGiven() {
        val device = JellyfinDevice("Auxio", "Pixel \"7\" é", "dev-1", "4.0")
        assertEquals(
            "MediaBrowser Client=\"Auxio\", Device=\"Pixel 7 \", DeviceId=\"dev-1\", " +
                "Version=\"4.0\", Token=\"tok\"",
            JellyfinUrls.authorization(device, "tok"),
        )
        assertFalse(JellyfinUrls.authorization(device, null).contains("Token"))
    }

    @Test
    fun isOnServer_onlyMatchesTheSameServer() {
        val server = "https://example.com"
        assertTrue(JellyfinUrls.isOnServer(server, "https://example.com/Audio/1/stream"))
        assertTrue(JellyfinUrls.isOnServer(server, "https://example.com:443/Audio/1/stream"))
        assertFalse(JellyfinUrls.isOnServer(server, "https://example.com:444/x"))
        assertFalse(JellyfinUrls.isOnServer(server, "http://example.com/x"))
        assertFalse(JellyfinUrls.isOnServer(server, "https://example.com.evil.io/x"))
        assertFalse(JellyfinUrls.isOnServer(server, "https://example.com@evil.io/x"))
    }

    @Test
    fun isOnServer_respectsBasePath() {
        val server = "https://example.com/jf"
        assertTrue(JellyfinUrls.isOnServer(server, "https://example.com/jf/Audio/1"))
        assertFalse(JellyfinUrls.isOnServer(server, "https://example.com/jfx/Audio/1"))
        assertFalse(JellyfinUrls.isOnServer(server, "https://example.com/other"))
    }

    @Test
    fun normalize_unparseable_isNull() {
        assertNull(JellyfinUrls.normalize("not a url"))
    }
}
