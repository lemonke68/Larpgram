/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.linkpreview.test

import io.element.android.libraries.linkpreview.api.LinkPreview
import io.element.android.libraries.linkpreview.api.LinkPreviewService

class FakeLinkPreviewService(
    private val previewResult: (String) -> LinkPreview? = { null },
) : LinkPreviewService {
    override suspend fun preview(url: String): LinkPreview? = previewResult(url)
}
