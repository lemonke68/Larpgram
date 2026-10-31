/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.architecture

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.clearAndSetSemantics

/** False only after a retained screen has finished leaving the visible navigation stack. */
val LocalScreenContentVisible = compositionLocalOf { true }

private class ScreenRetentionPolicy {
    val permissions = mutableStateMapOf<Any, Boolean>()
    val canRetain: Boolean get() = permissions.isNotEmpty() && permissions.values.all { it }
}

private val LocalScreenRetentionPolicy = staticCompositionLocalOf<ScreenRetentionPolicy?> { null }

/**
 * Opt a known safe scene into retaining its UI. Every reporter must permit retention; leaving
 * the scene removes its permission. Separate windows/media must also respect [LocalScreenContentVisible].
 * Unregistered scenes are disposed normally, including nested settings and profile screens.
 */
@Composable
fun ScreenContentRetention(eligible: Boolean) {
    val policy = LocalScreenRetentionPolicy.current ?: return
    val token = remember { Any() }
    DisposableEffect(policy, token) {
        onDispose { policy.permissions.remove(token) }
    }
    SideEffect { policy.permissions[token] = eligible }
}

/** Keep only opted-in scenes, without measuring, placing or exposing hidden screen content. */
@Composable
internal fun RetainedScreenContent(visible: Boolean, content: @Composable () -> Unit) {
    val policy = remember { ScreenRetentionPolicy() }
    CompositionLocalProvider(
        LocalScreenContentVisible provides visible,
        LocalScreenRetentionPolicy provides policy,
    ) {
        if (visible || policy.canRetain) {
            Layout(
                content = content,
                modifier = Modifier
                    .focusProperties { canFocus = visible }
                    .then(if (visible) Modifier else Modifier.clearAndSetSemantics { }),
            ) { measurables, constraints ->
                if (visible) {
                    val placeables = measurables.map { it.measure(constraints) }
                    layout(
                        placeables.maxOfOrNull { it.width } ?: constraints.minWidth,
                        placeables.maxOfOrNull { it.height } ?: constraints.minHeight,
                    ) {
                        placeables.forEach { it.place(0, 0) }
                    }
                } else {
                    layout(constraints.minWidth, constraints.minHeight) { }
                }
            }
        }
    }
}
