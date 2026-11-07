/*
 * Copyright (c) 2023 Auxio Project
 * SortDialog.kt is part of Auxio.
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
 
package org.oxycblt.auxio.list.sort

import android.os.Bundle
import android.view.LayoutInflater
import androidx.core.view.updatePadding
import androidx.recyclerview.widget.RecyclerView
import org.oxycblt.auxio.databinding.DialogSortBinding
import org.oxycblt.auxio.list.ClickableListListener
import org.oxycblt.auxio.list.adapter.UpdateInstructions
import org.oxycblt.auxio.ui.ViewBindingBottomSheetDialogFragment
import org.oxycblt.auxio.util.systemBarInsetsCompat

abstract class SortDialog :
    ViewBindingBottomSheetDialogFragment<DialogSortBinding>(), ClickableListListener<Sort.Mode> {
    private val modeAdapter = SortModeAdapter(@Suppress("LeakingThis") this)
    // The seed that will be used if the user applies a random sort. It's picked once per dialog
    // so that what is compared and what is applied are the same thing, and it always differs from
    // the seed of the current sort so that applying "random" again shuffles the list again.
    private var pendingSeed = Sort.newSeed(excluding = null)

    abstract fun getInitialSort(): Sort?

    abstract fun applyChosenSort(sort: Sort)

    abstract fun getModeChoices(): List<Sort.Mode>

    override fun onCreateBinding(inflater: LayoutInflater) = DialogSortBinding.inflate(inflater)

    override fun onBindingCreated(binding: DialogSortBinding, savedInstanceState: Bundle?) {
        super.onBindingCreated(binding, savedInstanceState)

        // --- UI SETUP ---
        binding.root.setOnApplyWindowInsetsListener { v, insets ->
            v.updatePadding(bottom = insets.systemBarInsetsCompat.bottom)
            insets
        }
        binding.sortModeRecycler.adapter = modeAdapter
        binding.sortCancel.setOnClickListener { dismiss() }
        binding.sortSave.setOnClickListener {
            applyChosenSort(requireNotNull(getCurrentSort()))
            dismiss()
        }
        binding.sortDirectionAsc.setOnClickListener { updateDirection(Sort.Direction.ASCENDING) }
        binding.sortDirectionDsc.setOnClickListener { updateDirection(Sort.Direction.DESCENDING) }

        // --- STATE SETUP ---
        modeAdapter.update(getModeChoices(), UpdateInstructions.Diff)

        val initial = getInitialSort()
        if (initial != null) {
            pendingSeed = Sort.newSeed(excluding = initial.seed)
            modeAdapter.setSelected(initial.mode)
            updateDirection(initial.direction)
        }
        updateButtons()
    }

    override fun onClick(item: Sort.Mode, viewHolder: RecyclerView.ViewHolder) {
        modeAdapter.setSelected(item)
        updateButtons()
    }

    private fun updateDirection(direction: Sort.Direction) {
        val binding = requireBinding()
        binding.sortDirectionAsc.isChecked = direction == Sort.Direction.ASCENDING
        binding.sortDirectionDsc.isChecked = direction == Sort.Direction.DESCENDING
        updateButtons()
    }

    private fun updateButtons() {
        val binding = requireBinding()
        // Direction means nothing when shuffling, so make that obvious.
        val directional = modeAdapter.currentMode != Sort.Mode.ByRandom
        binding.sortHeader.alpha = if (directional) 1f else DISABLED_ALPHA
        binding.sortDirectionAsc.isEnabled = directional
        binding.sortDirectionDsc.isEnabled = directional
        binding.sortSave.isEnabled = getCurrentSort().let { it != null && it != getInitialSort() }
    }

    private fun getCurrentSort(): Sort? {
        val binding = requireBinding()
        val mode = modeAdapter.currentMode ?: return null
        if (mode == Sort.Mode.ByRandom) {
            // Since a random sort carries a new seed, it'll always be different from the
            // initial sort, which is what lets the user re-shuffle by pressing OK again.
            return Sort(mode, Sort.Direction.ASCENDING, pendingSeed)
        }
        val direction =
            if (binding.sortDirectionAsc.isChecked) Sort.Direction.ASCENDING
            else Sort.Direction.DESCENDING
        return Sort(mode, direction)
    }

    private companion object {
        const val DISABLED_ALPHA = 0.38f
    }
}
