/*
 * Copyright (c) 2026 Auxio Project
 * JellyfinTrackTest.kt is part of Auxio.
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

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JellyfinTrackTest {
    private val full =
        """
        {"Id":"a1","Name":"Song","Album":"Alb","AlbumId":"al1","AlbumPrimaryImageTag":"tag9",
         "ImageTags":{"Primary":"t1"},"Artists":["A","B",null,""],
         "AlbumArtists":[{"Name":"AA","Id":"x"},{"Id":"y"}],"Genres":["Rock"],
         "IndexNumber":3,"ParentIndexNumber":1,"PremiereDate":"2020-05-04T00:00:00.0000000Z",
         "ProductionYear":2020,"RunTimeTicks":2450000000,
         "DateCreated":"2023-01-02T03:04:05.1234567Z","Path":"/music/a.flac",
         "NormalizationGain":-6.5,
         "MediaSources":[{"Container":"flac","Size":123456,"Bitrate":900000,
           "MediaStreams":[{"Type":"Video","Codec":"mjpeg"},
             {"Type":"Audio","Codec":"flac","SampleRate":44100,"BitRate":850000}]}]}
        """
            .trimIndent()

    @Test
    fun fromJson_readsEverythingThatIsUsed() {
        val track = JellyfinTrack.fromJson(JSONObject(full))
        assertNotNull(track)
        track!!
        assertEquals("a1", track.id)
        assertEquals("Song", track.name)
        // Blank and missing names are not artists.
        assertEquals(listOf("A", "B"), track.artistNames)
        assertEquals(listOf("AA"), track.albumArtistNames)
        assertEquals(245_000L, track.durationMs)
        assertEquals("flac", track.codec)
        assertEquals(44100, track.sampleRateHz)
        // The bitrate of the audio stream is preferred over that of the whole file.
        assertEquals(850_000, track.bitrateBps)
        assertEquals("flac", track.container)
        assertEquals(123_456L, track.sizeBytes)
        assertEquals(-6.5f, track.normalizationGain)
        assertEquals("t1", track.imageTag)
        assertEquals("tag9", track.albumImageTag)
        // Fractional seconds are cut off.
        assertEquals(1672628645123L, track.dateCreatedMs)
    }

    @Test
    fun fromJson_withoutId_isNull() {
        assertNull(JellyfinTrack.fromJson(JSONObject("""{"Name":"x"}""")))
    }

    @Test
    fun fromJson_sparseTrack_hasSafeDefaults() {
        val json = JSONObject("""{"Id":"z","Name":null,"Artists":null}""")
        val track = JellyfinTrack.fromJson(json)!!
        assertNull(track.name)
        assertTrue(track.artistNames.isEmpty())
        assertEquals(0L, track.durationMs)
        assertNull(track.container)
    }

    @Test
    fun toJson_roundTrips() {
        val track = JellyfinTrack.fromJson(JSONObject(full))!!
        assertEquals(track, JellyfinTrack.fromJson(JSONObject(track.toJson().toString())))
        val sparse = JellyfinTrack.fromJson(JSONObject("""{"Id":"z"}"""))!!
        assertEquals(sparse, JellyfinTrack.fromJson(JSONObject(sparse.toJson().toString())))
    }
}
