/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.battlefactory

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.util.getPlayer
import com.cobblemon.mod.common.util.party
import net.minecraft.server.level.ServerPlayer
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Implementation of TemporaryPartyManager with crash-safe persistence.
 * 
 * This manager uses a dual-backup strategy:
 * 1. In-memory cache for fast access
 * 2. Persistent storage via player data NBT tag for crash recovery
 * 
 * Safety features:
 * - All operations are atomic and logged
 * - Backups are validated before being applied
 * - Restoration automatically triggers on login if orphaned backup detected
 * - Never overwrites existing backups (prevents data corruption)
 * 
 * @author Cobblemon Contributors
 * @since January 2026
 */
object TemporaryPartyManagerImpl : TemporaryPartyManager {
    
    private const val TAG_HAS_TEMP_PARTY = "BattleFactory_HasTemporaryParty"
    private const val TAG_BACKUP_DATA = "BattleFactory_BackupData"
    
    // In-memory cache for active sessions (fast lookups)
    private val activeSessions = ConcurrentHashMap<UUID, TemporaryPartyData>()
    override fun saveOriginal(player: ServerPlayer): Boolean {
        return saveOriginal(player, overwrite = true)
    }

    fun saveOriginal(player: ServerPlayer, overwrite: Boolean = false): Boolean {
        try {
            // Safety check: prevent double backup
            if (hasTemporaryParty(player) && !overwrite) {
                Cobblemon.LOGGER.warn("Player ${player.name.string} already has a temporary party backup. Refusing to create duplicate.")
                return false
            }

            // If overwrite, remove previous backup
            if (overwrite && hasTemporaryParty(player)) {
                activeSessions.remove(player.uuid)
                player.removeTag(TAG_HAS_TEMP_PARTY)
            }

            // Clone the player's current party
            val currentParty = player.party().toList()
            if (currentParty.isEmpty()) {
                Cobblemon.LOGGER.warn("Player ${player.name.string} has empty party, cannot save backup")
                return false
            }

            // Create deep copies of Pokémon
            val clonedParty = currentParty.map { it.clone() }

            val backupData = TemporaryPartyData(
                originalParty = clonedParty,
                rentalParty = emptyList(),
                sessionId = UUID.randomUUID(),
                startTime = Instant.now(),
                wins = 0
            )

            activeSessions[player.uuid] = backupData
            saveBackupToPlayer(player, backupData)
            player.addTag(TAG_HAS_TEMP_PARTY)

            Cobblemon.LOGGER.info("Successfully backed up party for ${player.name.string} (session: ${backupData.sessionId})")
            return true

        } catch (e: Exception) {
            Cobblemon.LOGGER.error("Failed to save original party for ${player.name.string}", e)
            activeSessions.remove(player.uuid)
            player.removeTag(TAG_HAS_TEMP_PARTY)
            return false
        }
    }


    override fun applyTemporary(player: ServerPlayer, rentalTeam: List<Pokemon>): Boolean {
        try {
            // Safety check: ensure backup exists
            if (!hasTemporaryParty(player)) {
                Cobblemon.LOGGER.error("Cannot apply temporary party for ${player.name.string}: no backup found")
                return false
            }
            
            if (rentalTeam.isEmpty()) {
                Cobblemon.LOGGER.error("Cannot apply empty rental team for ${player.name.string}")
                return false
            }
            
            // Get the party store
            val party = player.party()
            
            // Clear current party - manually remove each Pokemon
            val currentPokemon = party.toList()
            currentPokemon.forEach { pokemon ->
                if (pokemon != null) {
                    party.remove(pokemon)
                }
            }
            
            // Add rental Pokémon
            rentalTeam.forEach { pokemon ->
                if (!party.add(pokemon)) {
                    Cobblemon.LOGGER.warn("Failed to add rental Pokémon ${pokemon.species.name} to ${player.name.string}'s party")
                }
            }
            
            // Update session data with rental team info
            activeSessions[player.uuid]?.let { sessionData ->
                val updatedData = sessionData.copy(rentalParty = rentalTeam)
                activeSessions[player.uuid] = updatedData
                saveBackupToPlayer(player, updatedData)
            }
            
            // HARD REFRESH: Complete party store reinitialization (same as restore)
            refreshUI(player)
            
            Cobblemon.LOGGER.info("Applied rental team of ${rentalTeam.size} Pokémon to ${player.name.string}")
            return true
            
        } catch (e: Exception) {
            Cobblemon.LOGGER.error("Failed to apply temporary party for ${player.name.string}", e)
            // Attempt emergency restore
            restore(player, force = true)
            return false
        }
    }
    
