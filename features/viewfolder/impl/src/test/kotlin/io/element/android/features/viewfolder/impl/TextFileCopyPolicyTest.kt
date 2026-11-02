/*
 * Copyright (c) 2026 Larpgram.
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */
package io.element.android.features.viewfolder.impl

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.androidutils.clipboard.FakeClipboardHelper
import io.element.android.libraries.androidutils.toast.FakeToastHelper
import kotlinx.coroutines.test.runTest
import org.junit.Test

class TextFileCopyPolicyTest {
    @Test
    fun `a stale visible row cannot overwrite the clipboard when the source becomes protected or unavailable`() = runTest {
        val clipboard = FakeClipboardHelper().apply { clipboardContents = "Previous clipboard" }
        val toasts = FakeToastHelper()
        val viewer = DefaultTextFileViewer(clipboard, toasts)
        viewer.copyLine("Protected contents") { false }
        viewer.copyLine("Protected contents") { error("offline") }
        assertThat(clipboard.clipboardContents).isEqualTo("Previous clipboard")
        assertThat(toasts.shownToasts).isEmpty()
        viewer.copyLine("Allowed contents") { true }
        assertThat(clipboard.clipboardContents).isEqualTo("Allowed contents")
        assertThat(toasts.shownToasts).hasSize(1)
    }
}
