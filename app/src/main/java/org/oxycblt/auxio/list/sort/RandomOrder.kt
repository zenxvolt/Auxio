/*
 * Copyright (c) 2026 Auxio Project
 * RandomOrder.kt is part of Auxio.
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

import java.util.Collections
import java.util.Random

/**
 * Shuffle [items] into an order that depends **only** on the set of items and the [seed].
 *
 * Several parts of the app (the home list, the detail lists, and the playback queue that is built
 * when a song is clicked) each sort the same songs independently. For "random" to be coherent, all
 * of them must arrive at the exact same order, so the result here must not depend on the iteration
 * order of the input collection (which might be backed by a hash set that changes between library
 * loads). To achieve this, the items are first put in a canonical order using [key], and only then
 * shuffled using a PRNG seeded with [seed]. [java.util.Random] and [Collections.shuffle] have fully
 * specified algorithms, so the outcome is reproducible across JVM/ART versions.
 *
 * @param items The items to shuffle.
 * @param seed The seed of the shuffle. The same seed always yields the same order.
 * @param key Produces a stable, unique-ish identity for an item, used to canonicalize the input.
 *   Items with equal keys keep their relative input order.
 * @return A new list containing every element of [items] exactly once.
 */
internal fun <T> shuffleDeterministically(
    items: Collection<T>,
    seed: Int,
    key: (T) -> String,
): List<T> {
    // Compute each key exactly once, as the keys can be somewhat expensive to build.
    val canonical =
        items.map { key(it) to it }.sortedBy { it.first }.map { it.second }.toMutableList()
    Collections.shuffle(canonical, Random(seed.toLong()))
    return canonical
}
