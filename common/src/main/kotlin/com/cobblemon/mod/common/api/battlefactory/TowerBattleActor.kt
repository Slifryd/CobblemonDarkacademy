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
 * This simulates a "Player-like" actor for UI purposes, similar to how RadGym handles gym trainers.
 */
class TowerBattleActor(
    npc: NPCEntity,
    pokemonList: List<BattlePokemon>,
    skill: Int
) : NPCBattleActor(npc, pokemonList, skill) {

    override val initialPos = npc.position().add(0.0, 0.2, 0.0).add(npc.lookAngle.scale(2.0))

    private var sendingOutStartTime: Long = 0L
    private var lastRequestTurn: Int = -1
    private var requestRetryCount: Int = 0

    init {
        npc.addTag("TowerDebug")
        Cobblemon.LOGGER.info("[TOWER DEBUG] Tagged NPC ${npc.uuid} for debug logging.")
    }

    /**
     * Syncs health from Showdown request to local Pokemon
     */
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

    /**
     * Intercept when choice is requested to sync health BEFORE AI makes decisions
     */
    override fun onChoiceRequested() {
        Cobblemon.LOGGER.info("[TOWER DEBUG] onChoiceRequested called for turn ${battle.turn}")

        // Track if this is a retry (same turn, multiple requests)
        if (battle.turn == lastRequestTurn) {
            requestRetryCount++
            Cobblemon.LOGGER.warn("[TOWER DEBUG] This is retry #$requestRetryCount for turn ${battle.turn}")
        } else {
            lastRequestTurn = battle.turn
            requestRetryCount = 0
        }

        // Sync health before AI decision
        syncHealthFromRequest()

        // Log available Pokemon before decision
        Cobblemon.LOGGER.info("[TOWER DEBUG] Available Pokemon for switch:")
        pokemonList.forEachIndexed { index, pokemon ->
            val showdownData = request?.side?.pokemon?.find { it.uuid == pokemon.uuid }
            val isInActivePokemon = activePokemon.any { it.battlePokemon?.uuid == pokemon.uuid }
            Cobblemon.LOGGER.info("[TOWER DEBUG]   [$index] ${pokemon.effectedPokemon.species.name}: HP=${pokemon.health}, Showdown=${showdownData?.condition ?: "N/A"}, ShowdownActive=${showdownData?.active ?: false}, InActiveList=$isInActivePokemon")
        }

        // If this is a retry and we only have dead Pokemon left, end the battle
        if (requestRetryCount > 2) {
            val allDead = pokemonList.all { it.health <= 0 }
            if (allDead) {
                Cobblemon.LOGGER.error("[TOWER DEBUG] All Pokemon dead after multiple retries - ending battle")
                battle.end()
                val playerActor = battle.actors.filterIsInstance<com.cobblemon.mod.common.battles.actor.PlayerBattleActor>().firstOrNull()
                playerActor?.entity?.let { player ->
                    BattleFactoryTowerManager.onArenaVictory(player)
                }
                return
            }
        }

        // Now let the parent class make decisions with correct health values
        super.onChoiceRequested()
    }

    override fun sendUpdate(packet: NetworkPacket<*>) {
        // CRITICAL: Sync health FIRST before calling super.sendUpdate which triggers onChoiceRequested
        if (packet is BattleQueueRequestPacket) {
            val requestSideId = packet.request.side?.id
            val mySideId = if (getSide() == battle.side1) "p1" else "p2"

            if (requestSideId == mySideId) {
                // Sync health BEFORE anything else
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

        if (currentUUID == null && battle.turn == 0) return

        if (expectedUUID != currentUUID) {
            Cobblemon.LOGGER.warn("[TOWER DEBUG] DESYNC DETECTED! Showdown expects $expectedUUID but we have $currentUUID")

            var repaired = false

            try {
                val correctPokemon = pokemonList.find { it.uuid == expectedUUID }

                if (correctPokemon != null) {
                    // cleanup old
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
                Cobblemon.LOGGER.error("[TOWER DEBUG] UNRECOVERABLE DESYNC -> Forcing battle end")
                battle.end()
                val playerActor = battle.actors.filterIsInstance<com.cobblemon.mod.common.battles.actor.PlayerBattleActor>().firstOrNull()
                playerActor?.entity?.let { player ->
                    BattleFactoryTowerManager.onArenaVictory(player)
                }
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