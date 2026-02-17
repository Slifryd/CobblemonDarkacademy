/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.battlefactory

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.net.NetworkPacket
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon
import com.cobblemon.mod.common.entity.npc.NPCBattleActor
import com.cobblemon.mod.common.entity.npc.NPCEntity
import com.cobblemon.mod.common.net.messages.client.battle.BattleQueueRequestPacket

/**
 * Custom battle actor for Tower Trainers that extends NPCBattleActor.
 */
class TowerBattleActor(
    npc: NPCEntity,
    pokemonList: List<BattlePokemon>,
    skill: Int
) : NPCBattleActor(npc, pokemonList, skill) {

    override val initialPos = npc.position().add(0.0, 0.2, 0.0).add(npc.lookAngle.scale(2.0))

    private var sendingOutStartTime: Long = 0L
    private var lastRequestTurnKey: String = ""
    private var requestRetryCount: Int = 0
    private val repairedTurns = mutableSetOf<Int>()

    init {
        npc.addTag("TowerDebug")
        Cobblemon.LOGGER.info("[TOWER DEBUG] Tagged NPC ${npc.uuid} for debug logging.")
    }

    private fun syncHealthFromRequest() {
        request?.side?.pokemon?.forEach { showdownPokemon ->
            val localPokemon = pokemonList.find { it.uuid == showdownPokemon.uuid }
            if (localPokemon != null) {
                val conditionParts = showdownPokemon.condition.split(" ")
                val isFainted = conditionParts.contains("fnt") || conditionParts[0] == "0"
                val showdownHealth = if (isFainted) {
                    0
                } else {
                    conditionParts[0].split("/").getOrNull(0)?.toIntOrNull() ?: localPokemon.health
                }

                if (showdownHealth != localPokemon.health) {
                    Cobblemon.LOGGER.warn("[TOWER DEBUG] Health sync: ${showdownPokemon.ident} -> $showdownHealth HP (was ${localPokemon.health})")
                    localPokemon.effectedPokemon.currentHealth = showdownHealth
                }
            }
        }
    }

    private fun forcePlayerVictory(reason: String) {
        Cobblemon.LOGGER.error("[TOWER DEBUG] Forcing player victory - reason: $reason")
        pokemonList.forEach { pokemon ->
            pokemon.effectedPokemon.currentHealth = 0
        }
        battle.end()
        val playerActor = battle.actors.filterIsInstance<com.cobblemon.mod.common.battles.actor.PlayerBattleActor>().firstOrNull()
        playerActor?.entity?.let { player ->
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§6[Tower] Battle desync detected - Victory awarded"))
            BattleFactoryTowerManager.onArenaVictory(player)
        }
    }

    override fun onChoiceRequested() {
        val currentTurn = battle.turn
        val isForceSwitch = request?.forceSwitch?.any { it } ?: false
        val isWaitRequest = request?.wait ?: false

        // Detect when Showdown sends a "move" request but we have no active pokemon
        // This happens when Showdown rejected our switch choice (active pokemon is dead)
        // and sends a fallback move request - treat this as a switch retry
        val activeIsDeadInShowdown = request?.side?.pokemon?.any {
            it.active && (it.condition.contains("fnt") || it.condition.startsWith("0 ") || it.condition == "0")
        } ?: false

        val effectiveTurnKey = if (!isForceSwitch && activeIsDeadInShowdown) {
            // Showdown sent a move request but active pokemon is dead - treat as switch retry
            Cobblemon.LOGGER.warn("[TOWER DEBUG] Move request but active Pokemon is dead - treating as switch retry")
            "$currentTurn-switch"
        } else {
            "$currentTurn-${if (isForceSwitch) "switch" else "move"}"
        }

        Cobblemon.LOGGER.info("[TOWER DEBUG] onChoiceRequested called for turn $currentTurn (forceSwitch=$isForceSwitch, activeIsDeadInShowdown=$activeIsDeadInShowdown, turnKey=$effectiveTurnKey)")

        if (repairedTurns.contains(currentTurn)) {
            Cobblemon.LOGGER.info("[TOWER DEBUG] Turn $currentTurn already repaired - skipping retry check")
        } else if (effectiveTurnKey == lastRequestTurnKey) {
            requestRetryCount++
            Cobblemon.LOGGER.warn("[TOWER DEBUG] This is retry #$requestRetryCount for $effectiveTurnKey")

            val hasAlivePokemon = pokemonList.any { it.health > 0 }
            Cobblemon.LOGGER.warn("[TOWER DEBUG] Retry analysis: hasAlivePokemon=$hasAlivePokemon")

            if (requestRetryCount >= 1 && !hasAlivePokemon) {
                forcePlayerVictory("All NPC Pokemon dead after retry on $effectiveTurnKey")
                return
            } else if (requestRetryCount >= 2) {
                forcePlayerVictory("Too many retries ($requestRetryCount) on $effectiveTurnKey")
                return
            }
            Cobblemon.LOGGER.warn("[TOWER DEBUG] Retry but NPC still has alive Pokemon - letting battle continue")
        } else {
            lastRequestTurnKey = effectiveTurnKey
            requestRetryCount = 0
            Cobblemon.LOGGER.info("[TOWER DEBUG] New request type: $effectiveTurnKey")
        }

        syncHealthFromRequest()

        val deadInActive = activePokemon.filter { activeSlot ->
            val pokemon = activeSlot.battlePokemon
            pokemon != null && pokemon.health <= 0
        }

        if (deadInActive.isNotEmpty()) {
            Cobblemon.LOGGER.warn("[TOWER DEBUG] Cleaning up ${deadInActive.size} dead Pokemon from activePokemon list")
            deadInActive.forEach { deadSlot ->
                deadSlot.battlePokemon?.entity?.discard()
                Cobblemon.LOGGER.info("[TOWER DEBUG] Removed dead ${deadSlot.battlePokemon?.getName()} from active slot")
            }
            activePokemon.removeAll(deadInActive)
        }

        Cobblemon.LOGGER.info("[TOWER DEBUG] Available Pokemon for switch:")
        pokemonList.forEachIndexed { index, pokemon ->
            val showdownData = request?.side?.pokemon?.find { it.uuid == pokemon.uuid }
            val isInActivePokemon = activePokemon.any { it.battlePokemon?.uuid == pokemon.uuid }
            Cobblemon.LOGGER.info("[TOWER DEBUG]   [$index] ${pokemon.effectedPokemon.species.name}: HP=${pokemon.health}, Showdown=${showdownData?.condition ?: "N/A"}, ShowdownActive=${showdownData?.active ?: false}, InActiveList=$isInActivePokemon")
        }

        super.onChoiceRequested()
    }

    override fun sendUpdate(packet: NetworkPacket<*>) {
        if (packet is BattleQueueRequestPacket) {
            val requestSideId = packet.request.side?.id
            val mySideId = if (getSide() == battle.side1) "p1" else "p2"

            if (requestSideId == mySideId) {
                packet.request.side?.pokemon?.forEach { showdownPokemon ->
                    val localPokemon = pokemonList.find { it.uuid == showdownPokemon.uuid }
                    if (localPokemon != null) {
                        val conditionParts = showdownPokemon.condition.split(" ")
                        val isFainted = conditionParts.contains("fnt") || conditionParts[0] == "0"
                        val showdownHealth = if (isFainted) {
                            0
                        } else {
                            conditionParts[0].split("/").getOrNull(0)?.toIntOrNull() ?: localPokemon.health
                        }

                        if (showdownHealth != localPokemon.health) {
                            Cobblemon.LOGGER.warn("[TOWER DEBUG] Pre-sendUpdate health sync: ${showdownPokemon.ident} -> $showdownHealth HP")
                            localPokemon.effectedPokemon.currentHealth = showdownHealth
                        }
                    }
                }
            }
        }

        super.sendUpdate(packet)

        if (packet !is BattleQueueRequestPacket) return

        Cobblemon.LOGGER.info("[TOWER DEBUG] BattleQueueRequestPacket received. Turn: ${battle.turn}")

        val requestSideId = packet.request.side?.id
        val mySideId = if (getSide() == battle.side1) "p1" else "p2"
        if (requestSideId != mySideId) return

        val requestActiveUUIDs = packet.request.side?.pokemon
            ?.filter { it.active }
            ?.map { it.uuid }
            ?: emptyList()

        if (requestActiveUUIDs.isEmpty()) return

        val expectedUUID = requestActiveUUIDs.first()
        val currentUUID = activePokemon.firstOrNull()?.battlePokemon?.uuid

        if (currentUUID == null) {
            Cobblemon.LOGGER.info("[TOWER DEBUG] activePokemon slot is null - normal transitional state, skipping desync check")
            return
        }

        if (expectedUUID != currentUUID) {
            Cobblemon.LOGGER.warn("[TOWER DEBUG] DESYNC DETECTED! Showdown expects $expectedUUID but we have $currentUUID")

            var repaired = false

            try {
                val correctPokemon = pokemonList.find { it.uuid == expectedUUID }

                if (correctPokemon != null) {
                    activePokemon.firstOrNull()?.battlePokemon?.entity?.discard()

                    if (activePokemon.isNotEmpty()) {
                        activePokemon[0].battlePokemon = correctPokemon
                    }

                    correctPokemon.entity?.discard()

                    if (npc.level() is net.minecraft.server.level.ServerLevel) {
                        val spawnPos = initialPos.add(npc.lookAngle.scale(2.0))
                        val spawned = correctPokemon.originalPokemon.sendOut(
                            npc.level() as net.minecraft.server.level.ServerLevel,
                            spawnPos,
                            null
                        )
                        spawned?.battleId = battle.battleId
                    }

                    val sideIndex = battle.actors.indexOf(this) + 1
                    val pnx = "p${sideIndex}a"

                    battle.sendSidedUpdate(
                        this,
                        com.cobblemon.mod.common.net.messages.client.battle.BattleSwitchPokemonPacket(pnx, correctPokemon, true, null),
                        com.cobblemon.mod.common.net.messages.client.battle.BattleSwitchPokemonPacket(pnx, correctPokemon, false, null)
                    )

                    correctPokemon.sendUpdate()
                    mustChoose = true

                    repairedTurns.add(battle.turn)
                    Cobblemon.LOGGER.info("[TOWER DEBUG] Marked turn ${battle.turn} as repaired")

                    onChoiceRequested()

                    val newCurrent = activePokemon.firstOrNull()?.battlePokemon?.uuid
                    if (newCurrent == expectedUUID) {
                        Cobblemon.LOGGER.info("[TOWER DEBUG] Desync successfully repaired.")
                        repaired = true
                    }
                }
            } catch (e: Exception) {
                Cobblemon.LOGGER.error("[TOWER DEBUG] Exception during resync: ${e.message}")
            }

            if (!repaired) {
                forcePlayerVictory("Unrecoverable desync on turn ${battle.turn}")
                return
            }

            battle.dispatchResult = com.cobblemon.mod.common.battles.dispatch.GO
        }
    }

    fun checkWatchdog() {
        if (stillSendingOutCount > 0 && sendingOutStartTime != 0L) {
            val elapsed = System.currentTimeMillis() - sendingOutStartTime
            if (elapsed > 5000) {
                Cobblemon.LOGGER.error("[TOWER WATCHDOG] Actor stuck for ${elapsed}ms. Forcing recovery...")

                stillSendingOutCount = 0
                sendingOutStartTime = 0L

                val currentPkm = activePokemon.firstOrNull()?.battlePokemon
                if (currentPkm != null && (currentPkm.entity == null || !currentPkm.entity!!.isAlive)) {
                    if (npc.level() is net.minecraft.server.level.ServerLevel) {
                        val spawnPos = initialPos.add(npc.lookAngle.scale(2.0))
                        val spawned = currentPkm.originalPokemon.sendOut(
                            npc.level() as net.minecraft.server.level.ServerLevel,
                            spawnPos,
                            null
                        )
                        spawned?.battleId = battle.battleId
                    }
                }

                battle.dispatchResult = com.cobblemon.mod.common.battles.dispatch.GO
            }
        }
    }
}