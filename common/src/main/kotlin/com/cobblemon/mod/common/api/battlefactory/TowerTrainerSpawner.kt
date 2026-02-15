/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.battlefactory

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.CobblemonEntities
import com.cobblemon.mod.common.api.npc.NPCClass
import com.cobblemon.mod.common.api.npc.NPCPartyProvider
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.api.storage.party.NPCPartyStore
import com.cobblemon.mod.common.battles.BattleBuilder
import com.cobblemon.mod.common.battles.BattleFormat
import com.cobblemon.mod.common.entity.npc.NPCEntity
import com.cobblemon.mod.common.pokemon.Pokemon
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import com.cobblemon.mod.common.api.battlefactory.ArenaLocation
import com.google.gson.JsonElement
import com.cobblemon.mod.common.api.npc.configuration.NPCInteractConfiguration
import net.minecraft.nbt.CompoundTag
import net.minecraft.network.RegistryFriendlyByteBuf

/**
 * Handles spawning and managing NPC trainers for Battle Factory Tower arenas.
 * 
 * @author Cobblemon Contributors
 * @since January 2026
 */
object TowerTrainerSpawner {
    
    /**
     * Spawns a trainer NPC for the current arena with a strategic team.
     * The NPC will initiate battle on right-click.
     * 
     * @return The spawned NPCEntity
     */

    /**
     * Spawns a dedicated trainer NPC for the Tower.
     * 
     * @param world The server level to spawn in
     * @param arena The arena location configuration
     * @param difficulty The difficulty settings
     * @param arenaNumber The current arena number (1-7)
     */
    fun spawnTrainer(
        world: ServerLevel,
        arena: ArenaLocation,
        difficulty: TowerDifficulty,
        arenaNumber: Int,
        challengerUUID: java.util.UUID
    ): NPCEntity? {
        try {
            // Generate strategic trainer team
            val team = generateTrainerTeam(difficulty, arenaNumber)
            
            Cobblemon.LOGGER.info("Spawning Tower trainer for arena $arenaNumber with ${team.size} Pokémon")
            
            // Create NPC entity
            val npc = NPCEntity(world)
            
            // Apply Tower Tags for Logic Handling
            npc.addTag("tower_npc")
            npc.addTag("challenger:${challengerUUID}")
            
            // Set Native Invulnerability
            npc.isInvulnerable = true
            
            // Position NPC at trainer spawn location
            val spawnPos = arena.trainerSpawn
            npc.moveTo(spawnPos.x.toDouble(), spawnPos.y.toDouble(), spawnPos.z.toDouble(), 180f, 0f) // Face player spawn

            // Configure NPC appearance and class
            val npcClass = createTowerTrainerClass(arenaNumber, difficulty)

            // Create party provider with our generated team
            val partyProvider = createPartyProvider(team)

// Assign the party provider to the NPC Class
            npcClass.party = partyProvider

// Assign interaction handling to initiate battle
            npcClass.interaction = createInteraction()

// IMPORTANT: Assign npc class BEFORE initializing
            npc.npc = npcClass

// Initialize the NPC (this will call the party provider and load appearance)
            npc.initialize(difficulty.pokemonLevelMin)

// Force dimensions refresh to apply appearance
            npc.refreshDimensions()
            
            // The NPC will automatically be challengeable via right-click
            // since it has a party assigned - Cobblemon handles this automatically
            
            // Spawn the NPC in the world
            if (world.addFreshEntity(npc)) {
                Cobblemon.LOGGER.info("Successfully spawned Tower trainer NPC at ${spawnPos}")
                return npc
            } else {
                Cobblemon.LOGGER.error("Failed to add Tower trainer NPC to world")
                return null
            }
        } catch (e: Exception) {
            Cobblemon.LOGGER.error("Error spawning Tower trainer NPC", e)
            return null
        }
    }
    
