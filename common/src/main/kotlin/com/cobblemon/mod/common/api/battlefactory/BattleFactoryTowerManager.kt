/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.battlefactory

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.CobblemonNetwork
import com.cobblemon.mod.common.pokemon.Pokemon
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import net.minecraft.server.level.ServerPlayer
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

import com.cobblemon.mod.common.api.npc.configuration.NPCInteractConfiguration
import com.cobblemon.mod.common.api.events.CobblemonEvents
import com.cobblemon.mod.common.api.events.battles.NPCDefineBattleActorEvent

/**
 * Manages the Battle Factory Tower system.
 * 
 * Central manager for tower sessions, configuration loading,
 * and coordinating the tower flow.
 * 
 * @author Cobblemon Contributors
 * @since January 2026
 */
object BattleFactoryTowerManager {
    
    private var config: BattleFactoryTowerConfig? = null
    private val activeSessions = ConcurrentHashMap<UUID, TowerSession>()
    private val gson = GsonBuilder().setPrettyPrinting().create()
    

    /**
     * Loads the tower configuration from file.
     * Creates default config if file doesn't exist.
     */
    fun loadConfig(configDir: File) {
        val configFile = File(configDir, "battle_factory_tower.json")
        
        if (!configFile.exists()) {
            Cobblemon.LOGGER.warn("Battle Factory Tower config not found, creating default at ${configFile.absolutePath}")
            createDefaultConfig(configFile)
        }
        
        try {
            FileReader(configFile).use { reader ->
                val json = JsonParser.parseReader(reader).asJsonObject
                config = BattleFactoryTowerConfig.fromJson(json)
                Cobblemon.LOGGER.info("Loaded Battle Factory Tower config: ${config?.towerName} with ${config?.arenas?.size} arenas")
            }
        } catch (e: Exception) {
            Cobblemon.LOGGER.error("Failed to load Battle Factory Tower config", e)
        }
    }
    
    /**
     * Creates a default configuration file.
     */
    private fun createDefaultConfig(file: File) {
        file.parentFile?.mkdirs()
        
        // Copy from resources
        val resourceStream = this.javaClass.classLoader.getResourceAsStream("config/cobblemon/battle_factory_tower.json")
        if (resourceStream != null) {
            FileWriter(file).use { writer ->
                resourceStream.bufferedReader().use { reader ->
                    writer.write(reader.readText())
                }
            }
            Cobblemon.LOGGER.info("Created default Battle Factory Tower config")
        } else {
            Cobblemon.LOGGER.error("Could not find default battle_factory_tower.json in resources")
        }
    }
    
    /**
     * Gets the loaded configuration.
     */
    fun getConfig(): BattleFactoryTowerConfig {
        return config ?: throw IllegalStateException("Battle Factory Tower config not loaded!")
    }
    /**
     * Reset all player security
     */
    fun resetAllPlayers(server: net.minecraft.server.MinecraftServer) {
        server.playerList.players.forEach { player ->
            TemporaryPartyManagerImpl.restore(player, force = true)
            activeSessions.remove(player.uuid)
            TowerPokemonCache.remove(player.uuid)

            Cobblemon.LOGGER.info("Tower reset for ${player.name.string}")
        }
    }


    /**
     * Checks if a player has an active tower session.
     */
    fun hasActiveSession(player: ServerPlayer): Boolean {

        return activeSessions.containsKey(player.uuid)
    }
    
    /**
     * Gets the active session for a player, if any.
     */
    fun getActiveSession(player: ServerPlayer): TowerSession? {
        return activeSessions[player.uuid]
    }
    
    /**
     * Gets a player's active tower session.
     */
    fun getSession(player: ServerPlayer): TowerSession? {
        return activeSessions[player.uuid]
    }
    
