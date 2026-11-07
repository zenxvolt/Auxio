/*
 * Copyright (c) 2026 Auxio Project
 * JellyfinDialog.kt is part of Auxio.
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

import android.os.Bundle
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import org.oxycblt.auxio.R
import org.oxycblt.auxio.databinding.DialogJellyfinBinding
import org.oxycblt.auxio.ui.ViewBindingMaterialDialogFragment
import org.oxycblt.auxio.util.collectImmediately

/**
 * A [ViewBindingMaterialDialogFragment] that lets the user sign in to a Jellyfin server, and see
 * and manage the account that they are signed in with.
 */
@AndroidEntryPoint
class JellyfinDialog : ViewBindingMaterialDialogFragment<DialogJellyfinBinding>() {
    private val model: JellyfinViewModel by viewModels()

    override fun onCreateBinding(inflater: LayoutInflater) =
        DialogJellyfinBinding.inflate(inflater)

    override fun onConfigDialog(builder: AlertDialog.Builder) {
        // All of the buttons have to exist for the dialog to be able to change what they do when
        // the account changes. Their text and behavior are set in render and onStart.
        builder
            .setTitle(R.string.set_jellyfin)
            .setNeutralButton(R.string.lbl_jellyfin_sync, null)
            .setNegativeButton(R.string.lbl_cancel, null)
            .setPositiveButton(R.string.lbl_jellyfin_sign_in, null)
    }

    override fun onBindingCreated(binding: DialogJellyfinBinding, savedInstanceState: Bundle?) {
        binding.jellyfinPassword.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submit()
                true
            } else {
                false
            }
        }
        // Whatever went wrong, the user is now doing something about it.
        binding.jellyfinServer.doAfterTextChanged { model.clearError() }
        binding.jellyfinUsername.doAfterTextChanged { model.clearError() }
        binding.jellyfinPassword.doAfterTextChanged { model.clearError() }

        collectImmediately(model.account, model.working, model.error) { _, _, _ -> render() }
    }

    override fun onStart() {
        super.onStart()
        // Setting the listeners after the dialog is shown stops the buttons from closing it, which
        // is what they would do on their own.
        val dialog = requireDialog() as AlertDialog
        dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
            if (model.account.value == null) submit() else dismiss()
        }
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setOnClickListener {
            if (model.account.value == null) dismiss() else model.signOut()
        }
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setOnClickListener { model.sync() }
        render()
    }

    private fun submit() {
        val binding = binding ?: return
        model.signIn(
            binding.jellyfinServer.text.toString(),
            binding.jellyfinUsername.text.toString(),
            binding.jellyfinPassword.text.toString(),
        )
    }

    private fun render() {
        val binding = binding ?: return
        val account = model.account.value
        val working = model.working.value
        val error = model.error.value

        binding.jellyfinForm.isVisible = account == null
        binding.jellyfinAccount.isVisible = account != null
        binding.jellyfinProgress.visibility = if (working) View.VISIBLE else View.INVISIBLE
        binding.jellyfinError.isVisible = error != null
        if (error != null) {
            binding.jellyfinError.setText(error)
        }

        binding.jellyfinServerContainer.isEnabled = !working
        binding.jellyfinUsernameContainer.isEnabled = !working
        binding.jellyfinPasswordContainer.isEnabled = !working

        if (account != null) {
            // The password has done its job, don't keep it around in the UI.
            if (!binding.jellyfinPassword.text.isNullOrEmpty()) {
                binding.jellyfinPassword.text?.clear()
            }
            binding.jellyfinAccountName.text =
                getString(
                    R.string.fmt_jellyfin_account,
                    account.session.userName,
                    account.session.serverName,
                )
            binding.jellyfinAccountUrl.text = account.session.serverUrl
            binding.jellyfinAccountStatus.text = statusOf(account)
        }

        val dialog = dialog as? AlertDialog ?: return
        val positive: Button? = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        val negative: Button? = dialog.getButton(AlertDialog.BUTTON_NEGATIVE)
        val neutral: Button? = dialog.getButton(AlertDialog.BUTTON_NEUTRAL)
        if (account == null) {
            positive?.setText(R.string.lbl_jellyfin_sign_in)
            positive?.isEnabled = !working
            negative?.setText(R.string.lbl_cancel)
            negative?.isEnabled = true
            neutral?.isVisible = false
        } else {
            positive?.setText(R.string.lbl_ok)
            positive?.isEnabled = true
            negative?.setText(R.string.lbl_jellyfin_sign_out)
            negative?.isEnabled = !working
            neutral?.setText(R.string.lbl_jellyfin_sync)
            neutral?.isVisible = true
            neutral?.isEnabled = !working
        }
    }

    private fun statusOf(account: JellyfinViewModel.Account): String {
        val sync = account.sync ?: return getString(R.string.lbl_jellyfin_never_synced)
        val songs =
            resources.getQuantityString(R.plurals.fmt_song_count, sync.songCount, sync.songCount)
        val time =
            DateUtils.getRelativeTimeSpanString(
                sync.timeMs,
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS,
            )
        val failure = sync.failure ?: return getString(R.string.fmt_jellyfin_synced, songs, time)
        return getString(R.string.fmt_jellyfin_sync_failed, songs, getString(messageOf(failure)))
    }
}
