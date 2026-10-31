/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

@file:OptIn(ExperimentalTestApi::class)

package io.element.android.libraries.designsystem.components.glass

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import dev.chrisbanes.haze.HazeState
import io.element.android.libraries.architecture.LocalScreenContentVisible
import io.element.android.tests.testutils.robolectric.RobolectricTest
import org.junit.Test

class GlassSourceVisibilityTest : RobolectricTest() {
    @Test
    fun `hidden retained screen detaches its Haze source and return attaches one source`() = runComposeUiTest {
        var visible by mutableStateOf(true)
        val haze = HazeState()
        setContent {
            CompositionLocalProvider(LocalScreenContentVisible provides visible) {
                Box(Modifier.size(80.dp).tgGlassSource(haze))
            }
        }
        runOnIdle {
            assertThat(haze.areas).hasSize(1)
            visible = false
        }
        runOnIdle {
            assertThat(haze.areas).isEmpty()
            visible = true
        }
        runOnIdle { assertThat(haze.areas).hasSize(1) }
    }
}
