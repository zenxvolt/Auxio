/*
 * Copyright (c) 2023 Auxio Project
 * MusicPreferenceFragment.kt is part of Auxio.
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
 
package org.oxycblt.auxio.settings.categories

import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.preference.Preference
import coil3.ImageLoader
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch
import org.oxycblt.auxio.R
import org.oxycblt.auxio.jellyfin.JellyfinSessionStore
import org.oxycblt.auxio.music.MusicViewModel
import org.oxycblt.auxio.settings.BasePreferenceFragment
import org.oxycblt.auxio.settings.ui.WrappedDialogPreference
import org.oxycblt.auxio.util.navigateSafe
import timber.log.Timber as L

/**
 * "Content" settings.
 *
 * @author Alexander Capehart (OxygenCobalt)
 */
@AndroidEntryPoint
class MusicPreferenceFragment : BasePreferenceFragment(R.xml.preferences_music) {
    private val musicModel: MusicViewModel by viewModels()
    @Inject lateinit var imageLoader: ImageLoader
    @Inject lateinit var jellyfinStore: JellyfinSessionStore

    override fun onOpenDialogPreference(preference: WrappedDialogPreference) {
        if (preference.key == getString(R.string.set_key_separators)) {
            L.d("Navigating to separator dialog")
            findNavController().navigateSafe(MusicPreferenceFragmentDirections.separatorsSettings())
        }
        if (preference.key == getString(R.string.set_key_jellyfin)) {
            L.d("Navigating to Jellyfin dialog")
            findNavController().navigateSafe(MusicPreferenceFragmentDirections.jellyfinSettings())
        }
    }

    override fun onSetupPreference(preference: Preference) {
        if (preference.key == getString(R.string.set_key_jellyfin)) {
            L.d("Configuring Jellyfin setting")
            // The summary says who is signed in, so it has to follow the account.
            viewLifecycleOwner.lifecycleScope.launch {
                viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    jellyfinStore.changes.collect {
                        preference.summary =
                            jellyfinStore.session()?.let {
                                getString(
                                    R.string.fmt_jellyfin_account,
                                    it.userName,
                                    it.serverName,
                                )
                            } ?: getString(R.string.set_jellyfin_desc)
                    }
                }
            }
        }
        if (preference.key == getString(R.string.set_key_cover_mode)) {
            L.d("Configuring cover mode setting")
            preference.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, _ ->
                L.d("Cover mode changed, reloading music")
                musicModel.refresh()
                true
            }
        }
        if (preference.key == getString(R.string.set_key_square_covers)) {
            L.d("Configuring square cover setting")
            preference.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, _ ->
                L.d("Cover mode changed, resetting image memory cache")
                imageLoader.memoryCache?.clear()
                true
            }
        }
        if (preference.key == getString(R.string.set_key_with_hidden)) {
            L.d("Configuring ignore hidden files setting")
            preference.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, _ ->
                L.d("Ignore hidden files setting changed, reloading music")
                musicModel.refresh()
                true
            }
        }
    }
}
