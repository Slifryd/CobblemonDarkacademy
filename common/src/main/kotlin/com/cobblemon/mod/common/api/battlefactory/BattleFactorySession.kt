/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.battlefactory

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.battles.pokemon.BattlePokemon
import com.cobblemon.mod.common.pokemon.Pokemon
import com.cobblemon.mod.common.util.getPlayer
import net.minecraft.server.level.ServerPlayer
import java.util.UUID

/**
 * A Battle Factory session implementation.
 * 
 * Manages a player's Battle Factory streak, including:
 * - Rental team management
 * - Win streak tracking
 * - Automatic party backup/restore
 * - Reward distribution
 * 
 * @property playerUUID UUID of the participating player
 * @property rentalTeam The rental Pokémon team being used
 * 
 * @author Cobblemon Contributors
 * @since January 2026
 */
class BattleFactorySession(
    override val playerUUID: UUID,
    val rentalTeam: List<Pokemon>
) : FacilitySession {
    
    override val sessionId: UUID = UUID.randomUUID()
    private var wins: Int = 0
    private var active: Boolean = false
    
    override fun start() {
        val player = playerUUID.getPlayer() ?: run {
            Cobblemon.LOGGER.error("Cannot start Battle Factory session: player $playerUUID not found")
            return
        }
        
        // Step 1: Save original party
        if (!TemporaryPartyManagerImpl.saveOriginal(player)) {
            Cobblemon.LOGGER.error("Failed to backup party for ${player.name.string}")
            return
        }
        
        // Step 2: Apply rental team
        if (!TemporaryPartyManagerImpl.applyTemporary(player, rentalTeam)) {
            Cobblemon.LOGGER.error("Failed to apply rental team for ${player.name.string}")
            // Attempt to restore original party
            TemporaryPartyManagerImpl.restore(player, force = true)
            return
        }
        
        active = true
        wins = 0
        
        Cobblemon.LOGGER.info("Started Battle Factory session for ${player.name.string} (session: $sessionId)")
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§aBattle Factory session started! Good luck!"))
    }
    
    override fun onWin() {
        wins++
        
        val player = playerUUID.getPlayer() ?: return
        
        // Update win count in backup data
        TemporaryPartyManagerImpl.updateWins(player, wins)
        
        Cobblemon.LOGGER.info("${player.name.string} won battle #$wins in Battle Factory")
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§aVictory! Win streak: $wins"))
        
        // Check for reward milestones
        when (wins) {
            7 -> {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§6Milestone reached! You've won 7 battles!"))
                // Rewards handled in end() if player chooses to stop
            }
            14 -> {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§6Amazing! 14 win streak!"))
            }
            21 -> {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§6Incredible! 21 win streak!"))
            }
        }
        
        // TODO: Offer player choice to continue or stop
        // For V1, just continue automatically
    }
    
    override fun onLose() {
        val player = playerUUID.getPlayer() ?: return
        
        Cobblemon.LOGGER.info("${player.name.string} lost in Battle Factory after $wins wins")
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§cDefeat! Your streak ended at $wins wins."))
        
        // End session without rewards on loss
        end(giveRewards = false)
    }
    
    override fun end(giveRewards: Boolean) {
        val player = playerUUID.getPlayer()
        
        if (player != null) {
            // Distribute rewards if applicable
            if (giveRewards && wins > 0) {
                BattleFactoryRewards.giveRewards(player, wins)
            }
            
            /// Restore original party and clear cache and remove session
            BattleFactoryTowerManager.reset(player)
            player.sendSystemMessage(
                net.minecraft.network.chat.Component.literal("§aYour original party has been restored.")
            )
            Cobblemon.LOGGER.info("Original party restored for ${player.name.string} at session end")

        }
        

        

        
        Cobblemon.LOGGER.info("Ended Battle Factory session for player $playerUUID (wins: $wins, rewards: $giveRewards)")
    }
    
    override fun isActive(): Boolean = active
    
    /**
     * Gets the current win count.
     */
    fun getWins(): Int = wins
}
