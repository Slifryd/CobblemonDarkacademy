/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.bedrock

import com.cobblemon.mod.common.client.render.models.blockbench.PosableState
import com.cobblemon.mod.common.client.render.models.blockbench.repository.RenderContext
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.client.renderer.RenderType
import net.minecraft.client.renderer.entity.ItemRenderer
import net.minecraft.client.renderer.texture.OverlayTexture
import net.minecraft.resources.ResourceLocation

object SimpleBedrockRenderer {

    private val renderContext = RenderContext()

    fun render(
        model: ResourceLocation,
        state: PosableState,
        texture: ResourceLocation,
        matrixStack: PoseStack,
        bufferSource: MultiBufferSource,
        packedLight: Int,
        ageInTicks: Float,
        animation: ResourceLocation? = null,
        animationAge: Int? = null
    ) {
        val bedrockModel = SimpleModelRepository.modelOf(model) ?: return
        val poseableModel = SimpleModelRepository.poseableModelOf(model) ?: return

        matrixStack.pushPose()

        poseableModel.setDefault()
        animationAge?.let { state.updateAge(it) }

        val bedrockAnimation = animation?.let { SimpleAnimationRepository.getAnimation(it) }
        bedrockAnimation?.run(renderContext, poseableModel.relevantPartsByName, poseableModel.rootPart, state, state.animationSeconds, 0f, 0f, ageInTicks, 1.0f)

        val buffer = ItemRenderer.getFoilBufferDirect(bufferSource, RenderType.entityCutout(texture), false, false)
        bedrockModel.render(matrixStack, buffer, packedLight, OverlayTexture.NO_OVERLAY, -0x1)

        matrixStack.popPose()
    }

}