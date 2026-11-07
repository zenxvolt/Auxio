/*
 * Copyright (c) 2026 Auxio Project
 * JellyfinViewModel.kt is part of Auxio.
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
 
package org.oxycblt.auxio.jellyfin.ui

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.oxycblt.auxio.R
import org.oxycblt.auxio.jellyfin.JellyfinApi
import org.oxycblt.auxio.jellyfin.JellyfinException
import org.oxycblt.auxio.jellyfin.JellyfinServer
import org.oxycblt.auxio.jellyfin.JellyfinSession
import org.oxycblt.auxio.jellyfin.JellyfinSessionStore
import org.oxycblt.auxio.jellyfin.JellyfinSnapshotStore
import org.oxycblt.auxio.jellyfin.JellyfinSyncState
import org.oxycblt.auxio.jellyfin.JellyfinUrls
import org.oxycblt.auxio.music.IndexingState
import org.oxycblt.auxio.music.MusicRepository
import timber.log.Timber as L

/**
 * A [ViewModel] that drives signing in to, and out of, a Jellyfin server.
 *
 * Changing the account changes which songs the library is made of, so every change here ends with a
 * request to reload the music library.
 */
@HiltViewModel
class JellyfinViewModel
@Inject
constructor(
    private val store: JellyfinSessionStore,
    private val snapshots: JellyfinSnapshotStore,
    private val api: JellyfinApi,
    private val musicRepository: MusicRepository,
) : ViewModel(), MusicRepository.IndexingListener {
    /** The account that is signed in, and how it last synchronized. */
    data class Account(val session: JellyfinSession, val sync: JellyfinSyncState?)

    /** The account that is signed in, or null if there is none. */
    val account: StateFlow<Account?> =
        store.changes
            .map { current() }
            .stateIn(viewModelScope, SharingStarted.Eagerly, current())

    private val signingIn = MutableStateFlow(false)
    private val indexing = MutableStateFlow(musicRepository.indexingState is IndexingState.Indexing)

    /** Whether something is going on that the user has to wait for. */
    val working: StateFlow<Boolean> =
        combine(signingIn, indexing) { signingIn, indexing -> signingIn || indexing }
            .stateIn(viewModelScope, SharingStarted.Eagerly, signingIn.value || indexing.value)

    private val _error = MutableStateFlow<Int?>(null)

    /** The message that explains why the last attempt failed, or null if it didn't. */
    val error: StateFlow<Int?> = _error

    init {
        musicRepository.addIndexingListener(this)
    }

    override fun onCleared() {
        musicRepository.removeIndexingListener(this)
    }

    override fun onIndexingStateChanged() {
        indexing.value = musicRepository.indexingState is IndexingState.Indexing
    }

    /** Forget about the last failure. */
    fun clearError() {
        _error.value = null
    }

    /**
     * Sign in to a server.
     *
     * @param address The address of the server, as the user typed it.
     * @param username The name of the user.
     * @param password The password of the user.
     */
    fun signIn(address: String, username: String, password: String) {
        if (signingIn.value) {
            return
        }
        if (address.isBlank() || username.isBlank()) {
            _error.value = R.string.err_jellyfin_empty
            return
        }
        val candidates = JellyfinUrls.candidates(address)
        if (candidates.isEmpty()) {
            _error.value = R.string.err_jellyfin_invalid_url
            return
        }
        _error.value = null
        signingIn.value = true
        viewModelScope.launch {
            try {
                val server = findServer(candidates)
                val session = api.signIn(server, username.trim(), password, store.device())
                // Songs of any earlier account must never be shown for this one.
                snapshots.clear()
                store.save(session)
                musicRepository.requestIndex(true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Whatever it was, signing in is something the user can try again.
                L.w(e, "Could not sign in to Jellyfin")
                _error.value =
                    (e as? JellyfinException)?.let { messageOf(it.kind) }
                        ?: R.string.err_jellyfin_malformed
            } finally {
                signingIn.value = false
            }
        }
    }

    /**
     * Sign out. This can't fail: the account is forgotten on the device whether or not the server
     * can be told about it.
     */
    fun signOut() {
        val session = store.session() ?: return
        val device = store.device()
        store.clear()
        snapshots.clear()
        musicRepository.requestIndex(true)
        // The dialog is probably closed right after this, so it can't be tied to its scope.
        revokeScope.launch { api.signOut(session, device) }
    }

    /** Reload the library, which fetches the catalog of the server again. */
    fun sync() {
        musicRepository.requestIndex(true)
    }

    private fun current() = store.session()?.let { Account(it, store.syncState) }

    /**
     * Find the server behind an address. If the user left out the scheme there are several
     * addresses to try, and then the most informative failure is the one that is reported: that
     * something answered but was not Jellyfin says more than that something else wasn't there.
     */
    private suspend fun findServer(candidates: List<String>): JellyfinServer {
        var failure: JellyfinException? = null
        for (candidate in candidates) {
            try {
                return api.probe(candidate)
            } catch (e: JellyfinException) {
                if (failure == null || rank(e.kind) > rank(failure.kind)) {
                    failure = e
                }
            }
        }
        throw checkNotNull(failure)
    }

    private fun rank(kind: JellyfinException.Kind) =
        when (kind) {
            JellyfinException.Kind.NOT_JELLYFIN -> 2
            JellyfinException.Kind.SERVER_ERROR -> 1
            else -> 0
        }

    private companion object {
        val revokeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}

/** The message that explains a failure to the user. */
@StringRes
internal fun messageOf(kind: JellyfinException.Kind) =
    when (kind) {
        JellyfinException.Kind.INVALID_URL -> R.string.err_jellyfin_invalid_url
        JellyfinException.Kind.UNREACHABLE -> R.string.err_jellyfin_unreachable
        JellyfinException.Kind.NOT_JELLYFIN -> R.string.err_jellyfin_not_jellyfin
        JellyfinException.Kind.INVALID_CREDENTIALS -> R.string.err_jellyfin_invalid_credentials
        JellyfinException.Kind.UNAUTHORIZED -> R.string.err_jellyfin_unauthorized
        JellyfinException.Kind.SERVER_ERROR -> R.string.err_jellyfin_server_error
        JellyfinException.Kind.MALFORMED -> R.string.err_jellyfin_malformed
    }
