/*
 * Copyright (c) 2026 Auxio Project
 * RandomOrderTest.kt is part of Auxio.
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
import org.junit.Assert.assertTrue
import org.junit.Test

class RandomOrderTest {
    private val items = (1..500).map { "item-$it" }

    @Test
    fun sameSeed_sameOrder() {
        assertEquals(
            shuffleDeterministically(items, 42) { it },
            shuffleDeterministically(items, 42) { it },
        )
    }

    @Test
    fun sameSeed_ignoresInputOrder() {
        assertEquals(
            shuffleDeterministically(items, 42) { it },
            shuffleDeterministically(items.shuffled(), 42) { it },
        )
        assertEquals(
            shuffleDeterministically(items, 42) { it },
            shuffleDeterministically(items.toSet(), 42) { it },
        )
    }

    @Test
    fun differentSeed_differentOrder() {
        assertNotEquals(
            shuffleDeterministically(items, 1) { it },
            shuffleDeterministically(items, 2) { it },
        )
    }

    @Test
    fun keepsEveryItemExactlyOnce() {
        val shuffled = shuffleDeterministically(items, 7) { it }
        assertEquals(items.size, shuffled.size)
        assertEquals(items.toSet(), shuffled.toSet())
        assertNotEquals(items, shuffled)
    }

    @Test
    fun emptyAndSingleton() {
        assertTrue(shuffleDeterministically(emptyList<String>(), 3) { it }.isEmpty())
        assertEquals(listOf("a"), shuffleDeterministically(listOf("a"), 3) { it })
    }
}
