/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.gifs.impl

import io.element.android.libraries.channelcomments.ChannelPostMirror
import io.element.android.libraries.matrix.test.FakeMatrixClient
import io.element.android.libraries.matrix.test.room.FakeJoinedRoom
import io.element.android.tests.testutils.consumeItemsUntilPredicate
import io.element.android.tests.testutils.test
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DefaultGifPickerPresenterTest {
    @Test
    fun `successful gif send mirrors its channel post`() = runTest {
        mockkObject(ChannelPostMirror)
        try {
            coEvery { ChannelPostMirror.myPostIds(any(), any()) } returns emptySet()
            coEvery { ChannelPostMirror.mirrorLastPost(any(), any(), any(), any(), any()) } returns Unit
            val repository = mockk<GifRepository>()
            coEvery { repository.search(any(), any()) } returns Result.success(GifPage(emptyList(), null))
            val sender = mockk<GifSender>()
            coEvery { sender.send(any(), any()) } returns Result.success(Unit)
            val recent = mockk<RecentGifsStore>(relaxed = true)
            coEvery { recent.getRecent() } returns emptyList()
            val presenter = DefaultGifPickerPresenter(repository, sender, FakeJoinedRoom(), FakeMatrixClient(), recent)
            presenter.test {
                val loaded = consumeItemsUntilPredicate { !it.isLoading }.last()
                loaded.eventSink(GifPickerEvents.SendGif(Gif("same", "gif", "http://gif", "http://preview", 10, 10, null)))
                runCurrent()
                coVerify(exactly = 1) { ChannelPostMirror.mirrorLastPost(any(), any(), any(), any(), any()) }
                cancelAndIgnoreRemainingEvents()
            }
        } finally {
            unmockkObject(ChannelPostMirror)
        }
    }
}
