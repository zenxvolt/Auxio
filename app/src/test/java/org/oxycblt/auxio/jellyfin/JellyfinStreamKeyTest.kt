/*
 * Copyright (c) 2026 Auxio Project
 * JellyfinStreamKeyTest.kt is part of Auxio.
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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JellyfinStreamKeyTest {
    @Test
    fun of_ignoresServerAddress() {
        // The same song stays cached when the server is reached over another address.
        assertEquals(
            JellyfinStreamKey.of(JellyfinUrls.stream("http://192.168.1.5:8096", "abc"), 1234),
            JellyfinStreamKey.of(JellyfinUrls.stream("https://music.example.com", "abc"), 1234),
        )
    }

    @Test
    fun of_includesSize() {
        val url = JellyfinUrls.stream("https://example.com", "abc")
        assertEquals("jellyfin:abc:1234", JellyfinStreamKey.of(url, 1234))
        // A file that was replaced on the server is not mixed with the old one.
        assertNotEquals(JellyfinStreamKey.of(url, 1234), JellyfinStreamKey.of(url, 1235))
    }

    @Test
    fun of_unknownSize_isJustTheSong() {
        val url = JellyfinUrls.stream("https://example.com", "abc")
        assertEquals("jellyfin:abc", JellyfinStreamKey.of(url, 0))
        assertEquals("jellyfin:abc", JellyfinStreamKey.of(url, -1))
    }

    @Test
    fun of_differentSongs_differ() {
        assertNotEquals(
            JellyfinStreamKey.of(JellyfinUrls.stream("https://example.com", "abc"), 1),
            JellyfinStreamKey.of(JellyfinUrls.stream("https://example.com", "abd"), 1),
        )
    }

    @Test
    fun of_songsOnTheDevice_haveNoKey() {
        assertNull(JellyfinStreamKey.of("content://media/external/audio/media/5", 1234))
        assertNull(JellyfinStreamKey.of("file:///sdcard/Music/song.flac", 1234))
    }
}
