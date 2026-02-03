/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.net.messages.client.tower

import com.cobblemon.mod.common.api.battlefactory.TowerPokemonDTO
import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.util.cobblemonResource
import net.minecraft.network.RegistryFriendlyByteBuf

/**
 * Packet sent from server to client to open the Tower Pokemon selection GUI.
 * 
 * Contains the 6 Pokemon offered for selection and the difficulty level.
 * 
 * @author Cobblemon Contributors
 * @since January 2026
 */
class OpenTowerSelectionPacket(
    val offeredPokemon: List<TowerPokemonDTO>,
    val difficulty: String
) : NetworkPacket<OpenTowerSelectionPacket> {
    
    companion object {
        val ID = cobblemonResource("open_tower_selection")

        fun decode(buffer: net.minecraft.network.FriendlyByteBuf): OpenTowerSelectionPacket {
            val registryBuffer = buffer as RegistryFriendlyByteBuf
            return OpenTowerSelectionPacket(
                offeredPokemon = registryBuffer.readCollection({ mutableListOf() }) {
                    TowerPokemonDTO.decode(it as RegistryFriendlyByteBuf)
                },
                difficulty = registryBuffer.readUtf()
            )
        }
    }
    
    override val id = ID
    
    override fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeCollection(offeredPokemon) { buf, pokemon -> pokemon.encode(buf as RegistryFriendlyByteBuf) }
        buffer.writeUtf(difficulty)
    }
}
