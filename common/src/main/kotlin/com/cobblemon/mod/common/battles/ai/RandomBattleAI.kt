/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.battles.ai

import com.cobblemon.mod.common.api.battles.model.PokemonBattle
import com.cobblemon.mod.common.api.battles.model.ai.BattleAI
import com.cobblemon.mod.common.battles.*

class RandomBattleAI : BattleAI {
    override fun choose(
        activeBattlePokemon: ActiveBattlePokemon,
        battle: PokemonBattle,
        aiSide: BattleSide,
        moveset: ShowdownMoveset?,
        forceSwitch: Boolean
    ): ShowdownActionResponse {

        // ===== FORCE SWITCH LOGIC (SAFE) =====
        if (forceSwitch || activeBattlePokemon.isGone()) {

            // UUID des Pokémon ACTIFS côté AI
            val activeUUIDs = aiSide.activePokemon
                .mapNotNull { it.battlePokemon?.uuid }
                .toSet()

            val candidates = activeBattlePokemon.actor.pokemonList
                .filter { it.health > 0 }                 // pas KO
                .filter { it.uuid !in activeUUIDs }      // pas déjà actif
                .filter { !it.willBeSwitchedIn }         // pas déjà choisi
                .filter { it.canBeSentOut() }            // Cobblemon safe check

            if (candidates.isEmpty()) {
                return DefaultActionResponse()
            }

            val switchTo = candidates.random()
            switchTo.willBeSwitchedIn = true
            return SwitchActionResponse(switchTo.uuid)
        }

        // ===== NORMAL MOVE LOGIC =====
        if (moveset == null) {
            return PassActionResponse
        }

        val move = moveset.moves
            .filter { it.canBeUsed() }
            .filter { it.mustBeUsed() || it.target.targetList(activeBattlePokemon)?.isEmpty() != true }
            .randomOrNull()
            ?: return MoveActionResponse("struggle")

        val target = if (move.mustBeUsed()) null else move.target.targetList(activeBattlePokemon)

        return if (target == null) {
            MoveActionResponse(move.id)
        } else {
            val chosenTarget = target
                .filter { !it.isAllied(activeBattlePokemon) }
                .randomOrNull()
                ?: target.random()

            MoveActionResponse(move.id, (chosenTarget as ActiveBattlePokemon).getPNX())
        }
    }
}
