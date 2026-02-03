/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.bedrock

import com.cobblemon.mod.common.Cobblemon
import com.cobblemon.mod.common.api.data.JsonDataRegistry
import com.cobblemon.mod.common.api.reactive.SimpleObservable
import com.cobblemon.mod.common.client.render.models.blockbench.PosableModel
import com.cobblemon.mod.common.client.render.models.blockbench.TexturedModel
import com.cobblemon.mod.common.util.cobblemonResource
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import net.minecraft.client.model.geom.ModelPart
import net.minecraft.resources.ResourceLocation
import net.minecraft.server.level.ServerPlayer
import net.minecraft.server.packs.PackType
import kotlin.collections.component1
import kotlin.collections.component2
import kotlin.collections.forEach

object SimpleModelRepository : JsonDataRegistry<TexturedModel> {

    override val id = cobblemonResource("bedrock_models")
    override val type = PackType.CLIENT_RESOURCES
    override val observable = SimpleObservable<SimpleModelRepository>()
    override val gson: Gson = TexturedModel.GSON
    override val typeToken: TypeToken<TexturedModel> = TypeToken.get(TexturedModel::class.java)
    override val resourcePath = "simple/models/bedrock/models"
    private val models = hashMapOf<ResourceLocation, ModelPart>()
    private val poseableModels = hashMapOf<ResourceLocation, PosableModel>()

    override fun sync(player: ServerPlayer) {}

    override fun reload(data: Map<ResourceLocation, TexturedModel>) {
        data.forEach { (identifier, model) ->
            try {
                val bakedModel = model.create().bakeRoot()
                models[identifier] = bakedModel
                poseableModels[identifier] = PosableModel(bakedModel).apply {
                    registerPartAndAllNamedChildren("root", bakedModel)
                }
            }
            catch (e: Exception) {
                Cobblemon.LOGGER.error("Failed to load model $identifier", e)
            }
        }
        observable.emit(this)
        Cobblemon.LOGGER.info("Loaded {} models", models.size)
        Cobblemon.LOGGER.info("Models: {}", models.keys.joinToString(", "))
    }

    fun modelOf(identifier: ResourceLocation) = models[identifier]
    fun poseableModelOf(identifier: ResourceLocation) = poseableModels[identifier]

}
