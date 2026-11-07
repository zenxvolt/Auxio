/*
 * Copyright (c) 2026 Auxio Project
 * SortTest.kt is part of Auxio.
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
 
package org.oxycblt.auxio.list.sort

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SortTest {
    private val directional =
        listOf(
            Sort.Mode.ByName,
            Sort.Mode.ByArtist,
            Sort.Mode.ByAlbum,
            Sort.Mode.ByDate,
            Sort.Mode.ByDuration,
            Sort.Mode.ByCount,
            Sort.Mode.ByDisc,
            Sort.Mode.ByTrack,
            Sort.Mode.ByDateAdded,
        )

    @Test
    fun intCode_staysCompatibleWithPreviouslySavedSorts() {
        for (mode in directional) {
            for (direction in Sort.Direction.entries) {
                val sort = Sort(mode, direction)
                val ascending = if (direction == Sort.Direction.ASCENDING) 1 else 0
                // This is the exact format that was used before random sorts existed.
                val legacy = mode.intCode.shl(1) or ascending
                assertEquals(legacy, sort.intCode)
                assertEquals(sort, Sort.fromIntCode(legacy))
            }
        }
    }

    @Test
    fun fromIntCode_rejectsInvalidCodes() {
        assertNull(Sort.fromIntCode(Int.MIN_VALUE))
        assertNull(Sort.fromIntCode(-1))
        assertNull(Sort.fromIntCode(0))
    }

    @Test
    fun intCode_roundTripsEveryRandomSeed() {
        for (seed in 0 until Sort.SEED_COUNT) {
            val sort = Sort(Sort.Mode.ByRandom, Sort.Direction.ASCENDING, seed)
            assertTrue(sort.intCode >= 0)
            assertEquals(sort, Sort.fromIntCode(sort.intCode))
        }
    }

    @Test
    fun intCode_changesWhenReshuffled() {
        // The preference listener only fires if the stored value changes, so a new seed must
        // change the code.
        val first = Sort(Sort.Mode.ByRandom, Sort.Direction.ASCENDING, 10)
        val second = Sort(Sort.Mode.ByRandom, Sort.Direction.ASCENDING, 11)
        assertNotEquals(first.intCode, second.intCode)
    }

    @Test
    fun newSeed_neverRepeatsExcludedSeed() {
        repeat(2000) { i ->
            val excluded = i % Sort.SEED_COUNT
            val seed = Sort.newSeed(excluded)
            assertNotEquals(excluded, seed)
            assertTrue(seed in 0 until Sort.SEED_COUNT)
        }
    }
}
