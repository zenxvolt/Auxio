/*
 * Copyright (c) 2026 Auxio Project
 * JellyfinCoverKeyTest.kt is part of Auxio.
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class JellyfinCoverKeyTest {
    private val variant = JellyfinCoverVariant(750, 85)

    @Test
    fun id_describesTheImageCompletely() {
        val key = JellyfinCoverKey.of(variant, "abc123", "tagFF")!!
        assertEquals("jf:750q85:abc123:tagFF", key.id)
        val parsed = JellyfinCoverKey.parse(key.id)
        assertNotNull(parsed)
        assertEquals(key.id, parsed!!.id)
        assertEquals(variant, parsed.variant)
    }

    @Test
    fun id_ofTheOriginalImage_roundTrips() {
        val original = JellyfinCoverVariant(null, null)
        val key = JellyfinCoverKey.of(original, "a", "b")!!
        assertEquals(original, JellyfinCoverKey.parse(key.id)!!.variant)
    }

    @Test
    fun url_asksTheServerToScale() {
        val key = JellyfinCoverKey.of(variant, "abc123", "tagFF")!!
        assertEquals(
            "https://example.com/Items/abc123/Images/Primary?tag=tagFF&maxWidth=750" +
                "&maxHeight=750&quality=85",
            key.url("https://example.com"),
        )
    }

    @Test
    fun unsafeIds_neverBecomeKeys() {
        assertNull(JellyfinCoverKey.of(variant, "../x", "t"))
        assertNull(JellyfinCoverKey.of(variant, "a", "b/c"))
        assertNull(JellyfinCoverKey.of(variant, "", "t"))
    }

    @Test
    fun foreignIds_areNotParsed() {
        assertNull(JellyfinCoverKey.parse("mcs:abc"))
        assertNull(JellyfinCoverKey.parse("jf:750q85:a:b:c"))
        assertNull(JellyfinCoverKey.parse("jf:750q85:../a:b"))
        assertNull(JellyfinCoverKey.parse("jf:x:a:b"))
    }

    @Test
    fun fileName_isSafeToUseOnDisk() {
        val name = JellyfinCoverKey.of(variant, "abc123", "tagFF")!!.fileName
        assertFalse(name.contains('/'))
        assertFalse(name.contains(':'))
    }
}