    /**
     * Starts a tower session for a player.
     * This opens the Pokemon selection UI.
     */
    fun reset(player: ServerPlayer){
        // Restore original party
        TemporaryPartyManagerImpl.restore(player, force = true)

        // Remove session
        activeSessions.remove(player.uuid)

        //clear cache
        TowerPokemonCache.remove(player.uuid)
        TemporaryPartyManagerImpl.clearBackupFromPlayer(player)
    }
    fun startTower(player: ServerPlayer, difficulty: TowerDifficulty) {
        // Check if already has session
        if (hasActiveSession(player)) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§cYou already have an active tower session!"))
            // Restore original party
            TemporaryPartyManagerImpl.restore(player, force = true)

            // Remove session
            activeSessions.remove(player.uuid)
            return
        }
        
        // Generate 6 Pokémon for selection
        val offeredPokemon = generateOfferedPokemon(difficulty, 6)
        
        Cobblemon.LOGGER.info("Starting tower for ${player.name.string} at difficulty ${difficulty.id}")
        
        // Store Pokemon in cache
        TowerPokemonCache.store(player.uuid, offeredPokemon)
        
        // Convert to DTOs for network transfer
        val pokemonDTOs = offeredPokemon.map { TowerPokemonDTO.fromPokemon(it) }
        
