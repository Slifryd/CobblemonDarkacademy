/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.battlefactory

import com.cobblemon.mod.common.api.npc.configuration.NPCInteractConfiguration
import com.cobblemon.mod.common.battles.BattleBuilder
import com.cobblemon.mod.common.battles.BattleFormat
import com.cobblemon.mod.common.entity.npc.NPCEntity
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.server.level.ServerPlayer

class TowerBattleInteraction : NPCInteractConfiguration {
    override val type = "tower_battle"

    override fun interact(npc: NPCEntity, player: ServerPlayer): Boolean {
        // Standard battle initiation - TowerBattleActor override happens via NPCDefineBattleActorEvent
        BattleBuilder.pvn(
            player = player,
            npcEntity = npc,
            battleFormat = BattleFormat.GEN_9_SINGLES
        )
        return true
    }

    override fun encode(buffer: RegistryFriendlyByteBuf) {}
    override fun decode(buffer: RegistryFriendlyByteBuf) {}
    
    override fun writeToNBT(compoundTag: CompoundTag) {
        // No extra data needs to be saved, the type is saved by the parent container
    }
    
    override fun readFromNBT(compoundTag: CompoundTag) {
        // No extra data to read
    }
    
    override fun isDifferentTo(other: NPCInteractConfiguration): Boolean {
        return other !is TowerBattleInteraction
    }
}
