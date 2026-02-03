/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.economy

import com.cobblemon.mod.common.api.permission.CobblemonPermissions
import com.cobblemon.mod.common.api.text.aqua
import com.cobblemon.mod.common.api.text.green
import com.cobblemon.mod.common.api.text.red
import com.cobblemon.mod.common.api.text.yellow
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.LongArgumentType
import com.mojang.brigadier.context.CommandContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.commands.arguments.EntityArgument
import net.minecraft.network.chat.Component
import net.minecraft.server.level.ServerPlayer

object EconomyCommand {

    fun register(dispatcher: CommandDispatcher<CommandSourceStack>) {
        val moneyCommand = Commands.literal("money")
            .executes { checkBalance(it) }
            .then(Commands.literal("pay")
                .then(Commands.argument("target", EntityArgument.player())
                    .then(Commands.argument("amount", LongArgumentType.longArg(1))
                        .executes { pay(it) }
                    )
                )
            )
            .then(Commands.literal("top")
                .executes { top(it) }
            )
            
        dispatcher.register(moneyCommand)
        
        val adminCommand = Commands.literal("adminmoney")
            .requires { it.hasPermission(2) }
            .then(Commands.literal("set")
                .then(Commands.argument("target", EntityArgument.player())
                    .then(Commands.argument("amount", LongArgumentType.longArg(0))
                        .executes { setBalance(it) }
                    )
                )
            )
            .then(Commands.literal("add")
                .then(Commands.argument("target", EntityArgument.player())
                    .then(Commands.argument("amount", LongArgumentType.longArg(1))
                        .executes { addBalance(it) }
                    )
                )
            )
            
        dispatcher.register(adminCommand)
    }

    private fun checkBalance(context: CommandContext<CommandSourceStack>): Int {
        val player = context.source.playerOrException
        val bal = EconomyManager.getBalance(player)
        player.sendSystemMessage(Component.literal("Balance: $$bal").green())
        return 1
    }

    private fun pay(context: CommandContext<CommandSourceStack>): Int {
        val player = context.source.playerOrException
        val target = EntityArgument.getPlayer(context, "target")
        val amount = LongArgumentType.getLong(context, "amount")
        
        if (player.uuid == target.uuid) {
            player.sendSystemMessage(Component.literal("Cannot pay yourself.").red())
            return 0
        }

        if (EconomyManager.remove(player, amount, "paid to ${target.name.string}")) {
            EconomyManager.add(target, amount, "received from ${player.name.string}")
            // Update leaderboard
            EconomyLeaderboard.update(player, EconomyManager.getBalance(player))
            EconomyLeaderboard.update(target, EconomyManager.getBalance(target))
        } else {
            player.sendSystemMessage(Component.literal("Insufficient funds.").red())
        }
        return 1
    }

    private fun top(context: CommandContext<CommandSourceStack>): Int {
        val top = EconomyLeaderboard.getTop()
        context.source.sendSuccess({ Component.literal("--- Top Balances ---").yellow() }, false)
        top.forEachIndexed { index, entry ->
            context.source.sendSuccess({ Component.literal("#${index + 1} ${entry.name}: $${entry.balance}").aqua() }, false)
        }
        return 1
    }

    private fun setBalance(context: CommandContext<CommandSourceStack>): Int {
        val target = EntityArgument.getPlayer(context, "target")
        val amount = LongArgumentType.getLong(context, "amount")
        EconomyManager.setBalance(target, amount)
        EconomyLeaderboard.update(target, amount)
        context.source.sendSuccess({ Component.literal("Set ${target.name.string}'s balance to $$amount").green() }, true)
        return 1
    }

    private fun addBalance(context: CommandContext<CommandSourceStack>): Int {
        val target = EntityArgument.getPlayer(context, "target")
        val amount = LongArgumentType.getLong(context, "amount")
        EconomyManager.add(target, amount, "admin grant")
        EconomyLeaderboard.update(target, EconomyManager.getBalance(target))
        context.source.sendSuccess({ Component.literal("Added $$amount to ${target.name.string}").green() }, true)
        return 1
    }
}
