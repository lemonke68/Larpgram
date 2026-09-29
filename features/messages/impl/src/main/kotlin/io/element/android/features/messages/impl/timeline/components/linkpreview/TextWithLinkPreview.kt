/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components.linkpreview

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.dp
import io.element.android.features.messages.impl.timeline.components.event.TimelineItemTextView
import io.element.android.features.messages.impl.timeline.components.layout.ContentAvoidingLayoutData
import io.element.android.features.messages.impl.timeline.di.LocalTimelineItemPresenterFactories
import io.element.android.features.messages.impl.timeline.di.rememberPresenter
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemTextBasedContent
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemTextContent
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.designsystem.utils.LocalUiTestMode
import io.element.android.libraries.linkpreview.api.LinkPreviewUrls
import io.element.android.wysiwyg.link.Link

/**
 * Текст сообщения и, если в нём есть ссылка и сервер отдал превью, карточка под ним. Пока превью
 * нет — обычный текст, время встаёт по последней строке. С карточкой время уходит под неё:
 * ленте сообщаем размер всего блока как «последней строки».
 */
@Composable
internal fun TextWithLinkPreview(
    content: TimelineItemTextBasedContent,
    onLinkClick: (Link) -> Unit,
    onLinkLongClick: (Link) -> Unit,
    onContentLayoutChange: (ContentAvoidingLayoutData) -> Unit,
) {
    val factories = LocalTimelineItemPresenterFactories.current
    val hasUrl = remember(content) {
        content is TimelineItemTextContent && LinkPreviewUrls.firstPreviewableUrl(content.plainText) != null
    }
    val enabled = hasUrl &&
        !LocalUiTestMode.current &&
        !LocalInspectionMode.current &&
        factories.has(TimelineItemTextContent::class)
    val preview = if (enabled) {
        val presenter: Presenter<LinkPreviewState> = factories.rememberPresenter(content as TimelineItemTextContent)
        presenter.present().preview
    } else {
        null
    }
    if (preview == null) {
        TimelineItemTextView(
            content = content,
            onLinkClick = onLinkClick,
            onLinkLongClick = onLinkLongClick,
            onContentLayoutChange = onContentLayoutChange,
        )
    } else {
        Column(
            modifier = Modifier.onSizeChanged { size ->
                onContentLayoutChange(
                    ContentAvoidingLayoutData(
                        contentWidth = size.width,
                        contentHeight = size.height,
                        nonOverlappingContentWidth = size.width,
                        nonOverlappingContentHeight = size.height,
                    )
                )
            },
        ) {
            TimelineItemTextView(
                content = content,
                onLinkClick = onLinkClick,
                onLinkLongClick = onLinkLongClick,
            )
            TgLinkPreviewCard(
                preview = preview,
                onClick = { onLinkClick(Link(preview.url, preview.url)) },
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}
