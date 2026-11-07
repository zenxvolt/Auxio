/*
 * Copyright (c) 2024 Auxio Project
 * HomeGenerator.kt is part of Auxio.
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
 
package org.oxycblt.auxio.home

import javax.inject.Inject
import org.oxycblt.auxio.home.tabs.Tab
import org.oxycblt.auxio.jellyfin.JellyfinSessionStore
import org.oxycblt.auxio.list.ListSettings
import org.oxycblt.auxio.list.adapter.UpdateInstructions
import org.oxycblt.auxio.music.MusicRepository
import org.oxycblt.auxio.music.MusicSource
import org.oxycblt.auxio.music.MusicType
import org.oxycblt.musikr.Album
import org.oxycblt.musikr.Artist
import org.oxycblt.musikr.Genre
import org.oxycblt.musikr.Playlist
import org.oxycblt.musikr.Song
import timber.log.Timber as L

interface HomeGenerator {
    fun attach()

    fun release()

    /** Whether there is a Jellyfin account, which is what makes the [MusicSource]s selectable. */
    fun hasJellyfin(): Boolean

    /**
     * Whether there is no music to show.
     *
     * @param source Only consider the music of this [MusicSource]. If null, or if there is no
     *   Jellyfin account, all music is considered.
     */
    fun empty(source: MusicSource? = null): Boolean

    /**
     * The functions below generate the lists of the home view, limited to a [MusicSource]. An item
     * belongs to a source if any of its songs does, the songs of an album are the songs it has. If
     * the source is null, or if there is no Jellyfin account, nothing is limited.
     */
    fun songs(source: MusicSource? = null): List<Song>

    fun albums(source: MusicSource? = null): List<Album>

    fun artists(source: MusicSource? = null): List<Artist>

    fun genres(source: MusicSource? = null): List<Genre>

    fun playlists(source: MusicSource? = null): List<Playlist>

    fun tabs(): List<MusicType>

    interface Invalidator {
        fun invalidateEmpty() {}

        fun invalidateMusic(type: MusicType, instructions: UpdateInstructions)

        /** Whether there is a Jellyfin account changed, which means the sources changed. */
        fun invalidateSources() {}

        fun invalidateTabs()
    }

    interface Factory {
        fun create(invalidator: Invalidator): HomeGenerator
    }
}

class HomeGeneratorFactoryImpl
@Inject
constructor(
    private val homeSettings: HomeSettings,
    private val listSettings: ListSettings,
    private val musicRepository: MusicRepository,
    private val jellyfin: JellyfinSessionStore,
) : HomeGenerator.Factory {
    override fun create(invalidator: HomeGenerator.Invalidator): HomeGenerator =
        HomeGeneratorImpl(invalidator, homeSettings, listSettings, musicRepository, jellyfin)
}

