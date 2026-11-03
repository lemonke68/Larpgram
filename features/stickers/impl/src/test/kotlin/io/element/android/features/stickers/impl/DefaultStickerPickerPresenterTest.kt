/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.stickers.impl

import com.google.common.truth.Truth.assertThat
import io.element.android.features.stickers.impl.import.ImportResult
import io.element.android.features.stickers.impl.import.StickerPackImporter
import io.element.android.libraries.imagepacks.api.ImagePack
import io.element.android.libraries.imagepacks.api.ImagePackId
import io.element.android.libraries.imagepacks.api.ImagePackSource
import io.element.android.libraries.matrix.test.FakeMatrixClient
import io.element.android.libraries.matrix.test.room.FakeJoinedRoom
import io.element.android.tests.testutils.consumeItemsUntilPredicate
import io.element.android.tests.testutils.test
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DefaultStickerPickerPresenterTest {
    @Test
    fun `import replaces existing pack and its visible stickers`() = runTest {
        val old = pack("Old")
        val updated = pack("Updated")
        val source = mockk<ImagePackSource>()
        coEvery { source.getSavedPacks() } returns listOf(old)
        coEvery { source.getUserPacks() } returns emptyList()
        coEvery { source.getAllPacks() } returns listOf(old)
        val importer = mockk<StickerPackImporter>()
        coEvery { importer.import(any()) } returns ImportResult.Success(updated, 0)
        val recent = mockk<RecentStickersStore>()
        coEvery { recent.getRecent() } returns emptyList()
        val presenter = DefaultStickerPickerPresenter(source, FakeJoinedRoom(), FakeMatrixClient(), importer, recent)
        presenter.test {
            val loaded = consumeItemsUntilPredicate { !it.isLoading }.last()
            loaded.eventSink(StickerPickerEvents.ImportPack("pack"))
            val imported = consumeItemsUntilPredicate { it.importState is ImportState.Done }.last()
            assertThat(imported.packs).containsExactly(updated)
            assertThat(imported.selectedPackIndex).isEqualTo(0)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun pack(name: String): ImagePack = mockk {
        every { id } returns ImagePackId.Saved("same-id")
        every { displayName } returns name
        every { stickers } returns listOf(mockk(relaxed = true))
    }
}
