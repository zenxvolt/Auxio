/*
 * Copyright (c) 2026 Auxio Project
 * JellyfinModule.kt is part of Auxio.
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
import androidx.preference.PreferenceManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import org.oxycblt.auxio.R

@Module
@InstallIn(SingletonComponent::class)
class JellyfinModule {
    @Provides
    @Singleton
    fun sessionStore(@ApplicationContext context: Context) = JellyfinSessionStore(context)

    @Provides
    @Singleton
    fun snapshotStore(@ApplicationContext context: Context) = JellyfinSnapshotStore(context)

    @Provides
    @Singleton
    fun audioCache(@ApplicationContext context: Context): JellyfinAudioCache {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context.applicationContext)
        val key = context.getString(R.string.set_key_stream_cache)
        val default = context.resources.getInteger(R.integer.stream_cache_default)
        // The setting is in megabytes. Reading it is a lookup in memory, so it's done every time.
        return JellyfinAudioCache(context) { prefs.getInt(key, default).coerceAtLeast(0) * MB }
    }
}

private const val MB = 1024L * 1024L
