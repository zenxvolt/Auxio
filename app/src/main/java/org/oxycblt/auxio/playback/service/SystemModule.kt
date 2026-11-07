/*
 * Copyright (c) 2023 Auxio Project
 * SystemModule.kt is part of Auxio.
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
 
package org.oxycblt.auxio.playback.service

import android.content.ContentResolver
import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.flac.FlacExtractor
import androidx.media3.extractor.mkv.MatroskaExtractor
import androidx.media3.extractor.mp3.Mp3Extractor
import androidx.media3.extractor.mp4.FragmentedMp4Extractor
import androidx.media3.extractor.mp4.Mp4Extractor
import androidx.media3.extractor.ogg.OggExtractor
import androidx.media3.extractor.text.DefaultSubtitleParserFactory
import androidx.media3.extractor.ts.AdtsExtractor
import androidx.media3.extractor.wav.WavExtractor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.IOException
import org.oxycblt.auxio.BuildConfig
import org.oxycblt.auxio.jellyfin.JellyfinAudioCache
import org.oxycblt.auxio.jellyfin.JellyfinAuthorizer
import org.oxycblt.auxio.jellyfin.JellyfinSessionStore
import org.oxycblt.auxio.jellyfin.JellyfinStreamKey

@Module
@InstallIn(SingletonComponent::class)
@OptIn(UnstableApi::class)
class SystemModule {
    @Provides
    fun mediaSourceFactory(
        dataSourceFactory: DataSource.Factory,
        extractorsFactory: ExtractorsFactory,
    ): MediaSource.Factory = ProgressiveMediaSource.Factory(dataSourceFactory, extractorsFactory)

    @Provides
    fun dataSourceFactory(
        @ApplicationContext context: Context,
        authorizer: JellyfinAuthorizer,
        audioCache: JellyfinAudioCache,
        sessionStore: JellyfinSessionStore,
    ): DataSource.Factory {
        // Songs are either on the device (content URIs) or streamed from the Jellyfin server that
        // the user signed in to. Nothing else is ever opened, and the access token is only ever
        // sent to that one server: the authorizer refuses every other address.
        val http =
            DefaultHttpDataSource.Factory()
                .setUserAgent("Auxio/${BuildConfig.VERSION_NAME}")
                .setConnectTimeoutMs(HTTP_TIMEOUT_MS)
                .setReadTimeoutMs(HTTP_TIMEOUT_MS)
        val authorized =
            ResolvingDataSource.Factory(http) { dataSpec ->
                dataSpec.withAdditionalHeaders(authorizer.headersFor(dataSpec.uri.toString()))
            }
        // The cache sits in front of the authorizer, so that a song that is already on the device
        // plays without asking anything of the server, even when it can't be reached. Only the
        // parts that are missing go through the authorizer. Every account has its own cache, see
        // JellyfinAudioCache. The cache is only set up once a song is streamed.
        val cached by lazy {
            CacheDataSource.Factory()
                .setCache(audioCache.cache)
                .setUpstreamDataSourceFactory(authorized)
                .setCacheKeyFactory { dataSpec ->
                    // The player always passes the key of the song, see ExoPlaybackStateHolder.
                    // Falling back to the URL is only a safety net.
                    dataSpec.key
                        ?: JellyfinStreamKey.of(dataSpec.uri.toString(), 0)
                        ?: dataSpec.uri.toString()
                }
                // A broken cache must never stop a song from playing, it's only a nicety.
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        }
        // This is checked whenever a song starts loading, so turning the cache off (or on) applies
        // to the very next song. DefaultDataSource asks for a remote source even for songs on the
        // device, so someone who isn't signed in never even sets up the cache.
        val remote = DataSource.Factory {
            if (audioCache.enabled && sessionStore.session() != null) {
                cached.createDataSource()
            } else {
                authorized.createDataSource()
            }
        }
        return ResolvingDataSource.Factory(DefaultDataSource.Factory(context, remote)) { dataSpec ->
            when (dataSpec.uri.scheme) {
                ContentResolver.SCHEME_CONTENT,
                "http",
                "https" -> dataSpec
                else -> throw IOException("Refusing to open ${dataSpec.uri.scheme} URI")
            }
        }
    }

    @Provides
    fun extractorsFactory() = ExtractorsFactory {
        // Define our own extractors so we can exclude non-audio parsers.
        // Ordering is derived from the DefaultExtractorsFactory's optimized ordering:
        // https://docs.google.com/document/d/1w2mKaWMxfz2Ei8-LdxqbPs1VLe_oudB-eryXXw9OvQQ.
        arrayOf(
            FlacExtractor(),
            WavExtractor(),
            FragmentedMp4Extractor(DefaultSubtitleParserFactory.UNSUPPORTED),
            Mp4Extractor(DefaultSubtitleParserFactory.UNSUPPORTED),
            OggExtractor(),
            MatroskaExtractor(DefaultSubtitleParserFactory.UNSUPPORTED),
            // Enable constant bitrate seeking so that certain MP3s/AACs are seekable
            AdtsExtractor(AdtsExtractor.FLAG_ENABLE_CONSTANT_BITRATE_SEEKING),
            Mp3Extractor(Mp3Extractor.FLAG_ENABLE_CONSTANT_BITRATE_SEEKING),
        )
    }
}

private const val HTTP_TIMEOUT_MS = 15_000