        // Send packet to client to open selection GUI
        CobblemonNetwork.sendPacketToPlayer(
            player,
            com.cobblemon.mod.common.net.messages.client.tower.OpenTowerSelectionPacket(
                offeredPokemon = pokemonDTOs,
                difficulty = difficulty.id
            )
        )
    }
    
    /**
     * Generates offered Pokémon for selection based on difficulty.
     */
    private fun generateOfferedPokemon(difficulty: TowerDifficulty, count: Int): List<Pokemon> {
        val generator = SimpleRentalTeamGenerator.loadFromFile()
        val options = generator.generateOptions(count)
        
        // Set Pokemon to appropriate levels for this difficulty
        options.forEach { pokemon ->
            val level = (difficulty.pokemonLevelMin..difficulty.pokemonLevelMax).random()
            pokemon.level = level
            pokemon.initialize()
        }
        
        return options
    }
    
    /**
     * Callback when player confirms Pokemon selection.
     */
    fun onPokemonSelected(player: ServerPlayer, difficulty: TowerDifficulty, selectedPokemon: List<Pokemon>) {

        // PREVENT DOUBLE SPAWN: Check if session already exists (race condition from spamming UI)
        if (hasActiveSession(player)) {
            Cobblemon.LOGGER.warn("Ignoring duplicate onPokemonSelected for ${player.name.string} - session already active")
            return
        }

        if (selectedPokemon.size != 3) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§cYou must select exactly 3 Pokémon!"))
            return
        }
        
        // Create session
        val session = TowerSession(
            playerUUID = player.uuid,
            difficulty = difficulty,
            selectedPokemon = selectedPokemon
        )
        // PREVENT DUPLICATE BACKUP
        BattleFactoryTowerManager.reset(player)

        if (!TemporaryPartyManagerImpl.saveOriginal(player)) {
            Cobblemon.LOGGER.error("Failed to backup party for tower session")
            TemporaryPartyManagerImpl.restore(player, force = true)
            activeSessions.remove(player.uuid)
            TowerPokemonCache.remove(player.uuid)

            return
        }

        activeSessions[player.uuid] = session
        
        // Backup original party and apply selected Pokémon
        if (!TemporaryPartyManagerImpl.saveOriginal(player)) {
            Cobblemon.LOGGER.error("Failed to backup party for tower session")
            activeSessions.remove(player.uuid)
            return
        }
        
        if (!TemporaryPartyManagerImpl.applyTemporary(player, selectedPokemon)) {
            Cobblemon.LOGGER.error("Failed to apply tower team")
            TemporaryPartyManagerImpl.restore(player, force = true)
            activeSessions.remove(player.uuid)
            return
        }

        // Teleport to first arena
        val ok = TowerTeleportHandler.teleportToArena(player, session)
        if (!ok) {
            TemporaryPartyManagerImpl.restore(player, force = true)
            activeSessions.remove(player.uuid)

            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§cErreur lors du lancement du combat."))

            return
        }
        
        // Spawn the trainer NPC for this arena
        val config = getConfig()
        val arena = config.arenas.getOrNull(0) ?: run {
            Cobblemon.LOGGER.error("No arenas configured for Tower!")
            return
        }
        
        val npc = TowerTrainerSpawner.spawnTrainer(player.serverLevel(), arena, session.difficulty, 1, player.uuid)
        session.currentTrainerNPC = npc?.uuid // Store UUID for cleanup
        
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§a§lTower session started! ${difficulty.displayName}"))
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§eArena 1/7 - Good luck!"))
    }
    
    /**
     * Called when player wins an arena battle.
     */
    fun onArenaVictory(player: ServerPlayer) {
        val session = activeSessions[player.uuid] ?: return
        
        // Cleanup previous trainer
        TowerTrainerSpawner.cleanupTrainer(player.serverLevel(), session.currentTrainerNPC)
        session.currentTrainerNPC = null

        
        session.nextArena()
        
        Cobblemon.LOGGER.info("${player.name.string} won arena ${session.currentArena} (total: ${session.wins} wins)")
        
        if (session.isComplete()) {
            completeTower(player, session)
        } else {
            // Teleport to next arena
            TowerTeleportHandler.teleportToArena(player, session)
            
            // Heal player's party for the next battle
            val partyStore = com.cobblemon.mod.common.Cobblemon.storage.getParty(player)
            partyStore.heal()
            
            // Spawn new trainer
            val config = getConfig()
            val arenaIndex = session.currentArena % config.arenas.size
            val arena = config.arenas.getOrNull(arenaIndex) ?: run {
                 Cobblemon.LOGGER.error("No arena configured for index $arenaIndex")
                 return
            }
            partyStore.heal()
            val npc = TowerTrainerSpawner.spawnTrainer(player.serverLevel(), arena, session.difficulty, session.currentArena + 1, player.uuid)
            session.currentTrainerNPC = npc?.uuid
            
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§a§lVictory! Party Healed."))
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§eArena ${session.getCurrentArenaDisplay()}/7 - Next challenge!"))
        }
    }
    
    /**
     * Called when player loses an arena battle.
     */
    fun onArenaDefeat(player: ServerPlayer) {
        val session = activeSessions[player.uuid] ?: return
        
        Cobblemon.LOGGER.info("${player.name.string} lost at arena ${session.getCurrentArenaDisplay()}")
        
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§cDefeat! Tower challenge ended."))
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§7Arenas cleared: ${session.wins}/7"))
        
        endSession(player, session, success = false)
    }
    
    /**
     * Completes the tower for a player.
     */
    private fun completeTower(player: ServerPlayer, session: TowerSession) {
        val duration = session.getDurationSeconds()
        
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§6§l★ TOWER COMPLETED! ★"))
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§aDifficulty: ${session.difficulty.displayName}"))
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§eTime: ${duration}s"))
        
        // Give completion rewards
        TowerRewardDistributor.giveCompletionRewards(player, session.difficulty)
        
        endSession(player, session, success = true)
    }
    
    /**
     * Ends a tower session and restores party.
     */
    private fun endSession(player: ServerPlayer, session: TowerSession, success: Boolean) {
        // Cleanup trainer NPC if exists
        TowerTrainerSpawner.cleanupTrainer(player.serverLevel(), session.currentTrainerNPC)
        session.currentTrainerNPC = null

        // Restore original party
        TemporaryPartyManagerImpl.restore(player, force = true)
        
        // Remove session
        activeSessions.remove(player.uuid)
        
        // Teleport back to lobby
        TowerTeleportHandler.teleportToLobby(player)
        
        Cobblemon.LOGGER.info("Ended tower session for ${player.name.string} (success: $success)")
    }
}
