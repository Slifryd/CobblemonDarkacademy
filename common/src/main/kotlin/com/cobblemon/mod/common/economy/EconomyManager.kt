/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.economy

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.storage.player.PlayerInstancedDataStoreTypes
import com.cobblemon.mod.common.api.storage.player.PlayerInstancedDataStoreType
import com.cobblemon.mod.common.api.storage.player.adapter.JsonBackedPlayerDataStoreBackend
import com.cobblemon.mod.common.api.storage.player.factory.CachedPlayerDataStoreFactory
import com.cobblemon.mod.common.api.text.green
import com.cobblemon.mod.common.api.text.yellow
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer
import java.util.UUID

object EconomyManager {

    val ECONOMY_DATA_TYPE = PlayerInstancedDataStoreType(
        net.minecraft.resources.ResourceLocation("cobblemon", "economy"),
        ClientPlayerEconomyData::decode,
        ClientPlayerEconomyData::runAction
    )

    fun load() {
        // Register Dialogue Action (Needed even if economy is disabled to prevent JSON errors)
        com.cobblemon.mod.common.api.dialogue.DialogueAction.types["economy_shop"] = com.cobblemon.mod.common.economy.shop.EconomyShopDialogueAction::class.java

        if (!Cobblemon.config.enableEconomy) return
        PlayerInstancedDataStoreTypes.register(ECONOMY_DATA_TYPE)

        // Register Event Handler
        EconomyEventHandler.register()
    }

    fun setup(server: net.minecraft.server.MinecraftServer) {
        if (!Cobblemon.config.enableEconomy) return
        val backend = object : JsonBackedPlayerDataStoreBackend<PlayerEconomyData>("economy", ECONOMY_DATA_TYPE) {
            override val gson = GsonBuilder().setPrettyPrinting().create()
            override val classToken = object : TypeToken<PlayerEconomyData>() {}
            override val defaultData = { uuid: UUID -> PlayerEconomyData(uuid) }
        }

        Cobblemon.playerDataManager.setFactory(CachedPlayerDataStoreFactory(backend), ECONOMY_DATA_TYPE)
    }

    private fun getData(player: ServerPlayer): PlayerEconomyData {
        return Cobblemon.playerDataManager.get(player, ECONOMY_DATA_TYPE) as PlayerEconomyData
    }
    
    private fun getData(uuid: UUID): PlayerEconomyData {
        return Cobblemon.playerDataManager.get(uuid, ECONOMY_DATA_TYPE) as PlayerEconomyData
    }

    fun getBalance(player: ServerPlayer): Long {
        return getData(player).balance
    }
    
    fun getBalance(uuid: UUID): Long {
        return getData(uuid).balance
    }

    fun has(player: ServerPlayer, amount: Long): Boolean {
        return getBalance(player) >= amount
    }

    fun add(player: ServerPlayer, amount: Long, reason: String = "") {
        val data = getData(player)
        data.balance += amount
        data.totalEarned += amount
        
        if (reason.isNotEmpty()) {
            player.sendSystemMessage(Component.literal("Received $$amount $reason").withStyle(net.minecraft.ChatFormatting.GREEN))
        }
        
        data.sendToPlayer(player)
    }

    fun remove(player: ServerPlayer, amount: Long, reason: String = ""): Boolean {
        val data = getData(player)
        if (data.balance < amount) return false
        
        data.balance -= amount
        data.totalSpent += amount
        
        if (reason.isNotEmpty()) {
            player.sendSystemMessage(Component.literal("Spent $$amount $reason").withStyle(net.minecraft.ChatFormatting.YELLOW))
        }
        
        data.sendToPlayer(player)
        return true
    }
    
    // For manual/admin changes
    fun setBalance(player: ServerPlayer, amount: Long) {
        val data = getData(player)
        data.balance = amount
        data.sendToPlayer(player)
    }
    
    private fun PlayerEconomyData.sendToPlayer(player: ServerPlayer) {
         Cobblemon.playerDataManager.factories[ECONOMY_DATA_TYPE]?.sendToPlayer(player)
    }
}
