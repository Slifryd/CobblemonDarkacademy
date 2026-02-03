/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.api.battlefactory

import com.cobblemon.mod.common.api.moves.MoveTemplate
import com.cobblemon.mod.common.api.pokemon.PokemonSpecies
import com.cobblemon.mod.common.api.pokemon.stats.Stats
import com.cobblemon.mod.common.pokemon.FormData
import com.cobblemon.mod.common.pokemon.Pokemon
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.resources.ResourceLocation

/**
 * Data Transfer Object for Pokemon to send over the network.
 * Contains only the essential information needed to display Pokemon in the selection GUI.
 * 
 * @author Cobblemon Contributors
 * @since January 2026
 */
data class TowerPokemonDTO(
    val species: ResourceLocation,
    val form: String,
    val level: Int,
    val shiny: Boolean,
    val moves: List<ResourceLocation>,
    val ability: String,
    val hp: Int,
    val attack: Int,
    val defense: Int,
    val specialAttack: Int,
    val specialDefense: Int,
    val speed: Int,
    val heldItem: String? // Item name or null
) {
    
    companion object {
        /**
         * Creates a DTO from a Pokemon instance.
         */
        fun fromPokemon(pokemon: Pokemon): TowerPokemonDTO {
            return TowerPokemonDTO(
                species = pokemon.species.resourceIdentifier,
                form = pokemon.form.name,
                level = pokemon.level,
                shiny = pokemon.shiny,
                moves = pokemon.moveSet.getMoves().map { ResourceLocation.parse(it.template.name) },
                ability = pokemon.ability.name,
                hp = pokemon.hp,
                attack = pokemon.attack,
                defense = pokemon.defence,
                specialAttack = pokemon.specialAttack,
                specialDefense = pokemon.specialDefence,
                speed = pokemon.speed,
                heldItem = pokemon.heldItem().item.toString().takeIf { pokemon.heldItem().item.toString() != "minecraft:air" }
            )
        }
        
        /**
         * Decodes from network buffer.
         */
        fun decode(buffer: RegistryFriendlyByteBuf): TowerPokemonDTO {
            return TowerPokemonDTO(
                species = buffer.readResourceLocation(),
                form = buffer.readUtf(),
                level = buffer.readInt(),
                shiny = buffer.readBoolean(),
                moves = buffer.readList { it.readResourceLocation() },
                ability = buffer.readUtf(),
                hp = buffer.readInt(),
                attack = buffer.readInt(),
                defense = buffer.readInt(),
                specialAttack = buffer.readInt(),
                specialDefense = buffer.readInt(),
                speed = buffer.readInt(),
                heldItem = buffer.readNullable { it.readUtf() }
            )
        }
    }
    
    /**
     * Encodes to network buffer.
     */
    fun encode(buffer: RegistryFriendlyByteBuf) {
        buffer.writeResourceLocation(species)
        buffer.writeUtf(form)
        buffer.writeInt(level)
        buffer.writeBoolean(shiny)
        buffer.writeCollection(moves) { buf, move -> buf.writeResourceLocation(move) }
        buffer.writeUtf(ability)
        buffer.writeInt(hp)
        buffer.writeInt(attack)
        buffer.writeInt(defense)
        buffer.writeInt(specialAttack)
        buffer.writeInt(specialDefense)
        buffer.writeInt(speed)
        buffer.writeNullable(heldItem) { buf, item -> buf.writeUtf(item) }
    }
    
    /**
     * Gets the display name for this Pokemon.
     */
    fun getDisplayName(): String {
        val speciesObj = PokemonSpecies.getByIdentifier(species)
        return speciesObj?.translatedName?.string ?: species.path
    }
    
    /**
     * Gets the form data.
     */
    fun getFormData(): FormData? {
        val speciesObj = PokemonSpecies.getByIdentifier(species) ?: return null
        return speciesObj.forms.find { it.name == form }
    }
}