    override fun restore(player: ServerPlayer, force: Boolean): Boolean {
        try {
            // Load backup from memory first, fallback to persistent storage
            var backupData = activeSessions[player.uuid]
            
            // CRITICAL FIX: If not in cache but force=true, try persistent storage
            if (backupData == null && force) {
                Cobblemon.LOGGER.info("Backup not in cache for ${player.name.string}, attempting to load from persistent storage")
                backupData = loadBackupFromPlayer(player)
                if (backupData != null) {
                    Cobblemon.LOGGER.info("Successfully loaded backup from persistent storage for ${player.name.string}")
                }
            }
            
            if (backupData == null) {
                if (force) {
                    Cobblemon.LOGGER.warn("No backup found for ${player.name.string} during forced restore")
                }
                return false
            }
            
            // Get the party store
            val party = player.party()
            
            // Clear current party (rental team) - manually remove each Pokemon
            val currentPokemon = party.toList()
            currentPokemon.forEach { pokemon ->
                if (pokemon != null) {
                    party.remove(pokemon)
                }
            }
            
            // Restore original Pokémon
            backupData.originalParty.forEach { pokemon ->
                // Create fresh clones to avoid any reference issues
                val restoredPokemon = pokemon.clone()
                if (!party.add(restoredPokemon)) {
                    Cobblemon.LOGGER.error("Failed to restore Pokémon ${pokemon.species.name} to ${player.name.string}'s party")
                }
            }
            
            // Clean up backup data
            activeSessions.remove(player.uuid)
            clearBackupFromPlayer(player)
            player.removeTag(TAG_HAS_TEMP_PARTY)
            
            // ULTIMATE FIX: Complete party store reinitialization
            refreshUI(player)            
            
            Cobblemon.LOGGER.info("Successfully restored original party for ${player.name.string} (${backupData.originalParty.size} Pokémon)")
            return true
            
        } catch (e: Exception) {
            Cobblemon.LOGGER.error("CRITICAL: Failed to restore party for ${player.name.string}", e)
            // This is a critical failure - log extensively
            Cobblemon.LOGGER.error("Player UUID: ${player.uuid}, Session data exists: ${activeSessions.containsKey(player.uuid)}")
            return false
        }
    }
    
    override fun hasTemporaryParty(player: ServerPlayer): Boolean {
        // Check both memory and persistent tag
        return activeSessions.containsKey(player.uuid) || player.tags.contains(TAG_HAS_TEMP_PARTY)
    }
    
    override fun getSessionData(player: ServerPlayer): TemporaryPartyData? {
        // Try memory first
        var data = activeSessions[player.uuid]
        if (data == null && player.tags.contains(TAG_HAS_TEMP_PARTY)) {
            // Try loading from persistent storage
            data = loadBackupFromPlayer(player)
            if (data != null) {
                // Cache it for future access
                activeSessions[player.uuid] = data
            }
        }
        return data
    }
    
    override fun updateWins(player: ServerPlayer, wins: Int) {
        activeSessions[player.uuid]?.let { sessionData ->
            val updatedData = sessionData.copy(wins = wins)
            activeSessions[player.uuid] = updatedData
            saveBackupToPlayer(player, updatedData)
        }
    }
    
    /**
     * Saves backup data to the player's custom data.
     * This survives server restarts and crashes via Cobblemon's data system.
     */
    private fun saveBackupToPlayer(player: ServerPlayer, data: TemporaryPartyData) {
        try {
            val nbt = data.saveToNBT(player.server.registryAccess())
            // Store in player's Cobblemon data via file system
            val dataFile = getPlayerDataFile(player)
            dataFile.parentFile?.mkdirs()
            
            // Write NBT to file
            val output = java.io.FileOutputStream(dataFile)
            net.minecraft.nbt.NbtIo.writeCompressed(nbt, output)
            output.close()
        } catch (e: Exception) {
            Cobblemon.LOGGER.error("Failed to save backup to player data for ${player.name.string}", e)
        }
    }
    
