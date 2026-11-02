/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.messagecomposer

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TgMediaPanelControllerTest {
    @Test
    fun `switching from focused search keeps the panel while its keyboard closes`() {
        val panel = TgMediaPanelController(1000)
        panel.open()
        panel.onImeChanged(0)
        panel.isSearchFocused = true
        panel.onImeChanged(1000)
        panel.selectTab(TgMediaPanelTab.Gif)
        panel.onImeChanged(1000)
        assertThat(panel.isVisible).isTrue()
        assertThat(panel.tab).isEqualTo(TgMediaPanelTab.Gif)
        assertThat(panel.isKeyboardForeign).isTrue()
        panel.onImeChanged(0)
        assertThat(panel.isVisible).isTrue()
        assertThat(panel.isKeyboardForeign).isFalse()
        // A subsequent keyboard opening for the chat composer still closes the panel.
        panel.onImeChanged(1000)
        assertThat(panel.isVisible).isFalse()
    }

    @Test
    fun `opening a panel over the composer keyboard waits until it has gone down`() {
        val panel = TgMediaPanelController(1000)
        panel.open()
        panel.onImeChanged(1000)
        assertThat(panel.isVisible).isTrue()
        panel.onImeChanged(0)
        panel.onImeChanged(1000)
        assertThat(panel.isVisible).isFalse()
    }

    @Test
    fun `dialog keyboard leaves the underlying panel open`() {
        val panel = TgMediaPanelController(1000)
        panel.open()
        panel.onImeChanged(0)
        panel.isDialogOpen = true
        panel.onImeChanged(1000)
        assertThat(panel.isVisible).isTrue()
        panel.isDialogOpen = false
        panel.onImeChanged(1000)
        assertThat(panel.isVisible).isTrue()
        panel.onImeChanged(0)
        assertThat(panel.isVisible).isTrue()
    }
}
