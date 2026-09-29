/*
 * Copyright (c) 2026 Larpgram.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components.linkpreview

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import io.element.android.compound.theme.ElementTheme
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.LocalOutgoingBubbleContentColor
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.linkpreview.api.LinkPreview
import io.element.android.libraries.matrix.api.media.MediaSource
import io.element.android.libraries.matrix.ui.media.MediaRequestData

/**
 * Карточка ссылки под текстом, как веб-превью TG (`ChatMessageCell`, webpage): полупрозрачный
 * блок с цветной чертой слева (тот же стиль, что у цитаты ответа), сайт акцентом, жирный заголовок,
 * описание. Широкая картинка — во всю ширину блока, маленькая — квадратиком справа.
 */
@Composable
internal fun TgLinkPreviewCard(
    preview: LinkPreview,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = LocalOutgoingBubbleContentColor.current ?: ElementTheme.colors.textActionAccent
    val textColor = LocalOutgoingBubbleContentColor.current ?: ElementTheme.colors.textPrimary
    val largeImage = preview.imageMxc != null && (preview.imageWidth ?: 0) >= LARGE_IMAGE_MIN_WIDTH
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(accent.copy(alpha = 0.14f))
            .drawBehind { drawRect(color = accent, size = size.copy(width = 3.dp.toPx())) }
            .clickable(onClick = onClick)
            .padding(start = 10.dp, top = 6.dp, end = 8.dp, bottom = 8.dp),
    ) {
        Row {
            Column(modifier = Modifier.weight(1f, fill = false)) {
                preview.siteName?.let {
                    Text(
                        text = it,
                        color = accent,
                        style = ElementTheme.typography.fontBodyMdMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                preview.title?.let {
                    Text(
                        text = it,
                        color = textColor,
                        style = ElementTheme.typography.fontBodyMdMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                preview.description?.let {
                    Text(
                        text = it,
                        color = textColor,
                        style = ElementTheme.typography.fontBodyMdRegular,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (preview.imageMxc != null && !largeImage) {
                Spacer(Modifier.width(8.dp))
                AsyncImage(
                    model = MediaRequestData(MediaSource(preview.imageMxc!!), MediaRequestData.Kind.Thumbnail(THUMB_PX, THUMB_PX)),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(6.dp)),
                )
            }
        }
        if (largeImage) {
            val ratio = (preview.imageWidth!!.toFloat() / (preview.imageHeight ?: preview.imageWidth!!)).coerceIn(MIN_RATIO, MAX_RATIO)
            AsyncImage(
                model = MediaRequestData(MediaSource(preview.imageMxc!!), MediaRequestData.Kind.Content),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .padding(top = 6.dp)
                    .fillMaxWidth()
                    .heightIn(max = 240.dp)
                    .aspectRatio(ratio)
                    .clip(RoundedCornerShape(6.dp)),
            )
        }
    }
}

private const val LARGE_IMAGE_MIN_WIDTH = 400
private const val THUMB_PX = 144L
private const val MIN_RATIO = 0.75f
private const val MAX_RATIO = 2.2f

internal class LinkPreviewPreviewParam : PreviewParameterProvider<LinkPreview> {
    override val values = sequenceOf(
        aLinkPreview(),
        aLinkPreview(imageWidth = 120, imageHeight = 120),
        aLinkPreview(imageMxc = null, description = null),
    )
}

internal fun aLinkPreview(
    imageMxc: String? = "mxc://mango-kokos.ru/abc",
    imageWidth: Int? = 1200,
    imageHeight: Int? = 600,
    description: String? = "Element messenger fork, made to look and feel more like Telegram.",
) = LinkPreview(
    url = "https://github.com/lemonke68/Larpgram",
    siteName = "GitHub",
    title = "GitHub - lemonke68/Larpgram",
    description = description,
    imageMxc = imageMxc,
    imageWidth = imageWidth,
    imageHeight = imageHeight,
)

@PreviewsDayNight
@Composable
internal fun TgLinkPreviewCardPreview(@PreviewParameter(LinkPreviewPreviewParam::class) preview: LinkPreview) = ElementPreview {
    TgLinkPreviewCard(preview = preview, onClick = {})
}
