/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.preferences.impl.store

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.preferences.api.store.ChatAppearance
import io.element.android.libraries.preferences.test.FakePreferenceDataStoreFactory
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DefaultChatAppearanceStoreTest {
    @Test
    fun `theme snapshot keeps defaults and persists every appearance setting`() = runTest {
        val store = DefaultChatAppearanceStore(FakePreferenceDataStoreFactory())
        store.getAppearanceFlow().test {
            var expected = ChatAppearance()
            assertThat(awaitItem()).isEqualTo(expected)
            store.setMessageTextSizeSp(20)
            expected = expected.copy(messageTextSizeSp = 20)
            assertThat(awaitItem()).isEqualTo(expected)
            store.setBubbleCornerRadiusDp(12)
            expected = expected.copy(bubbleCornerRadiusDp = 12)
            assertThat(awaitItem()).isEqualTo(expected)
            store.setChatWallpaperId("custom")
            expected = expected.copy(chatWallpaperId = "custom")
            assertThat(awaitItem()).isEqualTo(expected)
            store.setChatWallpaperCustomColorArgb(123)
            expected = expected.copy(chatWallpaperCustomColorArgb = 123)
            assertThat(awaitItem()).isEqualTo(expected)
            store.setChatBubbleColorArgb(456)
            expected = expected.copy(chatBubbleColorArgb = 456)
            assertThat(awaitItem()).isEqualTo(expected)
            store.setChatAccentColorArgb(789)
            expected = expected.copy(chatAccentColorArgb = 789)
            assertThat(awaitItem()).isEqualTo(expected)
            store.setChatWallpaperImageUri("content://test/wallpaper")
            expected = expected.copy(chatWallpaperImageUri = "content://test/wallpaper")
            assertThat(awaitItem()).isEqualTo(expected)
            store.setChatListThreeLine(true)
            expected = expected.copy(chatListThreeLine = true)
            assertThat(awaitItem()).isEqualTo(expected)
            store.setChatWallpaperGradient("1:2:45")
            expected = expected.copy(chatWallpaperGradient = "1:2:45")
            assertThat(awaitItem()).isEqualTo(expected)
            store.setChatAccentColorArgb(null)
            expected = expected.copy(chatAccentColorArgb = null)
            assertThat(awaitItem()).isEqualTo(expected)
        }
    }
}
