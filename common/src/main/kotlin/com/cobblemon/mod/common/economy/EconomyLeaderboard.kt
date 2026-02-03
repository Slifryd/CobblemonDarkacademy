/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.economy

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import net.minecraft.server.level.ServerPlayer

object EconomyLeaderboard {

    data class Entry(val uuid: UUID, val name: String, val balance: Long) : Comparable<Entry> {
        override fun compareTo(other: Entry): Int {
            return other.balance.compareTo(this.balance) // Descending
        }
    }

    private val entries = ConcurrentHashMap<UUID, Entry>()
    
    fun update(player: ServerPlayer, balance: Long) {
        entries[player.uuid] = Entry(player.uuid, player.name.string, balance)
    }

    fun getTop(offset: Int = 0, limit: Int = 10): List<Entry> {
        return entries.values.sorted().drop(offset).take(limit)
    }
}
