/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.economy

import com.cobblemon.mod.common.api.storage.player.InstancedPlayerData
import com.cobblemon.mod.common.api.storage.player.client.ClientInstancedPlayerData
import java.util.UUID

data class PlayerEconomyData(
    override val uuid: UUID,
    var balance: Long = 0,
    var totalEarned: Long = 0,
    var totalSpent: Long = 0
) : InstancedPlayerData {

    override fun toClientData(): ClientInstancedPlayerData {
        return ClientPlayerEconomyData(balance, totalEarned, totalSpent)
    }
}
