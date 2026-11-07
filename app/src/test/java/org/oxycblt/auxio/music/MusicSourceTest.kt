/*
 * Copyright (c) 2026 Auxio Project
 * MusicSourceTest.kt is part of Auxio.
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
 
package org.oxycblt.auxio.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.oxycblt.auxio.playback.PlaySong

class MusicSourceTest {
    @Test
    fun intCode_roundTrips() {
        for (source in MusicSource.entries) {
            assertEquals(source, MusicSource.fromIntCode(source.intCode))
        }
    }

    @Test
    fun intCode_isDistinct() {
        assertNotEquals(MusicSource.LOCAL.intCode, MusicSource.JELLYFIN.intCode)
    }

    @Test
    fun fromIntCode_invalid() {
        assertNull(MusicSource.fromIntCode(0))
        assertNull(MusicSource.fromIntCode(-1))
    }

    @Test
    fun playSong_fromSource_roundTrips() {
        for (source in MusicSource.entries) {
            val playSong = PlaySong.FromSource(source)
            assertEquals(playSong, PlaySong.fromIntCode(playSong.intCode))
        }
    }

    @Test
    fun playSong_fromSource_isNotFromAll() {
        for (source in MusicSource.entries) {
            assertNotEquals(PlaySong.FromAll.intCode, PlaySong.FromSource(source).intCode)
        }
    }
}
