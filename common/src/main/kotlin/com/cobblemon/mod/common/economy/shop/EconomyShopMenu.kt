/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.economy.shop

import com.cobblemon.mod.common.economy.EconomyManager
import com.cobblemon.mod.common.api.text.green
import com.cobblemon.mod.common.api.text.red
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.SimpleContainer
import net.minecraft.world.entity.player.Inventory
import net.minecraft.world.entity.player.Player
import net.minecraft.world.inventory.ChestMenu
import net.minecraft.world.inventory.ClickType
import net.minecraft.world.inventory.MenuType
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.Items
import net.minecraft.world.inventory.Slot
import net.minecraft.core.component.DataComponents
import net.minecraft.world.item.component.ItemLore

class EconomyShopMenu(
    containerId: Int, 
    playerInventory: Inventory,
    val player: ServerPlayer
) : ChestMenu(MenuType.GENERIC_9x6, containerId, playerInventory, createShopContainer(), 6) {

    init {
        com.cobblemon.mod.common.Cobblemon.LOGGER.info("Initializing EconomyShopMenu for ${player.name.string}")
        // Initialize slots with items from config
        val config = EconomyShopConfigManager.config
        val container = this.container // Access the SimpleContainer we passed
        
        // Clear first
        container.clearContent()
        
        config.items.forEach { shopItem ->
            if (shopItem.slot in 0 until 54) {
                // Parse ResourceLocation safely
                val location = ResourceLocation.tryParse(shopItem.item)
                val item = if (location != null) BuiltInRegistries.ITEM.get(location) else Items.AIR
                
                if (item != Items.AIR) {
                    val stack = ItemStack(item, shopItem.amount)
                    
                    val loreList = mutableListOf<Component>()
                    loreList.add(Component.literal("§6Price: $${shopItem.price}"))
                    shopItem.lore.forEach { loreList.add(Component.literal("§7$it")) }
                    
                    stack.set(DataComponents.LORE, ItemLore(loreList))
                    
                    container.setItem(shopItem.slot, stack)
                }
            }
        }
    }

    override fun clicked(slotId: Int, button: Int, clickType: ClickType, player: Player) {
        if (slotId < 0 || slotId >= 54) {
             super.clicked(slotId, button, clickType, player)
             return
        }
        
        // Prevent taking items
        if (clickType == ClickType.PICKUP || clickType == ClickType.QUICK_MOVE) {
             val slot = this.slots.getOrNull(slotId)
             if (slot != null && slot.hasItem()) {
                 
                 // Look up item in config to check if it is a shop item and get price
                 val shopItem = EconomyShopConfigManager.config.items.find { it.slot == slotId }
                 
                 if (shopItem != null) {
                     val price = shopItem.price
                     
                     // Buy logic
                     if (EconomyManager.has(this.player, price)) {
                         val stack = slot.item.copy()
                         if (this.player.inventory.add(stack)) {
                             EconomyManager.remove(this.player, price, "bought item")
                             this.player.sendSystemMessage(Component.literal("Purchase successful!").withStyle(net.minecraft.ChatFormatting.GREEN))
                         } else {
                             this.player.sendSystemMessage(Component.literal("Inventory full!").withStyle(net.minecraft.ChatFormatting.RED))
                         }
                     } else {
                         this.player.sendSystemMessage(Component.literal("Insufficient funds! Cost: $$price").withStyle(net.minecraft.ChatFormatting.RED))
                     }
                 }
             }
        }
        
        // Cancel the event so item isn't moved in the UI
        this.broadcastChanges()
    }
    
    override fun stillValid(player: Player): Boolean {
        return true
    }

    companion object {
        fun createShopContainer(): SimpleContainer {
            return SimpleContainer(54)
        }
    }
}
