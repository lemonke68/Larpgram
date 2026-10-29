/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.designsystem.animation

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec

/**
 * Larpgram motion tokens (redesign V1 final, plan 9.3). Values come from the canvas prototype
 * `F-Motion.dc.html`. The curves are Telegram Android's `CubicBezierInterpolator` constants.
 *
 * Every duration here is scaled by the system "Animator duration scale" (Compose reads it on
 * its own), so "Remove animations" in the accessibility settings turns all of these off.
 */
object TgMotion {
    /** Fast start, long soft landing (TG `EASE_OUT_QUINT`). Screens, pager, morphs. */
    val Out: Easing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

    /** Small overshoot (TG `EASE_OUT_BACK`). Popovers and menus that pop out of an anchor. */
    val Pop: Easing = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)

    /** Plain ease (TG `DEFAULT`). Fades, dims, colour changes. */
    val Std: Easing = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)

    /** Accelerating exit (TG `EASE_IN`). Things that close and leave. */
    val Exit: Easing = CubicBezierEasing(0.42f, 0f, 1f, 1f)

    const val CHAT_OPEN_MS = 280
    const val CHAT_BACK_MS = 240

    /** How far the screen underneath moves left while a new one slides in, and its shade. */
    const val CHAT_UNDER_SHIFT = 0.25f
    const val CHAT_UNDER_SHADE = 0.35f

    const val TAB_MS = 260
    const val FOLDER_MS = 280
    const val NOTICE_IN_MS = 320
    const val NOTICE_OUT_MS = 220
    const val ACCOUNTS_IN_MS = 260
    const val ACCOUNTS_OUT_MS = 160
    const val ACCOUNTS_DIM_MS = 180
    const val MENU_IN_MS = 300
    const val MENU_OUT_MS = 180
    const val MENU_DIM_MS = 260
    const val MENU_REACTIONS_DELAY_MS = 60
    const val MENU_LIST_DELAY_MS = 30

    /** Long-press delay of the prototype (TG uses the platform long-press timeout, ~400 ms). */
    const val HOLD_MS = 400L

    /** Quick fade used together with a pop, so the alpha never overshoots. */
    const val POP_FADE_MS = 120

    fun <T> tween(durationMillis: Int, easing: Easing = Out, delayMillis: Int = 0): FiniteAnimationSpec<T> =
        androidx.compose.animation.core.tween(durationMillis = durationMillis, delayMillis = delayMillis, easing = easing)
}
