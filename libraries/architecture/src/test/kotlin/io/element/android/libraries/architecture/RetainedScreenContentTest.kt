/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

@file:OptIn(ExperimentalTestApi::class)

package io.element.android.libraries.architecture

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.google.common.truth.Truth.assertThat
import io.element.android.tests.testutils.robolectric.RobolectricTest
import org.junit.Test

class RetainedScreenContentTest : RobolectricTest() {
    @Test
    fun `every scene reporter must allow retention and a hidden veto disposes cached content`() = runComposeUiTest {
        var visible by mutableStateOf(true)
        var allowed by mutableStateOf(false)
        var mounts = 0
        var disposals = 0
        setContent {
            RetainedScreenContent(visible) {
                ScreenContentRetention(eligible = true)
                ScreenContentRetention(eligible = allowed)
                DisposableEffect(Unit) {
                    mounts++
                    onDispose { disposals++ }
                }
                BasicText("scene")
            }
        }
        runOnIdle { visible = false }
        runOnIdle {
            assertThat(disposals).isEqualTo(1)
            visible = true
        }
        runOnIdle {
            allowed = true
            assertThat(mounts).isEqualTo(2)
        }
        runOnIdle { visible = false }
        runOnIdle {
            assertThat(disposals).isEqualTo(1)
            allowed = false
        }
        runOnIdle { assertThat(disposals).isEqualTo(2) }
    }

    @Test
    fun `a pending dialog never opens while the cached screen is hidden and appears on return`() = runComposeUiTest {
        var visible by mutableStateOf(true)
        var dialogPending by mutableStateOf(false)
        var windows = 0
        setContent {
            RetainedScreenContent(visible) {
                ScreenContentRetention(eligible = !dialogPending)
                if (LocalScreenContentVisible.current && dialogPending) {
                    Dialog(onDismissRequest = {}) {
                        DisposableEffect(Unit) {
                            windows++
                            onDispose { }
                        }
                        BasicText("dialog", Modifier.testTag("dialog"))
                    }
                }
            }
        }
        runOnIdle { visible = false }
        runOnIdle { dialogPending = true }
        onNodeWithTag("dialog").assertDoesNotExist()
        runOnIdle {
            assertThat(windows).isEqualTo(0)
            visible = true
        }
        onNodeWithTag("dialog").assertIsDisplayed()
        runOnIdle { assertThat(windows).isEqualTo(1) }
    }

    @Test
    fun `hidden screen cannot leave a separate dialog window above the current screen`() = runComposeUiTest {
        var visible by mutableStateOf(true)
        setContent {
            RetainedScreenContent(visible) {
                Dialog(onDismissRequest = {}) {
                    BasicText("dialog", Modifier.testTag("dialog"))
                }
            }
        }
        onNodeWithTag("dialog").assertIsDisplayed()
        runOnIdle { visible = false }
        onNodeWithTag("dialog").assertDoesNotExist()
    }

    @Test
    fun `hidden screen clears its text field focus and cannot steal focus from the current screen`() = runComposeUiTest {
        var visible by mutableStateOf(true)
        var homeFocused = false
        var roomFocused = false
        val homeFocus = FocusRequester()
        val roomFocus = FocusRequester()
        setContent {
            Box(Modifier.fillMaxSize()) {
                RetainedScreenContent(visible) {
                    ScreenContentRetention(eligible = true)
                    BasicTextField(
                        value = "",
                        onValueChange = {},
                        modifier = Modifier.testTag("home-input").focusRequester(homeFocus).onFocusChanged { homeFocused = it.isFocused },
                    )
                }
                if (!visible) {
                    BasicTextField(
                        value = "",
                        onValueChange = {},
                        modifier = Modifier.focusRequester(roomFocus).onFocusChanged { roomFocused = it.isFocused },
                    )
                }
            }
        }
        runOnIdle { assertThat(homeFocus.requestFocus()).isTrue() }
        runOnIdle {
            assertThat(homeFocused).isTrue()
            visible = false
        }
        runOnIdle {
            assertThat(homeFocused).isFalse()
            assertThat(roomFocus.requestFocus()).isTrue()
        }
        runOnIdle {
            assertThat(homeFocus.requestFocus()).isFalse()
            assertThat(roomFocused).isTrue()
        }
    }

    @Test
    fun `hidden screen retains state without layout draw or accessible content and disposes on removal`() = runComposeUiTest {
        var visible by mutableStateOf(true)
        var attached by mutableStateOf(true)
        var mounts = 0
        var disposals = 0
        var measures = 0
        var draws = 0
        var contentVisible = true
        setContent {
            Box(Modifier.fillMaxSize()) {
                if (attached) {
                    RetainedScreenContent(visible) {
                        ScreenContentRetention(eligible = true)
                        var count by remember { mutableIntStateOf(0) }
                        val localVisible = LocalScreenContentVisible.current
                        SideEffect { contentVisible = localVisible }
                        DisposableEffect(Unit) {
                            mounts++
                            onDispose { disposals++ }
                        }
                        BasicText(
                            text = count.toString(),
                            modifier = Modifier
                                .testTag("screen")
                                .size(80.dp)
                                .clickable { count++ }
                                .layout { measurable, constraints ->
                                    measures++
                                    val placeable = measurable.measure(constraints)
                                    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                                }
                                .drawWithContent {
                                    draws++
                                    drawContent()
                                },
                        )
                    }
                }
            }
        }
        onNodeWithTag("screen").performClick().assertTextEquals("1")
        runOnIdle { visible = false }
        onNodeWithTag("screen").assertDoesNotExist()
        runOnIdle {
            assertThat(contentVisible).isFalse()
            assertThat(mounts).isEqualTo(1)
            assertThat(disposals).isEqualTo(0)
        }
        val hiddenMeasures = measures
        val hiddenDraws = draws
        mainClock.advanceTimeBy(1000)
        runOnIdle {
            assertThat(measures).isEqualTo(hiddenMeasures)
            assertThat(draws).isEqualTo(hiddenDraws)
            visible = true
        }
        onNodeWithTag("screen").assertIsDisplayed().assertTextEquals("1")
        runOnIdle {
            assertThat(contentVisible).isTrue()
            assertThat(mounts).isEqualTo(1)
            attached = false
        }
        runOnIdle { assertThat(disposals).isEqualTo(1) }
    }
}
