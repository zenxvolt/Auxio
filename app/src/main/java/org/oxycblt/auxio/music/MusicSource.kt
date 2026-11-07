/*
 * Copyright (c) 2026 Auxio Project
 * MusicSource.kt is part of Auxio.
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

import org.oxycblt.auxio.IntegerTable
import org.oxycblt.auxio.jellyfin.isRemote
import org.oxycblt.musikr.Song

/**
 * Where the [Song]s of the library come from. The library is one, but the songs on the device and
 * the songs of a Jellyfin server can be told apart, and the home view can show either of them.
 *
 * @author Alexander Capehart (OxygenCobalt)
 */
enum class MusicSource {
    /** The songs that are on the device. */
    LOCAL,
    /** The songs that are streamed from a Jellyfin server. */
    JELLYFIN;

    /**
     * The integer representation of this instance.
     *
     * @see fromIntCode
     */
    val intCode: Int
        get() =
            when (this) {
                LOCAL -> IntegerTable.MUSIC_SOURCE_LOCAL
                JELLYFIN -> IntegerTable.MUSIC_SOURCE_JELLYFIN
            }

    /** Whether the given [Song] comes from this source. */
    operator fun contains(song: Song) = (this == JELLYFIN) == song.isRemote

    companion object {
        /**
         * Convert a [MusicSource] integer representation into an instance.
         *
         * @param intCode An integer representation of a [MusicSource]
         * @return The corresponding [MusicSource], or null if the [MusicSource] is invalid.
         * @see MusicSource.intCode
         */
        fun fromIntCode(intCode: Int) =
            when (intCode) {
                IntegerTable.MUSIC_SOURCE_LOCAL -> LOCAL
                IntegerTable.MUSIC_SOURCE_JELLYFIN -> JELLYFIN
                else -> null
            }
    }
}
