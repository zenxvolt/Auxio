/*
 * Copyright (c) 2026 Auxio Project
 * JellyfinCovers.kt is part of Auxio.
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
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.InputStream
import org.oxycblt.musikr.covers.Cover
import org.oxycblt.musikr.covers.CoverResult
import org.oxycblt.musikr.covers.FDCover
import org.oxycblt.musikr.covers.MutableCovers
import org.oxycblt.musikr.fs.File as MusikrFile
import org.oxycblt.musikr.metadata.Metadata
import timber.log.Timber as L

/**
 * A cover that lives on a Jellyfin server. The image is downloaded the first time it is needed and
 * kept in the cache directory of the app from then on.
 */
class JellyfinCover internal constructor(override val id: String, private val loader: Loader) :
    FDCover {
    override suspend fun open(): InputStream? = loader.file(id)?.inputStream()

    override suspend fun fd(): ParcelFileDescriptor? =
        loader.file(id)?.let { ParcelFileDescriptor.open(it, ParcelFileDescriptor.MODE_READ_ONLY) }

    override fun equals(other: Any?) = other is JellyfinCover && other.id == id

    override fun hashCode() = id.hashCode()

    override fun toString() = "JellyfinCover($id)"

    internal fun interface Loader {
        /** Get the file that holds the image of the cover with [id], or null if there is none. */
        suspend fun file(id: String): File?
    }
}

/**
 * The covers of the songs that come from Jellyfin.
 *
 * This is deliberately a [MutableCovers] only because the cleanup of unused images has to hook into
 * the same place as that of every other kind of cover. Nothing is ever *created* here: remote songs
 * have no files to extract covers from, they bring their own, see [cover].
 */
class JellyfinCovers(context: Context) : MutableCovers<JellyfinCover> {
    private val store = JellyfinSessionStore(context)
    private val api = JellyfinApi()
    private val directory = File(context.applicationContext.cacheDir, "jellyfin_covers")
    private val loader = JellyfinCover.Loader { id -> JellyfinCoverKey.parse(id)?.let { load(it) } }

    /**
     * Make the cover for an image of a server.
     *
     * @return The cover, or null if the image can't have one.
     */
    fun cover(variant: JellyfinCoverVariant, itemId: String, tag: String): JellyfinCover? =
        JellyfinCoverKey.of(variant, itemId, tag)?.let { JellyfinCover(it.id, loader) }

    override suspend fun obtain(id: String): CoverResult<JellyfinCover> =
        if (JellyfinCoverKey.parse(id) != null) {
            CoverResult.Hit(JellyfinCover(id, loader))
        } else {
            CoverResult.Miss()
        }

    override suspend fun create(file: MusikrFile, metadata: Metadata): CoverResult<JellyfinCover> =
        CoverResult.Miss()

    override suspend fun cleanup(excluding: Collection<Cover>) {
        val used =
            excluding.mapNotNullTo(mutableSetOf()) { JellyfinCoverKey.parse(it.id)?.fileName }
        directory.listFiles()?.filter { it.name !in used }?.forEach { it.delete() }
    }

    private suspend fun load(key: JellyfinCoverKey): File? {
        val target = File(directory, key.fileName)
        if (target.isFile && target.length() > 0) {
            return target
        }
        val session = store.session() ?: return null
        directory.mkdirs()
        return try {
            api.download(session, store.device(), key.url(session.serverUrl), target)
            target
        } catch (e: JellyfinException) {
            L.w(e, "Could not download Jellyfin cover ${key.id}")
            null
        }
    }
}
