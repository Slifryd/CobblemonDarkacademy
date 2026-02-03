/*
 * Copyright (C) 2023 Cobblemon Contributors
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package com.cobblemon.mod.common.client.render.bedrock

import com.cobblemon.mod.common.api.scheduling.SchedulingTracker
import com.cobblemon.mod.common.client.render.models.blockbench.PosableState

open class SimplePosableState : PosableState() {

    override val schedulingTracker = SchedulingTracker()

    override fun getEntity() = null

    override fun updatePartialTicks(partialTicks: Float) {
        this.currentPartialTicks = partialTicks
        schedulingTracker.update(0F)
    }

}
