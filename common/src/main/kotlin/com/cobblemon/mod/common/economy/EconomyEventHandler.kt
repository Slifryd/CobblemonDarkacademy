/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.economy

import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.battles.actor.PlayerBattleActor
import net.minecraft.network.chat.Component

object EconomyEventHandler {

    fun register() {
        CobblemonEvents.POKEMON_CAPTURED.subscribe { event ->
            val player = event.player
            val pokemon = event.pokemon
            
            var amount = 100L
            amount += pokemon.level * 10
            if (pokemon.shiny) amount += 500
            if (pokemon.isLegendary()) amount += 1000
            
            EconomyManager.add(player, amount, "for capturing ${pokemon.species.name}")
        }

        CobblemonEvents.POKEMON_FAINTED.subscribe { event ->
             // Implementation deferred
        }
        
        CobblemonEvents.BATTLE_VICTORY.subscribe { event ->
            event.winners
                .filterIsInstance<PlayerBattleActor>()
                .mapNotNull { it.entity }
                .forEach { player ->
                    EconomyManager.add(player, 500, "for winning a battle!")
                }
        }
    }
}
