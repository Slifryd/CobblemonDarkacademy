/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.gui.tower

import com.cobblemon.mod.common.CobblemonNetwork
import com.cobblemon.mod.common.CobblemonSounds
import com.cobblemon.mod.common.api.battlefactory.TowerPokemonDTO
import com.cobblemon.mod.common.api.gui.blitk
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.api.text.bold
import com.cobblemon.mod.common.client.CobblemonResources
import com.cobblemon.mod.common.client.gui.summary.widgets.ModelWidget
import com.cobblemon.mod.common.client.render.drawScaledText
import com.cobblemon.mod.common.net.messages.server.tower.TowerPokemonSelectionPacket
import com.cobblemon.mod.common.pokemon.RenderablePokemon
import com.cobblemon.mod.common.util.asTranslated
import com.cobblemon.mod.common.util.cobblemonResource
import com.cobblemon.mod.common.util.lang
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.client.gui.components.AbstractWidget
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.narration.NarrationElementOutput
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.network.chat.Component

/**
 * Client-side GUI for Tower Pokemon selection.
 *
 * Displays 6 Pokemon in a grid (3x2) and allows the player to select exactly 3.
 *
 * @author Cobblemon Contributors
 * @since January 2026
 */
class TowerPokemonSelectionScreen(
    private val offeredPokemon: List<TowerPokemonDTO>,
    private val difficulty: String
) : Screen(lang("ui.tower.selection.title")) {

    companion object {
        private const val BASE_WIDTH = 256
        private const val BASE_HEIGHT = 180

        private val background = cobblemonResource("textures/gui/pc/pc_select.png")

        private const val SLOT_SIZE = 60
        private const val SLOT_SPACING = 10
    }

    private val selectedIndices = mutableSetOf<Int>()
    private val pokemonWidgets = mutableListOf<PokemonSlotWidget>()
    private lateinit var confirmButton: Button

    override fun init() {
        super.init()

        val centerX = width / 2
        val centerY = height / 2

        // Create grid: 3 columns x 2 rows
        val startX = centerX - (3 * SLOT_SIZE + 2 * SLOT_SPACING) / 2
        val startY = centerY - (2 * SLOT_SIZE + SLOT_SPACING) / 2 - 10

        offeredPokemon.forEachIndexed { index, pokemonDTO ->
            val col = index % 3
            val row = index / 3

            val x = startX + col * (SLOT_SIZE + SLOT_SPACING)
            val y = startY + row * (SLOT_SIZE + SLOT_SPACING)

            val widget = PokemonSlotWidget(
                x = x,
                y = y,
                pokemon = pokemonDTO,
                index = index,
                onSelect = { idx -> toggleSelection(idx) }
            )

            pokemonWidgets.add(widget)
            addRenderableWidget(widget)
        }

        // Confirm button
        confirmButton = Button.builder(
            lang("ui.tower.selection.confirm"),
            Button.OnPress { confirm() }
        )
            .bounds(centerX - 40, centerY + SLOT_SIZE + 30, 80, 20)
            .build()

        addRenderableWidget(confirmButton)
        updateConfirmButton()
    }

    private fun toggleSelection(index: Int) {
        if (selectedIndices.contains(index)) {
            selectedIndices.remove(index)
        } else {
            if (selectedIndices.size < 3) {
                selectedIndices.add(index)
            } else {
                // Max 3, show feedback
                minecraft?.player?.displayClientMessage(
                    Component.literal("§cYou can only select 3 Pokémon!"),
                    true
                )
                return
            }
        }

        // Play sound
        minecraft?.soundManager?.play(SimpleSoundInstance.forUI(CobblemonSounds.GUI_CLICK, 1.0F))

        // Update widgets
        pokemonWidgets.forEach { it.updateSelection(selectedIndices) }
        updateConfirmButton()
    }

    private fun updateConfirmButton() {
        confirmButton.active = selectedIndices.size == 3
    }

    private fun confirm() {
        if (selectedIndices.size != 3) return

        // Send packet to server
        CobblemonNetwork.sendToServer(
            TowerPokemonSelectionPacket(
                selectedIndices = selectedIndices.toList(),
                difficulty = difficulty
            )
        )

        // Close screen
        minecraft?.setScreen(null)
    }

    override fun render(context: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
        renderBackground(context, mouseX, mouseY, delta)

        val matrices = context.pose()

        // Title
        drawScaledText(
            context = context,
            font = CobblemonResources.DEFAULT_LARGE,
            text = lang("ui.tower.selection.title").bold(),
            x = width / 2.0,
            y = height / 2.0 - 80.0,
            centered = true,
            scale = 1.5F,
            shadow = true
        )

        // Subtitle
        drawScaledText(
            context = context,
            text = lang("ui.tower.selection.subtitle", selectedIndices.size, 3),
            x = width / 2.0,
            y = height / 2.0 - 60.0,
            centered = true,
            scale = 0.8F,
            shadow = true
        )

        super.render(context, mouseX, mouseY, delta)
    }

    override fun isPauseScreen() = true

    /**
     * Widget for a single Pokemon slot.
     */
    private inner class PokemonSlotWidget(
        private val x: Int,
        private val y: Int,
        private val pokemon: TowerPokemonDTO,
        private val index: Int,
        private val onSelect: (Int) -> Unit
    ) : AbstractWidget(x, y, SLOT_SIZE, SLOT_SIZE, Component.empty()) {

        private var isSelected = false
        private var isHovered = false
        private lateinit var modelWidget: ModelWidget

        init {
            // Create RenderablePokemon for model widget
            val species = PokemonSpecies.getByIdentifier(pokemon.species)

            modelWidget = if (species != null) {
                val form = pokemon.getFormData()
                val aspects = mutableSetOf(form?.name ?: "default")
                if (pokemon.shiny) aspects.add("shiny")

                val renderablePokemon = RenderablePokemon(
                    species = species,
                    aspects = aspects
                )

                ModelWidget(
                    pX = x + 5,
                    pY = y + 5,
                    pWidth = SLOT_SIZE - 10,
                    pHeight = SLOT_SIZE - 10,
                    pokemon = renderablePokemon,
                    baseScale = 1.8F,
                    rotationY = -15F,
                    playCryOnClick = false
                )
            } else {
                // Fallback: afficher un Pokémon par défaut au lieu d'un aléatoire
                val fallbackSpecies = PokemonSpecies.getByIdentifier(cobblemonResource("bulbasaur")) ?: PokemonSpecies.random()
                ModelWidget(
                    pX = x + 5,
                    pY = y + 5,
                    pWidth = SLOT_SIZE - 10,
                    pHeight = SLOT_SIZE - 10,
                    pokemon = RenderablePokemon(fallbackSpecies, setOf()),
                    baseScale = 1.8F,
                    rotationY = -15F,
                    playCryOnClick = false
                )
            }
        }

        fun updateSelection(selected: Set<Int>) {
            isSelected = selected.contains(index)
        }

        override fun renderWidget(context: GuiGraphics, mouseX: Int, mouseY: Int, delta: Float) {
            val matrices = context.pose()
            isHovered = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height

            // Draw background
            val alpha = if (isHovered) 0.8F else if (isSelected) 0.6F else 0.4F
            context.fill(x, y, x + width, y + height, (alpha * 255).toInt() shl 24 or 0x333333)

            // Border
            val borderColor = when {
                isSelected -> 0xFF00FF00.toInt() // Green
                isHovered -> 0xFFFFFFFF.toInt()  // White
                else -> 0xFF666666.toInt()       // Gray
            }
            context.fill(x, y, x + width, y + 1, borderColor)
            context.fill(x, y + height - 1, x + width, y + height, borderColor)
            context.fill(x, y, x + 1, y + height, borderColor)
            context.fill(x + width - 1, y, x + width, y + height, borderColor)

            // Render Pokemon model
            modelWidget.render(context, mouseX, mouseY, delta)

            // Render name
            drawScaledText(
                context = context,
                text = Component.literal(pokemon.getDisplayName()),
                x = x + SLOT_SIZE / 2.0,
                y = y + SLOT_SIZE - 12.0,
                centered = true,
                scale = 0.5F,
                shadow = true
            )

            // Selection indicator
            if (isSelected) {
                drawScaledText(
                    context = context,
                    text = Component.literal("§a✓"),
                    x = x + SLOT_SIZE - 8.0,
                    y = y + 2.0,
                    centered = false,
                    scale = 1.2F,
                    shadow = true
                )
            }

            // Render tooltip when hovering
            if (isHovered) {
                renderTooltip(context, mouseX, mouseY)
            }
        }

        /**
         * Renders a detailed tooltip showing stats, moves, ability, and held item.
         */
        private fun renderTooltip(context: GuiGraphics, mouseX: Int, mouseY: Int) {
            val matrices = context.pose()

            // Push matrix and translate forward in z to render above everything else
            matrices.pushPose()
            matrices.translate(0.0, 0.0, 400.0)

            val tooltipX = x + SLOT_SIZE + 5
            val tooltipY = y
            val tooltipWidth = 140
            val lineHeight = 10

            // Calculate tooltip height based on content
            val moveCount = pokemon.moves.size
            val tooltipHeight = 90 + (moveCount * lineHeight)

            // Draw tooltip background
            context.fill(tooltipX, tooltipY, tooltipX + tooltipWidth, tooltipY + tooltipHeight, 0xE0000000.toInt())

            // Draw border
            context.fill(tooltipX, tooltipY, tooltipX + tooltipWidth, tooltipY + 1, 0xFFFFFFFF.toInt())
            context.fill(tooltipX, tooltipY + tooltipHeight - 1, tooltipX + tooltipWidth, tooltipY + tooltipHeight, 0xFFFFFFFF.toInt())
            context.fill(tooltipX, tooltipY, tooltipX + 1, tooltipY + tooltipHeight, 0xFFFFFFFF.toInt())
            context.fill(tooltipX + tooltipWidth - 1, tooltipY, tooltipX + tooltipWidth, tooltipY + tooltipHeight, 0xFFFFFFFF.toInt())

            var currentY = tooltipY + 5.0

            // Title: Pokemon name and level
            drawScaledText(
                context = context,
                text = Component.literal("§e${pokemon.getDisplayName()} §7Lv${pokemon.level}"),
                x = tooltipX + 5.0,
                y = currentY,
                centered = false,
                scale = 0.7F,
                shadow = true
            )
            currentY += lineHeight + 2

            // Stats section
            drawScaledText(
                context = context,
                text = Component.literal("§6Stats:"),
                x = tooltipX + 5.0,
                y = currentY,
                centered = false,
                scale = 0.6F,
                shadow = true
            )
            currentY += lineHeight

            // HP, ATK, DEF
            drawScaledText(
                context = context,
                text = Component.literal("§fHP: §a${pokemon.hp} §fATK: §c${pokemon.attack} §fDEF: §9${pokemon.defense}"),
                x = tooltipX + 5.0,
                y = currentY,
                centered = false,
                scale = 0.5F,
                shadow = true
            )
            currentY += lineHeight - 2

            // SP.ATK, SP.DEF, SPD
            drawScaledText(
                context = context,
                text = Component.literal("§fSp.A: §d${pokemon.specialAttack} §fSp.D: §3${pokemon.specialDefense} §fSpd: §e${pokemon.speed}"),
                x = tooltipX + 5.0,
                y = currentY,
                centered = false,
                scale = 0.5F,
                shadow = true
            )
            currentY += lineHeight + 2

            // Ability
            drawScaledText(
                context = context,
                text = Component.literal("§6Ability: §f${pokemon.ability}"),
                x = tooltipX + 5.0,
                y = currentY,
                centered = false,
                scale = 0.6F,
                shadow = true
            )
            currentY += lineHeight + 2

            // Moves section
            drawScaledText(
                context = context,
                text = Component.literal("§6Moves:"),
                x = tooltipX + 5.0,
                y = currentY,
                centered = false,
                scale = 0.6F,
                shadow = true
            )
            currentY += lineHeight

            pokemon.moves.forEach { moveId ->
                val moveName = moveId.path.replace("_", " ").split(" ").joinToString(" ") {
                    it.replaceFirstChar { c -> c.uppercase() }
                }
                drawScaledText(
                    context = context,
                    text = Component.literal("§7• §f$moveName"),
                    x = tooltipX + 10.0,
                    y = currentY,
                    centered = false,
                    scale = 0.5F,
                    shadow = true
                )
                currentY += lineHeight - 1
            }

            // Held item (if any)
            if (pokemon.heldItem != null) {
                currentY += 2
                val itemName = pokemon.heldItem!!.split(":").lastOrNull()?.replace("_", " ")?.split(" ")?.joinToString(" ") {
                    it.replaceFirstChar { c -> c.uppercase() }
                } ?: pokemon.heldItem!!
                drawScaledText(
                    context = context,
                    text = Component.literal("§6Held Item: §b$itemName"),
                    x = tooltipX + 5.0,
                    y = currentY,
                    centered = false,
                    scale = 0.6F,
                    shadow = true
                )
            }

            // Restore matrix state
            matrices.popPose()
        }

        override fun onClick(mouseX: Double, mouseY: Double) {
            onSelect(index)
        }

        override fun updateWidgetNarration(builder: NarrationElementOutput) {
            builder.add(net.minecraft.client.gui.narration.NarratedElementType.TITLE, pokemon.getDisplayName())
        }
    }
}