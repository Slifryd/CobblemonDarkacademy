/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.economy.shop

import com.cobblemon.mod.common.api.dialogue.ActiveDialogue
import com.cobblemon.mod.common.api.dialogue.DialogueAction
import com.cobblemon.mod.common.economy.EconomyManager
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.SimpleContainer
import net.minecraft.world.SimpleMenuProvider
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.AbstractContainerMenu

class EconomyShopDialogueAction : DialogueAction {
    
    override fun invoke(dialogue: ActiveDialogue, input: String?) {
        if (!com.cobblemon.mod.common.Cobblemon.config.enableEconomy) {
             dialogue.playerEntity.sendSystemMessage(Component.literal("§cThe economy system is currently disabled."))
             return
        }

        com.cobblemon.mod.common.Cobblemon.LOGGER.info("EconomyShopDialogueAction invoked with input: $input")
        val player = dialogue.playerEntity
        val option = input ?: return

        when(option) {
            "open" -> {
                 com.cobblemon.mod.common.Cobblemon.LOGGER.info("Opening EconomyShopMenu for ${player.name.string}")
                 player.openMenu(SimpleMenuProvider(
                     { id, inv, p -> EconomyShopMenu(id, inv, p as ServerPlayer) },
                     Component.literal(EconomyShopConfigManager.config.shop_name)
                 ))
            }
            "balance" -> {
                 val bal = EconomyManager.getBalance(player)
                 player.sendSystemMessage(Component.literal("§aCurrent Balance: §6$$bal"))
            }
            else -> {
                com.cobblemon.mod.common.Cobblemon.LOGGER.warn("Unknown EconomyShopDialogueAction option: $option")
            }
        }
    }
}
