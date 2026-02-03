/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.economy

import com.cobblemon.mod.common.api.storage.player.client.ClientInstancedPlayerData
import com.cobblemon.mod.common.net.messages.client.SetClientPlayerDataPacket
import com.cobblemon.mod.common.economy.EconomyManager
import net.minecraft.network.RegistryFriendlyByteBuf

class ClientPlayerEconomyData(
    val balance: Long,
    val totalEarned: Long,
    val totalSpent: Long
) : ClientInstancedPlayerData {

    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeLong(balance)
        buffer.writeLong(totalEarned)
        buffer.writeLong(totalSpent)
    }

    companion object {
        var clientData: ClientPlayerEconomyData? = null

        fun decode(buffer: RegistryFriendlyByteBuf): SetClientPlayerDataPacket {
            val data = ClientPlayerEconomyData(
                buffer.readLong(),
                buffer.readLong(),
                buffer.readLong()
            )
            return SetClientPlayerDataPacket(EconomyManager.ECONOMY_DATA_TYPE, data)
        }
        
        fun runAction(data: ClientInstancedPlayerData) {
             if (data is ClientPlayerEconomyData) {
                 clientData = data
             }
        }
    }
}
