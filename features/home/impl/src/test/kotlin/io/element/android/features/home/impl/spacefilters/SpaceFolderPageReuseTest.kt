/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

@file:OptIn(ExperimentalTestApi::class)

package io.element.android.features.home.impl.spacefilters

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import com.google.common.truth.Truth.assertThat
import io.element.android.tests.testutils.robolectric.RobolectricTest
import kotlinx.collections.immutable.persistentListOf
import org.junit.Test

class SpaceFolderPageReuseTest : RobolectricTest() {
    @Test
    fun `cached page becomes live without remounting either adjacent page`() = assertPageReuse(completeWithEmpty = false)

    @Test
    fun `empty response eventually becomes live and returning keeps the existing page`() = assertPageReuse(completeWithEmpty = true)

    @Test
    fun `left and right swipes preserve page direction and composition`() = assertPageReuse(completeWithEmpty = false, gestures = true)

    private fun assertPageReuse(completeWithEmpty: Boolean, gestures: Boolean = false) = runComposeUiTest {
        val filter = aSpaceServiceFilter(displayName = "Folder")
        val filters = persistentListOf(filter)
        val pager = SpaceFolderPagerState()
        var selected by mutableIntStateOf(0)
        var generation = 0
        var contentKey by mutableStateOf<Int?>(0)
        val mounts = mutableMapOf<Int, Int>()
        val disposals = mutableMapOf<Int, Int>()
        val livePages = mutableMapOf<Int, Boolean>()
        val rowMounts = mutableMapOf<Pair<Int, Int>, Int>()
        val activeListState = LazyListState()
        setContent {
            val state = if (selected == 0) {
                SpaceFiltersState.Unselected(filters) {
                    selected = 1
                    contentKey = if (completeWithEmpty) null else ++generation
                }
            } else {
                SpaceFiltersState.Selected(filters, filter) {
                    if (it == SpaceFiltersEvent.Selected.ClearSelection) {
                        selected = 0
                        contentKey = ++generation
                    }
                }
            }
            Box(Modifier.fillMaxSize()) {
                SpaceFolderSwipe(
                    state = state,
                    modifier = Modifier.fillMaxSize().testTag("pager"),
                    pagerState = pager,
                    contentKey = contentKey,
                    hasCachedPage = { true },
                    pageContent = { index, isLive ->
                        DisposableEffect(index) {
                            mounts[index] = mounts.getOrDefault(index, 0) + 1
                            onDispose { disposals[index] = disposals.getOrDefault(index, 0) + 1 }
                        }
                        SideEffect { livePages[index] = isLive }
                        val empty = isLive && contentKey == null
                        val tag = if (empty) "empty-$index" else "page-$index"
                        val cachedListState = rememberLazyListState()
                        LazyColumn(
                            state = if (isLive) activeListState else cachedListState,
                            modifier = Modifier.fillMaxSize().testTag(tag),
                        ) {
                            items(if (empty) 0 else 4) { row ->
                                DisposableEffect(index, row) {
                                    val key = index to row
                                    rowMounts[key] = rowMounts.getOrDefault(key, 0) + 1
                                    onDispose { }
                                }
                                Box(Modifier.fillMaxWidth().height(20.dp))
                            }
                        }
                    },
                ) { error("Folders must use the keyed page content") }
            }
        }
        if (gestures) {
            onNodeWithTag("pager").performTouchInput { swipeLeft() }
        } else {
            runOnIdle { assertThat(pager.selectIndex(1)).isTrue() }
        }
        mainClock.advanceTimeBy(4_000)
        waitForIdle()
        runOnIdle {
            assertThat(selected).isEqualTo(1)
            assertThat(livePages[1]).isTrue()
            assertThat(mounts).containsExactly(0, 1, 1, 1)
            assertThat(disposals).isEmpty()
            if (!completeWithEmpty) assertThat(rowMounts.values.toSet()).containsExactly(1)
        }
        if (completeWithEmpty) {
            onNodeWithTag("empty-1").assertIsDisplayed()
        } else {
            onNodeWithTag("page-1").assertIsDisplayed()
        }
        if (gestures) {
            onNodeWithTag("pager").performTouchInput { swipeRight() }
        } else {
            runOnIdle { assertThat(pager.selectIndex(0)).isTrue() }
        }
        mainClock.advanceTimeBy(2_000)
        waitForIdle()
        runOnIdle {
            assertThat(selected).isEqualTo(0)
            assertThat(livePages[0]).isTrue()
            assertThat(mounts).containsExactly(0, 1, 1, 1)
            assertThat(disposals).isEmpty()
            if (!completeWithEmpty) assertThat(rowMounts.values.toSet()).containsExactly(1)
        }
        onNodeWithTag("page-0").assertIsDisplayed()
    }
}
