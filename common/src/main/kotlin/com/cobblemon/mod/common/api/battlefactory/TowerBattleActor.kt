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
import com.cobblemon.mod.common.net.messages.client.battle.BattleMakeChoicePacket
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
    
    // Override initialPos to ensure a safe, elevated position for sending out Pokémon.
    // This prevents potential raycast failures if the NPC is spawned exactly on the ground or inside a block.
    override val initialPos = npc.position().add(0.0, 0.2, 0.0).add(npc.lookAngle.scale(2.0))

    private var sendingOutStartTime: Long = 0L

    init {
        npc.addTag("TowerDebug")
        Cobblemon.LOGGER.info("[TOWER DEBUG] Tagged NPC ${npc.uuid} for debug logging.")
    }



    override fun sendUpdate(packet: NetworkPacket<*>) {
        super.sendUpdate(packet)
        
        // Debug logging to trace packet flow
        if (packet is BattleQueueRequestPacket) {
            Cobblemon.LOGGER.info("[TOWER DEBUG] BattleQueueRequestPacket received. Turn: ${battle.turn}")
            
            // 1. ANIMATION BLOCK CHECK (Smart Wait)
            if (stillSendingOutCount > 0) {
                if (sendingOutStartTime == 0L) {
                    sendingOutStartTime = System.currentTimeMillis()
                }
                
                val elapsed = System.currentTimeMillis() - sendingOutStartTime
                 Cobblemon.LOGGER.info("[TOWER DEBUG] Actor is animating ($stillSendingOutCount). Waiting naturally... (Elapsed: ${elapsed}ms)")
                 // IMPORTANT: We ignore the request here to let the animation finish naturally.
                 // The Battle Dispatcher/Instructions will handle the flow once the future completes.
                 // A separate Watchdog (in BattleFactoryEventHandler) will handle timeouts.
                 return
            } else {
                sendingOutStartTime = 0L
            }

            // 2. STATE RECONCILIATION
            // Only examine packets meant for THIS actor's side
            val requestSideId = packet.request.side?.id
            val mySideId = if (getSide() == battle.side1) "p1" else "p2"
            
            // Showdown side IDs often look like "p1" or "p2". 
            // We need to ensure we are only validating our own state.
            if (requestSideId != null && requestSideId != mySideId) {
                 return
            }

            val requestActiveUUIDs = packet.request.side?.pokemon?.filter { it.active }?.map { it.uuid } ?: emptyList()
            if (requestActiveUUIDs.isNotEmpty()) {
                val expectedUUID = requestActiveUUIDs.first()
                val currentUUID = activePokemon.firstOrNull()?.battlePokemon?.uuid
                
                // Note: currentUUID can be null during Turn 0 initialization before InitializeInstruction finishes.
                // Or if a pokemon fainted and the replacement hasn't arrived/spawned yet.
                if (currentUUID == null && battle.turn == 0) {
                     Cobblemon.LOGGER.info("[TOWER DEBUG] Active Pokemon is null (Turn ${battle.turn}). Assuming Initialization race condition. Ignoring.")
                     return
                }
                
                if (expectedUUID != currentUUID) {
                    Cobblemon.LOGGER.warn("[TOWER DEBUG] DESYNC DETECTED! Showdown expects $expectedUUID but we have $currentUUID")
                    
                    val correctPokemon = pokemonList.find { it.uuid == expectedUUID }
                    if (correctPokemon != null) {
                        Cobblemon.LOGGER.info("[TOWER DEBUG] Reconciling Desync: Force-swapping to ${correctPokemon.originalPokemon.species.name}")
                        
                        // Cleanup Old
                        activePokemon.firstOrNull()?.battlePokemon?.entity?.let { oldEntity ->
                            oldEntity.discard()
                            oldEntity.remove(net.minecraft.world.entity.Entity.RemovalReason.DISCARDED)
                        }

                        // Assign New
                        if (activePokemon.isNotEmpty()) {
                            activePokemon[0].battlePokemon = correctPokemon
                        } else {
                             Cobblemon.LOGGER.error("[TOWER DEBUG] Active Pokemon List is EMPTY! Cannot assign new pokemon.")
                        }

                        // Cleanup Broken New
                        correctPokemon.entity?.let { brokenEntity ->
                             brokenEntity.discard()
                        }
                        
                        // Force Spawn
                        if (npc.level() is net.minecraft.server.level.ServerLevel) {
                             val spawnPos = initialPos.add(npc.lookAngle.scale(2.0))
                             val spawnedEntity = correctPokemon.originalPokemon.sendOut(
                                 npc.level() as net.minecraft.server.level.ServerLevel,
                                 spawnPos,
                                 null
                             )
                             spawnedEntity?.battleId = battle.battleId
                        }
                        
                        // Force Network Sync
                        try {
                             val sideIndex = battle.actors.indexOf(this) + 1
                             val pnx = "p${sideIndex}a"
                             battle.sendSidedUpdate(
                                 this, 
                                 com.cobblemon.mod.common.net.messages.client.battle.BattleSwitchPokemonPacket(pnx, correctPokemon, true, null), 
                                 com.cobblemon.mod.common.net.messages.client.battle.BattleSwitchPokemonPacket(pnx, correctPokemon, false, null)
                             )
                             correctPokemon.sendUpdate()
                             val message = com.cobblemon.mod.common.util.battleLang("switch.other", this.getName(), correctPokemon.effectedPokemon.getDisplayName(true))
                             battle.actors.forEach { it.sendMessage(message) }
                             
                             // Trigger AI if needed because we missed the natural trigger
                             mustChoose = true 
                             onChoiceRequested() 
                             
                        } catch (e: Exception) {
                             Cobblemon.LOGGER.error("[TOWER DEBUG] Failed to sync network: ${e.message}")
                        }

                        battle.dispatchResult = com.cobblemon.mod.common.battles.dispatch.GO
                    }
                }
            }
        }
    }

    /**
     * Called every server tick to check if the actor is stuck in an animation state.
     * If stuck for > 5 seconds, forces an unblock/spawn.
     */
    fun checkWatchdog() {
        if (stillSendingOutCount > 0 && sendingOutStartTime != 0L) {
            val elapsed = System.currentTimeMillis() - sendingOutStartTime
            if (elapsed > 5000) {
                Cobblemon.LOGGER.error("[TOWER WATCHDOG] Actor stuck for ${elapsed}ms. Forcing recovery...")
                
                // Reset state
                stillSendingOutCount = 0
                sendingOutStartTime = 0L
                
                // Force Spawn current active pokemon if missing
                val currentPkm = activePokemon.firstOrNull()?.battlePokemon
                if (currentPkm != null) {
                    val existingEntity = currentPkm.entity
                    if (existingEntity == null || !existingEntity.isAlive) {
                         Cobblemon.LOGGER.warn("[TOWER WATCHDOG] Forcing spawn of ${currentPkm.originalPokemon.species.name}...")
                         if (npc.level() is net.minecraft.server.level.ServerLevel) {
                             val spawnPos = initialPos.add(npc.lookAngle.scale(2.0))
                             val spawnedEntity = currentPkm.originalPokemon.sendOut(
                                 npc.level() as net.minecraft.server.level.ServerLevel,
                                 spawnPos,
                                 null
                             )
                             spawnedEntity?.battleId = battle.battleId
                         }
                    }
                }
                
                // Force unblock dispatcher
                battle.dispatchResult = com.cobblemon.mod.common.battles.dispatch.GO
                Cobblemon.LOGGER.info("[TOWER WATCHDOG] Recovery complete.")
            }
        }
    }
}
