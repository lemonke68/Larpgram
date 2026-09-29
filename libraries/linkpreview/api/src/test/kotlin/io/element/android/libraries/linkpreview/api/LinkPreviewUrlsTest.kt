/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.linkpreview.api

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LinkPreviewUrlsTest {
    @Test
    fun `the first http link is taken without trailing punctuation`() {
        assertThat(LinkPreviewUrls.firstPreviewableUrl("see https://github.com/lemonke68/Larpgram. and http://b.org"))
            .isEqualTo("https://github.com/lemonke68/Larpgram")
    }

    @Test
    fun `a closing bracket that belongs to the link is kept`() {
        assertThat(LinkPreviewUrls.firstPreviewableUrl("(https://en.wikipedia.org/wiki/Kotlin_(language))"))
            .isEqualTo("https://en.wikipedia.org/wiki/Kotlin_(language)")
    }

    @Test
    fun `matrix permalinks and hosts without a dot get no card`() {
        assertThat(LinkPreviewUrls.firstPreviewableUrl("https://matrix.to/#/@a:b.org https://localhost/x")).isNull()
    }

    @Test
    fun `text without links has nothing to preview`() {
        assertThat(LinkPreviewUrls.firstPreviewableUrl("just text, example.com")).isNull()
    }
}
