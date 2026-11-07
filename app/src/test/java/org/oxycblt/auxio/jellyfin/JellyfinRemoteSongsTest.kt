/*
 * Copyright (c) 2026 Auxio Project
 * JellyfinRemoteSongsTest.kt is part of Auxio.
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

private typealias Fetch = suspend (JellyfinSession, JellyfinDevice) -> List<JellyfinTrack>

class JellyfinRemoteSongsTest {
    @get:Rule val folder = TemporaryFolder()

    private val session = JellyfinSession("https://example.com", "Home", "u1", "bob", "tok")
    private val device = JellyfinDevice("Auxio", "Test", "device", "1.0")

    private fun tracks(count: Int) =
        (0 until count).map {
            JellyfinTrack.fromJson(JSONObject("""{"Id":"t$it","Name":"Song $it"}"""))!!
        }

    /** Everything that [JellyfinRemoteSongs] does, observable. */
    private inner class Setup(
        var current: Boolean = true,
        snapshotBudgetMs: Long = 20_000,
        coldBudgetMs: Long = 60_000,
        fetch: Fetch,
    ) {
        val snapshots = JellyfinSnapshotStore(File(folder.root, "catalog.jsonl"))
        val mapped = mutableListOf<String>()
        val states = mutableListOf<JellyfinSyncState>()
        val remote =
            JellyfinRemoteSongs(
                session = session,
                device = device,
                fetch = fetch,
                snapshots = snapshots,
                isCurrent = { current },
                onSynced = { states.add(it) },
                map = { track ->
                    mapped.add(track.id)
                    null
                },
                nowMs = { 42L },
                snapshotBudgetMs = snapshotBudgetMs,
                coldBudgetMs = coldBudgetMs,
            )
    }

    private fun fail(kind: JellyfinException.Kind): Fetch = { _, _ ->
        throw JellyfinException(kind, "test")
    }

    @Test
    fun load_success_recordsAndKeepsASnapshot() = runBlocking<Unit> {
        val setup = Setup { _, _ -> tracks(5) }
        setup.remote.load()
        assertEquals(tracks(5).map { it.id }, setup.mapped)
        assertEquals(listOf(JellyfinSyncState(42, 5, null)), setup.states)
        assertEquals(tracks(5), setup.snapshots.read(session))
    }

    @Test
    fun load_failureWithoutSnapshot_isEmptyButRecorded() = runBlocking<Unit> {
        val setup = Setup(fetch = fail(JellyfinException.Kind.UNREACHABLE))
        setup.remote.load()
        assertTrue(setup.mapped.isEmpty())
        assertEquals(
            listOf(JellyfinSyncState(42, 0, JellyfinException.Kind.UNREACHABLE)),
            setup.states,
        )
    }

    @Test
    fun load_failureWithSnapshot_fallsBackToIt() = runBlocking<Unit> {
        val setup = Setup(fetch = fail(JellyfinException.Kind.SERVER_ERROR))
        setup.snapshots.write(session, tracks(4))
        setup.remote.load()
        assertEquals(tracks(4).map { it.id }, setup.mapped)
        assertEquals(
            listOf(JellyfinSyncState(42, 4, JellyfinException.Kind.SERVER_ERROR)),
            setup.states,
        )
    }

    @Test
    fun load_unauthorized_stillShowsTheSnapshot() = runBlocking<Unit> {
        val setup = Setup(fetch = fail(JellyfinException.Kind.UNAUTHORIZED))
        setup.snapshots.write(session, tracks(2))
        setup.remote.load()
        assertEquals(2, setup.mapped.size)
        assertEquals(JellyfinException.Kind.UNAUTHORIZED, setup.states.single().failure)
    }

    @Test
    fun load_slowServer_fallsBackOnceTheBudgetIsSpent() = runBlocking<Unit> {
        val setup =
            Setup(snapshotBudgetMs = 100) { _, _ ->
                delay(60_000)
                tracks(9)
            }
        setup.snapshots.write(session, tracks(4))
        val start = System.currentTimeMillis()
        setup.remote.load()
        assertTrue(System.currentTimeMillis() - start < 10_000)
        assertEquals(4, setup.mapped.size)
        assertEquals(JellyfinException.Kind.UNREACHABLE, setup.states.single().failure)
    }

    @Test
    fun load_unexpectedFailure_isHandledLikeAnyOther() = runBlocking<Unit> {
        val setup = Setup { _, _ -> throw IllegalStateException("boom") }
        setup.snapshots.write(session, tracks(3))
        setup.remote.load()
        assertEquals(3, setup.mapped.size)
        assertEquals(JellyfinException.Kind.MALFORMED, setup.states.single().failure)
    }

    @Test
    fun load_afterTheAccountChanged_recordsNothing() = runBlocking<Unit> {
        val setup = Setup(current = false) { _, _ -> tracks(7) }
        setup.snapshots.write(session, tracks(2))
        setup.remote.load()
        // Still answers, but doesn't touch what belongs to whoever is signed in now.
        assertEquals(7, setup.mapped.size)
        assertTrue(setup.states.isEmpty())
        assertEquals(tracks(2), setup.snapshots.read(session))
    }

    @Test
    fun load_cancellation_isNotSwallowed() = runBlocking<Unit> {
        val setup = Setup { _, _ -> awaitCancellation() }
        var cancelled = false
        val job =
            launch(start = CoroutineStart.UNDISPATCHED) {
                try {
                    setup.remote.load()
                } catch (e: CancellationException) {
                    cancelled = true
                    throw e
                }
            }
        job.cancelAndJoin()
        assertTrue(cancelled)
        assertTrue(setup.states.isEmpty())
    }
}
