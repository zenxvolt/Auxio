/*
 * Copyright (c) 2026 Auxio Project
 * JellyfinFormatsTest.kt is part of Auxio.
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
import org.junit.Assert.assertTrue
import org.junit.Test

class JellyfinFormatsTest {
    @Test
    fun containerMimeType_understandsFfmpegContainerLists() {
        assertEquals(
            "audio/mp4",
            JellyfinFormats.containerMimeType("mov,mp4,m4a,3gp,3g2,mj2", null),
        )
        assertEquals("audio/x-matroska", JellyfinFormats.containerMimeType("matroska,webm", null))
        assertEquals("audio/flac", JellyfinFormats.containerMimeType("FLAC", null))
    }

    @Test
    fun containerMimeType_fallsBackToTheExtension() {
        assertEquals("audio/mpeg", JellyfinFormats.containerMimeType(null, "/music/a.MP3"))
        assertEquals("audio/ogg", JellyfinFormats.containerMimeType("", "C:\\music\\a.ogg"))
        assertEquals("application/octet-stream", JellyfinFormats.containerMimeType(null, null))
    }

    @Test
    fun codecMimeType_usesTheVocabularyOfTheTagExtractor() {
        assertEquals("audio/aac", JellyfinFormats.codecMimeType("aac", "audio/mp4"))
        assertEquals("audio/mpeg", JellyfinFormats.codecMimeType("mp3", "audio/mpeg"))
        assertEquals("audio/wav", JellyfinFormats.codecMimeType("pcm_s16le", "audio/wav"))
        assertEquals("audio/opus", JellyfinFormats.codecMimeType("Opus", "audio/ogg"))
    }

    @Test
    fun codecMimeType_unknownCodec_fallsBackToTheContainer() {
        assertEquals(
            "audio/x-matroska",
            JellyfinFormats.codecMimeType("dts", "audio/x-matroska"),
        )
        assertEquals("audio/flac", JellyfinFormats.codecMimeType(null, "audio/flac"))
    }

    @Test
    fun isPlayable_rejectsWhatAndroidCannotDemux() {
        assertFalse(JellyfinFormats.isPlayable("asf", null))
        assertFalse(JellyfinFormats.isPlayable("ape", null))
        assertFalse(JellyfinFormats.isPlayable(null, "/music/a.wv"))
        assertTrue(JellyfinFormats.isPlayable("flac", null))
        assertTrue(JellyfinFormats.isPlayable("mov,mp4,m4a,3gp,3g2,mj2", null))
        // Unknown things get the benefit of the doubt.
        assertTrue(JellyfinFormats.isPlayable("mystery", null))
        assertTrue(JellyfinFormats.isPlayable(null, null))
    }

    @Test
    fun extension_prefersTheContainer() {
        assertEquals("m4a", JellyfinFormats.extension("mov,mp4,m4a", "/music/a.flac"))
        assertEquals("flac", JellyfinFormats.extension(null, "/music/a.flac"))
    }
}
