/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.preferences.impl.advanced

import androidx.compose.ui.graphics.toArgb
import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.designsystem.theme.ChatThemeOption
import io.element.android.libraries.designsystem.theme.ChatWallpaperOption
import io.element.android.libraries.preferences.api.store.DEFAULT_MESSAGE_TEXT_SIZE_SP
import io.element.android.libraries.preferences.test.InMemoryChatAppearanceStore
import io.element.android.tests.testutils.WarmUpRule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class ChatAppearancePresenterTest {
    @get:Rule
    val warmUpRule = WarmUpRule()

    @Test
    fun `present - initial state uses the defaults`() = runTest {
        val presenter = createPresenter()
        moleculeFlow(RecompositionMode.Immediate) {
            presenter.present()
        }.test {
            with(awaitItem()) {
                assertThat(messageTextSizeSp).isEqualTo(DEFAULT_MESSAGE_TEXT_SIZE_SP)
                assertThat(chatWallpaperId).isEqualTo(ChatWallpaperOption.DEFAULT.id)
                assertThat(chatBubbleColorArgb).isNull()
                assertThat(chatListThreeLine).isFalse()
            }
        }
    }

    @Test
    fun `present - text size is saved`() = runTest {
        val store = InMemoryChatAppearanceStore()
        val presenter = createPresenter(store)
        moleculeFlow(RecompositionMode.Immediate) {
            presenter.present()
        }.test {
            awaitItem().eventSink(ChatAppearanceEvent.SetMessageTextSize(20))
            assertThat(awaitItem().messageTextSizeSp).isEqualTo(20)
        }
    }

    @Test
    fun `present - eyedropper color switches the wallpaper to the custom one`() = runTest {
        val store = InMemoryChatAppearanceStore()
        val presenter = createPresenter(store)
        moleculeFlow(RecompositionMode.Immediate) {
            presenter.present()
        }.test {
            awaitItem().eventSink(ChatAppearanceEvent.SetChatWallpaperCustomColor(0xFF112233.toInt()))
            val state = expectMostRecentItemAfter { it.chatWallpaperId == ChatWallpaperOption.CUSTOM_ID }
            assertThat(state.chatWallpaperCustomColorArgb).isEqualTo(0xFF112233.toInt())
        }
    }

    @Test
    fun `present - clearing the photo brings back the default wallpaper`() = runTest {
        val store = InMemoryChatAppearanceStore(
            chatWallpaperId = ChatWallpaperOption.CUSTOM_IMAGE_ID,
            chatWallpaperImageUri = "content://photo",
        )
        val presenter = createPresenter(store)
        moleculeFlow(RecompositionMode.Immediate) {
            presenter.present()
        }.test {
            awaitItem().eventSink(ChatAppearanceEvent.SetChatWallpaperImage(null))
            val state = expectMostRecentItemAfter { it.chatWallpaperId == ChatWallpaperOption.DEFAULT.id }
            assertThat(state.chatWallpaperImageUri).isNull()
        }
    }

    @Test
    fun `present - applying a theme sets wallpaper, bubble and accent together`() = runTest {
        val theme = ChatThemeOption.Ocean
        val presenter = createPresenter()
        moleculeFlow(RecompositionMode.Immediate) {
            presenter.present()
        }.test {
            awaitItem().eventSink(ChatAppearanceEvent.ApplyChatTheme(theme.id))
            val state = expectMostRecentItemAfter { it.chatAccentColorArgb != null }
            assertThat(state.chatWallpaperId).isEqualTo(theme.wallpaper.id)
            assertThat(state.chatBubbleColorArgb).isEqualTo(theme.bubbleColor?.toArgb())
            assertThat(state.chatAccentColorArgb).isEqualTo(theme.accentColor?.toArgb())
        }
    }

    private suspend fun app.cash.turbine.ReceiveTurbine<ChatAppearanceState>.expectMostRecentItemAfter(
        predicate: (ChatAppearanceState) -> Boolean,
    ): ChatAppearanceState {
        var item = awaitItem()
        while (!predicate(item)) item = awaitItem()
        return item
    }

    private fun CoroutineScope.createPresenter(
        store: InMemoryChatAppearanceStore = InMemoryChatAppearanceStore(),
    ) = ChatAppearancePresenter(
        chatAppearanceStore = store,
        sessionCoroutineScope = this,
    )
}
