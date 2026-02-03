/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.economy.shop

import com.cobblemon.mod.common.Cobblemon
import com.google.gson.GsonBuilder
import java.io.File
import java.io.FileReader
import java.io.FileWriter

data class ShopConfig(
    val shop_name: String = "Cobblemon Shop",
    val items: List<ShopItem> = listOf()
)

data class ShopItem(
    val slot: Int,
    val item: String,
    val price: Long,
    val amount: Int = 1,
    val lore: List<String> = listOf()
)

object EconomyShopConfigManager {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val configFile = File("config/cobblemon/economy_shop.json")
    
    var config: ShopConfig = ShopConfig()

    fun load() {
        if (!configFile.exists()) {
            configFile.parentFile.mkdirs()
            save() // Save default
        }
        try {
            FileReader(configFile).use { reader ->
                config = gson.fromJson(reader, ShopConfig::class.java)
            }
        } catch (e: Exception) {
            Cobblemon.LOGGER.error("Failed to load economy shop config", e)
        }
    }

    fun save() {
        try {
            FileWriter(configFile).use { writer ->
                gson.toJson(config, writer)
            }
        } catch (e: Exception) {
            Cobblemon.LOGGER.error("Failed to save economy shop config", e)
        }
    }
    
    init {
        load()
    }
}
