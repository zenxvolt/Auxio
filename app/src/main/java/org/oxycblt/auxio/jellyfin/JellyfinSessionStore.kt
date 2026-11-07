/*
 * Copyright (c) 2026 Auxio Project
 * JellyfinSessionStore.kt is part of Auxio.
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

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import java.util.UUID
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import org.oxycblt.auxio.BuildConfig

/**
 * Persists which Jellyfin account the app is signed in to.
 *
 * This lives in the app's private storage and is excluded from backups, since the token it holds
 * is only meaningful to the installation that obtained it. Any number of instances can be created,
 * they all see the same data.
 */
class JellyfinSessionStore(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** The account that is signed in, or null if there is none. */
    fun session(): JellyfinSession? {
        val serverUrl = prefs.getString(KEY_SERVER_URL, null) ?: return null
        val userId = prefs.getString(KEY_USER_ID, null) ?: return null
        val token = prefs.getString(KEY_TOKEN, null) ?: return null
        return JellyfinSession(
            serverUrl = serverUrl,
            serverName = prefs.getString(KEY_SERVER_NAME, null) ?: serverUrl,
            userId = userId,
            userName = prefs.getString(KEY_USER_NAME, null).orEmpty(),
            accessToken = token,
        )
    }

    /** Remember [session] as the account that is signed in. This replaces any other account. */
    fun save(session: JellyfinSession) {
        prefs
            .edit()
            .putString(KEY_SERVER_URL, session.serverUrl)
            .putString(KEY_SERVER_NAME, session.serverName)
            .putString(KEY_USER_ID, session.userId)
            .putString(KEY_USER_NAME, session.userName)
            .putString(KEY_TOKEN, session.accessToken)
            .remove(KEY_SYNC_TIME)
            .remove(KEY_SYNC_COUNT)
            .remove(KEY_SYNC_FAILURE)
            .apply()
    }

    /** Forget the account that is signed in. The identity of the device is kept. */
    fun clear() {
        prefs
            .edit()
            .remove(KEY_SERVER_URL)
            .remove(KEY_SERVER_NAME)
            .remove(KEY_USER_ID)
            .remove(KEY_USER_NAME)
            .remove(KEY_TOKEN)
            .remove(KEY_SYNC_TIME)
            .remove(KEY_SYNC_COUNT)
            .remove(KEY_SYNC_FAILURE)
            .apply()
    }

    /** How the device presents itself to servers. The ID is generated once and then kept. */
    fun device(): JellyfinDevice {
        val id =
            synchronized(LOCK) {
                prefs.getString(KEY_DEVICE_ID, null)
                    ?: UUID.randomUUID().toString().also {
                        prefs.edit().putString(KEY_DEVICE_ID, it).commit()
                    }
            }
        return JellyfinDevice(
            client = "Auxio",
            name = Build.MODEL?.takeIf { it.isNotBlank() } ?: "Android",
            id = id,
            version = BuildConfig.VERSION_NAME,
        )
    }

    /** What happened the last time that the catalog was synchronized, if it ever was. */
    var syncState: JellyfinSyncState?
        get() {
            if (!prefs.contains(KEY_SYNC_TIME)) {
                return null
            }
            return JellyfinSyncState(
                timeMs = prefs.getLong(KEY_SYNC_TIME, 0),
                songCount = prefs.getInt(KEY_SYNC_COUNT, 0),
                failure =
                    prefs.getString(KEY_SYNC_FAILURE, null)?.let { name ->
                        JellyfinException.Kind.entries.firstOrNull { it.name == name }
                    },
            )
        }
        set(value) {
            val edit = prefs.edit()
            if (value == null) {
                edit.remove(KEY_SYNC_TIME).remove(KEY_SYNC_COUNT).remove(KEY_SYNC_FAILURE)
            } else {
                edit
                    .putLong(KEY_SYNC_TIME, value.timeMs)
                    .putInt(KEY_SYNC_COUNT, value.songCount)
                    .putString(KEY_SYNC_FAILURE, value.failure?.name)
            }
            edit.apply()
        }

    /** Emits whenever anything in the store changes, and once when collection starts. */
    val changes: Flow<Unit> =
        callbackFlow {
                val listener =
                    SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> trySend(Unit) }
                prefs.registerOnSharedPreferenceChangeListener(listener)
                trySend(Unit)
                awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
            }
            .conflate()

    private companion object {
        const val FILE = "jellyfin"
        const val KEY_SERVER_URL = "server_url"
        const val KEY_SERVER_NAME = "server_name"
        const val KEY_USER_ID = "user_id"
        const val KEY_USER_NAME = "user_name"
        const val KEY_TOKEN = "access_token"
        const val KEY_DEVICE_ID = "device_id"
        const val KEY_SYNC_TIME = "sync_time"
        const val KEY_SYNC_COUNT = "sync_count"
        const val KEY_SYNC_FAILURE = "sync_failure"
        val LOCK = Any()
    }
}
