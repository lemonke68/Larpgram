/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components.linkpreview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import io.element.android.features.messages.impl.timeline.di.TimelineItemPresenterFactory
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemTextContent
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.linkpreview.api.LinkPreview
import io.element.android.libraries.linkpreview.api.LinkPreviewService
import io.element.android.libraries.linkpreview.api.LinkPreviewUrls

/** Карточка первой ссылки текстового сообщения; null — ссылки нет или превью не пришло. */
data class LinkPreviewState(
    val preview: LinkPreview?,
)

@AssistedInject
class LinkPreviewPresenter(
    private val linkPreviewService: LinkPreviewService,
    @Assisted private val content: TimelineItemTextContent,
) : Presenter<LinkPreviewState> {
    @AssistedFactory
    fun interface Factory : TimelineItemPresenterFactory<TimelineItemTextContent, LinkPreviewState> {
        override fun create(content: TimelineItemTextContent): LinkPreviewPresenter
    }

    @Composable
    override fun present(): LinkPreviewState {
        val preview by produceState<LinkPreview?>(null, content.plainText) {
            value = LinkPreviewUrls.firstPreviewableUrl(content.plainText)?.let { linkPreviewService.preview(it) }
        }
        return LinkPreviewState(preview = preview)
    }
}
