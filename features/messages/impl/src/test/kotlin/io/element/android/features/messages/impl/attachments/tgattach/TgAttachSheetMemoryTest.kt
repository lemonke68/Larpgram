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
    fun `caption follows the user from the sheet through the preview and back`() {
        val memory = TgAttachSheetMemory()
        memory.onOpenPreview(restore)
        assertThat(memory.previewCaption).isEqualTo("hi")
        memory.onPreviewCancelled("edited")
        assertThat(memory.restore.value?.caption).isEqualTo("edited")
        assertThat(memory.previewCaption).isNull()
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
    fun `preview preserves the chosen quality when returning to the sheet`() {
        val memory = TgAttachSheetMemory()
        memory.onOpenPreview(restore.copy(highQuality = true))
        assertThat(memory.previewHighQuality).isTrue()
        memory.onPreviewCancelled("new caption")
        assertThat(memory.restore.value?.highQuality).isTrue()
        assertThat(memory.restore.value?.caption).isEqualTo("new caption")
    }

    @Test
    fun `quality changed in the preview follows the user back to the sheet`() {
        val memory = TgAttachSheetMemory()
        memory.onOpenPreview(restore)
        memory.onPreviewCancelled("edited", highQuality = true)
        assertThat(memory.restore.value?.highQuality).isTrue()
        assertThat(memory.restore.value?.caption).isEqualTo("edited")
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
