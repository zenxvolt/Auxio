/*
 * Copyright (c) 2026 Auxio Project
 * JellyfinSnapshotStoreTest.kt is part of Auxio.
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
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class JellyfinSnapshotStoreTest {
    @get:Rule val folder = TemporaryFolder()

    private val session = JellyfinSession("https://example.com", "Home", "u1", "bob", "tok")

    private fun tracks(count: Int) =
        (0 until count).map {
            JellyfinTrack.fromJson(JSONObject("""{"Id":"t$it","Name":"Song $it"}"""))!!
        }

    private fun store() = JellyfinSnapshotStore(File(folder.root, "jellyfin/catalog.jsonl"))

    @Test
    fun noSnapshot_readsAsNothing() {
        val store = store()
        assertFalse(store.has(session))
        assertNull(store.read(session))
    }

    @Test
    fun write_thenRead_roundTrips() {
        val store = store()
        store.write(session, tracks(50))
        assertTrue(store.has(session))
        assertEquals(tracks(50), store.read(session))
    }

    @Test
    fun write_replacesThePreviousSnapshot() {
        val store = store()
        store.write(session, tracks(50))
        store.write(session, tracks(3))
        assertEquals(tracks(3), store.read(session))
    }

    @Test
    fun snapshot_isOnlyForTheAccountThatMadeIt() {
        val store = store()
        store.write(session, tracks(5))
        val otherUser = session.copy(userId = "u2")
        val otherServer = session.copy(serverUrl = "https://other.example.com")
        assertFalse(store.has(otherUser))
        assertNull(store.read(otherUser))
        assertFalse(store.has(otherServer))
        assertNull(store.read(otherServer))
    }

    @Test
    fun damagedSnapshot_readsAsNothing() {
        val file = File(folder.root, "jellyfin/catalog.jsonl")
        val store = JellyfinSnapshotStore(file)
        store.write(session, tracks(5))
        file.appendText("{this is not json\n")
        assertNull(store.read(session))
    }

    @Test
    fun clear_removesIt() {
        val store = store()
        store.write(session, tracks(5))
        store.clear()
        assertFalse(store.has(session))
    }

    @Test
    fun write_leavesNothingBehind() {
        val store = store()
        store.write(session, tracks(5))
        assertEquals(listOf("catalog.jsonl"), File(folder.root, "jellyfin").list()!!.toList())
    }
}
