/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components.linkpreview

import com.google.common.truth.Truth.assertThat
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemTextContent
import io.element.android.libraries.linkpreview.test.FakeLinkPreviewService
import io.element.android.tests.testutils.test
import kotlinx.coroutines.test.runTest
import org.junit.Test

class LinkPreviewPresenterTest {
    @Test
    fun `the first link of the message is previewed`() = runTest {
        val asked = mutableListOf<String>()
        val presenter = LinkPreviewPresenter(
            linkPreviewService = FakeLinkPreviewService { url ->
                asked += url
                aLinkPreview()
            },
            content = aTextContent("look https://github.com/lemonke68/Larpgram and https://b.org"),
        )
        presenter.test {
            assertThat(awaitItem().preview).isNull()
            assertThat(awaitItem().preview).isEqualTo(aLinkPreview())
            assertThat(asked).containsExactly("https://github.com/lemonke68/Larpgram")
        }
    }

    @Test
    fun `text without a link asks nothing`() = runTest {
        var asked = false
        val presenter = LinkPreviewPresenter(
            linkPreviewService = FakeLinkPreviewService {
                asked = true
                null
            },
            content = aTextContent("no links here"),
        )
        presenter.test {
            assertThat(awaitItem().preview).isNull()
            expectNoEvents()
        }
        assertThat(asked).isFalse()
    }

    private fun aTextContent(body: String) = TimelineItemTextContent(
        body = body,
        htmlDocument = null,
        formattedBody = body,
        isEdited = false,
    )
}
