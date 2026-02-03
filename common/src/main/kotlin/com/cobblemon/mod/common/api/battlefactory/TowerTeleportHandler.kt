/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.battlefactory

import com.cobblemon.mod.common.Cobblemon
import net.minecraft.core.BlockPos
import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.level.Level

/**
 * Handles teleportation for the Battle Factory Tower system.
 * 
 * Manages player teleportation between lobby and arenas,
 * and coordinates with trainer spawning.
 * 
 * @author Cobblemon Contributors
 * @since January 2026
 */
object TowerTeleportHandler {
    
    /**
     * Teleports a player to the current arena in their session.
     */
    fun teleportToArena(player: ServerPlayer, session: TowerSession): Boolean {
        return try {
        val config = BattleFactoryTowerManager.getConfig()
        val arena = config.getArena(session.currentArena)

        if (arena == null) {
            Cobblemon.LOGGER.error("Arena ${session.currentArena} not found in config!")
            return false
        }

        // Get world
        val worldKey = ResourceKey.create(
            net.minecraft.core.registries.Registries.DIMENSION,
            ResourceLocation.parse(arena.world)
        )
        val world = player.server.getLevel(worldKey)

        if (world == null) {
            Cobblemon.LOGGER.error("World ${arena.world} not found!")
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§cError: Arena world not loaded"))
            return false
        }
        
        // Teleport player
        player.teleportTo(
            world,
            arena.playerSpawn.x.toDouble() + 0.5,
            arena.playerSpawn.y.toDouble(),
            arena.playerSpawn.z.toDouble() + 0.5,
            0f,
            0f
        )
        
        Cobblemon.LOGGER.info("Teleported ${player.name.string} to arena ${session.getCurrentArenaDisplay()}: ${arena.name}")
        
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§6Arena ${session.getCurrentArenaDisplay()}/7§r"))
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§e${arena.name}"))
        
        true
        } catch (e: Exception) {
            Cobblemon.LOGGER.error("Teleport failed for ${player.name.string}", e)
            false
        }

    }
    
    /**
     * Teleports a player back to the tower lobby.
     */
    fun teleportToLobby(player: ServerPlayer) {
        val config = BattleFactoryTowerManager.getConfig()
        
        // Get lobby world
        val worldKey = ResourceKey.create(
            net.minecraft.core.registries.Registries.DIMENSION,
            ResourceLocation.parse(config.lobbyWorld)
        )
        val world = player.server.getLevel(worldKey)
        
        if (world == null) {
            Cobblemon.LOGGER.error("Lobby world ${config.lobbyWorld} not found!")
            return
        }
        
        // Teleport player
        player.teleportTo(
            world,
            config.lobbyNpcSpawn.x.toDouble() + 0.5,
            config.lobbyNpcSpawn.y.toDouble(),
            config.lobbyNpcSpawn.z.toDouble() + 0.5,
            0f,
            0f
        )
        
        Cobblemon.LOGGER.info("Teleported ${player.name.string} to tower lobby")
        player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§aReturned to lobby"))
    }
}
