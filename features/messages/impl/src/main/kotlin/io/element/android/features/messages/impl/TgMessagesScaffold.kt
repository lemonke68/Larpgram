/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.libraries.designsystem.theme.components.Scaffold
import io.element.android.libraries.designsystem.utils.scaffoldScrollableContentInsets

/** Floating chat bars need no measured scaffold slots; keep the thread's inset-aware layout. */
@Composable
internal fun TgMessagesScaffold(
    isThreadTimeline: Boolean,
    topBar: @Composable () -> Unit,
    snackbarHost: @Composable () -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    if (isThreadTimeline) {
        Scaffold(
            contentWindowInsets = scaffoldScrollableContentInsets,
            topBar = topBar,
            snackbarHost = snackbarHost,
            content = content,
        )
    } else {
        val background = ElementTheme.colors.bgCanvasDefault
        CompositionLocalProvider(LocalContentColor provides contentColorFor(background)) {
            Box(Modifier.fillMaxSize().background(background)) {
                content(PaddingValues(0.dp))
                Box(Modifier.align(Alignment.BottomCenter)) { snackbarHost() }
            }
        }
    }
}
