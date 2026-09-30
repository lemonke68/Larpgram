/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.attachments.tgattach

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TgAttachSheetMemoryTest {
    private val restore = TgAttachRestore(selection = emptyList(), caption = "hi", isExpanded = true)

    @Test
    fun `cancelling a preview opened from the sheet brings the sheet back`() {
        val memory = TgAttachSheetMemory()
        memory.onOpenPreview(restore)
        assertThat(memory.restore.value).isNull()
        memory.onPreviewCancelled()
        assertThat(memory.restore.value).isEqualTo(restore)
        memory.onRestored()
        assertThat(memory.restore.value).isNull()
    }

    @Test
    fun `sending from the preview forgets the sheet and resets the composer mode`() {
        val memory = TgAttachSheetMemory()
        memory.onOpenPreview(restore)
        memory.onPreviewSent()
        memory.onPreviewCancelled()
        assertThat(memory.restore.value).isNull()
        assertThat(memory.resetComposerMode.value).isTrue()
    }

    @Test
    fun `a preview not opened from the sheet changes nothing`() {
        val memory = TgAttachSheetMemory()
        memory.onPreviewCancelled()
        memory.onPreviewSent()
        assertThat(memory.restore.value).isNull()
        assertThat(memory.resetComposerMode.value).isFalse()
    }
}
