/*
 * Copyright (c) 2026 Auxio Project
 * JellyfinCoverKey.kt is part of Auxio.
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

/**
 * How big, and at which quality, the server should deliver cover images. This mirrors what the
 * "Album covers" setting does for covers read from files on the device.
 *
 * @param maxSize The maximum width and height in pixels, or null to get the original image.
 * @param quality The JPEG quality from 0 to 100 to use when scaling, or null to leave it up to the
 *   server.
 */
data class JellyfinCoverVariant(val maxSize: Int?, val quality: Int?) {
    internal val code: String
        get() = "${maxSize ?: 0}q${quality ?: 0}"
}

/**
 * Identifies one image on a server. This is what a [JellyfinCover.id] stands for. The ID has to
 * describe the image completely, as it is all that is known when the cover is looked up later (by
 * the content provider that hands covers to other apps, for example).
 *
 * The tag changes whenever the server's image does, so a changed image is a different cover, and a
 * cover never has to be refreshed.
 */
internal class JellyfinCoverKey
private constructor(
    val variant: JellyfinCoverVariant,
    val itemId: String,
    val tag: String,
) {
    val id = "$PREFIX${variant.code}:$itemId:$tag"

    /** The name of the file that the image is cached in. */
    val fileName = "${variant.code}-$itemId-$tag.img"

    /** The URL of the image on a server. */
    fun url(serverUrl: String) =
        JellyfinUrls.image(serverUrl, itemId, tag, variant.maxSize, variant.quality)

    companion object {
        private const val PREFIX = "jf:"
        private val PATTERN = Regex("""^jf:(\d{1,5})q(\d{1,3}):([A-Za-z0-9]+):([A-Za-z0-9]+)$""")

        /** @return The key, or null if the IDs can't be safely used in a file name. */
        fun of(variant: JellyfinCoverVariant, itemId: String, tag: String): JellyfinCoverKey? {
            val safe = Regex("[A-Za-z0-9]+")
            if (!safe.matches(itemId) || !safe.matches(tag)) {
                return null
            }
            return JellyfinCoverKey(variant, itemId, tag)
        }

        /** @return The key that [id] stands for, or null if it's not a Jellyfin cover ID. */
        fun parse(id: String): JellyfinCoverKey? {
            val (size, quality, itemId, tag) = PATTERN.matchEntire(id)?.destructured ?: return null
            val variant =
                JellyfinCoverVariant(
                    size.toInt().takeIf { it > 0 },
                    quality.toInt().takeIf { it > 0 },
                )
            return of(variant, itemId, tag)
        }
    }
}