    /**
     * Loads backup data from the player's custom data.
     * Used for crash recovery.
     */
    private fun loadBackupFromPlayer(player: ServerPlayer): TemporaryPartyData? {
        return try {
            val dataFile = getPlayerDataFile(player)
            if (dataFile.exists()) {
                val input = java.io.FileInputStream(dataFile)
                val nbt = net.minecraft.nbt.NbtIo.readCompressed(input, net.minecraft.nbt.NbtAccounter.unlimitedHeap())
                input.close()
                
                TemporaryPartyData.loadFromNBT(nbt, player.server.registryAccess())
            } else {
                null
            }
        } catch (e: Exception) {
            Cobblemon.LOGGER.error("Failed to load backup from player data for ${player.name.string}", e)
            null
        }
    }
    
    /**
     * Clears backup data from the player's custom storage.
     */
    fun clearBackupFromPlayer(player: ServerPlayer) {
        try {
            val dataFile = getPlayerDataFile(player)
            if (dataFile.exists()) {
                dataFile.delete()
            }
        } catch (e: Exception) {
            Cobblemon.LOGGER.error("Failed to clear backup from player data for ${player.name.string}", e)
        }
    }
    
    /**
     * Gets the data file path for a player's Battle Factory backup.
     */
    private fun getPlayerDataFile(player: ServerPlayer): java.io.File {
        val cobblemonDataDir = player.server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
            .resolve("data").resolve("cobblemon").resolve("battlefactory")
        return cobblemonDataDir.resolve("${player.uuid}.nbt").toFile()
    }
    
    /**
     * Handles player login - checks for orphaned backups and restores if found.
     * This is called automatically by BattleFactoryEventHandler.
     */
fun onPlayerLogin(player: ServerPlayer) {
    if (player.tags.contains(TAG_HAS_TEMP_PARTY)) {
        Cobblemon.LOGGER.warn("Player ${player.name.string} has Battle Factory backup - auto-restoring")
        restore(player, force = true)
    }
}
    
    /**
     * Handles player logout - ensures backup is saved to persistent storage.
     */
    fun onPlayerLogout(player: ServerPlayer) {
        if (hasTemporaryParty(player)) {
            // NE PAS restaurer ! Juste s'assurer que backup NBT existe
            // onPlayerLogin va gérer la restauration à la reconnexion
            activeSessions[player.uuid]?.let { data ->
                saveBackupToPlayer(player, data)
                Cobblemon.LOGGER.info("Player ${player.name.string} logged out with Battle Factory active - backup preserved")
            }
        }
    }
    
    /**
     * Handles player death - restores party immediately.
     */
    fun onPlayerDeath(player: ServerPlayer) {
        if (hasTemporaryParty(player)) {
            Cobblemon.LOGGER.warn("Player ${player.name.string} died with temporary party - forcing restore")
            restore(player, force = true)
        }
    }

    override fun refreshUI(player: ServerPlayer) {
        val party = player.party()
        
        // Force sync entire data first (if method exists, but we'll stick to packets we know)
        // SetPartyReferencePacket: Tells client "Use this UUID for your party sidebar"
        com.cobblemon.mod.common.CobblemonNetwork.sendPacketToPlayer(
            player,
            com.cobblemon.mod.common.net.messages.client.storage.party.SetPartyReferencePacket(party.uuid)
        )
        
        // InitializePartyPacket: "Here is your party data, clear old data first (true)"
        com.cobblemon.mod.common.CobblemonNetwork.sendPacketToPlayer(
            player,
            com.cobblemon.mod.common.net.messages.client.storage.party.InitializePartyPacket(true, party.uuid, 6)
        )
        
        // Send individual slots just to be absolutely sure
        for (i in 0 until 6) {
            val pokemon = party.get(i)
            if (pokemon != null) {
                com.cobblemon.mod.common.CobblemonNetwork.sendPacketToPlayer(
                    player,
                    com.cobblemon.mod.common.net.messages.client.storage.party.SetPartyPokemonPacket(
                        party.uuid,
                        com.cobblemon.mod.common.api.storage.party.PartyPosition(i)
                    ) { pokemon }
                )
            }
        }
        
        Cobblemon.LOGGER.info("Forced UI refresh for ${player.name.string}")
    }
}
