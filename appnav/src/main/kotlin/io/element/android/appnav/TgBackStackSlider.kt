/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.appnav

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Transition
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import com.bumble.appyx.core.navigation.transition.ModifierTransitionHandler
import com.bumble.appyx.core.navigation.transition.TransitionDescriptor
import com.bumble.appyx.navmodel.backstack.BackStack
import io.element.android.compound.theme.ElementTheme
import io.element.android.libraries.designsystem.animation.TgMotion

/**
 * Larpgram screen transition (plan 9.3), like Telegram: the new screen slides in from the right
 * edge while the one underneath moves a quarter of the way left under a 35 % shade. Opening takes
 * [TgMotion.CHAT_OPEN_MS], going back [TgMotion.CHAT_BACK_MS], both on [TgMotion.Out].
 */
class TgBackStackSlider<NavTarget> : ModifierTransitionHandler<NavTarget, BackStack.State>() {
    override fun createModifier(
        modifier: Modifier,
        transition: Transition<BackStack.State>,
        descriptor: TransitionDescriptor<NavTarget, BackStack.State>,
    ): Modifier = modifier.composed {
        val position = transition.animateFloat(transitionSpec = { specFor(this) }, label = "screenPosition") { state ->
            when (state) {
                BackStack.State.CREATED, BackStack.State.DESTROYED -> 1f
                BackStack.State.ACTIVE -> 0f
                BackStack.State.STASHED -> -TgMotion.CHAT_UNDER_SHIFT
            }
        }
        val shade = transition.animateFloat(transitionSpec = { specFor(this) }, label = "screenShade") { state ->
            if (state == BackStack.State.STASHED) TgMotion.CHAT_UNDER_SHADE else 0f
        }
        // A screen may still be loading while it slides in (a chat waits for its timeline): the
        // canvas colour keeps it from sliding in see-through.
        val canvas = ElementTheme.colors.bgCanvasDefault
        Modifier
            .graphicsLayer { translationX = position.value * size.width }
            .background(canvas)
            .drawWithContent {
                drawContent()
                val alpha = shade.value
                if (alpha > 0f) drawRect(Color.Black.copy(alpha = alpha))
            }
    }
}

private fun specFor(segment: Transition.Segment<BackStack.State>): FiniteAnimationSpec<Float> {
    val isBack = segment.targetState == BackStack.State.DESTROYED ||
        (segment.initialState == BackStack.State.STASHED && segment.targetState == BackStack.State.ACTIVE)
    return TgMotion.tween(if (isBack) TgMotion.CHAT_BACK_MS else TgMotion.CHAT_OPEN_MS)
}
