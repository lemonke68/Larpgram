/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.attachments.tgattach

import android.net.Uri
import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.channelcomments.ChannelPostMirror
import io.element.android.libraries.designsystem.utils.snackbar.SnackbarDispatcher
import io.element.android.libraries.matrix.test.FakeMatrixClient
import io.element.android.libraries.matrix.test.room.FakeJoinedRoom
import io.element.android.libraries.mediaupload.api.MediaOptimizationConfig
import io.element.android.libraries.mediaupload.api.MediaUploadInfo
import io.element.android.libraries.mediaupload.test.FakeMediaSender
import io.element.android.tests.testutils.robolectric.RobolectricTest
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import kotlinx.coroutines.test.runTest
import org.junit.Test

class TgQuickMediaSendTest : RobolectricTest() {
    @Test
    fun `every album chunk gets its own mirror even if a later chunk fails`() = runTest {
        mockkObject(ChannelPostMirror)
        try {
            coEvery { ChannelPostMirror.myPostIds(any(), any()) } returns emptySet()
            coEvery { ChannelPostMirror.mirrorLastPost(any(), any(), any(), any(), any()) } returns Unit
            var sent = 0
            val sender = FakeMediaSender(
                preProcessMediaResult = { Result.success(mockk<MediaUploadInfo.Image>()) },
                sendGalleryResult = {
                    sent++
                    Result.success(Unit)
                },
                sendPreProcessedMediaResult = { Result.failure(IllegalStateException("last chunk failed")) },
                cleanUpResult = {},
            )
            val result = sendGalleryMediaNow(
                media = List(21) { GalleryMedia(it.toLong(), Uri.EMPTY, "image/jpeg", false, 0) },
                caption = "Caption",
                inReplyToEventId = null,
                mediaOptimizationConfig = mockk<MediaOptimizationConfig>(),
                mediaSender = sender,
                room = FakeJoinedRoom(),
                matrixClient = FakeMatrixClient(),
                snackbarDispatcher = SnackbarDispatcher(),
            )
            assertThat(result).isFalse()
            assertThat(sent).isEqualTo(2)
            coVerify(exactly = 2) { ChannelPostMirror.mirrorLastPost(any(), any(), any(), any(), any()) }
        } finally {
            unmockkObject(ChannelPostMirror)
        }
    }
}