    /**
     * Creates a party provider for the given team of Pokémon.
     */
    private fun createPartyProvider(team: List<Pokemon>): NPCPartyProvider {
        return object : NPCPartyProvider {
            override val type = "tower_trainer"
            override var isStatic = true
            
            override fun provide(npc: NPCEntity, level: Int, players: List<ServerPlayer>): NPCPartyStore {
                val party = NPCPartyStore(npc)
                team.forEach { pokemon ->
                    party.add(pokemon)
                }
                return party
            }

            override fun loadFromJSON(json: JsonElement) {
                // No-op: This provider is created programmatically, not loaded from JSON
            }
        }
    }
    
    /**
     * Generates a strategic Pokémon team for a trainer based on difficulty and arena number.
     */
    fun generateTrainerTeam(difficulty: TowerDifficulty, arenaNumber: Int): List<Pokemon> {
        val teamSize = 3
        val level = calculateTrainerLevel(difficulty, arenaNumber)
        
        // Use SimpleRentalTeamGenerator to create strategic team
        val generator = SimpleRentalTeamGenerator.loadFromFile()
        val team = generator.generateOptions(teamSize)
        
        // Set all Pokémon to appropriate level and ensure they're battle-ready
        team.forEach { pokemon ->
            pokemon.level = level
            pokemon.initialize()
            pokemon.heal() // Full HP for trainer
        }
        
        Cobblemon.LOGGER.info("Generated Tower trainer team: ${team.map { "${it.species.name} Lv${it.level}" }}")
        
        return team
    }
    
    /**
     * Calculates trainer Pokémon level based on difficulty and arena progression.
     */
    private fun calculateTrainerLevel(difficulty: TowerDifficulty, arenaNumber: Int): Int {
        // Progressive difficulty: later arenas have stronger Pokémon
        val baseLevel = difficulty.pokemonLevelMin
        val maxLevel = difficulty.pokemonLevelMax
        val range = maxLevel - baseLevel
        
        // Arena 1-2: min level
        // Arena 3-4: mid level
        // Arena 5-6: high level  
        // Arena 7: max level
        val progression = (arenaNumber - 1) / 6.0 // 0.0 to 1.0
        return (baseLevel + (range * progression)).toInt().coerceIn(baseLevel, maxLevel)
    }
    
    /**
     * Creates a unique NPC class for Tower trainers.
     */
    private fun createTowerTrainerClass(arenaNumber: Int, difficulty: TowerDifficulty): NPCClass {
        val npcClass = NPCClass()
        // IMPORTANT: Use a valid ID that exists in the registry (via preset) so the client can render it.
        // We defined 'npc_presets/tower_trainer.json' for this purpose.
        npcClass.id = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("cobblemon", "tower_trainer")
        
        // We can override the name locally for this entity instance
        npcClass.names = mutableListOf(net.minecraft.network.chat.Component.literal("Tower Trainer #$arenaNumber"))
        
        // Set trainer skill level based on difficulty
        npcClass.skill = when (difficulty.id) {
            "easy" -> 0
            "normal" -> 128
            "hard" -> 255
            else -> 128
        }
        
        return npcClass
    }
    
    /**
     * Creates a simple interaction configuration that initiates a battle.
     */
    /**
     * Creates a simple interaction configuration that initiates a battle.
     */
    private fun createInteraction(): NPCInteractConfiguration {
        return TowerBattleInteraction()
    }
    
    /**
     * Cleans up a spawned trainer NPC.
     */
    fun cleanupTrainer(world: ServerLevel, npcUUID: java.util.UUID?) {
        if (npcUUID == null) return
        
        val entity = world.getEntity(npcUUID)
        if (entity is NPCEntity) {
            entity.discard()
            Cobblemon.LOGGER.info("Cleaned up trainer NPC: $npcUUID")
        } else {
            Cobblemon.LOGGER.warn("Could not find trainer NPC to cleanup: $npcUUID")
        }
    }
}
