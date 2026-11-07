/*
 * Copyright (c) 2026 Auxio Project
 * JellyfinSongs.kt is part of Auxio.
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

import org.oxycblt.musikr.Song

/**
 * Whether this [Song] is streamed from a Jellyfin server, rather than read from the device. The
 * songs of a server are the only ones that are played over the network, so that is what tells them
 * apart.
 */
val Song.isRemote: Boolean
    get() {
        val scheme = uri.scheme
        return scheme == "http" || scheme == "https"
    }