private class HomeGeneratorImpl(
    private val invalidator: HomeGenerator.Invalidator,
    private val homeSettings: HomeSettings,
    private val listSettings: ListSettings,
    private val musicRepository: MusicRepository,
    private val jellyfin: JellyfinSessionStore,
) : HomeGenerator, HomeSettings.Listener, ListSettings.Listener, MusicRepository.UpdateListener {
    // The account only matters to the lists once the library has been reloaded to match it, so
    // this follows it from there instead of reading the account every time.
    private var signedInToJellyfin = jellyfin.session() != null

    override fun attach() {
        homeSettings.registerListener(this)
        listSettings.registerListener(this)
        musicRepository.addUpdateListener(this)
    }

    override fun onTabsChanged() {
        invalidator.invalidateTabs()
    }

    override fun onHideCollaboratorsChanged() {
        // Changes in the hide collaborator setting will change the artist contents
        // of the library, consider it a library update.
        L.d("Collaborator setting changed, forwarding update")
        invalidator.invalidateMusic(MusicType.ARTISTS, UpdateInstructions.Diff)
    }

    override fun onSongSortChanged() {
        super.onSongSortChanged()
        invalidator.invalidateMusic(MusicType.SONGS, UpdateInstructions.Replace(0))
    }

    override fun onAlbumSortChanged() {
        super.onAlbumSortChanged()
        invalidator.invalidateMusic(MusicType.ALBUMS, UpdateInstructions.Replace(0))
    }

    override fun onArtistSortChanged() {
        super.onArtistSortChanged()
        invalidator.invalidateMusic(MusicType.ARTISTS, UpdateInstructions.Replace(0))
    }

    override fun onGenreSortChanged() {
        super.onGenreSortChanged()
        invalidator.invalidateMusic(MusicType.GENRES, UpdateInstructions.Replace(0))
    }

    override fun onPlaylistSortChanged() {
        super.onPlaylistSortChanged()
        invalidator.invalidateMusic(MusicType.PLAYLISTS, UpdateInstructions.Replace(0))
    }

    override fun onMusicChanges(changes: MusicRepository.Changes) {
        val signedIn = jellyfin.session() != null
        if (signedIn != signedInToJellyfin) {
            L.d("Jellyfin account changed, sources must change")
            signedInToJellyfin = signedIn
            invalidator.invalidateSources()
        }

        invalidator.invalidateEmpty()

        val library = musicRepository.library
        if (changes.deviceLibrary && library != null) {
            L.d("Refreshing library")
            // Get the each list of items in the library to use as our list data.
            // Applying the preferred sorting to them.
            invalidator.invalidateMusic(MusicType.SONGS, UpdateInstructions.Diff)
            invalidator.invalidateMusic(MusicType.ALBUMS, UpdateInstructions.Diff)
            invalidator.invalidateMusic(MusicType.ARTISTS, UpdateInstructions.Diff)
            invalidator.invalidateMusic(MusicType.GENRES, UpdateInstructions.Diff)
        }

        if (changes.userLibrary && library != null) {
            L.d("Refreshing playlists")
            invalidator.invalidateMusic(MusicType.PLAYLISTS, UpdateInstructions.Diff)
        }
    }

    override fun release() {
        musicRepository.removeUpdateListener(this)
        listSettings.unregisterListener(this)
        homeSettings.unregisterListener(this)
    }

    override fun hasJellyfin() = signedInToJellyfin

    override fun empty(source: MusicSource?): Boolean {
        val library = musicRepository.library ?: return true
        val limit = effective(source) ?: return library.empty()
        return library.songs.none { it in limit }
    }

    override fun songs(source: MusicSource?) =
        musicRepository.library?.let { library ->
            val limit = effective(source)
            val songs = if (limit != null) library.songs.filter { it in limit } else library.songs
            listSettings.songSort.songs(songs)
        } ?: emptyList()

    override fun albums(source: MusicSource?) =
        musicRepository.library?.let { library ->
            val limit = effective(source)
            val albums =
                if (limit != null) {
                    library.albums.filter { album -> album.songs.any { it in limit } }
                } else {
                    library.albums
                }
            listSettings.albumSort.albums(albums)
        } ?: emptyList()

    override fun artists(source: MusicSource?) =
        musicRepository.library?.let { library ->
            val limit = effective(source)
            val artists =
                if (limit != null) {
                    library.artists.filter { artist -> artist.songs.any { it in limit } }
                } else {
                    library.artists
                }
            val sorted = listSettings.artistSort.artists(artists)
            if (homeSettings.shouldHideCollaborators) {
                sorted.filter { it.explicitAlbums.isNotEmpty() }
            } else {
                sorted
            }
        } ?: emptyList()

    override fun genres(source: MusicSource?) =
        musicRepository.library?.let { library ->
            val limit = effective(source)
            val genres =
                if (limit != null) {
                    library.genres.filter { genre -> genre.songs.any { it in limit } }
                } else {
                    library.genres
                }
            listSettings.genreSort.genres(genres)
        } ?: emptyList()

    override fun playlists(source: MusicSource?) =
        musicRepository.library?.let { library ->
            val limit = effective(source)
            val playlists =
                if (limit != null) {
                    // Playlists are made on the device. One that is empty is only shown there, as
                    // there is nothing in it that could say where it belongs.
                    library.playlists.filter { playlist ->
                        if (playlist.songs.isEmpty()) {
                            limit == MusicSource.LOCAL
                        } else {
                            playlist.songs.any { it in limit }
                        }
                    }
                } else {
                    library.playlists
                }
            listSettings.playlistSort.playlists(playlists)
        } ?: emptyList()

    /** The source to actually limit to, which is none if there is no one to choose it. */
    private fun effective(source: MusicSource?) = source?.takeIf { signedInToJellyfin }

    override fun tabs() = homeSettings.homeTabs.filterIsInstance<Tab.Visible>().map { it.type }
}
